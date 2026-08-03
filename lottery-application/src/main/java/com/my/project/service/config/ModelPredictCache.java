package com.my.project.service.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.my.project.persistence.entity.PredictRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.support.KryoSerializerUtils;
import com.my.project.service.support.Lz4Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

/**
 * ModelPredictCache
 *
 * @author 刘强
 * @version 2026/07/17 11:32
 **/
public class ModelPredictCache {

    private static final Logger log = LoggerFactory.getLogger(ModelPredictCache.class);

    private final Cache<String, byte[]> cache;

    // 索引：分数 -> Key集合（ConcurrentSkipListMap 支持排序和范围查询）
    private final ConcurrentSkipListMap<Double, Set<String>> scoreIndex = new ConcurrentSkipListMap<>();

    // 单例模式
    private static final ModelPredictCache INSTANCE = new ModelPredictCache();

    private ModelPredictCache() {
        this.cache = Caffeine.newBuilder().maximumSize(100_000_000)// 根据内存估算调整
            .evictionListener((String key, byte[] value, RemovalCause cause) -> {
                log.warn("Cache evicted: key={}, cause={}", key, cause);
            }).recordStats()                        // 开启统计
            .build();
        log.info("Cache initialized: maxSize=10m");
    }

    public static ModelPredictCache getInstance() {
        return INSTANCE;
    }

    public void put(String key, double score, byte[] compressedData) {
        cache.put(key, compressedData);
        // 更新索引
        scoreIndex.computeIfAbsent(score, k -> ConcurrentHashMap.newKeySet()).add(key);
        log.debug("Cache put: key={}, size={} bytes", key, compressedData.length);
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
        byte[] compressed = cache.getIfPresent(key);
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
    public void clear() {
        cache.invalidateAll();
    }

    public String getStats() {
        return cache.stats().toString();
    }

    // 可选：获取当前缓存大小
    public long size() {
        return cache.estimatedSize();
    }

    public byte[] get(String key) {
        return cache.getIfPresent(key);
    }
}
