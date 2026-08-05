package com.my.project.service.support.analyse;

import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.support.LotteryTrendUtils;
import com.my.project.service.support.OmissionUtils;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * 均线分析工具：对红球 1–33、蓝球 1–16 取 5/10/20 期均线，识别形态后给出选号 / 杀号 / 保留。
 * <ul>
 *   <li>死亡谷：短、中期均线先后下穿长期均线，下跌初期 → 杀号</li>
 *   <li>粘合向上发散：三线缠绕后向上开口 → 选号</li>
 *   <li>粘合向下发散：三线缠绕后向下开口 → 杀号</li>
 *   <li>上山爬坡：三线沿坡上行 → 选号</li>
 *   <li>下山滑坡：三线沿坡下行 → 杀号</li>
 * </ul>
 */
public class MaAnalysisUtils {

    private static final int SLOPE_LOOKBACK = 3;
    /** 死亡谷：短/中期下穿长期的回看期数 */
    private static final int CROSS_LOOKBACK = 8;
    /** 粘合段最少期数 */
    private static final int COIL_MIN_BARS = 5;
    private static final int COIL_LOOKBACK = 8;
    /** 粘合结束后用于观察发散的近期期数 */
    private static final int DIVERGE_BARS = 3;
    /** 相对开口小于该值视为缠绕粘合 */
    private static final double COIL_RELATIVE = 0.12;
    /** 当前开口相对粘合段放大倍数，视为发散 */
    private static final double DIVERGE_RATIO = 1.5;

    /**
     * @param latest 历史开奖，最新在前（与 {@code omissionBallAnalyzer} 入参一致）
     */
    public static MaAnalysisResult analyze(List<HistoryRecord> latest) {
        Assert.notEmpty(latest, "历史开奖不能为空");
        var redBalls = IntStream.rangeClosed(1, 33).boxed()
            .map(ball -> analyzeBall(latest, "red", ball))
            .toList();
        var blueBalls = IntStream.rangeClosed(1, 16).boxed()
            .map(ball -> analyzeBall(latest, "blue", ball))
            .toList();
        return MaAnalysisResult.builder()
            .redBalls(redBalls)
            .blueBalls(blueBalls)
            .build();
    }

    private static MaResult analyzeBall(List<HistoryRecord> latest, String ballType, int ball) {
        var trend = OmissionUtils.omissionBallAnalyzer(latest, ballType, ball);
        var pattern = classify(trend.getMa5(), trend.getMa10(), trend.getMa20());
        MaResult build = MaResult.builder()
                .ball(ball)
                .ballType(ballType)
                .ma5(lastValue(trend.getMa5()))
                .ma10(lastValue(trend.getMa10()))
                .ma20(lastValue(trend.getMa20()))
                .arrangement(trend.getArrangement())
                .ma5Slope(trend.getMa5Slope())
                .phase(trend.getPhase())
                .pattern(pattern.label())
                .action(pattern.action())
                .build();
        build.setConfidence(maKillConfidence(build));
        return build;
    }

    private static double maKillConfidence(MaAnalysisUtils.MaResult r) {
        if (!"杀号".equals(r.getAction())) {
            return 0.0;
        }
        double base = switch (r.getPattern()) {
            case "死亡谷"       -> 0.80;
            case "粘合向下发散"  -> 0.70;
            case "下山滑坡"      -> 0.55;
            default             -> 0.0;   // 理论上不会进
        };
        if (base == 0.0) return 0.0;
        Double ma5 = r.getMa5(), ma10 = r.getMa10(), ma20 = r.getMa20();
        if (ma5 == null || ma10 == null || ma20 == null) {
            return base;
        }
        // 1) 空头开口度：(ma20 - ma5) / |ma20|，越大表示下跌越陡
        double spread = (ma20 - ma5) / Math.max(Math.abs(ma20), 0.5);
        double spreadBonus = Math.min(spread, 0.20);   // 上限 0.20
        // 2) 排列规整度：完美空头 ma5 < ma10 < ma20 给 0.05，否则 0
        double orderBonus = (ma5 < ma10 && ma10 < ma20) ? 0.05 : 0.0;
        return Math.min(base + spreadBonus + orderBonus, 1.0);
    }

