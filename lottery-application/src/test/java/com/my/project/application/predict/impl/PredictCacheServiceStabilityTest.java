package com.my.project.application.predict.impl;

import com.my.project.application.predict.cache.PredictionCandidateCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;

class PredictCacheServiceStabilityTest {
    @AfterEach void clear() { PredictionCandidateCache.getInstance().clear(); }

    @Test void evictedKeysAreSkippedInsteadOfDecompressingNull() {
        var cache = PredictionCandidateCache.getInstance();
        cache.clear();
        cache.getScoreIndex().computeIfAbsent(.5, ignored -> ConcurrentHashMap.newKeySet()).add("evicted");
        var service = new PredictCacheServiceImpl();
        assertTrue(service.queryCache(0, List.of()).getCacheDatas().isEmpty());
        assertTrue(service.queryCache(0, List.of("evicted")).getCacheDatas().isEmpty());
    }
}
