package com.my.project.service.llm.impl;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.*;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.service.ILotteryAdjustService;
import com.my.project.llm.service.ILotteryAnalysisService;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.event.AdjustCompleteEvent;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.llm.IColdHotAnalysisService;
import com.my.project.service.llm.IKillNumberService;
import com.my.project.service.llm.ILotteryFeatureAnalysisService;
import com.my.project.service.llm.IThreeZoneRatioPredictService;
import com.my.project.service.llm.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.service.llm.pojo.dto.LLmAdjustDto;
import com.my.project.service.predict.pojo.vo.PredictCacheVo;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.support.FeatureIntervalForecastUtils;
import com.my.project.service.support.LotteryFeatureTrendUtils;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.service.support.LotteryMorphologySnapshotUtils;
import com.my.project.service.support.LotteryTrendUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * LotteryFeatureAnalysisServiceImpl
 *
 * <p>产出调优 / 推荐用特征报告：杀号 / 冷热温 / 三区预测 / 趋势相位 / 形态推算。
 * <p>{@code featureForecast} 由 {@code lottery.llm.analysis.engine} 控制：
 * {@code java}（默认）走间隔评分；{@code llm} 各维线程问 LLM，不合规主推回退 Java 并做红蓝自洽。
 *
 * @author 刘强
 * @version 2026/08/18
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryFeatureAnalysisServiceImpl implements ILotteryFeatureAnalysisService {

    private final IHistoryRecordService historyRecordService;
    private final ILotteryAnalysisService lotteryAnalysisService;
    private final ILotteryAdjustService lotteryAdjustService;
    private final IPredictCacheService predictCacheService;
    private final ISmartSelectService smartSelectService;
    private final LotteryAnalysisMultiLevelCache multiLevelCache;

    private final IKillNumberService killNumberService;
    private final IColdHotAnalysisService coldHotAnalysisService;
    private final IThreeZoneRatioPredictService threeZoneRatioPredictService;

    private static final String CACHE_KEY_PREFIX = "feature.analysis.cache";
    /** 间隔节奏预测最少样本；不足时用全量，最多取最近 100 期 */
    private static final int INTERVAL_FORECAST_MAX = 100;
    private static final int STATS_SAMPLE_SIZE = 30;

    @Value("${lottery.llm.analysis.engine:java}")
    private String analysisEngine;

    private static final Executor FEATURE_LLM_EXECUTOR = Executors.newFixedThreadPool(
        FeatureKind.values().length,
        r -> {
            Thread t = new Thread(r, "feature-llm");
            t.setDaemon(true);
            return t;
        });

    private final LotteryModelConfig lotteryModelConfig;

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public LotteryAnalysisRespBo analyzeLatest(int sampleSize) {
        int count = Math.max(sampleSize, 1);
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        String period = latest.getFirst().getPeriod();
        String cacheKey = CACHE_KEY_PREFIX + period + sampleSize;
        //        String cacheKey = CACHE_KEY_PREFIX + period + UUID.randomUUID().toString();
        var respBo = multiLevelCache.get(cacheKey, k -> doAnalyze(count));
        Assert.notNull(respBo, "特征数据获取异常，请稍后重试");
        if (respBo.getFeatureForecast() == null) {
            log.warn("缓存缺少 featureForecast，即时补算 key={}", cacheKey);
            var records = historyRecordService.getLatestRecords(INTERVAL_FORECAST_MAX);
            if (CollectionUtils.isNotEmpty(records)) {
                var forecastRecords = records.stream().map(this::toDrawRecord).collect(Collectors.toList());
                respBo.setFeatureForecast(useLlmEngine()
                    ? forecastFeaturesByLlm(forecastRecords)
                    : FeatureIntervalForecastUtils.forecast(forecastRecords));
                multiLevelCache.put(cacheKey, respBo);
            }
        }
        return respBo;
    }

    private LotteryAnalysisRespBo doAnalyze(int sampleSize) {
        int fetchSize = Math.max(sampleSize, INTERVAL_FORECAST_MAX);
        log.info("拉取最近 {} 期历史开奖记录用于特征分析（杀号/冷热/趋势{}期，形态预测最多{}期，engine={}）",
            fetchSize, STATS_SAMPLE_SIZE, INTERVAL_FORECAST_MAX, analysisEngine);
        ReentrantLock lock = lockMap.computeIfAbsent(String.valueOf(sampleSize), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.warn("Key [" + sampleSize + "] 已被其他线程锁定，立即返回");
            return null;
        }
        try {
            var records = historyRecordService.getLatestRecords(fetchSize);
            if (CollectionUtils.isEmpty(records)) {
                throw new IllegalStateException("无可用的历史开奖记录用于分析");
            }
            var latestRecord = records.getFirst();
            var statsRecords = records.subList(0, Math.min(STATS_SAMPLE_SIZE, records.size())).stream()
                .map(this::toDrawRecord)
                .collect(Collectors.toList());
            int forecastSize = Math.min(INTERVAL_FORECAST_MAX, records.size());
            var forecastRecords = records.subList(0, forecastSize).stream()
                .map(this::toDrawRecord)
                .collect(Collectors.toList());
            var reqBo = LotteryAnalysisReqBo.builder().sampleSize(statsRecords.size()).enableKillNumber(true)
                .defaultKillNumbers(toDrawRecord(latestRecord)).records(statsRecords).build();

            LotteryAnalysisRespBo result = new LotteryAnalysisRespBo();
            result.setFeatureForecast(useLlmEngine()
                ? forecastFeaturesByLlm(forecastRecords)
                : FeatureIntervalForecastUtils.forecast(forecastRecords));
            result.setKillNumbers(Boolean.TRUE.equals(reqBo.getEnableKillNumber())
                ? killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers())
                : null);
            result.setColdHotAnalysis(coldHotAnalysisService.calculate(reqBo.getRecords()));
            result.setPredictedThreeZoneRatio(threeZoneRatioPredictService.predict(reqBo.getRecords()));
            result.setTrendAnalysis(calcTrendAnalysis(statsRecords));

            var calculate10 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 2);
            var calculate12 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 4);
            var calculate14 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 6);
            var calculate16 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 8);
            var calculate18 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 10);
            var calculate20 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 12);

            FileUtil.writeString(JSON.toJSONString(calculate10),
                new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_10.json"),
                StandardCharsets.UTF_8);
            FileUtil.writeString(JSON.toJSONString(calculate12),
                new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_12.json"),
                StandardCharsets.UTF_8);
            FileUtil.writeString(JSON.toJSONString(calculate14),
                new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_14.json"),
                StandardCharsets.UTF_8);
            FileUtil.writeString(JSON.toJSONString(calculate16),
                new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_16.json"),
                StandardCharsets.UTF_8);
            FileUtil.writeString(JSON.toJSONString(calculate18),
                new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_18.json"),
                StandardCharsets.UTF_8);
            FileUtil.writeString(JSON.toJSONString(calculate20),
                new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_20.json"),
                StandardCharsets.UTF_8);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 红球 11 + 蓝球 4：各开一条线程，形态指数趋势快照 → LLM → Java 回填间隔字段；失败回退纯 Java。
     */
    public FeatureForecastBo forecastFeaturesByLlm(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords) {
        EnumMap<FeatureKind, CompletableFuture<FeatureForecastItem>> futures = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            futures.put(kind, CompletableFuture.supplyAsync(
                () -> forecastOneFeature(kind, forecastRecords), FEATURE_LLM_EXECUTOR));
        }
        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

        EnumMap<FeatureKind, FeatureForecastItem> items = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            items.put(kind, futures.get(kind).join());
        }
        FeatureIntervalForecastUtils.reconcileBlueItems(items);
        FeatureIntervalForecastUtils.reconcileRedItems(items, forecastRecords);

        FeatureForecastBo forecast = FeatureIntervalForecastUtils.toBo(items);
        forecast.setBasis(String.format(Locale.ROOT,
            "红球11维+蓝球4维：与形态指数页同源，按最近%d期各分桶遗漏/指数/命中间隔序列交 LLM 推算；"
                + "间隔扩张走冷、收缩走热；不合规主推回退 Java。",
            forecastRecords.size()));
        return forecast;
    }

    /**
     * 纯 Java 间隔评分形态推算（含稀有过滤与红蓝自洽）。
     */
    public FeatureForecastBo forecastFeaturesByJava(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords) {
        return FeatureIntervalForecastUtils.forecast(forecastRecords);
    }

    private boolean useLlmEngine() {
        return "llm".equalsIgnoreCase(analysisEngine);
    }

    private FeatureForecastItem forecastOneFeature(FeatureKind kind,
        List<LotteryAnalysisReqBo.DrawRecord> forecastRecords) {
        try {
            LotteryAnalysisReqBo.DrawRecord newest = forecastRecords.getFirst();
            String lastRatio = LotteryFeatureTrendUtils.extract(
                newest.getRedBalls(), newest.getBlueBall(), kind);
            List<HistoryRecord> newestFirst = forecastRecords.stream().map(this::toHistoryRecord).toList();
            var trend = historyRecordService.analyzePatternTrend(kind.getCode(), lastRatio, newestFirst);
            String snapshot = LotteryMorphologySnapshotUtils.fromPatternTrendForLlm(trend);
            FeatureForecastItem llmItem =
                lotteryAnalysisService.forecastOne(kind.getLabel(), kind.valueHint(), snapshot);
            if (llmItem == null || llmItem.getValue() == null || llmItem.getValue().isBlank()) {
                log.warn("形态 [{}] LLM 返回空，回退 Java 间隔评分", kind.getLabel());
                return FeatureIntervalForecastUtils.forecastOneKind(kind, forecastRecords);
            }
            return FeatureIntervalForecastUtils.enrichFromSnapshot(kind, llmItem, forecastRecords);
        } catch (Exception e) {
            log.warn("形态 [{}] LLM 推算失败，回退 Java 间隔评分", kind.getLabel(), e);
            try {
                return FeatureIntervalForecastUtils.forecastOneKind(kind, forecastRecords);
            } catch (Exception ex) {
                log.error("形态 [{}] Java 回退也失败", kind.getLabel(), ex);
                return null;
            }
        }
    }

    /**
     * 计算红球 1-33 和蓝球 1-16 的趋势相位（堆叠 + MA5 斜率）。
     * <p>样本须先转为最旧→最新（与趋势页 analyzeTrend 一致）。
     */
    private LotteryAnalysisRespBo.TrendAnalysisBo calcTrendAnalysis(List<LotteryAnalysisReqBo.DrawRecord> records) {
        List<LotteryAnalysisReqBo.DrawRecord> chronological = new ArrayList<>(records);
        Collections.reverse(chronological);

        List<Set<Integer>> redDraws = new ArrayList<>();
        List<Set<Integer>> blueDraws = new ArrayList<>();
        for (var r : chronological) {
            redDraws.add(r.getRedBalls() != null ? new HashSet<>(r.getRedBalls()) : new HashSet<>());
            blueDraws.add(r.getBlueBall() != null ? Set.of(r.getBlueBall()) : new HashSet<>());
        }

        List<Integer> risingRed = new ArrayList<>();
        List<Integer> reboundingRed = new ArrayList<>();
        List<Integer> fallingRed = new ArrayList<>();
        List<Integer> coolingRed = new ArrayList<>();
        for (int b = 1; b <= 33; b++) {
            classifyBall(LotteryTrendUtils.analyze(redDraws, b).getPhase(), b,
                risingRed, reboundingRed, fallingRed, coolingRed);
        }

        List<Integer> risingBlue = new ArrayList<>();
        List<Integer> reboundingBlue = new ArrayList<>();
        List<Integer> fallingBlue = new ArrayList<>();
        List<Integer> coolingBlue = new ArrayList<>();
        for (int b = 1; b <= 16; b++) {
            classifyBall(LotteryTrendUtils.analyze(blueDraws, b).getPhase(), b,
                risingBlue, reboundingBlue, fallingBlue, coolingBlue);
        }

        log.info("趋势分析: 红 rising={}, rebounding={}, falling={}, cooling={}; 蓝 rising={}, rebounding={}, falling={}, cooling={}",
            risingRed, reboundingRed, fallingRed, coolingRed,
            risingBlue, reboundingBlue, fallingBlue, coolingBlue);

        return LotteryAnalysisRespBo.TrendAnalysisBo.builder()
            .risingRedBalls(risingRed).reboundingRedBalls(reboundingRed)
            .fallingRedBalls(fallingRed).coolingRedBalls(coolingRed)
            .risingBlueBalls(risingBlue).reboundingBlueBalls(reboundingBlue)
            .fallingBlueBalls(fallingBlue).coolingBlueBalls(coolingBlue)
            .build();
    }

    private static void classifyBall(String phase, int ball,
        List<Integer> rising, List<Integer> rebounding, List<Integer> falling, List<Integer> cooling) {
        if (phase == null) {
            return;
        }
        switch (phase) {
            case "rising" -> rising.add(ball);
            case "rebounding" -> rebounding.add(ball);
            case "falling" -> falling.add(ball);
            case "cooling" -> cooling.add(ball);
            default -> {
                // neutral：不入榜
            }
        }
    }

    @Override
    public LotteryAdjustRespBo adjust(LLmAdjustDto dto) {
        var respBo = analyzeLatest(100);
        var tickets = CollectionUtils.emptyIfNull(dto.getDrawRecords()).stream().map(
                d -> LotteryAdjustReqBo.PredictTicket.builder().redBalls(d.getRedballs()).blueBall(d.getBlueball()).build())
            .toList();
        var adjustReqBo =
            LotteryAdjustReqBo.builder().analysisReportJson(JSONObject.toJSONString(respBo)).tickets(tickets)
                .count(dto.getCount()).build();
        var adjust = lotteryAdjustService.adjust(adjustReqBo);
        AdjustCompleteEvent.of(this, adjust).publish();
        return adjust;
    }

    @Override
    public LotteryAdjustRespBo adjust(Integer count, boolean isTopN) {
        count = Math.min(count, 10);
        Map<String, ModelPredictOutputBo> predictCacheMaps = null;
        if (isTopN) {
            predictCacheMaps =
                Optional.ofNullable(predictCacheService.queryCache(count, null)).map(PredictCacheVo::getCacheDatas)
                    .orElse(Map.of());
        } else {
            predictCacheMaps = smartSelectService.percentileSample(count);
        }

        Assert.isTrue(MapUtils.isNotEmpty(predictCacheMaps), "预选号码组获取异常");
        var tickets = predictCacheMaps.keySet().stream().map(modelPredictOutput -> {
            var split = modelPredictOutput.split("\\|");
            var redBalls = Arrays.stream(split[1].split(",")).map(Integer::parseInt).toList();
            return LotteryAdjustReqBo.PredictTicket.builder().redBalls(redBalls).blueBall(Integer.valueOf(split[2]))
                .build();
        }).toList();
        var respBo = analyzeLatest(100);

        var adjustReqBo =
            LotteryAdjustReqBo.builder().analysisReportJson(JSONObject.toJSONString(respBo)).tickets(tickets).build();
        var adjust = lotteryAdjustService.adjust(adjustReqBo);
        AdjustCompleteEvent.of(this, adjust).publish();
        return adjust;
    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
            Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
            .blueBall(record.getSpecial()).build();
    }

    private HistoryRecord toHistoryRecord(LotteryAnalysisReqBo.DrawRecord draw) {
        List<Integer> reds = draw.getRedBalls() == null ? List.of() : draw.getRedBalls();
        return HistoryRecord.builder()
            .period(draw.getPeriod())
            .num1(reds.size() > 0 ? reds.get(0) : null)
            .num2(reds.size() > 1 ? reds.get(1) : null)
            .num3(reds.size() > 2 ? reds.get(2) : null)
            .num4(reds.size() > 3 ? reds.get(3) : null)
            .num5(reds.size() > 4 ? reds.get(4) : null)
            .num6(reds.size() > 5 ? reds.get(5) : null)
            .special(draw.getBlueBall())
            .build();
    }
}
