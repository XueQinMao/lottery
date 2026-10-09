package com.my.project.application.feature;

import com.my.project.llm.bo.*;
import com.my.project.application.config.AnalysisCacheProperties;
import com.my.project.application.feature.cache.LotteryAnalysisMultiLevelCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PatternAnalysisCacheTest {
    @TempDir Path directory;

    @Test
    void partialFailuresDoNotPersistButSuccessfulReportsCanBeLoadedFromDisk() throws Exception {
        var props = new AnalysisCacheProperties(); props.setDiskDir(directory.toString());
        var cache = new LotteryAnalysisMultiLevelCache(props); cache.init();
        AtomicInteger calls = new AtomicInteger();
        var failed = LotteryAnalysisRespBo.builder().featureAnalysis(List.of(PatternFeatureAnalysisBo.builder()
            .source("llm-pattern").status("UNAVAILABLE").build())).build();
        var success = LotteryAnalysisRespBo.builder().featureAnalysis(List.of(PatternFeatureAnalysisBo.builder()
            .source("llm-pattern").status("OK").build())).build();
        assertSame(failed, cache.get("pattern-v2:P1", key -> { calls.incrementAndGet(); return failed; }));
        try (var paths = java.nio.file.Files.list(directory)) { assertEquals(0, paths.count()); }
        assertSame(success, cache.get("pattern-v2:P1", key -> { calls.incrementAndGet(); return success; }));
        cache.get("pattern-v2:P1", key -> { fail("成功报告应命中缓存"); return null; });
        assertEquals(2, calls.get());
        var reopened = new LotteryAnalysisMultiLevelCache(props); reopened.init();
        var restored = reopened.get("pattern-v2:P1", key -> { fail("应读取磁盘缓存"); return null; });
        assertEquals("OK", restored.getFeatureAnalysis().getFirst().getStatus());
    }
}
