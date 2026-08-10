package com.my.project.service.selection.impl;

import com.my.project.persistence.repository.IPredictHitRecordRepository;
import com.my.project.persistence.entity.PredictHitRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.predict.IPredictRecordService;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;
import com.my.project.service.support.WinningNumberAnalyzerUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SmartSelectServiceImpl
 *
 * <p>当前主路径：
 * <ol>
 *   <li>命中样本计算 P5/P95 与分层比例</li>
 *   <li>按模型分高/中/低分层抽样</li>
 *   <li>落库预测记录</li>
 * </ol>
 *
 * @author 刘强
 * @version 2026/08/10 17:02
 **/
@Slf4j
@Service
@Primary
public class SmartSelectServiceImpl implements ISmartSelectService {

    @Resource
    private IPredictHitRecordRepository predictHitRecordRepository;

    @Resource
    private WinningNumberAnalyzerUtils winningNumberAnalyzerUtils;

    @Resource
    private IPredictRecordService predictRecordService;

    @Resource
    private IPredictCacheService predictCacheService;

    /** 目标抽样总数 */
    private static final int RANDOM_SELECTION_COUNT = 10000;
    /** 权重/分桶分析使用的命中样本上限 */
    private static final int HIT_SAMPLE_LIMIT = 50;
    /** 纳入分析的最高奖级（1=一等奖） */
    private static final int HIT_LEVEL_MAX = 4;

    private final AtomicReference<WeightConfigBo> cachedOptimizedConfig =
        new AtomicReference<>(new WeightConfigBo());

    @Override
    public WeightConfigBo getWeightConfig() {
        return cachedOptimizedConfig.get();
    }

    @Override
    public void refreshWeightConfig() {
        WeightConfigBo weightConfigBo = generateConfig(HIT_LEVEL_MAX);
        cachedOptimizedConfig.set(weightConfigBo);
        log.info("权重已更新: probP5/P95=[{}, {}], high/mid/low={}/{}/{}",
            weightConfigBo.getProbabilityMin(), weightConfigBo.getProbabilityMax(),
            weightConfigBo.getHighScoreRatio(), weightConfigBo.getMidScoreRatio(),
            weightConfigBo.getLowScoreRatio());
    }

    @Override
    public void recommendFushi(LocalDate openDate, int budget) {
        var candidateMaps = percentileSample(RANDOM_SELECTION_COUNT);
        if (candidateMaps.isEmpty()) {
            log.warn("开奖日 {} 无可用预测候选，跳过智能选号", openDate);
            return;
        }
        predictRecordService.saveBatch(candidateMaps, openDate);
    }

    /**
     * 分位数分层抽样：
     * 按模型分 DESC 连续遍历高/中/低三段配额，同一 iterator 推进，保证 key 不重复。
     */
    @Override
    public Map<String, ModelPredictOutputBo> percentileSample(int quantity) {
        var cache = predictCacheService.queryCache(0, null);
        var config = getWeightConfig();

        long totalSize = cache.getTotalCount();
        if (totalSize == 0 || quantity <= 0) {
            return Collections.emptyMap();
        }

        double highR = normalizeRatio(config.getHighScoreRatio(), 0.40);
        double midR = normalizeRatio(config.getMidScoreRatio(), 0.40);
        double lowR = normalizeRatio(config.getLowScoreRatio(), 0.20);
        double sum = highR + midR + lowR;
        highR /= sum;
        midR /= sum;
        lowR /= sum;

        int highQuota = (int) Math.round(quantity * highR);
        int midQuota = (int) Math.round(quantity * midR);
        int lowQuota = quantity - highQuota - midQuota;

        NavigableMap<Double, Set<String>> descendingMap = cache.getScoreIndex().descendingMap();
        Iterator<Map.Entry<Double, Set<String>>> scoreIt = descendingMap.entrySet().iterator();
        LinkedHashSet<String> sampledKeys = new LinkedHashSet<>();
        collectUniqueKeys(scoreIt, highQuota, sampledKeys);
        collectUniqueKeys(scoreIt, midQuota, sampledKeys);
        collectUniqueKeys(scoreIt, lowQuota, sampledKeys);
        if (sampledKeys.size() < quantity) {
            collectUniqueKeys(scoreIt, quantity - sampledKeys.size(), sampledKeys);
        }

        Map<String, ModelPredictOutputBo> resultData = new LinkedHashMap<>(sampledKeys.size());
        for (String key : sampledKeys) {
            var predictOutput = cache.getCacheDatas().get(key);
            if (predictOutput != null) {
                resultData.put(key, predictOutput);
            }
        }
        log.info("分层抽样: total={}, quantity={}, sampledUnique={}, result={}",
            totalSize, quantity, sampledKeys.size(), resultData.size());
        return resultData;
    }

    private void collectUniqueKeys(Iterator<Map.Entry<Double, Set<String>>> scoreIt,
                                   int count,
                                   LinkedHashSet<String> collected) {
        if (count <= 0 || scoreIt == null) {
            return;
        }
        int remaining = count;
        while (scoreIt.hasNext() && remaining > 0) {
            Set<String> keys = scoreIt.next().getValue();
            if (keys == null || keys.isEmpty()) {
                continue;
            }
            for (String key : keys) {
                if (remaining <= 0) {
                    break;
                }
                if (key != null && collected.add(key)) {
                    remaining--;
                }
            }
        }
    }

    private static double normalizeRatio(Double value, double defaultVal) {
        if (value == null || value < 0) {
            return defaultVal;
        }
        return value;
    }

    private WeightConfigBo generateConfig(int level) {
        List<PredictHitRecord> winningNumbers =
            predictHitRecordRepository.lambdaQuery().le(PredictHitRecord::getLevel, level)
                .orderByDesc(PredictHitRecord::getOpenDate).last(" limit " + HIT_SAMPLE_LIMIT).list();
        return winningNumberAnalyzerUtils.buildWeightConfig(
            winningNumbers == null ? Collections.emptyList() : winningNumbers);
    }
}
