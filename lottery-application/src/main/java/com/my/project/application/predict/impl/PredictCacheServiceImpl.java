package com.my.project.application.predict.impl;

import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.application.predict.cache.PredictionCandidateCache;
import com.my.project.application.predict.vo.PredictCacheVo;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.application.support.compress.KryoSerializerUtils;
import com.my.project.application.support.compress.Lz4Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * PredictCacheServiceImpl
 *
 * @author 刘强
 * @version 2026/07/28 19:39
 **/
@Service
@Slf4j
public class PredictCacheServiceImpl implements PredictCacheService {
    @Override
    public void addCache(ModelPredictOutputBo predictResultBo, SsqCombinationBo combination, LocalDate localDate) {
        try {
            // 1. 序列化
            byte[] serialized = KryoSerializerUtils.serialize(predictResultBo);
            // 2. 压缩（带长度头）
            byte[] compressed = Lz4Utils.compressWithLength(serialized);
            // 3. 生成唯一缓存 Key
            var cacheKey = getCacheKey(localDate, combination.getRedBalls(), combination.getBlueBall());
            // 4. 存入 Caffeine
            PredictionCandidateCache.getInstance().put(cacheKey, predictResultBo.getProbability().doubleValue(), compressed);
        } catch (Exception e) {
            log.error("Error serializing predict result: {}", e.getMessage(), e);
        }
    }

    @Override
    public void addCache(String cacheKey, ModelPredictOutputBo modelPredictOutput) {
        // 1. 序列化
        byte[] serialized = KryoSerializerUtils.serialize(modelPredictOutput);
        // 2. 压缩（带长度头）
        byte[] compressed = Lz4Utils.compressWithLength(serialized);

        PredictionCandidateCache.getInstance().put(cacheKey, modelPredictOutput.getProbability().doubleValue(), compressed);
    }

    @Override
    public String getCacheKey(LocalDate openDate, List<Integer> redBalls, Integer blue) {
        return StringUtils.join(
            List.of(openDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), StringUtils.join(redBalls, ","),
                String.valueOf(blue)), "|");
    }

    /**
     * 查询缓存数据，size=0时获取全量的数据
     * @param size
     * @param keys
     * @return
     */
    @Override
    public PredictCacheVo queryCache(Integer size, List<String> keys) {
        Map<String, ModelPredictOutputBo> cacheDatas = new HashMap<>();
        if (CollectionUtils.isNotEmpty(keys)) {
            keys.forEach(key -> {
                byte[] bytes = PredictionCandidateCache.getInstance().get(key);
                byte[] serialized = Lz4Utils.decompressWithLength(bytes);
                ModelPredictOutputBo deserialize = KryoSerializerUtils.deserialize(serialized, ModelPredictOutputBo.class);
                cacheDatas.put(key, deserialize);
            });
            return new PredictCacheVo(PredictionCandidateCache.getInstance().getScoreIndex(), cacheDatas,
                PredictionCandidateCache.getInstance().size());
        }
        ConcurrentSkipListMap<Double, Set<String>> scoreIndex = PredictionCandidateCache.getInstance().getScoreIndex();
        for (Map.Entry<Double, Set<String>> entry : scoreIndex.descendingMap().entrySet()) {
            if (size > 0 && cacheDatas.size() > size) {
                break;
            }
            Set<String> strings = scoreIndex.get(entry.getKey());
            strings.forEach(key -> {
                if (size > 0 && cacheDatas.size() >= size) {
                    return;
                }
                byte[] bytes = PredictionCandidateCache.getInstance().get(key);
                byte[] serialized = Lz4Utils.decompressWithLength(bytes);
                ModelPredictOutputBo deserialize = KryoSerializerUtils.deserialize(serialized, ModelPredictOutputBo.class);
                cacheDatas.put(key, deserialize);
            });
        }
        return new PredictCacheVo(PredictionCandidateCache.getInstance().getScoreIndex(), cacheDatas,
            PredictionCandidateCache.getInstance().size());
    }

}
