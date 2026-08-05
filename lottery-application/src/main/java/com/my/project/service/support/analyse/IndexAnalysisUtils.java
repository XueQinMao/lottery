package com.my.project.service.support.analyse;

import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.support.LotteryPatternTrendUtils;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 指数分析工具 选号杀号
 * * 步骤	计算内容	结果
 * * ①	统计到 26102 期为止，01 累计出现次数	677
 * * ②	统计到 26102 期为止，总期数 × 6/33	636.18
 * * ③	指数 = 677 − 636.18	40.82
 * * ④	对比 26101 期指数 41.00	下降 → 指数同比“小”
 * * ⑤	查近100期历史：指数下降时 01 开出概率	仅 9.86%
 * * ⑥	给出结论	信心指数 9.86% < 50%，建议杀号
 */
public class IndexAnalysisUtils {
    public static final double RED_P = (double) LotteryPatternTrendUtils.RED_DRAW / LotteryPatternTrendUtils.RED_TOTAL;
    public static final double BLUE_P = (double) 1 / 16;
    /** 信心指数回看窗口：近 100 期 */
    private static final int CONFIDENCE_WINDOW = 100;

    /**
     * @param historyRecords
     * @return
     */


    public static IndexAnalysisResult analyze(List<HistoryRecord> historyRecords){
        //计算计算上一期的指数
        var first = historyRecords.getFirst();
        var filterFirst = historyRecords.stream().filter(h -> !Objects.equals(h.getPeriod(), first.getPeriod())).toList();
        var lastIndexAnalysisResult = calculateIndex(filterFirst);

        //计算当前期的数据
        var nextIndexAnalysisResult = calculateIndex(historyRecords);
        //计算next的指数同比和信息都
        fillIndexTbAndConfidence(historyRecords, lastIndexAnalysisResult, nextIndexAnalysisResult);
        return nextIndexAnalysisResult;
    }

    /**
     * 计算子数
     * @param historyRecords
     * @return
     */
    private static IndexAnalysisResult calculateIndex(List<HistoryRecord> historyRecords) {
        Assert.notEmpty(historyRecords, "历史开奖不能为空");
        var chronological = historyRecords.stream().sorted(Comparator.comparing(HistoryRecord::getPeriod)).toList();
        //统计红球出现的次数
        Map<Integer, Integer> readCountMap = new HashMap<>();
        Map<Integer, Integer> blueCountMap = new HashMap<>();
        chronological.forEach(h -> {
            readCountMap.merge(h.getNum1(), 1, Integer::sum);
            readCountMap.merge(h.getNum2(), 1, Integer::sum);
            readCountMap.merge(h.getNum3(), 1, Integer::sum);
            readCountMap.merge(h.getNum4(), 1, Integer::sum);
            readCountMap.merge(h.getNum5(), 1, Integer::sum);
            readCountMap.merge(h.getNum6(), 1, Integer::sum);
            blueCountMap.merge(h.getSpecial(), 1, Integer::sum);

        });
        HistoryRecord first = historyRecords.getFirst();
        var redBallIndex = IntStream.rangeClosed(1, 33).boxed().map(mapper(first.getPeriod(), readCountMap, historyRecords.size(), RED_P)).toList();
        var blueBallIndex = IntStream.rangeClosed(1, 16).boxed().map(mapper(first.getPeriod(), blueCountMap, historyRecords.size(), BLUE_P)).toList();
        //计算指数同比和信任度
        return IndexAnalysisResult.builder().redBalls(redBallIndex).blueBalls(blueBallIndex).build();
    }

    private static Function<Integer, IndexResult> mapper(String period, Map<Integer, Integer> readCountMap, Integer totalPeriod, double theoryP) {
        return ball -> {
            var nextPeriod = Integer.parseInt(period) + 1;
            var theoryCount = totalPeriod * theoryP;
            var totalCount = readCountMap.getOrDefault(ball, NumberUtils.INTEGER_ZERO);
            return IndexResult.builder().ball(ball).period(nextPeriod).realCount(totalCount).theoryCount(theoryCount).index(totalCount - theoryCount).build();
        };
    }

