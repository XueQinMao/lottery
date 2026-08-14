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
import com.my.project.service.support.LotteryFeatureStatsUtils;
import com.my.project.service.support.LotteryFeatureTrendUtils;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.service.support.LotteryMorphologySnapshotUtils;
import com.my.project.service.support.LotteryTrendUtils;
import com.my.project.service.support.LotteryTrendUtils.TrendAnalysisResult;
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
 * <p>{@code lottery.llm.analysis.engine=java}：主线程用 Java 统计直方图（含连号、邻狐传、蓝球），
 * 形态目标取样本最高频。
 * <p>{@code lottery.llm.analysis.engine=llm}：主线程仍用 Java 统计直方图 / 连号 / 邻狐传 / 蓝球 /
 * 杀号 / 冷热温 / 三区预测 / 趋势；红球 11 个 + 蓝球 4 个形态各开一条线程向 LLM 要下一期值或区间。
 *
 * @author 刘强
 * @version 2026/08/14
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

    private static final Executor FEATURE_LLM_EXECUTOR = Executors.newFixedThreadPool(
        FeatureKind.values().length,
        r -> {
            Thread t = new Thread(r, "feature-llm");
            t.setDaemon(true);
            return t;
        });

    private final LotteryModelConfig lotteryModelConfig;

    @Value("${lottery.llm.analysis.engine:java}")
    private String analysisEngine;

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
        return respBo;
    }

    private LotteryAnalysisRespBo doAnalyze(int sampleSize) {
        log.info("拉取最近 {} 期历史开奖记录用于特征分析", sampleSize);
        ReentrantLock lock = lockMap.computeIfAbsent(String.valueOf(sampleSize), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.warn("Key [" + sampleSize + "] 已被其他线程锁定，立即返回");
            return null;
        }
        try {
            var records = historyRecordService.getLatestRecords(sampleSize);
            if (CollectionUtils.isEmpty(records)) {
                throw new IllegalStateException("无可用的历史开奖记录用于分析");
            }
            var latestRecord = records.getFirst();
            var drawRecords = records.subList(0, Math.min(30, sampleSize)).stream().map(this::toDrawRecord)
                .collect(Collectors.toList());
            var reqBo = LotteryAnalysisReqBo.builder().sampleSize(drawRecords.size()).enableKillNumber(true)
                .defaultKillNumbers(toDrawRecord(latestRecord)).records(drawRecords).build();

            LotteryAnalysisRespBo result = LotteryFeatureStatsUtils.analyze(reqBo.getRecords());
            if (useLlmEngine()) {
                result.setFeatureForecast(forecastFeaturesByLlm(drawRecords));
            } else {
                result.setFeatureForecast(forecastFeaturesByJava(result));
            }
            result.setKillNumbers(Boolean.TRUE.equals(reqBo.getEnableKillNumber())
                ? killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers())
                : null);
            result.setColdHotAnalysis(coldHotAnalysisService.calculate(reqBo.getRecords()));
            result.setPredictedThreeZoneRatio(threeZoneRatioPredictService.predict(reqBo.getRecords()));
            result.setTrendAnalysis(calcTrendAnalysis(drawRecords));

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

    private boolean useLlmEngine() {
        return "llm".equalsIgnoreCase(analysisEngine);
    }

    /**
     * 红球 11 个 + 蓝球 4 个形态各开一条线程请求 LLM；指数数据走形态指数页同一套 analyzePatternTrend。
     */
    private FeatureForecastBo forecastFeaturesByLlm(List<LotteryAnalysisReqBo.DrawRecord> drawRecords) {
        List<HistoryRecord> latestNewestFirst = drawRecords.stream().map(this::toHistoryRecord).toList();
        EnumMap<FeatureKind, CompletableFuture<FeatureForecastItem>> futures = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            futures.put(kind, CompletableFuture.supplyAsync(
                () -> forecastOneFeature(kind, latestNewestFirst), FEATURE_LLM_EXECUTOR));
        }
        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

        FeatureForecastBo forecast = new FeatureForecastBo();
        forecast.setOddEven(joinItem(futures, FeatureKind.ODD_EVEN));
        forecast.setBigSmall(joinItem(futures, FeatureKind.BIG_SMALL));
        forecast.setPrimeComposite(joinItem(futures, FeatureKind.PRIME_COMP));
        forecast.setRatio012(joinItem(futures, FeatureKind.RATIO_012));
        forecast.setSpan(joinItem(futures, FeatureKind.SPAN));
        forecast.setSumRange(joinItem(futures, FeatureKind.SUM_RANGE));
        forecast.setSumTail(joinItem(futures, FeatureKind.SUM_TAIL));
        forecast.setThreeZone(joinItem(futures, FeatureKind.THREE_ZONE));
        forecast.setZone1Count(joinItem(futures, FeatureKind.ZONE1_COUNT));
        forecast.setZone2Count(joinItem(futures, FeatureKind.ZONE2_COUNT));
        forecast.setZone3Count(joinItem(futures, FeatureKind.ZONE3_COUNT));
        forecast.setBlueOddEven(joinItem(futures, FeatureKind.BLUE_ODD_EVEN));
        forecast.setBlueBigSmall(joinItem(futures, FeatureKind.BLUE_BIG_SMALL));
        forecast.setBlueBigSmallOddEven(joinItem(futures, FeatureKind.BLUE_BIG_SMALL_ODD_EVEN));
        forecast.setBlueRatio012(joinItem(futures, FeatureKind.BLUE_RATIO_012));
        forecast.setBasis("红球与蓝球形态指数与形态指数页同源，由独立线程向 LLM 推算下一期值或区间");
        return forecast;
    }

    private FeatureForecastItem forecastOneFeature(FeatureKind kind, List<HistoryRecord> latestNewestFirst) {
        try {
            HistoryRecord newest = latestNewestFirst.getFirst();
            List<Integer> lastReds = Arrays.asList(
                newest.getNum1(), newest.getNum2(), newest.getNum3(),
                newest.getNum4(), newest.getNum5(), newest.getNum6());
            String lastRatio = LotteryFeatureTrendUtils.extract(lastReds, newest.getSpecial(), kind);
            var trend = historyRecordService.analyzePatternTrend(
                kind.getCode(), lastRatio, latestNewestFirst);
            String snapshot = LotteryMorphologySnapshotUtils.fromPatternTrend(trend);
            return lotteryAnalysisService.forecastOne(kind.getLabel(), kind.valueHint(), snapshot);
        } catch (Exception e) {
            log.warn("形态 [{}] 推算失败", kind.getLabel(), e);
            return null;
        }
    }

    private static FeatureForecastItem joinItem(EnumMap<FeatureKind, CompletableFuture<FeatureForecastItem>> futures,
        FeatureKind kind) {
        return futures.get(kind).join();
    }

    /** Java 引擎：形态目标取样本最高频，不请求 LLM。 */
    private FeatureForecastBo forecastFeaturesByJava(LotteryAnalysisRespBo stats) {
        FeatureForecastBo forecast = new FeatureForecastBo();
        forecast.setOddEven(topItem(stats.getOddEvenRatio()));
        forecast.setBigSmall(topItem(stats.getBigSmallRatio()));
        forecast.setPrimeComposite(topItem(stats.getPrimeCompositeRatio()));
        forecast.setRatio012(topItem(stats.getRatio012()));
        forecast.setSpan(topItem(stats.getSpan()));
        forecast.setSumRange(topItem(stats.getSumRange()));
        forecast.setSumTail(topItem(stats.getSumTail()));
        forecast.setThreeZone(topItem(stats.getThreeZoneRatio()));
        forecast.setZone1Count(topItem(stats.getZone1Count()));
        forecast.setZone2Count(topItem(stats.getZone2Count()));
        forecast.setZone3Count(topItem(stats.getZone3Count()));
        var blue = stats.getBlue();
        if (blue != null) {
            forecast.setBlueOddEven(topItem(blue.getOddEvenRatio()));
            forecast.setBlueBigSmall(topItem(blue.getBigSmallRatio()));
            forecast.setBlueBigSmallOddEven(topItem(blue.getBigSmallOddEvenRatio()));
            forecast.setBlueRatio012(topItem(blue.getRatio012()));
        }
        forecast.setBasis("Java 统计：红球与蓝球各形态取样本最高频作为下一期参考");
        return forecast;
    }

    private static FeatureForecastItem topItem(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String value = map.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(null);
        if (value == null) {
            return null;
        }
        FeatureForecastItem item = new FeatureForecastItem();
        item.setValue(value);
        item.setConfidence(1.0);
        item.setReason("样本最高频");
        return item;
    }

    /**
     * 计算红球 1-33 和蓝球 1-16 的趋势均线排列。
     * <p>基于反向指数的 MA5/MA10/MA20 均线：
     * <ul>
     *     <li>多头排列（MA5 > MA10 > MA20）→ 指数上升 → 号码趋热 → rising</li>
     *     <li>空头排列（MA5 < MA10 < MA20）→ 指数下降 → 号码趋冷 → falling</li>
     * </ul>
     */
    private LotteryAnalysisRespBo.TrendAnalysisBo calcTrendAnalysis(List<LotteryAnalysisReqBo.DrawRecord> records) {
        // 红球：每期 6 个红球转为 Set
        List<Set<Integer>> redDraws = new ArrayList<>();
        List<Set<Integer>> blueDraws = new ArrayList<>();
        for (var r : records) {
            redDraws.add(r.getRedBalls() != null ? new HashSet<>(r.getRedBalls()) : new HashSet<>());
            blueDraws.add(r.getBlueBall() != null ? Set.of(r.getBlueBall()) : new HashSet<>());
        }

        List<Integer> risingRed = new ArrayList<>();
        List<Integer> fallingRed = new ArrayList<>();
        for (int b = 1; b <= 33; b++) {
            TrendAnalysisResult result = LotteryTrendUtils.analyze(redDraws, b);
            int arr = result.getArrangement();
            if (arr == 1) {
                risingRed.add(b);
            } else if (arr == -1) {
                fallingRed.add(b);
            }
        }

        List<Integer> risingBlue = new ArrayList<>();
        List<Integer> fallingBlue = new ArrayList<>();
        for (int b = 1; b <= 16; b++) {
            TrendAnalysisResult result = LotteryTrendUtils.analyze(blueDraws, b);
            int arr = result.getArrangement();
            if (arr == 1) {
                risingBlue.add(b);
            } else if (arr == -1) {
                fallingBlue.add(b);
            }
        }

        log.info("趋势分析: 红球上升={}, 下降={}, 蓝球上升={}, 下降={}", risingRed, fallingRed, risingBlue,
            fallingBlue);

        return LotteryAnalysisRespBo.TrendAnalysisBo.builder().risingRedBalls(risingRed).fallingRedBalls(fallingRed)
            .risingBlueBalls(risingBlue).fallingBlueBalls(fallingBlue).build();
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
