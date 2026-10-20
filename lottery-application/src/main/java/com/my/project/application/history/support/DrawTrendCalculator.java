package com.my.project.application.history.support;

import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.history.vo.DrawTrendVo.Group;
import com.my.project.application.history.vo.DrawTrendVo.Series;
import com.my.project.application.support.LotteryFeatureTrendUtils;
import com.my.project.application.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.ColdHotAnalysisBo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public final class DrawTrendCalculator {
    public static final int COLD_HOT_WINDOW = 30;

    private DrawTrendCalculator() { }

    public static DrawTrendVo calculate(List<HistoryRecord> newestFirst, int sampleSize) {
        List<Snapshot> snapshots = new ArrayList<>();
        for (int index = Math.min(sampleSize, newestFirst.size()) - 1; index >= 0; index--) {
            var record = newestFirst.get(index);
            var reds = IntStream.of(record.getNum1(), record.getNum2(), record.getNum3(),
                record.getNum4(), record.getNum5(), record.getNum6()).sorted().boxed().toList();
            ColdHotAnalysisBo bands = index + COLD_HOT_WINDOW < newestFirst.size()
                ? ColdHotAnalysisUtils.calculate(new ArrayList<>(newestFirst.subList(index + 1,
                    index + COLD_HOT_WINDOW + 1))) : null;
            snapshots.add(new Snapshot(new DrawTrendVo.Draw(record.getPeriod(), reds, record.getSpecial()), bands));
        }
        return new DrawTrendVo(snapshots.stream().map(s -> s.draw().period()).toList(),
            snapshots.stream().map(Snapshot::draw).toList(), redFeatures(snapshots), blueFeatures(snapshots),
            "每期仅使用开奖前 30 期，复用系统冷温热算法（频次初档 + 遗漏趋势纠偏）；前置历史不足 30 期时留空，不参与均值。");
    }

    private static List<Group> redFeatures(List<Snapshot> rows) {
        List<Group> groups = new ArrayList<>();
        groups.add(temperature(rows, "coldHot", "冷温热个数", false, n -> true));
        groups.add(group(rows, "zones", "三区个数", "一区 1–11，二区 12–22，三区 23–33。", false, 0, 6,
            IntStream.rangeClosed(1, 3).mapToObj(zone -> metric("zone" + zone, zoneLabel(zone),
                s -> s.redCount(n -> (n - 1) / 11 + 1 == zone))).toList()));
        groups.add(ratios(rows, "threeZone", "三区比", "比例顺序：一区:二区:三区；含 0 的全部 28 种组合。", FeatureKind.THREE_ZONE));
        groups.add(parity(rows, false));
        groups.add(ratios(rows, "oddEvenRatio", "奇偶比", "比例顺序：奇数:偶数；全部 7 种组合。", FeatureKind.ODD_EVEN));
        groups.add(roads(rows, "road", "012 路个数", "号码除以 3 的余数为路数。", false,
            (s, road) -> s.redCount(n -> n % 3 == road)));
        groups.add(ratios(rows, "roadRatio", "012 路比", "比例顺序：0路:1路:2路；含 0 的全部 28 种组合。", FeatureKind.RATIO_012));
        groups.add(group(rows, "sum", "和值", "六个红球号码相加。", false, 21, 183,
            List.of(metric("sum", "红球和值", s -> s.draw().reds().stream().mapToInt(Integer::intValue).sum()))));
        groups.add(group(rows, "span", "跨度", "最大红球减去最小红球。", false, 5, 32,
            List.of(metric("span", "红球跨度", s -> s.draw().reds().getLast() - s.draw().reds().getFirst()))));
        groups.add(roads(rows, "headRoad", "龙头 012 路", "龙头为当期最小红球；1=属于该路，0=不属于。", true,
            (s, road) -> flag(s.draw().reds().getFirst() % 3 == road)));
        groups.add(roads(rows, "tailRoad", "凤尾 012 路", "凤尾为当期最大红球；1=属于该路，0=不属于。", true,
            (s, road) -> flag(s.draw().reds().getLast() % 3 == road)));
        groups.add(size(rows, false));
        for (int zone = 1; zone <= 3; zone++) {
            int selectedZone = zone;
            groups.add(temperature(rows, "zone" + zone + "ColdHot", zoneLabel(zone) + "冷温热", false,
                n -> (n - 1) / 11 + 1 == selectedZone));
        }
        return groups;
    }

    private static List<Group> blueFeatures(List<Snapshot> rows) {
        return List.of(temperature(rows, "coldHot", "冷温热个数", true, n -> true), parity(rows, true),
            roads(rows, "road", "012 路", "蓝球号码除以 3 的余数；1=属于该路，0=不属于。", true,
                (s, road) -> flag(s.draw().blue() % 3 == road)),
            group(rows, "intervals", "四区间分布", "1–4、5–8、9–12、13–16；1=落入该区间，0=未落入。", true, 0, 1,
                IntStream.rangeClosed(1, 4).mapToObj(zone -> metric("zone" + zone,
                    (zone * 4 - 3) + "–" + zone * 4,
                    s -> flag((s.draw().blue() - 1) / 4 + 1 == zone))).toList()), size(rows, true));
    }

    private static Group parity(List<Snapshot> rows, boolean blue) {
        return group(rows, "oddEven", "奇偶个数", blue ? "每期蓝球奇、偶分别记为 0 或 1。" : "每期奇数与偶数个数之和为 6。",
            false, 0, blue ? 1 : 6, List.of(
                metric("odd", "奇数", s -> s.count(blue, n -> n % 2 == 1)),
                metric("even", "偶数", s -> s.count(blue, n -> n % 2 == 0))));
    }

    private static Group size(List<Snapshot> rows, boolean blue) {
        int boundary = blue ? 8 : 16;
        return group(rows, "size", "大小个数", blue ? "小号 1–8，大号 9–16。" : "小号 1–16，大号 17–33。",
            blue, 0, blue ? 1 : 6, List.of(
                metric("small", blue ? "小号 · 1–8" : "小号 · 1–16", s -> s.count(blue, n -> n <= boundary)),
                metric("big", blue ? "大号 · 9–16" : "大号 · 17–33", s -> s.count(blue, n -> n > boundary))));
    }

    private static Group roads(List<Snapshot> rows, String code, String label, String description,
                               boolean occurrence, RoadCounter counter) {
        return group(rows, code, label, description, occurrence, 0, occurrence ? 1 : 6,
            IntStream.range(0, 3).mapToObj(road -> metric(String.valueOf(road), road + " 路",
                s -> counter.count(s, road))).toList());
    }

    private static Group ratios(List<Snapshot> rows, String code, String label, String description, FeatureKind kind) {
        return group(rows, code, label, description, true, 0, 1,
            LotteryFeatureTrendUtils.buckets(kind).stream().map(ratio -> metric(ratio, ratio,
                s -> flag(ratio.equals(LotteryFeatureTrendUtils.extract(s.draw().reds(), kind))))).toList());
    }

    private static Group temperature(List<Snapshot> rows, String code, String label, boolean blue, IntPredicate range) {
        return group(rows, code, label, "沿用系统冷温热分类；仅使用开奖前 30 期，样本不足留空。", false, 0, blue ? 1 : 6,
            List.of(bandMetric("cold", "冷球", blue, range, blue ? ColdHotAnalysisBo::getBlueColdBalls : ColdHotAnalysisBo::getRedColdBalls),
                bandMetric("warm", "温球", blue, range, blue ? ColdHotAnalysisBo::getBlueWarmBalls : ColdHotAnalysisBo::getRedWarmBalls),
                bandMetric("hot", "热球", blue, range, blue ? ColdHotAnalysisBo::getBlueHotBalls : ColdHotAnalysisBo::getRedHotBalls)));
    }

    private static Metric bandMetric(String code, String label, boolean blue, IntPredicate range,
                                     Function<ColdHotAnalysisBo, List<Integer>> balls) {
        return metric(code, label, s -> s.bands() == null ? null
            : s.count(blue, n -> range.test(n) && balls.apply(s.bands()).contains(n)));
    }

    private static Group group(List<Snapshot> rows, String code, String label, String description,
                               boolean occurrence, int min, int max, List<Metric> metrics) {
        return new Group(code, label, description, occurrence, min, max, metrics.stream().map(metric -> {
            List<Integer> values = rows.stream().map(metric.value()).toList();
            int total = values.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            int count = (int) values.stream().filter(Objects::nonNull).count();
            return new Series(metric.code(), metric.label(), values, count == 0 ? 0 : (double) total / count, total, count);
        }).toList());
    }

    private static Metric metric(String code, String label, Function<Snapshot, Integer> value) {
        return new Metric(code, label, value);
    }

    private static String zoneLabel(int zone) {
        return List.of("一区 · 1–11", "二区 · 12–22", "三区 · 23–33").get(zone - 1);
    }

    private static int flag(boolean condition) { return condition ? 1 : 0; }

    private record Metric(String code, String label, Function<Snapshot, Integer> value) { }

    private interface RoadCounter { int count(Snapshot snapshot, int road); }

    private record Snapshot(DrawTrendVo.Draw draw, ColdHotAnalysisBo bands) {
        int count(boolean blue, IntPredicate condition) {
            return blue ? flag(condition.test(draw.blue())) : redCount(condition);
        }

        int redCount(IntPredicate condition) {
            return (int) draw.reds().stream().mapToInt(Integer::intValue).filter(condition).count();
        }
    }
}
