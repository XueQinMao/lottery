package com.my.project.application.feature;

import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.report.ReportFileIndexService;
import com.my.project.application.report.PredictFileRecordType;
import com.my.project.application.report.LotteryReportService;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternReportPersistenceTest {
    @TempDir Path directory;
    private final ReportFileIndexService metadata = mock(ReportFileIndexService.class);

    private LotteryReportService reports() {
        var properties = new LotteryModelProperties();
        properties.setPath(directory.toString());
        return new LotteryReportService(properties, metadata);
    }

    @Test
    void analysisFileOmitsObservationsAndKeepsPredictionAndFailureDetails() throws Exception {
        var reports = reports();
        var feature = com.my.project.llm.bo.PatternFeatureAnalysisBo.builder().code("oddEven")
            .status("UNAVAILABLE").failureReason("HTTP 503")
            .observations(java.util.List.of(com.my.project.llm.bo.PatternFeatureAnalysisBo.Observation.builder()
                .value("3:3").build())).likelyValues(java.util.List.of()).build();
        reports.saveAnalysis(com.my.project.llm.bo.LotteryAnalysisRespBo.builder()
            .featureAnalysis(java.util.List.of(feature)).build(), "test-analysis");
        try (var files = Files.list(directory.resolve("analysis"))) {
            String json = Files.readString(files.findFirst().orElseThrow());
            assertFalse(json.contains("\"observations\""));
            assertFalse(json.contains("不提供下期推演"));
            assertTrue(json.contains("\"failureReason\":\"HTTP 503\""));
            assertTrue(json.contains("\"status\":\"UNAVAILABLE\""));
        }
        assertNotNull(feature.getObservations());
    }

    @Test
    void keepsFileFormatMetadataAndBestEffortSaveWhenMetadataFails() {
        var reports = reports();
        var view = new LotteryAdjustViewBo();
        view.setConclusion("saved recommendation");
        doThrow(new IllegalStateException("metadata unavailable")).when(metadata)
            .record(eq(PredictFileRecordType.RECOMMEND), anyString(), anyString());
        assertSame(view, reports.saveRecommendation(view));
        assertTrue(view.getFileName().matches("adjust_\\d{14}_[0-9a-f-]{36}\\.json"));
        assertTrue(Files.isRegularFile(directory.resolve("adjust").resolve(view.getFileName())));
        assertEquals("saved recommendation", reports.loadAdjustHistory(view.getFileName()).getConclusion());
        verify(metadata).record(PredictFileRecordType.RECOMMEND, view.getFileName(),
            directory.resolve("adjust").resolve(view.getFileName()).toFile().getAbsolutePath());
    }

    @Test
    void readsLegacyRecommendationsAndRejectsTraversal() throws Exception {
        var reports = reports();
        var folder = Files.createDirectories(directory.resolve("adjust"));
        String name = "adjust_20261118120000_c2_topNtrue.json";
        Files.writeString(folder.resolve(name), """
            {"finalRecommendation":{"singleTickets":[{"redBalls":[1,2,3,4,5,6],"blueBall":9}]},
             "finalSingleHits":[{"mainHitCount":1}]}
            """);
        var legacy = reports.loadAdjustHistory(name);
        assertEquals(9, legacy.getFinalRecommendation().getSingleTickets().getFirst().getBlueBall());
        assertEquals(1, legacy.getFinalSingleHits().getFirst().getMainHitCount());
        assertThrows(IllegalArgumentException.class, () -> reports.loadAdjustHistory("../adjust_old.json"));
        assertThrows(IllegalArgumentException.class, () -> reports.loadAnalysisHistory("..\\analysis_old.json"));
        verifyNoInteractions(metadata);
    }

    @Test
    void findsOnlySamePeriodRecommendationsPublishedBeforeTheDraw() throws Exception {
        var folder = Files.createDirectories(directory.resolve("adjust"));
        String olderFile = "adjust_20261119180000_c2_topNfalse.json";
        Files.writeString(folder.resolve(olderFile), """
            {"targetPeriod":"2026132"}
            """);
        Files.writeString(folder.resolve("adjust_20261119180900.json"), """
            {"targetPeriod":"2026133"}
            """);
        Files.writeString(folder.resolve("adjust_20261119190000.json"), """
            {"targetPeriod":"2026132"}
            """);
        Files.writeString(folder.resolve("adjust_20261119180300.json"), "{}");

        var zone = ZoneId.systemDefault();
        Instant cutoff = LocalDateTime.of(2026, 11, 19, 19, 0).atZone(zone).toInstant();
        var service = reports();
        var results = service.findRecommendations("2026132", cutoff);
        assertEquals(List.of(olderFile), results.stream().map(LotteryAdjustViewBo::getFileName).toList());
        assertEquals(LocalDateTime.of(2026, 11, 19, 18, 0).atZone(zone).toInstant(), results.getFirst().getGeneratedAt());
        assertTrue(service.findRecommendation(olderFile, "2026132", cutoff).isPresent());
        assertTrue(service.findRecommendation(olderFile, "2026133", cutoff).isEmpty());
        assertTrue(service.findRecommendation("adjust_20261119190000.json", "2026132", cutoff).isEmpty());
    }
}
