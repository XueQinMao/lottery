package com.my.project.service.support;

import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 按当前遗漏相对平均开出周期的位置，估计下一期开出概率。
 *
 * <p>单号 / 形态取值的计算口径一致：
 * <ol>
 *     <li>当前遗漏：从最近一期往前连续未出现的期数（最近一期出现则为 0）</li>
 *     <li>平均遗漏：理论开出周期 {@code 1/p}（红球 33/6=5.5，蓝球 16，形态为 1/理论概率）</li>
 *     <li>若当前遗漏 ≥ 平均周期：在全部历史开出次数中，统计「开出前遗漏 ≥ 平均周期」的次数，
 *         概率 = 该次数 / 总开出次数</li>
 *     <li>若当前遗漏 &lt; 平均周期（还差 {@code avg-current}）：统计「开出前遗漏恰好等于当前遗漏」
 *         的次数，概率 = 该次数 / 总开出次数</li>
 * </ol>
 *
 * <p>例：红球 15 出现 157 次，当前遗漏 6、平均周期 5.5（已达 1 个周期），
 * 其中开出前遗漏 ≥ 5.5 的有 23 次 → 23/157。
 * 若当前遗漏 3（距平均还差 2.5），则改用开出前遗漏 = 3 的次数作分子。
 **/
public final class OmissionDueProbabilityUtils {

    public static final double RED_THEORETICAL_CYCLE = (double) LotteryPatternTrendUtils.RED_TOTAL
        / LotteryPatternTrendUtils.RED_DRAW;
    public static final double BLUE_THEORETICAL_CYCLE = (double) LotteryFeatureTrendUtils.BLUE_TOTAL;

    public static final FeatureKind[] FEATURE_KINDS = {
        FeatureKind.ODD_EVEN,
        FeatureKind.BIG_SMALL,
        FeatureKind.PRIME_COMP,
        FeatureKind.RATIO_012,
        FeatureKind.SPAN,
        FeatureKind.SUM_RANGE,
        FeatureKind.SUM_TAIL,
        FeatureKind.THREE_ZONE,
        FeatureKind.ZONE1_COUNT,
        FeatureKind.ZONE2_COUNT,
        FeatureKind.ZONE3_COUNT
    };

    public static Map<String, Map<String, Double>> analyzeHistory(List<HistoryRecord> records) {
        return analyze(records);
    }

    public static Map<String, Map<String, Double>> analyze(List<HistoryRecord> historyRecords) {
        var historyMaps = historyRecords.stream().map(
                h -> Triple.of(h.getPeriod(),
                    List.of(h.getNum1(), h.getNum2(), h.getNum3(), h.getNum4(), h.getNum5(), h.getNum6()), h.getSpecial()))
            .collect(Collectors.toMap(Triple::getLeft, Function.identity(), (o1, o2) -> o2));

        Map<String, Map<String, Double>> probabilityMap = new HashMap<>();
        Map<String, Double> redProbabilityMap = new HashMap<>();
        IntStream.range(1, 34)
            .boxed()
            .map(ballNumber -> Pair.of(ballNumber, OmissionUtils.omissionBallAnalyzer(historyRecords, "red", ballNumber)))
            .forEach(pair -> {
                var vo = pair.getRight();
                var diffAvg = vo.getStats().getCurrentOmission() - vo.getStats().getAvgOmission();
                int hitCount = 0;
                int omissionSize = vo.getOmissions().size();
                for (int i = 0; i < omissionSize; i++) {
                    var diff = vo.getOmissions().get(i) - vo.getStats().getAvgOmission();
                    if (diff == diffAvg && i + 1 < vo.getPeriods().size()) {
                        var nextPeriod = vo.getPeriods().get(i + 1);
                        var triple = historyMaps.get(nextPeriod);
                        if (triple != null && triple.getMiddle().contains(pair.getLeft())) {
                            hitCount++;
                        }
                    }
                }
                double nextProbability = (double) hitCount / historyRecords.size();
                redProbabilityMap.put(String.valueOf(pair.getLeft()), nextProbability);
            });
        probabilityMap.put("红球", redProbabilityMap);

        Map<String, Double> blueProbabilityMap = new HashMap<>();
        IntStream.range(1, 17)
            .boxed()
            .map(ballNumber -> Pair.of(ballNumber, OmissionUtils.omissionBallAnalyzer(historyRecords, "blue", ballNumber)))
            .forEach(pair -> {
                var vo = pair.getRight();
                var diffAvg = vo.getStats().getCurrentOmission() - vo.getStats().getAvgOmission();
                int hitCount = 0;
                int omissionSize = vo.getOmissions().size();
                for (int i = 0; i < omissionSize; i++) {
                    var diff = vo.getOmissions().get(i) - vo.getStats().getAvgOmission();
                    if (diff == diffAvg && i + 1 < vo.getPeriods().size()) {
                        var nextPeriod = vo.getPeriods().get(i + 1);
                        var triple = historyMaps.get(nextPeriod);
                        if (triple != null && Objects.equals(triple.getRight(), pair.getLeft())) {
                            hitCount++;
                        }
                    }
                }
                double nextProbability = (double) hitCount / historyRecords.size();
                blueProbabilityMap.put(String.valueOf(pair.getLeft()), nextProbability);
            });
        probabilityMap.put("蓝球", blueProbabilityMap);
        //特征计算
        Arrays.stream(FeatureKindEnums.values()).forEach(enums ->{
            Map<String, Double> featureKindMap = new HashMap<>();
            enums.getVals().forEach(val ->{
                var vo =
                    OmissionUtils.omissionFeatureTrendAnalyzer(enums.getCode(), val, historyRecords);
                var diffAvg = vo.getStats().getCurrentOmission() - vo.getStats().getAvgOmission();
                int omissionSize = vo.getOmissions().size();
                int hitCount = 0;
                for (int i = 0; i < omissionSize; i++) {
                    var diff = vo.getOmissions().get(i) - vo.getStats().getAvgOmission();
                    if (diff == diffAvg && i + 1 < vo.getPeriods().size()) {
                        String s = vo.getActuals().get(i + 1);
                        if(s.equals(val)){
                            hitCount= hitCount+1;
                        }
                    }
                }
                double nextProbability = (double) hitCount / historyRecords.size();
                featureKindMap.put(val, nextProbability);
            });
            probabilityMap.put(enums.getLabel(), featureKindMap);
        });
        return probabilityMap;
    }

