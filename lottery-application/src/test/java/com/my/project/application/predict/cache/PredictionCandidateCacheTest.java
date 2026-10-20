package com.my.project.application.predict.cache;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PredictionCandidateCacheTest {
    @Test void updatesRemoveOldScoreAndClearRemovesEveryIndex() {
        var cache = new PredictionCandidateCache(100);
        cache.put("same", .1, new byte[]{1});
        cache.put("same", .9, new byte[]{2});
        assertFalse(cache.getScoreIndex().containsKey(.1));
        assertEquals(java.util.Set.of("same"), cache.getKeysByScoreRange(0, 1));
        assertArrayEquals(new byte[]{2}, cache.get("same"));
        cache.clear();
        assertTrue(cache.getScoreIndex().isEmpty());
        assertNull(cache.get("same"));
        cache.put("same", .8, new byte[]{3});
        assertArrayEquals(new byte[]{3}, cache.get("same"));
        assertEquals(java.util.Set.of("same"), cache.getScoreIndex().get(.8));
    }

    @Test void evictionAndDeletionEventuallyRemoveStaleKeys() throws Exception {
        var cache = new PredictionCandidateCache(1);
        for (int i = 0; i < 100; i++) cache.put("key" + i, .5, new byte[]{1});
        // Caffeine淘汰及通知异步执行，不假定put返回时淘汰已完成。
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (cache.getKeysByScoreRange(0, 1).size() > 1 && System.nanoTime() < deadline) Thread.sleep(10);
        var keys = cache.getKeysByScoreRange(0, 1);
        assertTrue(keys.size() <= 1);
        for (String key : keys) { assertNotNull(cache.get(key)); cache.invalidate(key); }
        assertTrue(cache.getScoreIndex().isEmpty());
    }

    @Test void concurrentClearReplaceAndLateNotificationsKeepNewEntries() throws Exception {
        var cache = new PredictionCandidateCache(10000);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int worker = 0; worker < 4; worker++) {
                int id = worker;
                tasks.add(() -> {
                    for (int i = 0; i < 200; i++) {
                        cache.put("same", id / 10.0, new byte[]{(byte) id});
                        if (i % 10 == 0) cache.clear();
                    }
                    return null;
                });
            }
            for (var future : workers.invokeAll(tasks)) future.get();
        }
        cache.clear();
        cache.put("same", .99, new byte[]{99});
        // 等待历史替换/清空通知排队执行，确保它们不能删除新版本。
        ForkJoinPool.commonPool().awaitQuiescence(2, TimeUnit.SECONDS);
        assertArrayEquals(new byte[]{99}, cache.get("same"));
        assertEquals(java.util.Set.of("same"), cache.getScoreIndex().get(.99));
        assertEquals(1, cache.getScoreIndex().size());
    }
}
