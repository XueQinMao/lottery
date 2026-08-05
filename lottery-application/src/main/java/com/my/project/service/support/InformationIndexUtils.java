package com.my.project.service.support;

import cn.hutool.core.collection.CollectionUtil;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 指数趋势工具
 *
 * @author 刘强
 * @version 2026/08/31
 **/
public final class InformationIndexUtils {

    public static final double RED_P =
        (double) LotteryPatternTrendUtils.RED_DRAW / LotteryPatternTrendUtils.RED_TOTAL;
    public static final double BLUE_P = 1.0 / LotteryFeatureTrendUtils.BLUE_TOTAL;
    /** 指数预测窗口：近 2 期，均值作预测值，窗口内高于预测值的占比决定同比 */
    public static final int PREDICT_WINDOW = 2;
    private static final double EPS = 1e-9;

    public static Report analyze(List<HistoryRecord> newestFirst) {
        Assert.notEmpty(newestFirst, "历史开奖不能为空");
        var chronological = newestFirst.stream().sorted(Comparator.comparing(HistoryRecord::getPeriod)).toList();
        var latest = chronological.getLast();
        var latestReds = redsOf(latest);
        var redBalls = IntStream.rangeClosed(1, 33).boxed()
                .map(ball -> forecastBall(chronological, "red", ball, RED_P, latestReds.contains(ball))).toList();

        var blueBalls = IntStream.rangeClosed(1, 16).boxed()
                .map(ball -> forecastBall(chronological, "blue", ball, BLUE_P,
                        latest.getSpecial() != null && latest.getSpecial().equals(ball))).toList();
        var featureGroups = Arrays.stream(FeatureKindEnums.values()).map(kindEnumsFeatureGroupFunction(newestFirst)).toList();

        return Report.builder()
                .sampleSize(chronological.size())
                .fromPeriod(chronological.getFirst().getPeriod())
                .toPeriod(latest.getPeriod())
                .redBalls(redBalls)
                .blueBalls(blueBalls)
                .features(featureGroups)
                .build();


    }

    private static Function<FeatureKindEnums, FeatureGroup> kindEnumsFeatureGroupFunction(List<HistoryRecord> newestFirst) {
        return e -> {
            var trend = OmissionUtils.omissionFeatureTrendAnalyzer(e.getCode(), e.getVals().getFirst(), newestFirst);
            var buckets = CollectionUtils.emptyIfNull(trend.getRatioOptions()).stream().map(opt -> fromRatioOption(trend, opt)).toList();
            return FeatureGroup.builder()
                    .code(e.getCode())
                    .label(e.getLabel())
                    .latestValue(trend.getLatestRatio())
                    .buckets(buckets)
                    .build();
        };
    }

    /**
     * 用 {@code historyRecordService.analyzePatternTrend} 返回的趋势，计算当前 ratio 的信息指数。
     */
    public static Forecast fromPatternTrend(PatternTrendVo trend) {
        if (trend == null || trend.getStats() == null || CollectionUtil.isEmpty(trend.getIndexValues())) {
            throw new IllegalArgumentException("形态趋势不能为空");
        }
        boolean inLatest = trend.getRatio() != null && trend.getRatio().equals(trend.getLatestRatio());
        return forecast(trend.getRatio(), trend.getHits(), trend.getIndexValues(),
            trend.getStats().getTheoreticalProb(), trend.getStats().getHitCount(),
            trend.getStats().getTotalPeriods(), inLatest);
    }

    static Forecast fromRatioOption(PatternTrendVo trend, PatternTrendVo.RatioOption opt) {
        var hits = trend.getActuals().stream().map(opt.getRatio()::equals).toList();
        return forecast(opt.getRatio(), hits, opt.getIndexValues(), opt.getTheoreticalProb(),
            opt.getHitCount(), trend.getStats().getTotalPeriods(), opt.getRatio().equals(trend.getLatestRatio()));
    }

