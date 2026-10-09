package com.my.project.application.feature.support;

import com.my.project.llm.bo.*;
import com.my.project.llm.service.LotteryAnalysisService;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.support.LotteryFeatureTrendUtils;
import com.my.project.application.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.application.support.FeatureForecastHitUtils;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PatternFeatureAnalysisSupport implements AutoCloseable {
    private static final int HISTORY_SIZE = PatternFeatureAnalysisReqBo.HISTORY_SIZE;
    private static final int COLD_HOT_WINDOW = 30;
    private final LotteryAnalysisService analysisService;
    // One worker for the whole application, including different reports/HTTP requests.
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("llm-pattern").daemon(true).factory());

    @jakarta.annotation.PreDestroy
    @Override
    public void close() { executor.shutdownNow(); }

    public List<PatternFeatureAnalysisBo> analyze(List<HistoryRecord> newestFirst) {
        List<PatternFeatureAnalysisReqBo> requests = prepare(newestFirst);
        log.info("形态分析已提交：执行模式=单线程，特征数={}，截止期号={}", requests.size(), newestFirst.getFirst().getPeriod());
        List<CompletableFuture<PatternFeatureAnalysisBo>> futures = requests.stream()
            .map(request -> CompletableFuture.supplyAsync(() -> analyzeOne(request), executor)).toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private PatternFeatureAnalysisBo analyzeOne(PatternFeatureAnalysisReqBo request) {
        long started = System.nanoTime();
        log.info("开始分析形态 [{}]，code={}", request.getLabel(), request.getCode());
        try {
            var result = analysisService.analyzePattern(request);
            log.info("形态 [{}] 分析完成，status={}，耗时={}ms", request.getLabel(),
                result == null ? "EMPTY" : result.getStatus(), (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (RuntimeException failure) {
            log.error("形态 [{}] 执行异常，耗时={}ms", request.getLabel(),
                (System.nanoTime() - started) / 1_000_000, failure);
            throw failure;
        }
    }

    public List<PatternFeatureAnalysisReqBo> prepare(List<HistoryRecord> newestFirst) {
        if (newestFirst.size() < HISTORY_SIZE + COLD_HOT_WINDOW) {
            throw new IllegalStateException("最近30期冷温热分析需要每期前30期，至少需60期开奖");
        }
        // 与冷温热统计页面一致：每期只用它之前30期分类，红蓝球共用同一快照。
        var priorPools = java.util.stream.IntStream.range(0, HISTORY_SIZE)
            .mapToObj(index -> ColdHotAnalysisUtils.calculate(newestFirst.subList(index + 1, index + 1 + COLD_HOT_WINDOW)))
            .toList();
        List<PatternFeatureAnalysisReqBo> requests = new ArrayList<>();
        for (FeatureKind kind : FeatureKind.values()) {
            requests.add(request(kind.getCode(), kind.getLabel(), definition(kind),
                LotteryFeatureTrendUtils.buckets(kind), newestFirst, priorPools));
        }
        requests.add(request("tail", "尾数", "每个红球号码%10；逐期按0尾:1尾:...:9尾提取十项计数，合计6。"
            + "候选池填写尾数0到9，出现表示至少开出一个该尾号码，断档表示该尾一个也不开。",
            java.util.stream.IntStream.range(0, 10).mapToObj(String::valueOf).toList(), newestFirst, priorPools));
        List<String> ratios = LotteryFeatureTrendUtils.buckets(FeatureKind.THREE_ZONE);
        requests.add(request("coldWarmHot", "红球冷温热数量组合", "逐期使用该期priorColdHot分类本期六个红球，"
            + "特征提取及候选比例顺序均为热:温:冷，合计6。不可用最新分类池倒推过去。", ratios, newestFirst, priorPools));
        requests.add(request("blueColdWarmHot", "蓝球冷温热", "逐期使用该期开奖前30期的蓝球冷热池，"
            + "标记该期蓝球来自热、温、冷哪组；候选只填热、温、冷，不是红球数量比例。",
            List.of("热", "温", "冷"), newestFirst, priorPools));
        return requests;
    }

    private PatternFeatureAnalysisReqBo request(String code, String label, String definition,
                                                List<String> allowed, List<HistoryRecord> records,
                                                List<ColdHotAnalysisBo> priorPools) {
        List<PatternFeatureAnalysisBo.Observation> observations = new ArrayList<>();
        for (int index = HISTORY_SIZE - 1; index >= 0; index--) {
            HistoryRecord record = records.get(index);
            List<Integer> reds = reds(record);
            var row = PatternFeatureAnalysisBo.Observation.builder().position(HISTORY_SIZE - index)
                .period(record.getPeriod()).redBalls(reds).blueBall(record.getSpecial());
            if ("tail".equals(code)) {
                int[] counts = new int[10];
                reds.forEach(ball -> counts[ball % 10]++);
                row.value(Arrays.stream(counts).mapToObj(String::valueOf).collect(Collectors.joining(":")));
            } else if ("coldWarmHot".equals(code) || "blueColdWarmHot".equals(code)) {
                ColdHotAnalysisBo prior = priorPools.get(index);
                row.priorColdHot(prior).value("coldWarmHot".equals(code)
                    ? coldWarmHotValue(reds, prior) : blueColdWarmHotValue(record.getSpecial(), prior));
            } else {
                row.value(LotteryFeatureTrendUtils.extract(reds, record.getSpecial(), FeatureKind.fromCode(code)));
            }
            observations.add(row.build());
        }
        return PatternFeatureAnalysisReqBo.builder().code(code).label(label).definition(definition)
            .allowedValues(allowed).observations(observations).build();
    }

    public static String coldWarmHotValue(List<Integer> reds, ColdHotAnalysisBo pools) {
        long hot = reds.stream().filter(pools.getRedHotBalls()::contains).count();
        long warm = reds.stream().filter(pools.getRedWarmBalls()::contains).count();
        long cold = reds.stream().filter(pools.getRedColdBalls()::contains).count();
        if (hot + warm + cold != 6) throw new IllegalArgumentException("冷热池未完整覆盖六个红球");
        return hot + ":" + warm + ":" + cold;
    }

    public static String blueColdWarmHotValue(Integer blue, ColdHotAnalysisBo pools) {
        if (blue == null || blue < 1 || blue > 16) throw new IllegalArgumentException("蓝球号码必须为1到16");
        if (pools.getBlueHotBalls().contains(blue)) return "热";
        if (pools.getBlueWarmBalls().contains(blue)) return "温";
        if (pools.getBlueColdBalls().contains(blue)) return "冷";
        throw new IllegalArgumentException("冷热池未覆盖本期蓝球");
    }

    /** Java分支只适配其实际产出的维度，不将事实统计伪装成大模型预测。 */
    public List<PatternFeatureAnalysisBo> adaptJava(FeatureForecastBo forecast, List<HistoryRecord> records) {
        if (records.size() < HISTORY_SIZE + COLD_HOT_WINDOW) {
            throw new IllegalStateException("新形态报告至少需要60期开奖");
        }
        return prepare(records).stream().map(req -> {
            var item = forecast.itemOf(req.getCode());
            List<String> values = item == null ? List.of() : req.getAllowedValues().stream()
                .filter(value -> FeatureForecastHitUtils.matches(item.getValue(), value)
                    || (item.getAlternatives() != null && item.getAlternatives().stream()
                        .anyMatch(alt -> FeatureForecastHitUtils.matches(alt, value)))).toList();
            return PatternFeatureAnalysisBo.builder().code(req.getCode()).label(req.getLabel())
                .status(item == null ? "UNAVAILABLE" : "OK").source("java").observations(req.getObservations())
                .sampleSize(req.getObservations().size())
                .patternSummary(item == null ? "Java指数分支未提供该特征推演，仅展示历史事实。" : item.getReason())
                .nextPeriodInference(item == null ? "无下期推演。" : "Java指数候选：" + String.join("、", values))
                .likelyValues(values).unlikelyValues(List.of()).likelyReason(item == null ? null : item.getReason())
                .build();
        }).toList();
    }

    /** 旧文件只有指数预测，无当时开奖上下文；只迁移八维已有信息，不重算过去。 */
    public static List<PatternFeatureAnalysisBo> adaptLegacy(FeatureForecastBo forecast) {
        if (forecast == null) return List.of();
        return Arrays.stream(FeatureKind.values()).filter(kind -> !kind.isBlue()
            && !kind.getCode().startsWith("zone")).map(kind -> {
                var item = forecast.itemOf(kind.getCode());
                List<String> likely = item == null ? List.of() : LotteryFeatureTrendUtils.buckets(kind).stream()
                    .filter(value -> FeatureForecastHitUtils.matches(item.getValue(), value)
                        || (item.getAlternatives() != null && item.getAlternatives().stream()
                            .anyMatch(alt -> FeatureForecastHitUtils.matches(alt, value)))).toList();
                return PatternFeatureAnalysisBo.builder().code(kind.getCode()).label(kind.getLabel())
                    .status(item == null ? "UNAVAILABLE" : "OK").source("legacy").observations(List.of())
                    .patternSummary(item == null ? "旧报告未包含该项。" : item.getReason())
                    .nextPeriodInference("历史指数预测，非最近30期大模型形态观察；旧文件没有完整开奖上下文。")
                    .likelyValues(likely).unlikelyValues(List.of()).build();
            }).toList();
    }

    private static List<Integer> reds(HistoryRecord r) {
        return List.of(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
    }

    private static String definition(FeatureKind kind) {
        return switch (kind) {
            case ODD_EVEN -> "奇数:偶数，两项合计6。";
            case BIG_SMALL -> "大号:小号；17-33为大，01-16为小，两项合计6。";
            case PRIME_COMP -> "质:合；01计为质数，质数池1,2,3,5,7,11,13,17,19,23,29,31，两项合计6。";
            case RATIO_012 -> "红球%3的余数0:1:2的个数比例，三项合计6。";
            case SPAN -> "最大红球减最小红球，精确值5-32。";
            case SUM_RANGE -> "六个红球之和落入的候选闭区间，含两端。";
            case SUM_TAIL -> "六个红球之和%10，取值0-9；不是单个号码尾数。";
            case THREE_ZONE -> "一区:二区:三区的个数比，一区01-11，二区12-22，三区23-33，合计6。";
            case ZONE1_COUNT -> "本期六个红球中落在一区01-11的个数，取值0到6；观察实际个数序列。";
            case ZONE2_COUNT -> "本期六个红球中落在二区12-22的个数，取值0到6；观察实际个数序列。";
            case ZONE3_COUNT -> "本期六个红球中落在三区23-33的个数，取值0到6；观察实际个数序列。";
            case BLUE_ODD_EVEN -> "本期蓝球的奇偶，取值奇、偶；只观察类别的连出与交替。";
            case BLUE_BIG_SMALL -> "本期蓝球的大小，01-08为小，09-16为大；取值小、大。";
            case BLUE_BIG_SMALL_ODD_EVEN -> "本期蓝球大小与奇偶的组合，取值小奇、小偶、大奇、大偶。";
            case BLUE_RATIO_012 -> "本期蓝球号码%3对应0路、1路、2路；是单个蓝球类别，不是红球比例。";
        };
    }
}
