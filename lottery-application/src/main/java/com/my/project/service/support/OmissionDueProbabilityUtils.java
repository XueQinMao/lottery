package com.my.project.service.support;

import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 遗漏宝 杀特征
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

    public static Map<String, Map<String, Double>> analyze(List<HistoryRecord> historyRecords) {
        var historyMaps = historyRecords.stream().map(
                        h -> Triple.of(h.getPeriod(),
                                List.of(h.getNum1(), h.getNum2(), h.getNum3(), h.getNum4(), h.getNum5(), h.getNum6()), h.getSpecial()))
                .collect(Collectors.toMap(Triple::getLeft, Function.identity(), (o1, o2) -> o2));
        var part100 = historyRecords.stream().limit(100).toList();
        int totalSize = part100.size();
        Map<String, Map<String, Double>> probabilityMap = new HashMap<>();
        // 红球：按倍数匹配计算
        Map<String, Double> redProbabilityMap = IntStream.rangeClosed(1, 33)
                .boxed()
                .collect(Collectors.toMap(
                        String::valueOf,
                        ball -> {
                            var vo = OmissionUtils.omissionBallAnalyzer(part100, "red", ball);
                            var currentMultiple = toMultiple(vo.getStats().getCurrentOmission(), vo.getStats().getAvgOmission());
                            var periods = vo.getPeriods();
                            return calcRollingMultipleProbability(
                                    periods,
                                    currentMultiple,
                                    i -> {
                                        var subset = rollingSubset(historyRecords, periods.get(i));
                                        var rvo = OmissionUtils.omissionBallAnalyzer(subset, "red", ball);
                                        return toMultiple(rvo.getStats().getCurrentOmission(), rvo.getStats().getAvgOmission());
                                    },
                                    i -> {
                                        var triple = historyMaps.get(periods.get(i + 1));
                                        return triple != null && triple.getMiddle().contains(ball);
                                    },
                                    totalSize);
                        },
                        (o1, o2) -> o2));
        probabilityMap.put("红球", redProbabilityMap);

        // 蓝球：按倍数匹配计算
        Map<String, Double> blueProbabilityMap = IntStream.rangeClosed(1, 16)
                .boxed()
                .collect(Collectors.toMap(
                        String::valueOf,
                        ball -> {
                            var vo = OmissionUtils.omissionBallAnalyzer(part100, "blue", ball);
                            var currentMultiple = toMultiple(vo.getStats().getCurrentOmission(), vo.getStats().getAvgOmission());
                            var periods = vo.getPeriods();
                            return calcRollingMultipleProbability(
                                    periods,
                                    currentMultiple,
                                    i -> {
                                        var subset = rollingSubset(historyRecords, periods.get(i));
                                        var rvo = OmissionUtils.omissionBallAnalyzer(subset, "blue", ball);
                                        return toMultiple(rvo.getStats().getCurrentOmission(), rvo.getStats().getAvgOmission());
                                    },
                                    i -> {
                                        var triple = historyMaps.get(periods.get(i + 1));
                                        return triple != null && Objects.equals(triple.getRight(), ball);
                                    },
                                    totalSize);
                        },
                        (o1, o2) -> o2));
        probabilityMap.put("蓝球", blueProbabilityMap);
        // 特征计算：按倍数匹配计算
        Arrays.stream(FeatureKindEnums.values()).forEach(enums -> {
            Map<String, Double> featureKindMap = enums.getVals().stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            val -> {
                                var vo = OmissionUtils.omissionFeatureTrendAnalyzer(enums.getCode(), val, part100);
                                var currentMultiple = toMultiple(vo.getStats().getCurrentOmission(), vo.getStats().getAvgOmission());
                                var periods = vo.getPeriods();
                                return calcRollingMultipleProbability(
                                        periods,
                                        currentMultiple,
                                        i -> {
                                            var subset = rollingSubset(historyRecords, periods.get(i));
                                            var rvo = OmissionUtils.omissionFeatureTrendAnalyzer(enums.getCode(), val, subset);
                                            return toMultiple(rvo.getStats().getCurrentOmission(), rvo.getStats().getAvgOmission());
                                        },
                                        i -> val.equals(vo.getActuals().get(i + 1)),
                                        totalSize);
                            },
                            (o1, o2) -> o2));
            probabilityMap.put(enums.getLabel(), featureKindMap);
        });
        return probabilityMap;
    }

    /**
     * 分析红球篮球的遗漏
     *
     * @param part100
     * @return
     */
    public static Pair<Map<Integer, TrendAnalysisVo>, Map<Integer, TrendAnalysisVo>> analyzeRedBlueOmission(List<HistoryRecord> part100) {
        var redBallTrendMaps = IntStream.rangeClosed(1, 33)
                .boxed().map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(part100, "red", ball)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getRight, (o1, o2) -> o1));

        var blueBallTrendMaps = IntStream.rangeClosed(1, 16)
                .boxed().map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(part100, "blue", ball)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getRight, (o1, o2) -> o1));
        return Pair.of(redBallTrendMaps, blueBallTrendMaps);
    }

    /**
     * 计算滚动倍数匹配命中概率。
     * <p>对每个历史期号，取截止该期的最近 100 期重算统计，得到当时遗漏倍数；
     * 若倍数与当前倍数一致且下一期命中目标，则计为命中。概率 = 命中次数 / 总期数。
     *
     * @param periods         全样本期号序列
     * @param currentMultiple 当前遗漏倍数（当前遗漏 / 平均遗漏）
     * @param rollingMultiple 给定期号索引 i，返回该期滚动窗口下的遗漏倍数
     * @param nextHit         给定期号索引 i，返回第 i+1 期是否命中目标
     * @param totalSize       概率分母（总期数）
     * @return 命中概率
     */
    private static double calcRollingMultipleProbability(
            List<String> periods,
            double currentMultiple,
            IntToDoubleFunction rollingMultiple,
            IntPredicate nextHit,
            int totalSize) {
        if (totalSize == 0 || currentMultiple == 0.0) {
            return 0.0;
        }
        long hitCount = IntStream.range(0, periods.size())
                .filter(i -> i + 1 < periods.size())
                .filter(i -> {
                    double rolling = rollingMultiple.applyAsDouble(i);
                    return rolling == currentMultiple && nextHit.test(i);
                })
                .count();
        return (double) hitCount / totalSize;
    }

    /**
     * 取截止指定期号的最近 100 期历史记录（降序，最新在前）。
     */
    private static List<HistoryRecord> rollingSubset(List<HistoryRecord> historyRecords, String period) {
        return historyRecords.stream()
                .filter(h -> Integer.parseInt(h.getPeriod()) <= Integer.parseInt(period))
                .limit(100)
                .toList();
    }

    /**
     * 计算遗漏倍数 = 当前遗漏 / 平均遗漏；avgOmission 为 null 或 0 时返回 0.0。
     */
    private static int toMultiple(Integer currentOmission, Double avgOmission) {
        if (avgOmission == null || avgOmission == 0 || currentOmission == null) {
            return NumberUtils.INTEGER_ZERO;
        }
        return (int) Math.round((currentOmission - avgOmission) / avgOmission);
    }

    @Data
    @Builder
    public static class DueStat {
        /**
         * 球号（两位）或形态取值（如 3:3、97-102）
         */
        private String key;
        /**
         * 是否出现在最近一期（红/蓝）或是否为最近一期该形态取值
         */
        private boolean inLatest;
        private int currentOmission;
        /**
         * 理论开出周期 1/p
         */
        private double theoreticalCycle;
        /**
         * 样本内 n / 出现次数
         */
        private double empiricalCycle;
        /**
         * 样本内 (n - 出现次数) / 出现次数
         */
        private double empiricalAvgOmission;
        /**
         * 平均周期 − 当前遗漏，&gt;0 还差，&lt;0 已超
         */
        private double remainingToAvg;
        private boolean reachedAvgCycle;
        /**
         * 当前遗漏 / 理论周期
         */
        private double cyclesReached;
        private int hitCount;
        private int matchedHits;
        private int unmatchedHits;
        /**
         * matchedHits / hitCount
         */
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