    private static Forecast forecastBall(List<HistoryRecord> chronological, String type, int ball, double p,
                                         boolean inLatest) {
        //计算出所有周期内指定球命中的情况
        var hits = chronological.stream().map(r -> "red".equals(type) ? redsOf(r).contains(ball) : (r.getSpecial() != null && r.getSpecial() == ball)).toList();
        //计算均线趋势
        var result = LotteryPatternTrendUtils.analyze(hits, p);
        return forecast(String.valueOf(ball), hits, result.getIndexValues(), p, result.getStats().getHitCount(),
                result.getStats().getTotalPeriods(), inLatest);
    }

    static Forecast forecast(String key, List<Boolean> hits, List<Double> indexValues, double p, int hitCount,
        int totalPeriods, boolean inLatest) {
        var series = indexValues == null ? List.<Double>of() : indexValues;
        var current = windowSignal(series, series.size() - 1);
        var tally = backtest(hits, series);
        var information = tally.informationOf(current.trend(), hitCount, totalPeriods);

        return Forecast.builder()
            .key(key)
            .actualHits(hitCount)
            .theoreticalHits(LotteryPatternTrendUtils.round2(totalPeriods * p))
            .index(LotteryPatternTrendUtils.round2(hitCount - totalPeriods * p))
            .predictedIndex(LotteryPatternTrendUtils.round2(current.predictedIndex()))
            .comparison(current.trend().label())
            .informationIndex(LotteryPatternTrendUtils.round4(information.index()))
            .theoreticalProb(LotteryPatternTrendUtils.round6(p))
            .inLatest(inLatest)
            .upSignals(tally.upSignals())
            .upHits(tally.upHits())
            .downSignals(tally.downSignals())
            .downHits(tally.downHits())
            .signalCount(information.signalCount())
            .signalHits(information.signalHits())
            .build();
    }

    /**
     * 站在 {@code endInclusive} 期：近窗均值作预测值，窗口内高于预测值的占比决定同比。
     */
    private static WindowSignal windowSignal(List<Double> indexValues, int endInclusive) {
        var window = windowEndingAt(indexValues, endInclusive).stream()
            .filter(Objects::nonNull)
            .toList();
        double predicted = mean(window);
        double greaterRatio = window.isEmpty() ? 0.5
            : window.stream().filter(v -> v > predicted + EPS).count() / (double) window.size();
        return new WindowSignal(predicted, Trend.fromGreaterRatio(greaterRatio));
    }

    private static SignalTally backtest(List<Boolean> hits, List<Double> indexValues) {
        int n = indexValues.size();
        int start = PREDICT_WINDOW - 1;
        if (hits == null || n <= start + 1) {
            return SignalTally.empty();
        }
        var grouped = IntStream.range(start, n - 1)
            .mapToObj(i -> new DatedSignal(windowSignal(indexValues, i).trend(), Boolean.TRUE.equals(hits.get(i + 1))))
            .filter(s -> s.trend() != Trend.FLAT)
            .collect(Collectors.groupingBy(DatedSignal::trend));
        var ups = grouped.getOrDefault(Trend.UP, List.of());
        var downs = grouped.getOrDefault(Trend.DOWN, List.of());
        return new SignalTally(
            ups.size(),
            (int) ups.stream().filter(DatedSignal::nextHit).count(),
            downs.size(),
            (int) downs.stream().filter(DatedSignal::nextHit).count());
    }

    private static List<Double> windowEndingAt(List<Double> values, int endInclusive) {
        if (values == null || values.isEmpty() || endInclusive < 0) {
            return List.of();
        }
        int to = Math.min(endInclusive, values.size() - 1);
        int from = Math.max(0, to - InformationIndexUtils.PREDICT_WINDOW + 1);
        return values.subList(from, to + 1);
    }

    private static double mean(List<Double> values) {
        return values.stream()
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
    }

