package com.my.project.application.predict.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.my.project.infrastructure.persistence.entity.PredictRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.application.support.compress.KryoSerializerUtils;
import com.my.project.application.support.compress.Lz4Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * PredictionCandidateCache
 *
 * @author 刘强
 * @version 2026/07/17 11:32
 **/
public class PredictionCandidateCache {

    private static final Logger log = LoggerFactory.getLogger(PredictionCandidateCache.class);

    private record Entry(double score, byte[] bytes) { }
    private final Cache<String, Entry> cache;
    private final Map<String, Entry> indexed = new ConcurrentHashMap<>();

    // 索引：分数 -> Key集合（ConcurrentSkipListMap 支持排序和范围查询）
    private final ConcurrentSkipListMap<Double, Set<String>> scoreIndex = new ConcurrentSkipListMap<>();

    // 单例模式
    private static final PredictionCandidateCache INSTANCE = new PredictionCandidateCache();

    private PredictionCandidateCache() {
        this(100_000_000);
    }

    PredictionCandidateCache(long maximumSize) {
        this.cache = Caffeine.newBuilder().maximumSize(maximumSize)
            .removalListener((String key, Entry value, RemovalCause cause) -> {
                removeIndexIfCurrent(key, value);
            }).recordStats()                        // 开启统计
            .build();
        log.info("Cache initialized: maxSize={}", maximumSize);
    }

    public static PredictionCandidateCache getInstance() {
        return INSTANCE;
    }

    public List<String> sampleKeys(Predicate<String> eligible, int size) {
        if (size <= 0) return List.of();
        var sample = new ArrayList<String>(size);
        long seen = 0;
        // 蓄水池抽样不复制缓存键，也不解压不需要的预测结果。
        for (String key : cache.asMap().keySet()) {
            if (!eligible.test(key)) continue;
            seen++;
            if (sample.size() < size) {
                sample.add(key);
            } else {
                long index = ThreadLocalRandom.current().nextLong(seen);
                if (index < size) sample.set((int) index, key);
            }
        }
        return List.copyOf(sample);
    }

    public synchronized void put(String key, double score, byte[] compressedData) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(compressedData);
        Entry previous = indexed.get(key);
        if (previous != null) removeIndexIfCurrent(key, previous);
        Entry current = new Entry(score, compressedData);
        indexed.put(key, current);
        scoreIndex.computeIfAbsent(score, k -> ConcurrentHashMap.newKeySet()).add(key);
        cache.put(key, current);
        log.debug("Cache put: key={}, size={} bytes", key, compressedData.length);
    }

    private synchronized void removeIndexIfCurrent(String key, Entry expected) {
        // 异步淘汰/替换通知可能迟到；只能删除它对应的旧版本，不能删除新号码记录。
        if (indexed.get(key) != expected) return;
        indexed.remove(key);
        Set<String> keys = scoreIndex.get(expected.score());
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) scoreIndex.remove(expected.score(), keys);
        }
    }

    public synchronized void invalidate(String key) {
        Entry previous = indexed.get(key);
        if (previous != null) removeIndexIfCurrent(key, previous);
        cache.invalidate(key);
    }

    /**
     * 获取索引对象（用于查询）
     */
    public ConcurrentSkipListMap<Double, Set<String>> getScoreIndex() {
        return scoreIndex;
    }

    /**
     * 获取所有 key（用于抽样）
     * 根据分数段返回对应的 key 列表，注意只返回引用，不复制数据
     */
    public Set<String> getKeysByScoreRange(double low, double high) {
        NavigableMap<Double, Set<String>> subMap = scoreIndex.subMap(low, true, high, true);
        Set<String> result = new HashSet<>();
        for (Set<String> set : subMap.values()) {
            result.addAll(set);
        }
        return result;
    }

    /**
     * 从缓存中获取压缩数据并解压
     */
    public PredictRecord getAndDeserialize(String key) {
        byte[] compressed = get(key);
        if (compressed == null) return null;
        try {
            String[] split = key.split(Pattern.quote("|"));
            PredictRecord result = new PredictRecord();
            result.setOpenDate(LocalDate.parse(split[0]));
            result.setRedBalls(split[1]);
            result.setBlueBall(Integer.valueOf(split[2]));
            byte[] serialized = Lz4Utils.decompressWithLength(compressed);
            ModelPredictOutputBo deserialize = KryoSerializerUtils.deserialize(serialized, ModelPredictOutputBo.class);
            result.setTotalScore(deserialize.getProbability());
            result.setExplanation(deserialize.getReason());
            return result;
        } catch (Exception e) {
            log.error("Deserialize failed for key: {}", key, e);
            return null;
        }
    }
    /**
     * 清除所有的key
     */
    public synchronized void clear() {
        cache.invalidateAll();
        indexed.clear();
        scoreIndex.clear();
    }

    public String getStats() {
        return cache.stats().toString();
    }

    // 可选：获取当前缓存大小
    public long size() {
        return cache.estimatedSize();
    }

    public byte[] get(String key) {
        Entry entry = cache.getIfPresent(key);
        return entry == null ? null : entry.bytes();
    }
}
