package com.my.project.application.feature;

import com.my.project.application.event.AdjustCompleteEvent;
import com.my.project.application.feature.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.application.feature.dto.LlmAdjustmentCommand;
import com.my.project.application.feature.dto.RecommendationCommand;
import com.my.project.application.feature.impl.LotteryFeatureAnalysisServiceImpl;
import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.predict.vo.PredictCacheVo;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.support.FeatureForecastHitUtils;
import com.my.project.application.support.SpringContextUtils;
import com.my.project.llm.bo.*;
import com.my.project.llm.service.LotteryAdjustService;
import com.my.project.llm.service.LotteryAnalysisService;
import com.my.project.python.bo.ModelPredictOutputBo;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationFlowTest {
    private final LotteryAdjustService llm = mock(LotteryAdjustService.class);
    private final PredictCacheService cache = mock(PredictCacheService.class);
    private final SmartSelectService selection = mock(SmartSelectService.class);
    private final LotteryReportService files = mock(LotteryReportService.class);
    private final LotteryFeatureAnalysisServiceImpl service = spy(new LotteryFeatureAnalysisServiceImpl(
        mock(HistoryRecordService.class), mock(PatternFeatureAnalysisSupport.class),
        llm, cache, selection,
        mock(LotteryAnalysisMultiLevelCache.class), files));
    private final LotteryAnalysisRespBo report = LotteryAnalysisRespBo.builder()
        .featureAnalysis(List.of(PatternFeatureAnalysisBo.builder().code("oddEven").status("OK")
            .observations(List.of(PatternFeatureAnalysisBo.Observation.builder().value("3:3").build()))
            .likelyValues(List.of("3:3")).build()))
        .recentDrawRecords(List.of(LotteryAnalysisReqBo.DrawRecord.builder()
            .redBalls(List.of(1, 2, 3, 4, 5, 6)).blueBall(7).build())).build();

    @Test
    void featureRecommendationKeepsCountExplicitLastDrawAndPublishBeforeSave() {
        var explicit = List.of(2, 4, 6, 8, 10, 12);
        var input = LlmAdjustmentCommand.builder().count(3).lastDrawRedBalls(explicit)
            .lastDrawBlueBall(9).userRequirement("requirement").build();
        var request = execute(new RecommendationCommand.Feature(input), 3, false);
        assertEquals(3, request.getCount());
        assertEquals(explicit, request.getLastDrawRedBalls());
        assertEquals(9, request.getLastDrawBlueBall());
        assertEquals("requirement", request.getUserRequirement());
        assertTrue(request.getTickets().isEmpty());
    }

    @Test
    void cachedTopSelectionKeepsClampedArchiveCountAndDerivesLlmCountFromTickets() {
        var cached = new PredictCacheVo();
        cached.setCacheDatas(candidates());
        when(cache.queryCache(10, null)).thenReturn(cached);
        var request = execute(new RecommendationCommand.Cache(15, true, "cache"), 10, true);
        verify(cache).queryCache(10, null);
        verifyNoInteractions(selection);
        assertNull(request.getCount());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), request.getTickets().getFirst().getRedBalls());
        assertEquals(8, request.getTickets().getFirst().getBlueBall());
        assertEquals(7, request.getLastDrawBlueBall());
    }

    @Test
    void percentileSelectionKeepsTheLegacySamplingBranch() {
        when(selection.percentileSample(2)).thenReturn(candidates());
        execute(new RecommendationCommand.Cache(2, false, null), 2, false);
        verify(selection).percentileSample(2);
        verifyNoInteractions(cache);
    }

    private LotteryAdjustReqBo execute(RecommendationCommand command, int archiveCount, boolean topN) {
        doReturn(report).when(service).analyzeLatest(100);
        var response = new LotteryAdjustRespBo();
        var view = new LotteryAdjustViewBo();
        var context = mock(ApplicationContext.class);
        when(llm.adjust(any())).thenReturn(response);
        when(files.saveRecommendation(view, archiveCount, topN)).thenReturn(view);
        try (var spring = mockStatic(SpringContextUtils.class);
             var converter = mockStatic(FeatureForecastHitUtils.class)) {
            spring.when(SpringContextUtils::getApplicationContext).thenReturn(context);
            converter.when(() -> FeatureForecastHitUtils.toPatternView(response, report)).thenReturn(view);
            assertSame(view, service.recommend(command));
            var sequence = inOrder(llm, context, files);
            sequence.verify(llm).adjust(any());
            sequence.verify(context).publishEvent(any(AdjustCompleteEvent.class));
            sequence.verify(files).saveRecommendation(view, archiveCount, topN);
        }
        var captured = org.mockito.ArgumentCaptor.forClass(LotteryAdjustReqBo.class);
        verify(llm).adjust(captured.capture());
        assertFalse(captured.getValue().getAnalysisReportJson().contains("\"observations\""));
        assertTrue(captured.getValue().getAnalysisReportJson().contains("\"likelyValues\":[\"3:3\"]"));
        return captured.getValue();
    }

    private Map<String, ModelPredictOutputBo> candidates() {
        return Map.of("2026-10-15|1,2,3,4,5,6|8", new ModelPredictOutputBo());
    }
}