    public static Draw toDraw(HistoryRecord record) {
        return Draw.builder()
            .period(record.getPeriod())
            .openDate(record.getOpenDate())
            .redBalls(List.of(record.getNum1(), record.getNum2(), record.getNum3(),
                record.getNum4(), record.getNum5(), record.getNum6()))
            .blueBall(record.getSpecial())
            .build();
    }

    public static String formatReport(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("样本 %d 期（%s → %s）%n%n",
            report.getSampleSize(), report.getFromPeriod(), report.getToPeriod()));
        sb.append("## 红球\n");
        appendTable(sb, report.getRedBalls(), true);
        sb.append("\n## 蓝球\n");
        appendTable(sb, report.getBlueBalls(), true);
        for (FeatureGroup group : report.getFeatures()) {
            sb.append("\n## ").append(group.getLabel())
                .append("（最近一期 ").append(group.getLatestValue()).append("）\n");
            appendTable(sb, group.getBuckets(), false);
        }
        return sb.toString();
    }

    static DueStat analyzeKey(String key, double theoreticalCycle, List<Boolean> hits, boolean inLatest) {
        int n = hits.size();
        List<Integer> gaps = new ArrayList<>();
        int lastHit = -1;
        for (int i = 0; i < n; i++) {
            if (Boolean.TRUE.equals(hits.get(i))) {
                gaps.add(lastHit < 0 ? i : i - lastHit - 1);
                lastHit = i;
            }
        }
        int hitCount = gaps.size();
        int currentOmission = lastHit < 0 ? n : n - 1 - lastHit;
        double remaining = LotteryPatternTrendUtils.round2(theoreticalCycle - currentOmission);
        boolean reachedAvgCycle = hitCount > 0 && remaining <= 0;
        int matchedHits = 0;
        for (int gap : gaps) {
            double gapRemaining = LotteryPatternTrendUtils.round2(theoreticalCycle - gap);
            if (reachedAvgCycle) {
                if (gapRemaining <= 0) {
                    matchedHits++;
                }
            } else if (gap == currentOmission) {
                matchedHits++;
            }
        }
        double empiricalCycle = hitCount > 0 ? (double) n / hitCount : 0;
        double empiricalAvgOmission = hitCount > 0 ? (double) (n - hitCount) / hitCount : 0;
        double probability = hitCount > 0 ? (double) matchedHits / hitCount : 0;
        double cyclesReached = theoreticalCycle > 0 ? currentOmission / theoreticalCycle : 0;
        return DueStat.builder()
            .key(key)
            .inLatest(inLatest)
            .currentOmission(currentOmission)
            .theoreticalCycle(LotteryPatternTrendUtils.round2(theoreticalCycle))
            .empiricalCycle(LotteryPatternTrendUtils.round2(empiricalCycle))
            .empiricalAvgOmission(LotteryPatternTrendUtils.round2(empiricalAvgOmission))
            .remainingToAvg(remaining)
            .reachedAvgCycle(reachedAvgCycle)
            .cyclesReached(LotteryPatternTrendUtils.round2(cyclesReached))
            .hitCount(hitCount)
            .matchedHits(matchedHits)
            .unmatchedHits(Math.max(0, hitCount - matchedHits))
            .nextHitProbability(LotteryPatternTrendUtils.round4(probability))
            .matchRule(reachedAvgCycle
                ? String.format(Locale.ROOT, "当前遗漏%d已达/超过平均周期%.2f，匹配历史「开出前遗漏≥%.2f」",
                    currentOmission, theoreticalCycle, theoreticalCycle)
                : String.format(Locale.ROOT, "当前遗漏%d距平均周期%.2f还差%.2f，匹配历史「开出前遗漏=%d」",
                    currentOmission, theoreticalCycle, remaining, currentOmission))
            .build();
    }

    private static List<Boolean> hitFlags(List<Draw> draws, Predicate<Draw> hit) {
        List<Boolean> flags = new ArrayList<>(draws.size());
        for (Draw draw : draws) {
            flags.add(hit.test(draw));
        }
        return flags;
    }

    private static boolean containsRed(Draw draw, int ball) {
        return draw.getRedBalls() != null && draw.getRedBalls().contains(ball);
    }

    private static void appendTable(StringBuilder sb, List<DueStat> rows, boolean alwaysPrint) {
        sb.append("| 取值 | 当前遗漏 | 理论周期 | 距平均 | 达到周期 | 出现 | 匹配开出 | 未匹配 | 下期概率 | 最近 |\n");
        sb.append("|---|---:|---:|---|---|---:|---:|---:|---:|---|\n");
        for (DueStat row : rows) {
            if (!alwaysPrint && row.getHitCount() <= 0 && !row.isInLatest()) {
                continue;
            }
            sb.append("| ").append(row.getKey())
                .append(" | ").append(row.getCurrentOmission())
                .append(" | ").append(fmt(row.getTheoreticalCycle()))
                .append(" | ").append(remainingLabel(row))
                .append(" | ").append(row.isReachedAvgCycle()
                    ? String.format(Locale.ROOT, "是(%.2fx)", row.getCyclesReached()) : "否")
                .append(" | ").append(row.getHitCount())
                .append(" | ").append(row.getMatchedHits())
                .append(" | ").append(row.getUnmatchedHits())
                .append(" | ").append(pct(row.getNextHitProbability()))
                .append(" | ").append(row.isInLatest() ? "是" : "")
                .append(" |\n");
        }
    }

    private static String remainingLabel(DueStat row) {
        double r = row.getRemainingToAvg();
        if (r > 0) {
            return String.format(Locale.ROOT, "还差%.2f", r);
        }
        if (r == 0) {
            return "刚好到期";
        }
        return String.format(Locale.ROOT, "已超%.2f", Math.abs(r));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String pct(double p) {
        return String.format(Locale.ROOT, "%.2f%%", p * 100);
    }

    @Data
    @Builder
    public static class Draw {
        private String period;
        private LocalDate openDate;
        private List<Integer> redBalls;
        private Integer blueBall;
    }

    @Data
    @Builder
    public static class DueStat {
        /** 球号（两位）或形态取值（如 3:3、97-102） */
        private String key;
        /** 是否出现在最近一期（红/蓝）或是否为最近一期该形态取值 */
        private boolean inLatest;
        private int currentOmission;
        /** 理论开出周期 1/p */
        private double theoreticalCycle;
        /** 样本内 n / 出现次数 */
        private double empiricalCycle;
        /** 样本内 (n - 出现次数) / 出现次数 */
        private double empiricalAvgOmission;
        /** 平均周期 − 当前遗漏，&gt;0 还差，&lt;0 已超 */
        private double remainingToAvg;
        private boolean reachedAvgCycle;
        /** 当前遗漏 / 理论周期 */
        private double cyclesReached;
        private int hitCount;
        private int matchedHits;
        private int unmatchedHits;
        /** matchedHits / hitCount */
        private double nextHitProbability;
        private String matchRule;
    }

    @Data
    @Builder
    public static class FeatureGroup {
        private FeatureKind kind;
        private String label;
        private String latestValue;
        private List<DueStat> buckets;
    }

    @Data
    @Builder
    public static class Report {
        private int sampleSize;
        private String fromPeriod;
        private String toPeriod;
        private List<DueStat> redBalls;
        private List<DueStat> blueBalls;
        private List<FeatureGroup> features;
    }
}
