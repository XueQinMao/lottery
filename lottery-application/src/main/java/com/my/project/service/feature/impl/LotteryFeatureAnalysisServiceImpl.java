package com.my.project.service.feature.impl;

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
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.event.AdjustCompleteEvent;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.feature.ILotteryFeatureAnalysisService;
import com.my.project.service.feature.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.service.feature.pojo.dto.LLmAdjustDto;
import com.my.project.service.feature.pojo.vo.AdjustHistoryFileVo;
import com.my.project.service.record.IPredictFileRecordService;
import com.my.project.service.record.pojo.enums.PredictFileRecordType;
import com.my.project.service.record.pojo.vo.PredictFileRecordVo;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.predict.pojo.vo.PredictCacheVo;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.support.*;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.service.support.analyse.ColdHotAnalysisUtils;
import com.my.project.service.support.analyse.KillBallUtils;
import com.my.project.service.support.analyse.MaAnalysisUtils;
import com.my.project.service.support.analyse.ThreeZoneRatioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * {@code java}（默认）用 snapshot 的 indexValues 前后期差值趋势本地计算主推 （收缩=倾向命中，扩张=开出概率低，平稳按间隔估介入时机）； {@code llm} 压缩同一套候选表后问大模型选值，再
 * {@code applyGuard} 硬校验。两路都做红蓝自洽。
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
    private final IPredictFileRecordService predictFileRecordService;


    private static final String CACHE_KEY_PREFIX = "feature.analysis.cache";
    /**
     * 间隔节奏预测最少样本；不足时用全量，最多取最近 100 期
     */
    private static final int INTERVAL_FORECAST_MAX = 100;
    /** 均线趋势与杀号共用窗口（与 {@link MaAnalysisUtils} / KillBall 一致） */
    private static final int TREND_MA_SAMPLE_SIZE = 100;
    private static final int ADJUST_HISTORY_MAX = 20;
    private static final DateTimeFormatter ADJUST_FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Executor FEATURE_FORECAST_EXECUTOR =
            Executors.newFixedThreadPool(FeatureKind.values().length, r -> {
                Thread t = new Thread(r, "feature-forecast");
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
        var respBo = multiLevelCache.get(cacheKey, k -> {
            var result = doAnalyze(count);
            // 特征预测解释生成后落盘并写入文件记录表，供历史列表回看
            if (result != null) {
                persistAnalysisView(result, count);
            }
            return result;
        });
        Assert.notNull(respBo, "特征数据获取异常，请稍后重试");
        return respBo;
    }

    @Override
    public LotteryAdjustViewBo adjust(LLmAdjustDto dto) {
        var respBo = analyzeLatest(100);
        var tickets = CollectionUtils.emptyIfNull(dto.getDrawRecords()).stream().map(
                        d -> LotteryAdjustReqBo.PredictTicket.builder().redBalls(d.getRedballs()).blueBall(d.getBlueball()).build())
                .toList();
        var lastDraw = resolveLastDraw(dto.getLastDrawRedBalls(), dto.getLastDrawBlueBall());
        var adjustReqBo =
                LotteryAdjustReqBo.builder().analysisReportJson(JSONObject.toJSONString(respBo)).tickets(tickets)
                        .lastDrawRedBalls(lastDraw.redBalls())
                        .lastDrawBlueBall(lastDraw.blueBall())
                        .count(dto.getCount()).build();
        var adjust = lotteryAdjustService.adjust(adjustReqBo);
        AdjustCompleteEvent.of(this, adjust).publish();
        return persistAdjustView(FeatureForecastHitUtils.toView(adjust, respBo.getFeatureForecast()),
                dto.getCount(), false);
    }

    @Override
    public LotteryAdjustViewBo adjust(Integer count, boolean isTopN) {
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
        var lastDraw = resolveLastDraw(null, null);

        var adjustReqBo =
                LotteryAdjustReqBo.builder().analysisReportJson(JSONObject.toJSONString(respBo)).tickets(tickets)
                        .lastDrawRedBalls(lastDraw.redBalls())
                        .lastDrawBlueBall(lastDraw.blueBall()).build();
        var adjust = lotteryAdjustService.adjust(adjustReqBo);
        AdjustCompleteEvent.of(this, adjust).publish();
        return persistAdjustView(FeatureForecastHitUtils.toView(adjust, respBo.getFeatureForecast()), count, isTopN);
    }

    /**
     * 解析上一期开奖号码：优先使用入参，为空则自动拉取最近一期历史记录。
     *
     * @param redBalls 入参上期红球（可为 null/空）
     * @param blueBall 入参上期蓝球（可为 null）
     * @return 上一期开奖号码（红球升序、蓝球）；若无历史记录则红球返回 null、蓝球返回 null
     */
    private LastDraw resolveLastDraw(List<Integer> redBalls, Integer blueBall) {
        if (CollectionUtils.isNotEmpty(redBalls) && blueBall != null) {
            return new LastDraw(redBalls, blueBall);
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

    @Override
    public List<AdjustHistoryFileVo> listAdjustHistory(int limit) {
        int size = Math.clamp(limit, 1, ADJUST_HISTORY_MAX);
        File dir = adjustHistoryDir();
        File[] files = dir.listFiles((d, name) -> isSafeAdjustFileName(name) && new File(d, name).isFile());
        if (files == null || files.length == 0) {
            return List.of();
        }
        return Arrays.stream(files).sorted(Comparator.comparingLong(File::lastModified).reversed()).limit(size)
                .map(f -> AdjustHistoryFileVo.builder().fileName(f.getName()).lastModified(f.lastModified())
                        .size(f.length()).build())
                .toList();
    }

    @Override
    public LotteryAdjustViewBo loadAdjustHistory(String fileName) {
        Assert.isTrue(isSafeAdjustFileName(fileName), "非法推荐文件名");
        File file = new File(adjustHistoryDir(), fileName);
        Assert.isTrue(file.isFile(), "推荐文件不存在");
        LotteryAdjustViewBo view = JSON.parseObject(FileUtil.readString(file, StandardCharsets.UTF_8),
                LotteryAdjustViewBo.class);
        Assert.notNull(view, "推荐文件解析失败");
        return view;
    }

    @Override
    public List<PredictFileRecordVo> listFileHistory(String type, int limit) {
        PredictFileRecordType recordType = PredictFileRecordType.of(type);
        return predictFileRecordService.listByType(recordType, limit);
    }

    @Override
    public LotteryAnalysisRespBo loadAnalysisHistory(String fileName) {
        Assert.isTrue(isSafeFileName(fileName), "非法特征预测文件名");
        // 优先按库记录的绝对路径读取，兼容存量文件（如 feature.analysis.cache*.json 在 cache 目录）
        String filePath = predictFileRecordService.findPathByFileName(fileName);
        File file = StringUtils.isNotBlank(filePath) && new File(filePath).isFile()
                ? new File(filePath)
                : new File(analysisHistoryDir(), fileName);
        Assert.isTrue(file.isFile(), "特征预测文件不存在");
        LotteryAnalysisRespBo view = JSON.parseObject(FileUtil.readString(file, StandardCharsets.UTF_8),
                LotteryAnalysisRespBo.class);
        Assert.notNull(view, "特征预测文件解析失败");
        return view;
    }

    private LotteryAdjustViewBo persistAdjustView(LotteryAdjustViewBo view, Integer count, boolean isTopN) {
        try {
            String name = String.format("adjust_%s_c%s_topN%s.json", ADJUST_FILE_TIME.format(LocalDateTime.now()),
                    count == null ? 0 : count, isTopN);
            File file = new File(adjustHistoryDir(), name);
            FileUtil.writeString(JSON.toJSONString(view), file, StandardCharsets.UTF_8);
            predictFileRecordService.record(PredictFileRecordType.RECOMMEND, name, file.getAbsolutePath());
        } catch (Exception e) {
            log.warn("保存推荐结果文件失败", e);
        }
        return view;
    }

    /**
     * 将特征预测结果落盘并写入文件记录表（仅在多级缓存未命中、重新计算后调用）。
     */
    private void persistAnalysisView(LotteryAnalysisRespBo view, int sampleSize) {
        try {
            String name = String.format("analysis_%s_s%d.json", ADJUST_FILE_TIME.format(LocalDateTime.now()), sampleSize);
            File file = new File(analysisHistoryDir(), name);
            FileUtil.writeString(JSON.toJSONString(view), file, StandardCharsets.UTF_8);
            predictFileRecordService.record(PredictFileRecordType.ANALYSIS, name, file.getAbsolutePath());
        } catch (Exception e) {
            log.warn("保存特征预测结果文件失败", e);
        }
    }

    private File adjustHistoryDir() {
        File dir = new File(lotteryModelConfig.getPath(), "adjust");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("创建推荐结果目录失败: {}", dir.getAbsolutePath());
        }
        return dir;
    }

    private File analysisHistoryDir() {
        File dir = new File(lotteryModelConfig.getPath(), "analysis");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("创建特征预测结果目录失败: {}", dir.getAbsolutePath());
        }
        return dir;
    }

    private static boolean isSafeAdjustFileName(String fileName) {
        return fileName != null && !fileName.contains("..") && !fileName.contains("/") && !fileName.contains("\\")
                && fileName.matches("adjust_[\\w.-]+\\.json");
    }

    private static boolean isSafeAnalysisFileName(String fileName) {
        return isSafeFileName(fileName)
                && (fileName.matches("analysis_[\\w.-]+\\.json")
                    || fileName.matches("feature\\.analysis[\\w.-]+\\.json"));
    }

    /** 通用文件名安全校验：防路径穿越，允许字母数字下划线点连字符。 */
    private static boolean isSafeFileName(String fileName) {
        return fileName != null && !fileName.contains("..") && !fileName.contains("/") && !fileName.contains("\\")
                && fileName.matches("[\\w.-]+\\.json");
    }

    public LotteryAnalysisRespBo doAnalyze(int sampleSize) {
        int fetchSize = Math.max(sampleSize, INTERVAL_FORECAST_MAX);
        log.info("拉取最近 {} 期历史开奖记录用于特征分析（杀号/冷热/趋势均线{}期，形态预测最多{}期）", fetchSize,
                TREND_MA_SAMPLE_SIZE, INTERVAL_FORECAST_MAX);
        ReentrantLock lock = lockMap.computeIfAbsent(String.valueOf(sampleSize), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.warn("Key [{}] 已被其他线程锁定，立即返回", sampleSize);
            return null;
        }
        try {
            var records = historyRecordService.getLatestRecords(100000);
            if (CollectionUtils.isEmpty(records)) {
                throw new IllegalStateException("无可用的历史开奖记录用于分析");
            }
            int forecastSize = Math.min(INTERVAL_FORECAST_MAX, records.size());
            var forecastRecords =
                    records.subList(0, forecastSize).stream().map(this::toDrawRecord).collect(Collectors.toList());
            return LotteryAnalysisRespBo.builder().killNumbers(KillBallUtils.calculate(records))
                    .coldHotAnalysis(ColdHotAnalysisUtils.calculate(records))
                    .predictedThreeZoneRatio(ThreeZoneRatioUtils.calculate(records))
                    .trendAnalysis(calcTrendAnalysis(records)).featureForecast(forecastFeaturesByIndex(forecastRecords, records)).build();
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
        return assembleFeatureForecast(forecastRecords, false, dueByCode);
    }



    private FeatureForecastBo assembleFeatureForecast(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords,
                                                      boolean useLlm, Map<String, Set<String>> dueByCode) {
        EnumMap<FeatureKind, CompletableFuture<FeatureForecastItem>> futures = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            Set<String> dueValues = dueByCode == null ? null : dueByCode.get(kind.getCode());
            futures.put(kind, CompletableFuture.supplyAsync(
                    () -> forecastOneFeature(kind, forecastRecords, useLlm, dueValues),
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
        String engineHint = useLlm ? "大模型从压缩候选表选值，applyGuard 硬校验；" : "本地按 indexValues 差值趋势评分；";
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
     * <p>一区/二区/三区个数不参与排除；其余维度按 {@link FeatureKindEnums} 的 label 取对应概率 Map，
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
        for (FeatureKindEnums kind : FeatureKindEnums.values()) {
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

    private static boolean isZoneCountKind(FeatureKindEnums kind) {
        return kind == FeatureKindEnums.ZONE1_COUNT
                || kind == FeatureKindEnums.ZONE2_COUNT
                || kind == FeatureKindEnums.ZONE3_COUNT;
    }

    private FeatureForecastItem forecastOneFeature(FeatureKind kind,
                                                   List<LotteryAnalysisReqBo.DrawRecord> forecastRecords, boolean useLlm, Set<String> dueValues) {
        try {
            String snapshot = buildFeatureSnapshot(kind, forecastRecords);
            if (!useLlm) {
                return MorphologySnapshotForecast.forecast(snapshot, dueValues);
            }
            String compact = MorphologySnapshotForecast.compactForLlm(snapshot);
            FeatureForecastItem llmItem =
                    lotteryAnalysisService.forecastOne(kind.getLabel(), kind.valueHint(), compact);
            if (llmItem == null || llmItem.getValue() == null || llmItem.getValue().isBlank()) {
                log.warn("形态 [{}] LLM 返回空，回退 indexValues 差值趋势", kind.getLabel());
                return MorphologySnapshotForecast.forecast(snapshot, dueValues);
            }
            return MorphologySnapshotForecast.applyGuard(llmItem, snapshot, dueValues);
        } catch (Exception e) {
            log.warn("形态 [{}] {}推算失败，回退 indexValues 差值趋势", kind.getLabel(), useLlm ? "LLM " : "", e);
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

    /**
     * 用 {@link MaAnalysisUtils} 同一份 MA5/MA10/MA20 按堆叠 + MA5 斜率分组趋势相位。
     * <p>窗口与均线杀号一致（近 100 期，最新在前）；neutral / 数据不足不入榜。
     */
    private LotteryAnalysisRespBo.TrendAnalysisBo calcTrendAnalysis(List<HistoryRecord> historyRecords) {
        int size = Math.min(TREND_MA_SAMPLE_SIZE, historyRecords.size());
        var ma = MaAnalysisUtils.analyze(historyRecords.subList(0, size));
        var risingRed = filterPhase(ma.getRedBalls(), "rising");
        var reboundingRed = filterPhase(ma.getRedBalls(), "rebounding");
        var fallingRed = filterPhase(ma.getRedBalls(), "falling");
        var coolingRed = filterPhase(ma.getRedBalls(), "cooling");
        var risingBlue = filterPhase(ma.getBlueBalls(), "rising");
        var reboundingBlue = filterPhase(ma.getBlueBalls(), "rebounding");
        var fallingBlue = filterPhase(ma.getBlueBalls(), "falling");
        var coolingBlue = filterPhase(ma.getBlueBalls(), "cooling");
        log.info(
                "趋势分析: 红 rising={}, rebounding={}, falling={}, cooling={}; 蓝 rising={}, rebounding={}, falling={}, cooling={}",
                risingRed, reboundingRed, fallingRed, coolingRed, risingBlue, reboundingBlue, fallingBlue, coolingBlue);
        return LotteryAnalysisRespBo.TrendAnalysisBo.builder().risingRedBalls(risingRed)
                .reboundingRedBalls(reboundingRed).fallingRedBalls(fallingRed).coolingRedBalls(coolingRed)
                .risingBlueBalls(risingBlue).reboundingBlueBalls(reboundingBlue).fallingBlueBalls(fallingBlue)
                .coolingBlueBalls(coolingBlue).build();
    }

    private static List<Integer> filterPhase(List<MaAnalysisUtils.MaResult> balls, String phase) {
        if (CollectionUtils.isEmpty(balls)) {
            return List.of();
        }
        return balls.stream().filter(b -> phase.equals(b.getPhase())).map(MaAnalysisUtils.MaResult::getBall).toList();
    }


    private HistoryRecord toHistoryRecord(LotteryAnalysisReqBo.DrawRecord draw) {
        List<Integer> reds = draw.getRedBalls() == null ? List.of() : draw.getRedBalls();
        return HistoryRecord.builder().period(draw.getPeriod()).num1(!reds.isEmpty() ? reds.get(0) : null)
                .num2(reds.size() > 1 ? reds.get(1) : null).num3(reds.size() > 2 ? reds.get(2) : null)
                .num4(reds.size() > 3 ? reds.get(3) : null).num5(reds.size() > 4 ? reds.get(4) : null)
                .num6(reds.size() > 5 ? reds.get(5) : null).special(draw.getBlueBall()).build();
    }
}