    private static MaPattern classify(List<Double> ma5, List<Double> ma10, List<Double> ma20) {
        int last = ma5 == null ? -1 : ma5.size() - 1;
        return pointAt(ma5, ma10, ma20, last)
            .map(now -> classify(new MaContext(ma5, ma10, ma20, last, now)))
            .orElse(MaPattern.INSUFFICIENT);
    }

    private static MaPattern classify(MaContext ctx) {
        if (isDeathValley(ctx)) {
            return MaPattern.DEATH_VALLEY;
        }
        if (isCoilUp(ctx)) {
            return MaPattern.COIL_UP;
        }
        if (isCoilDown(ctx)) {
            return MaPattern.COIL_DOWN;
        }
        if (isClimb(ctx)) {
            return MaPattern.CLIMB;
        }
        if (isSlide(ctx)) {
            return MaPattern.SLIDE;
        }
        return MaPattern.HOLD;
    }

    /** 短、中期均线在回看窗口内先后下穿长期均线，且短期已拐头向下。 */
    private static boolean isDeathValley(MaContext ctx) {
        var now = ctx.now();
        if (now.ma5() >= now.ma20() || now.ma10() >= now.ma20() || ctx.slope5() >= 0) {
            return false;
        }
        var cross5 = lastDeathCross(ctx.ma5(), ctx.ma20(), ctx.last());
        var cross10 = lastDeathCross(ctx.ma10(), ctx.ma20(), ctx.last());
        return cross5.isPresent() && cross10.isPresent() && cross5.getAsInt() <= cross10.getAsInt();
    }

    /** 粘合后向上开口：三线曾缠绕，现多头排列且短、中期上行。 */
    private static boolean isCoilUp(MaContext ctx) {
        return isCoilDiverge(ctx, true);
    }

    /** 粘合后向下开口：三线曾缠绕，现空头排列且短、中期下行。 */
    private static boolean isCoilDown(MaContext ctx) {
        return isCoilDiverge(ctx, false);
    }

    private static boolean isCoilDiverge(MaContext ctx, boolean up) {
        return coilStats(ctx)
            .filter(coil -> relativeSpread(ctx.now()) > Math.max(coil.avgRelative() * DIVERGE_RATIO, COIL_RELATIVE))
            .filter(coil -> up
                ? ctx.now().bullishStack() && ctx.slope5() > 0 && ctx.slope10() > 0
                : ctx.now().bearishStack() && ctx.slope5() < 0 && ctx.slope10() < 0)
            .isPresent();
    }

    /** 三线沿坡上行。 */
    private static boolean isClimb(MaContext ctx) {
        return ctx.slope5() > 0 && ctx.slope10() > 0 && ctx.slope20() > 0
            && (ctx.now().bullishStack() || ctx.now().ma5() > ctx.now().ma20());
    }

    /** 三线沿坡下行。 */
    private static boolean isSlide(MaContext ctx) {
        return ctx.slope5() < 0 && ctx.slope10() < 0 && ctx.slope20() < 0
            && (ctx.now().bearishStack() || ctx.now().ma5() < ctx.now().ma20());
    }

    private static Optional<CoilStats> coilStats(MaContext ctx) {
        int coilEnd = ctx.last() - DIVERGE_BARS;
        int coilStart = coilEnd - COIL_LOOKBACK + 1;
        if (coilStart < 0 || coilEnd < coilStart) {
            return Optional.empty();
        }
        var relatives = IntStream.rangeClosed(coilStart, coilEnd)
            .mapToObj(i -> pointAt(ctx.ma5(), ctx.ma10(), ctx.ma20(), i))
            .flatMap(Optional::stream)
            .mapToDouble(MaAnalysisUtils::relativeSpread)
            .boxed()
            .toList();
        if (relatives.size() < COIL_MIN_BARS) {
            return Optional.empty();
        }
        double avg = relatives.stream().mapToDouble(Double::doubleValue).average().orElse(1);
        double max = relatives.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (avg >= COIL_RELATIVE || max >= COIL_RELATIVE * 2) {
            return Optional.empty();
        }
        return Optional.of(new CoilStats(avg));
    }