    private enum Trend {
        UP("大"),
        DOWN("小"),
        FLAT("平");

        private final String label;

        Trend(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Trend fromGreaterRatio(double greaterRatio) {
            if (greaterRatio > 0.5 + EPS) {
                return UP;
            }
            if (greaterRatio < 0.5 - EPS) {
                return DOWN;
            }
            return FLAT;
        }
    }

    private record WindowSignal(double predictedIndex, Trend trend) {}

    private record DatedSignal(Trend trend, boolean nextHit) {}

    private record Information(double index, int signalCount, int signalHits) {}

    private record SignalTally(int upSignals, int upHits, int downSignals, int downHits) {
        static SignalTally empty() {
            return new SignalTally(0, 0, 0, 0);
        }

        Information informationOf(Trend trend, int hitCount, int totalPeriods) {
            return switch (trend) {
                case UP -> ofRate(upHits, upSignals);
                case DOWN -> ofRate(downHits, downSignals);
                case FLAT -> ofRate(hitCount, totalPeriods);
            };
        }

        private static Information ofRate(int hits, int total) {
            return new Information(total > 0 ? (double) hits / total : 0, total, hits);
        }
    }

    private static List<Integer> redsOf(HistoryRecord r) {
        return List.of(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
    }

    private static void appendTable(StringBuilder sb, List<Forecast> rows) {
        sb.append("| 取值 | 实出 | 理论 | 指数 | 预测指数 | 同比 | 信息指数 | 最近 |\n");
        sb.append("|---|---:|---:|---:|---:|---|---:|---|\n");
        for (Forecast row : rows) {
            sb.append("| ").append(row.getKey())
                .append(" | ").append(row.getActualHits())
                .append(" | ").append(fmt(row.getTheoreticalHits()))
                .append(" | ").append(fmt(row.getIndex()))
                .append(" | ").append(fmt(row.getPredictedIndex()))
                .append(" | ").append(row.getComparison())
                .append(" | ").append(pct(row.getInformationIndex()))
                .append(" | ").append(row.isInLatest() ? "是" : "")
                .append(" |\n");
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String pct(double p) {
        return String.format(Locale.ROOT, "%.2f%%", p * 100);
    }

    @Data
    @Builder
    public static class Forecast {
        /** 球号或形态取值 */
        private String key;
        /** 实出次数 */
        private int actualHits;
        /** 理论次数 n×p */
        private double theoreticalHits;
        /** 最新指数 = 实出 − 理论 */
        private double index;
        /** 下一期指数预测（近 {@link InformationIndexUtils#PREDICT_WINDOW} 期均值） */
        private double predictedIndex;
        /** 大 / 小 / 平 */
        private String comparison;
        /** 信息指数（信心指数），0~1 */
        private double informationIndex;
        /** 理论概率 p（红球 6/33、蓝球 1/16，形态按组合数） */
        private double theoreticalProb;
        /** 该取值是否出现在最近一期 */
        private boolean inLatest;
        /** 样本内发出「大」信号的次数 */
        private int upSignals;
        /** 「大」信号后下一期实际命中的次数 */
        private int upHits;
        /** 样本内发出「小」信号的次数 */
        private int downSignals;
        /** 「小」信号后下一期实际命中的次数 */
        private int downHits;
        /** 当前同比对应的历史信号次数；平则用全样本期数 */
        private int signalCount;
        /** 当前同比对应的历史命中次数；平则用全样本实出次数 */
        private int signalHits;

        public boolean isBullish() {
            return "大".equals(comparison);
        }
    }

    @Data
    @Builder
    public static class FeatureGroup {
        private String code;
        private String label;
        private String latestValue;
        private List<Forecast> buckets;
    }

    @Data
    @Builder
    public static class Report {
        private int sampleSize;
        private String fromPeriod;
        private String toPeriod;
        private List<Forecast> redBalls;
        private List<Forecast> blueBalls;
        private List<FeatureGroup> features;
    }
}
