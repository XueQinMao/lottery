package com.my.project.service.support;

import cn.hutool.core.collection.CollectionUtil;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 信息指数（信心指数）工具：不接入推荐/调优主流程。
 *
 * <p>与形态趋势页同一套超额指数：指数 = 累计实出次数 − n×p（命中 +(1-p)，未命中 −p）。
 * 下一期指数预测取近 {@link #PREDICT_WINDOW} 期指数均值（均值回归）。
 * 预测值 &gt; 最近一期指数 → 同比「大」，认为倾向开出；反之「小」。
 * 信息指数 = 样本内同样发出该信号后，下一期实际命中的比例。
 *
 * @author 刘强
 * @version 2026/08/31
 **/
public final class InformationIndexUtils {

    public static final double RED_P =
        (double) LotteryPatternTrendUtils.RED_DRAW / LotteryPatternTrendUtils.RED_TOTAL;
    public static final double BLUE_P = 1.0 / LotteryFeatureTrendUtils.BLUE_TOTAL;
    /** 指数预测窗口：近 2 期均值，对应预测表「本期指数预测」 */
    public static final int PREDICT_WINDOW = 2;
    private static final double EPS = 1e-9;

    private InformationIndexUtils() {
    }

    /**
     * @param newestFirst 历史开奖，最新在前（与 {@code analyzePatternTrend} 入参一致）
     */
    public static Report analyze(List<HistoryRecord> newestFirst) {
        if (CollectionUtil.isEmpty(newestFirst)) {
            throw new IllegalArgumentException("历史开奖不能为空");
        }
        List<HistoryRecord> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        HistoryRecord latest = chronological.get(chronological.size() - 1);
        List<Integer> latestReds = redsOf(latest);

        List<Forecast> redBalls = new ArrayList<>(LotteryPatternTrendUtils.RED_TOTAL);
        for (int ball = 1; ball <= LotteryPatternTrendUtils.RED_TOTAL; ball++) {
            redBalls.add(forecastBall(chronological, "red", ball, RED_P, latestReds.contains(ball)));
        }
        List<Forecast> blueBalls = new ArrayList<>(LotteryFeatureTrendUtils.BLUE_TOTAL);
        for (int ball = 1; ball <= LotteryFeatureTrendUtils.BLUE_TOTAL; ball++) {
            blueBalls.add(forecastBall(chronological, "blue", ball, BLUE_P,
                latest.getSpecial() != null && latest.getSpecial() == ball));
        }

        List<FeatureGroup> features = new ArrayList<>();
        for (FeatureKindEnums kind : FeatureKindEnums.values()) {
            PatternTrendVo trend =
                OmissionUtils.omissionFeatureTrendAnalyzer(kind.getCode(), kind.getVals().get(0), newestFirst);
            List<Forecast> buckets = new ArrayList<>(kind.getVals().size());
            for (PatternTrendVo.RatioOption opt : trend.getRatioOptions()) {
                buckets.add(fromRatioOption(trend, opt));
            }
            features.add(FeatureGroup.builder()
                .code(kind.getCode())
                .label(kind.getLabel())
                .latestValue(trend.getLatestRatio())
                .buckets(buckets)
                .build());
        }

        return Report.builder()
            .sampleSize(chronological.size())
            .fromPeriod(chronological.get(0).getPeriod())
            .toPeriod(latest.getPeriod())
            .redBalls(redBalls)
            .blueBalls(blueBalls)
            .features(features)
            .build();
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

    public static String formatReport(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "样本 %d 期（%s → %s）%n%n",
            report.getSampleSize(), report.getFromPeriod(), report.getToPeriod()));
        sb.append("## 红球\n");
        appendTable(sb, report.getRedBalls());
        sb.append("\n## 蓝球\n");
        appendTable(sb, report.getBlueBalls());
        for (FeatureGroup group : report.getFeatures()) {
            sb.append("\n## ").append(group.getLabel())
                .append("（最近一期 ").append(group.getLatestValue()).append("）\n");
            appendTable(sb, group.getBuckets());
        }
        return sb.toString();
    }

    static Forecast fromRatioOption(PatternTrendVo trend, PatternTrendVo.RatioOption opt) {
        List<Boolean> hits = new ArrayList<>(trend.getActuals().size());
        for (String actual : trend.getActuals()) {
            hits.add(opt.getRatio().equals(actual));
        }
        boolean inLatest = opt.getRatio().equals(trend.getLatestRatio());
        return forecast(opt.getRatio(), hits, opt.getIndexValues(), opt.getTheoreticalProb(),
            opt.getHitCount(), trend.getStats().getTotalPeriods(), inLatest);
    }

    private static Forecast forecastBall(List<HistoryRecord> chronological, String type, int ball, double p,
        boolean inLatest) {
        List<Boolean> hits = new ArrayList<>(chronological.size());
        for (HistoryRecord r : chronological) {
            if ("red".equals(type)) {
                hits.add(redsOf(r).contains(ball));
            } else {
                hits.add(r.getSpecial() != null && r.getSpecial() == ball);
            }
        }
        LotteryPatternTrendUtils.PatternTrendResult result = LotteryPatternTrendUtils.analyze(hits, p);
        LotteryPatternTrendUtils.PatternTrendStats stats = result.getStats();
        return forecast(String.valueOf(ball), hits, result.getIndexValues(), p, stats.getHitCount(),
            stats.getTotalPeriods(), inLatest);
    }

    static Forecast forecast(String key, List<Boolean> hits, List<Double> indexValues, double p, int hitCount,
        int totalPeriods, boolean inLatest) {
        int n = indexValues == null ? 0 : indexValues.size();
        double lastIndex = n == 0 ? 0 : indexValues.get(n - 1);
        double predictedIndex = n == 0 ? 0 : meanTail(indexValues, PREDICT_WINDOW);
        String comparison = compare(predictedIndex, lastIndex);

        int upSignals = 0;
        int upHits = 0;
        int downSignals = 0;
        int downHits = 0;
        int start = PREDICT_WINDOW - 1;
        if (hits != null && n > start + 1) {
            for (int i = start; i < n - 1; i++) {
                double pred = meanRange(indexValues, i - PREDICT_WINDOW + 1, i);
                String signal = compare(pred, indexValues.get(i));
                boolean nextHit = Boolean.TRUE.equals(hits.get(i + 1));
                if ("大".equals(signal)) {
                    upSignals++;
                    if (nextHit) {
                        upHits++;
                    }
                } else if ("小".equals(signal)) {
                    downSignals++;
                    if (nextHit) {
                        downHits++;
                    }
                }
            }
        }

        double informationIndex;
        int signalCount;
        int signalHits;
        if ("大".equals(comparison)) {
            signalCount = upSignals;
            signalHits = upHits;
            informationIndex = upSignals > 0 ? (double) upHits / upSignals : 0;
        } else if ("小".equals(comparison)) {
            signalCount = downSignals;
            signalHits = downHits;
            informationIndex = downSignals > 0 ? (double) downHits / downSignals : 0;
        } else {
            signalCount = totalPeriods;
            signalHits = hitCount;
            informationIndex = totalPeriods > 0 ? (double) hitCount / totalPeriods : 0;
        }

        return Forecast.builder()
            .key(key)
            .actualHits(hitCount)
            .theoreticalHits(LotteryPatternTrendUtils.round2(totalPeriods * p))
            .index(LotteryPatternTrendUtils.round2(hitCount - totalPeriods * p))
            .predictedIndex(LotteryPatternTrendUtils.round2(predictedIndex))
            .comparison(comparison)
            .informationIndex(LotteryPatternTrendUtils.round4(informationIndex))
            .theoreticalProb(LotteryPatternTrendUtils.round6(p))
            .inLatest(inLatest)
            .upSignals(upSignals)
            .upHits(upHits)
            .downSignals(downSignals)
            .downHits(downHits)
            .signalCount(signalCount)
            .signalHits(signalHits)
            .build();
    }

    private static String compare(double predicted, double last) {
        if (predicted > last + EPS) {
            return "大";
        }
        if (predicted < last - EPS) {
            return "小";
        }
        return "平";
    }

    private static double meanTail(List<Double> values, int window) {
        int n = values.size();
        int from = Math.max(0, n - window);
        return meanRange(values, from, n - 1);
    }

    private static double meanRange(List<Double> values, int fromInclusive, int toInclusive) {
        int from = Math.max(0, fromInclusive);
        int to = Math.min(values.size() - 1, toInclusive);
        if (to < from) {
            return 0;
        }
        double sum = 0;
        int count = 0;
        for (int i = from; i <= to; i++) {
            if (values.get(i) != null) {
                sum += values.get(i);
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
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
        /** 下一期指数预测（近窗均值） */
        private double predictedIndex;
        /** 大 / 小 / 平 */
        private String comparison;
        /** 信息指数（信心指数），0~1 */
        private double informationIndex;
        private double theoreticalProb;
        /** 是否出现在最近一期 */
        private boolean inLatest;
        private int upSignals;
        private int upHits;
        private int downSignals;
        private int downHits;
        private int signalCount;
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