    private static OptionalInt lastDeathCross(List<Double> fast, List<Double> slow, int last) {
        int from = Math.max(1, last - CROSS_LOOKBACK + 1);
        return IntStream.iterate(last, i -> i >= from, i -> i - 1)
            .filter(i -> fast.get(i) != null && slow.get(i) != null
                && fast.get(i - 1) != null && slow.get(i - 1) != null)
            .filter(i -> fast.get(i - 1) >= slow.get(i - 1) && fast.get(i) < slow.get(i))
            .findFirst();
    }

    private static Optional<MaPoint> pointAt(List<Double> ma5, List<Double> ma10, List<Double> ma20, int i) {
        if (ma5 == null || ma10 == null || ma20 == null || i < 0 || i >= ma5.size()) {
            return Optional.empty();
        }
        if (ma5.get(i) == null || ma10.get(i) == null || ma20.get(i) == null) {
            return Optional.empty();
        }
        return Optional.of(new MaPoint(ma5.get(i), ma10.get(i), ma20.get(i)));
    }

    private static double relativeSpread(MaPoint point) {
        return point.spread() / Math.max(Math.abs(point.mid()), 0.5);
    }

    private static Double lastValue(List<Double> values) {
        return values == null || values.isEmpty() ? null : values.getLast();
    }

    private enum MaPattern {
        DEATH_VALLEY("死亡谷", "杀号"),
        COIL_UP("粘合向上发散", "选号"),
        COIL_DOWN("粘合向下发散", "杀号"),
        CLIMB("上山爬坡", "选号"),
        SLIDE("下山滑坡", "杀号"),
        HOLD("无", "保留"),
        INSUFFICIENT("数据不足", "保留");

        private final String label;
        private final String action;

        MaPattern(String label, String action) {
            this.label = label;
            this.action = action;
        }

        String label() {
            return label;
        }

        String action() {
            return action;
        }
    }

    private record MaPoint(double ma5, double ma10, double ma20) {
        double spread() {
            return Math.max(ma5, Math.max(ma10, ma20)) - Math.min(ma5, Math.min(ma10, ma20));
        }

        double mid() {
            return (ma5 + ma10 + ma20) / 3.0;
        }

        boolean bullishStack() {
            return ma5 > ma10 && ma10 > ma20;
        }

        boolean bearishStack() {
            return ma5 < ma10 && ma10 < ma20;
        }
    }

    private record MaContext(List<Double> ma5, List<Double> ma10, List<Double> ma20, int last, MaPoint now) {
        double slope5() {
            return LotteryTrendUtils.calcSlope(ma5, SLOPE_LOOKBACK);
        }

        double slope10() {
            return LotteryTrendUtils.calcSlope(ma10, SLOPE_LOOKBACK);
        }

        double slope20() {
            return LotteryTrendUtils.calcSlope(ma20, SLOPE_LOOKBACK);
        }
    }

    private record CoilStats(double avgRelative) {}

    @Data
    @Builder
    public static class MaResult {
        private Integer ball;
        private String ballType;
        /** 最新 5 期均线 */
        private Double ma5;
        /** 最新 10 期均线 */
        private Double ma10;
        /** 最新 20 期均线 */
        private Double ma20;
        /** 均线排列：1=多头, -1=空头, 0=交叉 */
        private Integer arrangement;
        /** MA5 近 3 期斜率 */
        private Double ma5Slope;
        /** rising / rebounding / falling / cooling / neutral */
        private String phase;
        /** 死亡谷 / 粘合向上发散 / 粘合向下发散 / 上山爬坡 / 下山滑坡 / 无 / 数据不足 */
        private String pattern;
        /** 选号 / 杀号 / 保留 */
        private String action;

        /**
         * 信任度
         */
        private Double confidence;
    }

    @Data
    @Builder
    public static class MaAnalysisResult {
        private List<MaResult> redBalls;
        private List<MaResult> blueBalls;
    }
}
