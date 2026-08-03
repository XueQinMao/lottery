package com.my.project.service.selection.impl;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.LotteryAdjustReqBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.service.ILotteryAdjustService;
import com.my.project.llm.service.ILotteryAnalysisService;
import com.my.project.persistence.repository.IPredictHitRecordRepository;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.entity.PredictHitRecord;
import com.my.project.persistence.entity.PredictRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.predict.IPredictRecordService;
import com.my.project.service.selection.IBuyRecordService;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.selection.enums.BuyRecordTypeEnums;
import com.my.project.service.selection.pojo.bo.PredictedFeaturesBo;
import com.my.project.service.selection.pojo.bo.ScoredCandidateBo;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;
import com.my.project.service.support.SmartSelectUtils;
import com.my.project.service.support.WinningNumberAnalyzerUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * SmartSelectServiceImpl
 *
 * <p>优化后流程：
 * <ol>
 *   <li>扩大命中样本计算权重</li>
 *   <li>历史分位数特征区间（替代 ARIMA 点预测）</li>
 *   <li>按模型分分位数分层 + 内存抽样（替代 ORDER BY RAND）</li>
 *   <li>统一分数语义打分 + Jaccard 多样性选 Top-N</li>
 *   <li>仅对 Top-N 调用大模型调优（避免全量候选进 LLM）</li>
 *   <li>复式扩展 / 落库购彩</li>
 * </ol>
 *
 * @author 刘强
 * @version 2025/11/07 16:32
 **/
@Slf4j
@Service
@Primary
public class SmartSelectServiceImpl implements ISmartSelectService {

    @Resource
    private IHistoryRecordService historyRecordService;

    @Resource
    private IPredictHitRecordRepository predictHitRecordRepository;

    @Resource
    private WinningNumberAnalyzerUtils winningNumberAnalyzerUtils;

    @Resource
    private ILotteryAdjustService lotteryAdjustService;

    @Resource
    private ILotteryAnalysisService lotteryAnalysisService;

    @Resource
    private IBuyRecordService buyRecordService;

    @Resource
    private IPredictRecordService predictRecordService;

    @Resource
    private IPredictCacheService predictCacheService;

