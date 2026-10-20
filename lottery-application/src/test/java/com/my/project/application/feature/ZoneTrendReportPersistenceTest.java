package com.my.project.application.feature;

import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.report.ReportFileIndexService;
import com.my.project.llm.bo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoneTrendReportPersistenceTest {
    @TempDir Path directory;

    @Test void savesAndLoadsVisionReportWithFactsAndImageMetadataWithoutBase64() throws Exception {
        var config = new LotteryModelProperties();
        config.setPath(directory.toString());
        var reports = new LotteryReportService(config, mock(ReportFileIndexService.class));
        var input = new ZoneTrendSnapshot("2026002", List.of("2026001", "2026002"),
            List.of(new ZoneTrendSnapshot.Series("zone1", List.of(1,2))), List.of());
        var inference = new ZoneTrendInference(List.of(), "走势测试", List.of(
            new ZoneTrendInference.Candidate("2:2:2", "延续假设", "可能中断", List.of("2026002"))), "反向证据");
        var vision = new ZoneTrendAnalysisBo("vision-test", "2026-11-18T00:00:00Z", "zone-trend-test.json", input,
            List.of(new ZoneTrendAnalysisBo.ImageInfo("counts", "hash",1200,880)), inference);
        reports.saveAnalysis(LotteryAnalysisRespBo.builder().zoneTrendAnalysis(vision).build(), "zone-trend-test");
        var read = reports.loadAnalysisHistory("zone-trend-test.json");
        assertEquals(vision, read.getZoneTrendAnalysis());
        assertFalse(Files.readString(directory.resolve("analysis/zone-trend-test.json")).contains("data:image"));
    }
}