    /**
     * 给当期每个号码填指数同比、信心指数。
     * <p>同比：当期指数 vs 上期指数，上升为「大」，下降为「小」。
     * 信心指数：近 {@link #CONFIDENCE_WINDOW} 期内，出现与当期相同同比后，下一期该号实际开出的比例。
     */
    private static void fillIndexTbAndConfidence(List<HistoryRecord> historyRecords,
        IndexAnalysisResult lastResult, IndexAnalysisResult nextResult) {
        var chronological = historyRecords.stream().sorted(Comparator.comparing(HistoryRecord::getPeriod)).toList();
        fillBallGroup(chronological, lastResult.getRedBalls(), nextResult.getRedBalls(), true);
        fillBallGroup(chronological, lastResult.getBlueBalls(), nextResult.getBlueBalls(), false);
    }

    private static void fillBallGroup(List<HistoryRecord> chronological, List<IndexResult> lastBalls,
        List<IndexResult> nextBalls, boolean red) {
        var lastByBall = lastBalls.stream().collect(Collectors.toMap(IndexResult::getBall, Function.identity()));
        int ballMax = red ? 33 : 16;
        int n = chronological.size();
        boolean[][] hits = new boolean[ballMax + 1][n];
        int[] counts = new int[ballMax + 1];
        double[][] indexes = new double[ballMax + 1][n];
        for (int i = 0; i < n; i++) {
            var drawn = drawnBalls(chronological.get(i), red);
            for (int ball = 1; ball <= ballMax; ball++) {
                hits[ball][i] = drawn.contains(ball);
                if (hits[ball][i]) {
                    counts[ball]++;
                }
                indexes[ball][i] = counts[ball] - (i + 1) * RED_P;
            }
        }
        nextBalls.forEach(next -> {
            var last = lastByBall.get(next.getBall());
            String indexTb = compareIndex(next.getIndex(), last == null ? null : last.getIndex());
            next.setIndexTb(indexTb);
            next.setConfidence(confidenceOf(indexes[next.getBall()], hits[next.getBall()], indexTb));
        });
    }

    /**
     * 当期指数与上期指数相比：变大「大」，变小「小」，相等「平」。
     */
    private static String compareIndex(Double current, Double previous) {
        if (current == null || previous == null) {
            return "平";
        }
        int cmp = Double.compare(current, previous);
        if (cmp > 0) {
            return "大";
        }
        if (cmp < 0) {
            return "小";
        }
        return "平";
    }

    /**
     * 近 100 期中，同比与当期相同的期，下一期该号开出的比例。
     * 指数升降发生在当期，开出看的是随后一期，否则「下降时开出」会被同一期命中左右成 0%/100%。
     */
    private static double confidenceOf(double[] indexes, boolean[] hits, String indexTb) {
        int end = indexes.length - 2;
        if (end < 1) {
            return 0;
        }
        int start = Math.max(1, end - CONFIDENCE_WINDOW + 1);
        int[] samples = IntStream.rangeClosed(start, end)
            .filter(i -> compareIndex(indexes[i], indexes[i - 1]).equals(indexTb))
            .toArray();
        if (samples.length == 0) {
            return 0;
        }
        long hitCount = Arrays.stream(samples).filter(i -> hits[i + 1]).count();
        return (double) hitCount / samples.length;
    }

//    private static String formatConfidence(double rate) {
//        return String.format(Locale.ROOT, "%.2f%%", rate * 100);
//    }

    private static Set<Integer> drawnBalls(HistoryRecord record, boolean red) {
        if (red) {
            return Set.of(record.getNum1(), record.getNum2(), record.getNum3(),
                record.getNum4(), record.getNum5(), record.getNum6());
        }
        return record.getSpecial() == null ? Set.of() : Set.of(record.getSpecial());
    }

    /**
     * 指数结果
     */
    @Data
    @Builder
    public static class IndexResult {
        private Integer ball;
        /**
         * 期数
         */
        private Integer period;
        /**
         * 真实出现次数
         */
        private Integer realCount;
        /**
         * 理论出现次数
         */
        private Double theoryCount;
        /**
         * 指数
         */
        private Double index;

        /**
         * 指数同比
         */
        private String indexTb;

        /**
         * 信息指数
         */
        private Double confidence;
    }

    @Data
    @Builder
    public static class IndexAnalysisResult{
        private List<IndexResult>redBalls;
        private List<IndexResult>blueBalls;
    }
}