    /** 目标抽样总数（本地打分池，不全量进大模型） */
    private static final int RANDOM_SELECTION_COUNT = 10000;
    /** 送入大模型调优的上限注数（本地打分 + 多样性筛选后） */
    private static final int LLM_ADJUST_TOP_N = 30;
    /** 大模型单次调优批大小 */
    private static final int LLM_ADJUST_BATCH_SIZE = 10;
    /** 权重/分桶分析使用的命中样本上限 */
    private static final int HIT_SAMPLE_LIMIT = 50;
    /** 纳入分析的最高奖级（1=一等奖，放宽到 3 以扩大样本） */
    private static final int HIT_LEVEL_MAX = 3;
    /** 历史期数（用于特征区间） */
    private static final int HISTORY_WINDOW = 100;

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
        log.info("权重已更新: modelW={}, rangeW={}, probP5/P95=[{}, {}], high/mid/low={}/{}/{}, diversity={}",
            weightConfigBo.getModelScoreWeight(), weightConfigBo.getRangeConstraintWeight(),
            weightConfigBo.getProbabilityMin(), weightConfigBo.getProbabilityMax(), weightConfigBo.getHighScoreRatio(),
            weightConfigBo.getMidScoreRatio(), weightConfigBo.getLowScoreRatio(),
            weightConfigBo.getDiversityThreshold());
    }

    @Override
    public void recommendFushi(LocalDate openDate, int budget) {
        var config = getWeightConfig();
        var historyDesc = historyRecordService.getLatestRecords(HISTORY_WINDOW);
        // 1. 分层抽样扩大候选池（不全量进大模型）
        var candidateMaps = percentileSample(RANDOM_SELECTION_COUNT);
        if (candidateMaps.isEmpty()) {
            log.warn("开奖日 {} 无可用预测候选，跳过智能选号", openDate);
            return;
        }
        // 4. 仅持久化并调优 Top-N（约 ceil(topN/batch) 次 LLM）
        predictRecordService.saveBatch(candidateMaps, openDate);

        var candidates = candidateMaps.entrySet().stream().map(entry -> {
            String[] split = entry.getKey().split("\\|");
            PredictRecord predictRecord = new PredictRecord();
            predictRecord.setOpenDate(openDate);
            predictRecord.setRedBalls(split[1]);
            predictRecord.setBlueBall(Integer.parseInt(split[2]));
            predictRecord.setTotalScore(entry.getValue().getProbability());
            predictRecord.setExplanation(entry.getValue().getReason());
            predictRecord.setCreateTime(LocalDateTime.now());
            return predictRecord;
        }).toList();
        // 2. 本地统一打分 + Jaccard 多样性，收成少量 Top-N 再调 LLM
        int topN = resolveLlmTopN(30);
        List<PredictRecord> topCandidates = localScoreAndSelect(candidates, historyDesc, config, topN);
        if (topCandidates.isEmpty()) {
            log.warn("开奖日 {} 本地筛选后无候选，跳过智能选号", openDate);
            return;
        }
        log.info("智能选号漏斗: sampled={}, llmTopN={}, openDate={}", candidateMaps.size(), topCandidates.size(), openDate);
        // 3. 大模型特征分析（仅 1 次）
        var drawRecords = CollectionUtils.emptyIfNull(historyDesc).stream().map(
            h -> LotteryAnalysisReqBo.DrawRecord.builder().period(h.getPeriod())
                .redBalls(List.of(h.getNum1(), h.getNum2(), h.getNum3(), h.getNum4(), h.getNum5(), h.getNum6()))
                .blueBall(h.getSpecial()).build()).toList();
        var report = lotteryAnalysisService.analyze(
            LotteryAnalysisReqBo.builder().records(drawRecords).sampleSize(drawRecords.size()).build());

        Function<String, List<Integer>> redBallsFunction =
            p -> Arrays.stream(p.split(",")).map(Integer::parseInt).toList();
        Function<PredictRecord, LotteryAdjustReqBo.PredictTicket> toTicket =
            p -> LotteryAdjustReqBo.PredictTicket.builder().blueBall(p.getBlueBall())
                .redBalls(redBallsFunction.apply(p.getRedBalls())).build();

        ListUtils.partition(topCandidates, LLM_ADJUST_BATCH_SIZE).forEach(list -> {
            var tickets = list.stream().map(toTicket).toList();
            var req = LotteryAdjustReqBo.builder()
                .analysisReportJson(JSON.toJSONString(report))
                .tickets(tickets)
                .build();
            var adjust = lotteryAdjustService.adjust(req);
            buyRecordService.batchSave(adjust, openDate, BuyRecordTypeEnums.AUTO);
        });
    }

    /**
     * budget 为购彩注数时，至少送 LLM_ADJUST_TOP_N 注给大模型，避免过少；
     * budget &lt;= 0 时退回默认上限。
     */
    private static int resolveLlmTopN(int budget) {
        if (budget <= 0) {
            return LLM_ADJUST_TOP_N;
        }
        return Math.max(budget, LLM_ADJUST_TOP_N);
    }

    /**
     * 本地统一打分 + 多样性选 Top-N，作为大模型调优前的漏斗。
     */
    private List<PredictRecord> localScoreAndSelect(List<PredictRecord> candidates,
                                                    List<HistoryRecord> historyDesc,
                                                    WeightConfigBo config,
                                                    int topN) {
        List<HistoryRecord> history =
            historyDesc == null ? Collections.emptyList() : historyDesc;
        PredictedFeaturesBo predictedFeatures =
            SmartSelectUtils.buildPredictedFeatures(history, config);

        List<ScoredCandidateBo> scored = candidates.stream()
            .map(c -> SmartSelectUtils.scorePrediction(c, predictedFeatures, history, config))
            .toList();

        return SmartSelectUtils.selectDiverseTop(scored, topN, config.getDiversityThreshold())
            .stream()
            .map(scoredCand -> {
                PredictRecord record = scoredCand.getResult();
                record.setTotalScore(
                    BigDecimal.valueOf(scoredCand.getFinalScore()).setScale(4, RoundingMode.HALF_UP));
                record.setExplanation(scoredCand.getExplanation());
                return record;
            })
            .toList();
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
        // 必须共用同一个 iterator：旧实现每次 collectKeys 都从头部重扫，导致高分 key 被三段重复加入
        Iterator<Map.Entry<Double, Set<String>>> scoreIt = descendingMap.entrySet().iterator();
        LinkedHashSet<String> sampledKeys = new LinkedHashSet<>();
        collectUniqueKeys(scoreIt, highQuota, sampledKeys);
        collectUniqueKeys(scoreIt, midQuota, sampledKeys);
        collectUniqueKeys(scoreIt, lowQuota, sampledKeys);
        // 配额取整/同分桶跳过导致不足时，继续往后补齐到 quantity
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

    /**
     * 沿已有降序 iterator 继续取最多 {@code count} 个尚未收集的 key。
     */
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

    /**
     * 扩大命中样本：奖级放宽 + 条数增加，降低小样本过拟合
     */
    private WeightConfigBo generateConfig(int level) {
        List<PredictHitRecord> winningNumbers =
            predictHitRecordRepository.lambdaQuery().le(PredictHitRecord::getLevel, level)
                .orderByDesc(PredictHitRecord::getOpenDate).last(" limit " + HIT_SAMPLE_LIMIT).list();
        WeightConfigBo config = winningNumberAnalyzerUtils.buildWeightConfig(
            winningNumbers == null ? Collections.emptyList() : winningNumbers);
        // 兜底：若分析器未写出区间，本地按 P5/P95 补齐
        if (config.getProbabilityMin() == null && winningNumbers != null && !winningNumbers.isEmpty()) {
            List<Double> scores = winningNumbers.stream().map(PredictHitRecord::getTotalScore).filter(Objects::nonNull)
                .map(BigDecimal::doubleValue).sorted().toList();
            if (!scores.isEmpty()) {
                double p5 = WinningNumberAnalyzerUtils.percentile(scores, 0.05);
                double p95 = WinningNumberAnalyzerUtils.percentile(scores, 0.95);
                config.setProbabilityMin(p5);
                config.setProbabilityMax(p95);
                config.setProbabilityRange(p95 - p5);
            }
        }
        return config;
    }
}
