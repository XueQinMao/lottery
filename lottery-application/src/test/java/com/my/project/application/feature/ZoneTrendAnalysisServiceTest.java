package com.my.project.application.feature;

import com.my.project.application.config.AnalysisCacheProperties;
import com.my.project.application.feature.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.application.feature.dto.ZoneTrendAnalysisCommand;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.support.DrawTrendCalculator;
import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.report.LotteryReportService;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.*;
import com.my.project.llm.service.ZoneTrendModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoneTrendAnalysisServiceTest {
    private final HistoryRecordService history = mock(HistoryRecordService.class);
    private final ZoneTrendModelService model = mock(ZoneTrendModelService.class);
    private final LotteryReportService reports = mock(LotteryReportService.class);
    private ZoneTrendAnalysisService service;
    private DrawTrendVo trends;
    private ZoneTrendSnapshot snapshot;
    private List<ZoneTrendImage> images;

    @BeforeEach void setup() throws Exception {
        var config = new AnalysisCacheProperties();
        config.setDiskEnabled(false);
        var cache = new LotteryAnalysisMultiLevelCache(config);
        cache.init();
        service = new ZoneTrendAnalysisService(history, model, cache, reports);
        trends = DrawTrendCalculator.calculate(IntStream.rangeClosed(1, 30).mapToObj(i -> HistoryRecord.builder()
            .period("2026%03d".formatted(31-i)).num1(1).num2(11).num3(12).num4(22).num5(23).num6(33).special(1).build()).toList(), 30);
        snapshot = snapshot(trends);
        images = new ArrayList<>();
        images.add(new ZoneTrendImage("counts", png(880)));
        for (int i=1; i<=4; i++) images.add(new ZoneTrendImage("ratios-"+i, png(1700)));
        when(history.analyzeDrawTrends(30, "2026030")).thenReturn(trends);
        when(model.modelIdentity()).thenReturn("test-provider|vision-test|4096");
        when(model.modelName()).thenReturn("vision-test");
        when(model.analyze(any(), any())).thenReturn(new ZoneTrendInference(List.of(), "test", List.of(), "test"));
    }

    @Test void anchorsFactsToUploadedCutoffAndCachesOnlySuccessfulReports() {
        var command = new ZoneTrendAnalysisCommand(snapshot, images);
        var report = service.analyze(command);
        assertEquals("2026030", report.getZoneTrendAnalysis().input().endPeriod());
        assertEquals(3, report.getZoneTrendAnalysis().input().counts().size());
        assertEquals(28, report.getZoneTrendAnalysis().input().ratios().size());
        assertEquals(5, report.getZoneTrendAnalysis().images().size());
        assertSame(report, service.analyze(command));
        verify(model).analyze(eq(snapshot), eq(images));
        verify(reports).saveAnalysis(eq(report), startsWith("zone-trend-v3-2026030-"));
        assertTrue(report.getZoneTrendAnalysis().fileName().endsWith(".json"));
    }

    @Test void rejectsMismatchedFactsMissingRatioPanelsAndWrongCutoffBeforeCallingModel() {
        var changed = new ArrayList<>(snapshot.counts());
        changed.set(0, new ZoneTrendSnapshot.Series("zone1", Collections.nCopies(30, 3)));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ZoneTrendAnalysisCommand(
            new ZoneTrendSnapshot(snapshot.endPeriod(), snapshot.periods(), changed, snapshot.ratios()), images)));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ZoneTrendAnalysisCommand(
            new ZoneTrendSnapshot(snapshot.endPeriod(), snapshot.periods(), snapshot.counts(), snapshot.ratios().subList(0,27)), images)));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ZoneTrendAnalysisCommand(
            new ZoneTrendSnapshot("2026999", snapshot.periods(), snapshot.counts(), snapshot.ratios()), images)));
        verify(model, never()).analyze(any(), any());
    }

    @Test void rejectsInvalidPngDuplicateImagesAndInsufficientHistory() {
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ZoneTrendAnalysisCommand(snapshot, List.of())));
        var bad = new ArrayList<>(images);
        bad.set(1, bad.getFirst());
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ZoneTrendAnalysisCommand(snapshot, bad)));
        bad.set(1, new ZoneTrendImage("ratios-1", "data:image/png;base64,aGVsbG8="));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ZoneTrendAnalysisCommand(snapshot, bad)));
        when(history.analyzeDrawTrends(30, "2026030")).thenReturn(DrawTrendCalculator.calculate(List.of(), 30));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ZoneTrendAnalysisCommand(snapshot, images)));
        verify(model, never()).analyze(any(), any());
    }

    @Test void failedModelCallIsNotSavedOrCachedAndCanBeRetried() {
        when(model.analyze(any(), any())).thenThrow(new IllegalStateException("provider unavailable"));
        var command = new ZoneTrendAnalysisCommand(snapshot, images);
        assertThrows(IllegalStateException.class, () -> service.analyze(command));
        assertThrows(IllegalStateException.class, () -> service.analyze(command));
        verify(model, times(2)).analyze(any(), any());
        verifyNoInteractions(reports);
    }

    static ZoneTrendSnapshot snapshot(DrawTrendVo trends) {
        var counts = trends.redFeatures().stream().filter(g -> g.code().equals("zones")).findFirst().orElseThrow();
        var ratios = trends.redFeatures().stream().filter(g -> g.code().equals("threeZone")).findFirst().orElseThrow();
        return new ZoneTrendSnapshot(trends.periods().getLast(), trends.periods(),
            counts.series().stream().map(s -> new ZoneTrendSnapshot.Series(s.code(),s.values())).toList(),
            ratios.series().stream().map(s -> new ZoneTrendSnapshot.Series(s.code(),s.values())).toList());
    }

    private static String png(int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1200, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
