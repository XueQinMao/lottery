package com.my.project.application.feature;

import com.my.project.application.report.LotteryReportService;

import com.my.project.llm.bo.*;
import com.my.project.llm.service.*;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.feature.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.application.feature.impl.LotteryFeatureAnalysisServiceImpl;
import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.report.ReportFileIndexService;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.application.support.analyse.KillBallUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternAnalysisFlowTest {
    @TempDir Path directory;
    private final HistoryRecordService history = mock(HistoryRecordService.class);
    private final PatternFeatureAnalysisSupport support = mock(PatternFeatureAnalysisSupport.class);
    private final LotteryAnalysisMultiLevelCache cache = mock(LotteryAnalysisMultiLevelCache.class);
    private final ReportFileIndexService files = mock(ReportFileIndexService.class);

    private LotteryFeatureAnalysisServiceImpl service() {
        var config = new LotteryModelProperties(); config.setPath(directory.toString());
        var service = spy(new LotteryFeatureAnalysisServiceImpl(history, support,
            mock(LotteryAdjustService.class), mock(PredictCacheService.class), mock(SmartSelectService.class),
            cache, new LotteryReportService(config, files)));
        ReflectionTestUtils.setField(service, "analysisEngine", "llm-pattern");
        return service;
    }

    @Test
    void locksHistoryAnchorReturnsLatestThirtyAndSeparatesEngineCacheKeys() {
        var service = service();
        var records = records();
        when(history.getLatestRecords(1)).thenReturn(List.of(records.getFirst()));
        when(history.getRecordsEndingAt("P120", 100000)).thenReturn(records);
        when(support.analyze(records)).thenReturn(items("OK"));
        when(cache.get(anyString(), any())).thenAnswer(call -> {
            Function<String, LotteryAnalysisRespBo> loader = call.getArgument(1);
            return loader.apply(call.getArgument(0));
        });
        try (var kill = mockStatic(KillBallUtils.class); var cold = mockStatic(ColdHotAnalysisUtils.class)) {
            kill.when(() -> KillBallUtils.calculate(records)).thenReturn(KillNumberResultBo.builder().build());
            cold.when(() -> ColdHotAnalysisUtils.calculate(records)).thenReturn(ColdHotAnalysisBo.builder().build());
            var report = service.analyzeLatest(30);
            assertEquals(30, report.getRecentDrawRecords().size());
            assertEquals("P91", report.getRecentDrawRecords().getFirst().getPeriod());
            assertEquals("P120", report.getRecentDrawRecords().getLast().getPeriod());
            assertEquals(18, report.getFeatureAnalysis().size());
            verify(service, never()).forecastFeaturesByIndex(anyList(), anyList());
            String endpoint = cn.hutool.crypto.digest.DigestUtil
                .md5Hex("https://dashscope.aliyuncs.com/compatible-mode").substring(0, 12);
            verify(cache).get(eq("feature.analysis.cache:pattern-v5:llm-pattern:bailian:qwen-flash:" + endpoint + ":P120:30"), any());
            verify(history).getRecordsEndingAt("P120", 100000);

            ReflectionTestUtils.setField(service, "analysisEngine", "java");
            var forecast = FeatureForecastBo.builder().build();
            doReturn(forecast).when(service).forecastFeaturesByIndex(anyList(), eq(records));
            when(support.adaptJava(forecast, records)).thenReturn(items("OK"));
            service.analyzeLatest(30);
            verify(cache).get(eq("feature.analysis.cache:pattern-v5:java:P120:30"), any());
        }
    }

    @Test
    void changingBailianModelOrEndpointDoesNotReuseAnotherModelsCache() {
        var service = service();
        when(history.getLatestRecords(1)).thenReturn(List.of(records().getFirst()));
        when(cache.get(anyString(), any())).thenReturn(LotteryAnalysisRespBo.builder().build());
        service.analyzeLatest(100);
        ReflectionTestUtils.setField(service, "analysisModel", "qwen-plus");
        service.analyzeLatest(100);
        ReflectionTestUtils.setField(service, "analysisBaseUrl", "https://dashscope-intl.aliyuncs.com/compatible-mode");
        service.analyzeLatest(100);
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cache, times(3)).get(keys.capture(), any());
        assertEquals(3, keys.getAllValues().stream().distinct().count());
        assertTrue(keys.getAllValues().stream().allMatch(key -> key.endsWith(":P120:30")),
            "大模型分支忽略旧调用方的100期窗口，始终以30期缓存");
        verifyNoInteractions(support);
    }

    @Test
    void rejectsAllFailedFeaturesRatherThanReturningInventedPredictions() {
        var service = service(); var records = records();
        when(history.getLatestRecords(1)).thenReturn(List.of(records.getFirst()));
        when(history.getRecordsEndingAt("P120", 100000)).thenReturn(records);
        when(support.analyze(records)).thenReturn(items("UNAVAILABLE"));
        assertThrows(IllegalStateException.class, () -> service.doAnalyze(100));
    }

    @Test
    void loadsOldFilesWithoutFabricatingRecentHistory() throws Exception {
        var service = service();
        Path dir = java.nio.file.Files.createDirectories(directory.resolve("analysis"));
        java.nio.file.Files.writeString(dir.resolve("analysis_old.json"),
            "{\"featureForecast\":{\"oddEven\":{\"value\":\"3:3\",\"reason\":\"旧指数\"}}}");
        var report = new LotteryReportService(modelProperties(), files)
            .loadAnalysisHistory("analysis_old.json");
        assertEquals("legacy", report.getFeatureAnalysis().getFirst().getSource());
        assertEquals(List.of("3:3"), report.getFeatureAnalysis().getFirst().getLikelyValues());
        assertNull(report.getRecentDrawRecords());
    }

    private LotteryModelProperties modelProperties() {
        var properties = new LotteryModelProperties();
        properties.setPath(directory.toString());
        return properties;
    }

    private List<HistoryRecord> records() {
        return IntStream.range(0, 120).mapToObj(i -> HistoryRecord.builder().period("P" + (120 - i))
            .num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(1).build()).toList();
    }

    private List<PatternFeatureAnalysisBo> items(String status) {
        return IntStream.range(0, 18).mapToObj(i -> PatternFeatureAnalysisBo.builder().code("F" + i)
            .source("llm-pattern").status(status).likelyValues(List.of()).unlikelyValues(List.of()).build()).toList();
    }
}
