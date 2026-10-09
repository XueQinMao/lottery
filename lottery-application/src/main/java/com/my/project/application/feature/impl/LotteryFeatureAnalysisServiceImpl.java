package com.my.project.application.feature.impl;

import com.my.project.application.report.LotteryReportService;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.*;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.service.LotteryAdjustService;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.application.enums.FeatureDimension;
import com.my.project.application.event.AdjustCompleteEvent;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.feature.LotteryFeatureAnalysisService;
import com.my.project.application.feature.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.application.feature.dto.LlmAdjustmentCommand;
import com.my.project.application.feature.dto.RecommendationCommand;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.predict.vo.PredictCacheVo;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.support.*;
import com.my.project.application.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.application.support.analyse.KillBallUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import org.springframework.util.Assert;

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
 * <p>报告输出红蓝球形态分析、杀号、当前冷热池和历史开奖。
 * {@code llm-pattern} 为最近30期逐项大模型描述分支；{@code java} 适配原指数预测。
 *
 * @author 刘强
 * @version 2026/08/18
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryFeatureAnalysisServiceImpl implements LotteryFeatureAnalysisService {

    private final HistoryRecordService historyRecordService;
    private final PatternFeatureAnalysisSupport patternAnalysisSupport;

    @Value("${lottery.llm.analysis.engine:llm-pattern}")
    private String analysisEngine;
    @Value("${lottery.llm.analysis.model:qwen-flash}")
    private String analysisModel = "qwen-flash";
    @Value("${lottery.llm.analysis.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String analysisBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode";
    private final LotteryAdjustService lotteryAdjustService;
    private final PredictCacheService predictCacheService;
    private final SmartSelectService smartSelectService;
    private final LotteryAnalysisMultiLevelCache multiLevelCache;
    private final LotteryReportService reportService;


    private static final String CACHE_KEY_PREFIX = "feature.analysis.cache";
    /** 本地 Java 指数计算使用的执行器，不用于大模型调用。 */
    private static final Executor FEATURE_FORECAST_EXECUTOR =
            Executors.newFixedThreadPool(FeatureKind.values().length, r -> {
                Thread t = new Thread(r, "feature-forecast");
                t.setDaemon(true);
                return t;
            });


    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public LotteryAnalysisRespBo analyzeLatest(int sampleSize) {
        int count = "java".equals(analysisEngine) ? Math.max(sampleSize, 1) : PatternFeatureAnalysisReqBo.HISTORY_SIZE;
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        String period = latest.getFirst().getPeriod();
        String cacheScope = "java".equals(analysisEngine) ? "java"
            : "llm-pattern:bailian:" + analysisModel + ":" +
                cn.hutool.crypto.digest.DigestUtil.md5Hex(analysisBaseUrl).substring(0, 12);
        String cacheKey = CACHE_KEY_PREFIX + ":pattern-v5:" + cacheScope + ":" + period + ":" + count;
        var analysisReport = multiLevelCache.get(cacheKey, k -> {
            var result = doAnalyze(count, period);
            // 特征预测解释生成后落盘并写入文件记录表，供历史列表回看
            if (result != null) {
                reportService.saveAnalysis(result, count);
            }
            return result;
        });
        Assert.notNull(analysisReport, "特征数据获取异常，请稍后重试");
        return analysisReport;
    }

    @Override
    public LotteryAdjustViewBo recommend(RecommendationCommand command) {
        AdjustmentContext context = switch (command) {
            case RecommendationCommand.Feature(var adjustment) -> prepareFeatureRecommendation(adjustment);
            case RecommendationCommand.Cache(var count, var topN, var requirement) ->
                prepareCacheAdjustment(count, topN, requirement);
        };
        var request = LotteryAdjustReqBo.builder()
            .analysisReportJson(JSON.toJSONString(context.report()))
            .tickets(context.tickets())
            .lastDrawRedBalls(context.lastDraw().redBalls())
            .lastDrawBlueBall(context.lastDraw().blueBall())
            .count(context.recommendCount())
            .userRequirement(context.userRequirement()).build();
        var recommendation = lotteryAdjustService.adjust(request);
        AdjustCompleteEvent.of(this, recommendation).publish();
        return reportService.saveRecommendation(
            FeatureForecastHitUtils.toPatternView(recommendation, context.report()),
            context.archiveCount(), context.topN());
    }

    private AdjustmentContext prepareFeatureRecommendation(LlmAdjustmentCommand command) {
        var report = analyzeLatest(100);
        var tickets = CollectionUtils.emptyIfNull(command.getDrawRecords()).stream()
            .map(draw -> LotteryAdjustReqBo.PredictTicket.builder()
                .redBalls(draw.getRedBalls()).blueBall(draw.getBlueBall()).build()).toList();
        return new AdjustmentContext(report, tickets,
            resolveLastDraw(command.getLastDrawRedBalls(), command.getLastDrawBlueBall(), report),
            command.getCount(), command.getCount(), false, command.getUserRequirement());
    }

    private AdjustmentContext prepareCacheAdjustment(Integer requestedCount, boolean topN, String requirement) {
        int count = Math.min(requestedCount, 10);
        Map<String, ModelPredictOutputBo> candidates = topN
            ? Optional.ofNullable(predictCacheService.queryCache(count, null))
                .map(PredictCacheVo::getCacheDatas).orElse(Map.of())
            : smartSelectService.percentileSample(count);
        Assert.isTrue(MapUtils.isNotEmpty(candidates), "预选号码组获取异常");
        var tickets = candidates.keySet().stream().map(cacheKey -> {
            var parts = cacheKey.split("\\|");
            var redBalls = Arrays.stream(parts[1].split(",")).map(Integer::parseInt).toList();
            return LotteryAdjustReqBo.PredictTicket.builder().redBalls(redBalls)
                .blueBall(Integer.valueOf(parts[2])).build();
        }).toList();
        var report = analyzeLatest(100);
        // Cached adjustment derives its size from tickets, exactly as the legacy API did.
        return new AdjustmentContext(report, tickets, resolveLastDraw(null, null, report),
            null, count, topN, requirement);
    }

    private record AdjustmentContext(LotteryAnalysisRespBo report,
            List<LotteryAdjustReqBo.PredictTicket> tickets, LastDraw lastDraw,
            Integer recommendCount, Integer archiveCount, boolean topN, String userRequirement) { }

    /**
     * 解析上一期开奖号码：优先使用入参，为空则自动拉取最近一期历史记录。
     *
     * @param redBalls 入参上期红球（可为 null/空）
     * @param blueBall 入参上期蓝球（可为 null）
     * @return 上一期开奖号码（红球升序、蓝球）；若无历史记录则红球返回 null、蓝球返回 null
     */
    private LastDraw resolveLastDraw(List<Integer> redBalls, Integer blueBall, LotteryAnalysisRespBo report) {
        if (CollectionUtils.isNotEmpty(redBalls) && blueBall != null) {
            return new LastDraw(redBalls, blueBall);
        }
        if (CollectionUtils.isNotEmpty(report.getRecentDrawRecords())) {
            var newest = report.getRecentDrawRecords().getLast();
            return new LastDraw(newest.getRedBalls(), newest.getBlueBall());
        }
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) {
            log.warn("未取到历史开奖记录，上期号码约束将忽略");
            return new LastDraw(null, null);
        }
        var record = latest.getFirst();
        List<Integer> autoReds = Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(),
                record.getNum4(), record.getNum5(), record.getNum6());
        return new LastDraw(autoReds, record.getSpecial());
    }

    private record LastDraw(List<Integer> redBalls, Integer blueBall) {
    }

    public LotteryAnalysisRespBo doAnalyze(int sampleSize) {
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) throw new IllegalStateException("无可用的历史开奖记录用于分析");
        return doAnalyze(sampleSize, latest.getFirst().getPeriod());
    }

    private LotteryAnalysisRespBo doAnalyze(int sampleSize, String endPeriod) {
        String engine = StringUtils.defaultIfBlank(analysisEngine, "llm-pattern");
        if (!Set.of("java", "llm-pattern").contains(engine)) {
            throw new IllegalArgumentException("analysis.engine仅支持java/llm-pattern");
        }
        ReentrantLock lock = lockMap.computeIfAbsent(engine + ":" + endPeriod + ":" + sampleSize,
            key -> new ReentrantLock());
        lock.lock();
        try {
            // 固定报告截止期，避免新增开奖导致报告与缓存期号错位。
            var records = historyRecordService.getRecordsEndingAt(endPeriod, 100000);
            if (CollectionUtils.isEmpty(records)) throw new IllegalStateException("无可用的历史开奖记录用于分析");
            if (records.size() < 60) throw new IllegalStateException("最近30期形态分析及历史冷热分类至少需要60期开奖");
            int historySize = "llm-pattern".equals(engine) ? PatternFeatureAnalysisReqBo.HISTORY_SIZE : 100;
            List<LotteryAnalysisReqBo.DrawRecord> recent = new ArrayList<>(records.subList(0,
                Math.min(historySize, records.size())).stream().map(this::toDrawRecord).toList());
            List<PatternFeatureAnalysisBo> analysis;
            if ("llm-pattern".equals(engine)) {
                analysis = patternAnalysisSupport.analyze(records);
                if (analysis.stream().noneMatch(item -> "OK".equals(item.getStatus()))) {
                    throw new IllegalStateException("所有大模型形态分析均失败，请重试");
                }
            } else {
                analysis = patternAnalysisSupport.adaptJava(forecastFeaturesByIndex(recent, records), records);
            }
            Collections.reverse(recent);
            return LotteryAnalysisRespBo.builder().featureAnalysis(analysis)
                .killNumbers(KillBallUtils.calculate(records))
                .coldHotAnalysis(ColdHotAnalysisUtils.calculate(records))
                .recentDrawRecords(recent).build();
        } finally {
            lock.unlock();
        }
    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
                Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                        record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
                .blueBall(record.getSpecial()).build();
    }


    /**
     * 红球 11 + 蓝球 4：用 forecastRecords 算出各形态 snapshot，按 indexValues 前后期差值趋势本地选主推。
     * <p>生成阶段即按遗漏到期概率把概率 &gt; 0 的形态取值排除（一/二/三区个数除外）：
     * 主推与备选都不会落入被排除值，避免事后剔除导致整维被排空。
     */
    public FeatureForecastBo forecastFeaturesByIndex(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords,
                                                     List<HistoryRecord> historyRecords) {
        Map<String, Set<String>> dueByCode = buildOmissionDueByCode(historyRecords);
        return assembleFeatureForecast(forecastRecords, dueByCode);
    }



    private FeatureForecastBo assembleFeatureForecast(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords,
                                                      Map<String, Set<String>> dueByCode) {
        EnumMap<FeatureKind, CompletableFuture<FeatureForecastItem>> futures = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            Set<String> dueValues = dueByCode == null ? null : dueByCode.get(kind.getCode());
            futures.put(kind, CompletableFuture.supplyAsync(
                    () -> forecastOneFeature(kind, forecastRecords, dueValues),
                    FEATURE_FORECAST_EXECUTOR));
        }
        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

        EnumMap<FeatureKind, FeatureForecastItem> items = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            items.put(kind, futures.get(kind).join());
        }
        FeatureIntervalForecastUtils.reconcileBlueItems(items);
        FeatureIntervalForecastUtils.reconcileRedItems(items, forecastRecords);

        FeatureForecastBo forecast = FeatureIntervalForecastUtils.toBo(items);
        String engineHint = "本地按 indexValues 差值趋势评分；";
        String dueHint = dueByCode == null || dueByCode.values().stream().allMatch(Set::isEmpty)
                ? "" : "已按遗漏到期概率>0在生成阶段排除形态取值（一/二/三区个数除外）；";
        forecast.setBasis(String.format(Locale.ROOT,
                "红球11维+蓝球4维：主推贴理论众数，指数差值只做轻量加减分；%s%s"
                        + "众数刚出仍可主推；低频刚出与热度断档不主推；"
                        + "跨度/和值/区个数相邻高分合并为区间。样本%d期。",
                engineHint, dueHint, forecastRecords.size()));
        return forecast;
    }

    /**
     * 计算各形态维需排除的取值集合（遗漏到期概率 &gt; 0）。
     * <p>一区/二区/三区个数不参与排除；其余维度按 {@link FeatureDimension} 的 label 取对应概率 Map，
     * 概率 &gt; 0 的取值进入排除集。返回 code -&gt; 排除取值集合。
     */
    private Map<String, Set<String>> buildOmissionDueByCode(List<HistoryRecord> historyRecords) {
        if (CollectionUtils.isEmpty(historyRecords)) {
            return Map.of();
        }
        Map<String, Map<String, Double>> dueMap = OmissionDueProbabilityUtils.analyze(historyRecords);
        if (MapUtils.isEmpty(dueMap)) {
            return Map.of();
        }
        Map<String, Set<String>> dueByCode = new HashMap<>();
        List<String> excludedKinds = new ArrayList<>();
        for (FeatureDimension kind : FeatureDimension.values()) {
            if (isZoneCountKind(kind)) {
                continue;
            }
            Map<String, Double> valueProbs = dueMap.get(kind.getLabel());
            if (MapUtils.isEmpty(valueProbs)) {
                continue;
            }
            Set<String> dueValues = valueProbs.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if (dueValues.isEmpty()) {
                continue;
            }
            dueByCode.put(kind.getCode(), dueValues);
            excludedKinds.add(kind.getLabel() + "=" + StringUtils.join(dueValues, ","));
        }
        if (!excludedKinds.isEmpty()) {
            log.info("featureForecast 生成阶段排除遗漏到期概率>0的形态取值: {}", excludedKinds);
        }
        return dueByCode;
    }

    private static boolean isZoneCountKind(FeatureDimension kind) {
        return kind == FeatureDimension.ZONE1_COUNT
                || kind == FeatureDimension.ZONE2_COUNT
                || kind == FeatureDimension.ZONE3_COUNT;
    }

    private FeatureForecastItem forecastOneFeature(FeatureKind kind,
                                                   List<LotteryAnalysisReqBo.DrawRecord> forecastRecords, Set<String> dueValues) {
        try {
            String snapshot = buildFeatureSnapshot(kind, forecastRecords);
            return MorphologySnapshotForecast.forecast(snapshot, dueValues);
        } catch (Exception e) {
            log.warn("形态 [{}] 推算失败，回退 indexValues 差值趋势", kind.getLabel(), e);
            try {
                return MorphologySnapshotForecast.forecast(buildFeatureSnapshot(kind, forecastRecords), dueValues);
            } catch (Exception ex) {
                log.error("形态 [{}] indexValues 回退也失败", kind.getLabel(), ex);
                return null;
            }
        }
    }

    private String buildFeatureSnapshot(FeatureKind kind, List<LotteryAnalysisReqBo.DrawRecord> forecastRecords) {
        LotteryAnalysisReqBo.DrawRecord newest = forecastRecords.getFirst();
        String lastRatio = LotteryFeatureTrendUtils.extract(newest.getRedBalls(), newest.getBlueBall(), kind);
        List<HistoryRecord> newestFirst = forecastRecords.stream().map(this::toHistoryRecord).toList();
        var trend = historyRecordService.analyzePatternTrend(kind.getCode(), lastRatio, newestFirst);
        return LotteryMorphologySnapshotUtils.fromPatternTrendForLlm(trend);
    }

    private HistoryRecord toHistoryRecord(LotteryAnalysisReqBo.DrawRecord draw) {
        List<Integer> reds = draw.getRedBalls() == null ? List.of() : draw.getRedBalls();
        return HistoryRecord.builder().period(draw.getPeriod()).num1(!reds.isEmpty() ? reds.get(0) : null)
                .num2(reds.size() > 1 ? reds.get(1) : null).num3(reds.size() > 2 ? reds.get(2) : null)
                .num4(reds.size() > 3 ? reds.get(3) : null).num5(reds.size() > 4 ? reds.get(4) : null)
                .num6(reds.size() > 5 ? reds.get(5) : null).special(draw.getBlueBall()).build();
    }
}
