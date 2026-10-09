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
import java.nio.file.attribute.FileTime;

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
            .featureAnalysis(java.util.List.of(feature)).build(), 100);
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
        assertSame(view, reports.saveRecommendation(view, 2, true));
        assertTrue(view.getFileName().matches("adjust_\\d{14}_c2_topNtrue\\.json"));
        assertTrue(Files.isRegularFile(directory.resolve("adjust").resolve(view.getFileName())));
        assertEquals("saved recommendation", reports.loadAdjustHistory(view.getFileName()).getConclusion());
        verify(metadata).record(PredictFileRecordType.RECOMMEND, view.getFileName(),
            directory.resolve("adjust").resolve(view.getFileName()).toFile().getAbsolutePath());
    }

    @Test
    void keepsNewestFirstLimitAndRejectsTraversalBeforeReadingMetadata() throws Exception {
        var reports = reports();
        var folder = Files.createDirectories(directory.resolve("adjust"));
        var old = Files.writeString(folder.resolve("adjust_old.json"), "{}");
        var recent = Files.writeString(folder.resolve("adjust_recent.json"), "{}");
        Files.setLastModifiedTime(old, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(recent, FileTime.fromMillis(2000));
        Files.writeString(folder.resolve("unrelated.json"), "{}");
        var history = reports.listAdjustHistory(0);
        assertEquals(1, history.size());
        assertEquals("adjust_recent.json", history.getFirst().getFileName());
        assertThrows(IllegalArgumentException.class, () -> reports.loadAdjustHistory("../adjust_old.json"));
        assertThrows(IllegalArgumentException.class, () -> reports.loadAnalysisHistory("..\\analysis_old.json"));
        verifyNoInteractions(metadata);
    }
}
