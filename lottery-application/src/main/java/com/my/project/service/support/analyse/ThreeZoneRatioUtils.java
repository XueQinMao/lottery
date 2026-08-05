package com.my.project.service.support.analyse;

import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.ThreeZoneRatioPredictBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.feature.config.ThreeZoneRatioPredictConfig;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 三区分析工具
 */
public class ThreeZoneRatioUtils {

    private static final int RED_ZONE_SIZE = 11;

    private static final ThreeZoneRatioPredictConfig config = new ThreeZoneRatioPredictConfig();

    public static ThreeZoneRatioPredictBo calculate(List<HistoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return emptyResult();
        }

        var statsRecords =
                records.subList(0, Math.min(30, records.size())).stream().map(ThreeZoneRatioUtils::toDrawRecord)
                        .toList();

        // 统一为期号升序（最旧→最新），兼容上游降序传入
        List<LotteryAnalysisReqBo.DrawRecord> chronological = toAscending(statsRecords);

        // 1. 将每期红球转为三区比字符串（按期号升序）
        List<String> ratioSequence = chronological.stream()
                .map(ThreeZoneRatioUtils::toThreeZoneRatio)
                .toList();

        // 2. 频率先验：统计各三区比出现频率
        Map<String, Integer> freqCount = new LinkedHashMap<>();
        for (String ratio : ratioSequence) {
            freqCount.merge(ratio, 1, Integer::sum);
        }
        int sampleSize = ratioSequence.size();
        Map<String, Double> freqProb = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : freqCount.entrySet()) {
            freqProb.put(e.getKey(), (double) e.getValue() / sampleSize);
        }

        // 3. 马尔可夫转移：统计「上期 A → 下期 B」
        String lastRatio = ratioSequence.get(sampleSize - 1);
        Map<String, Integer> transitionFromLast = new HashMap<>();
        int transitionTotal = 0;
        for (int i = 0; i < sampleSize - 1; i++) {
            String from = ratioSequence.get(i);
            String to = ratioSequence.get(i + 1);
            if (from.equals(lastRatio)) {
                transitionFromLast.merge(to, 1, Integer::sum);
                transitionTotal++;
            }
        }
        Map<String, Double> markovProb = new LinkedHashMap<>();
        boolean hasMarkov = transitionTotal > 0;
        if (hasMarkov) {
            for (Map.Entry<String, Integer> e : transitionFromLast.entrySet()) {
                markovProb.put(e.getKey(), (double) e.getValue() / transitionTotal);
            }
        }

        // 4. 混合：finalProb = freqWeight × P_freq + markovWeight × P_markov
        //    无马尔可夫数据时回退为纯频率先验
        double freqWeight = config.getFreqWeight();
        double markovWeight = hasMarkov ? config.getMarkovWeight() : 0.0;
        double weightSum = freqWeight + markovWeight;
        if (weightSum <= 0) {
            freqWeight = 1.0;
            markovWeight = 0.0;
            weightSum = 1.0;
        }

        // 候选集合 = 频率出现过的所有三区比 ∪ 转移出现过的所有三区比
        Map<String, double[]> rawScores = new LinkedHashMap<>();
        for (String ratio : freqProb.keySet()) {
            double f = freqProb.getOrDefault(ratio, 0.0);
            double m = markovProb.getOrDefault(ratio, 0.0);
            rawScores.put(ratio, new double[]{f, m});
        }
        for (String ratio : markovProb.keySet()) {
            rawScores.putIfAbsent(ratio, new double[]{0.0, markovProb.get(ratio)});
        }

        Map<String, Double> finalProb = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : rawScores.entrySet()) {
            double f = e.getValue()[0];
            double m = e.getValue()[1];
            finalProb.put(e.getKey(), (freqWeight * f + markovWeight * m) / weightSum);
        }

        // 5. 归一化（保证概率和为 1）
        double probSum = finalProb.values().stream().mapToDouble(Double::doubleValue).sum();
        if (probSum > 0) {
            finalProb.replaceAll((k, v) -> v / probSum);
        }

        // 6. 取 Top-K
        int topK = Math.max(config.getTopK(), 1);
        List<ThreeZoneRatioPredictBo.Candidate> candidates = finalProb.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> ThreeZoneRatioPredictBo.Candidate.builder()
                        .ratio(e.getKey())
                        .probability(round(e.getValue()))
                        .frequencyProb(round(freqProb.getOrDefault(e.getKey(), 0.0)))
                        .markovProb(round(markovProb.getOrDefault(e.getKey(), 0.0)))
                        .reason(buildReason(e.getKey(), freqProb.getOrDefault(e.getKey(), 0.0),
                                markovProb.getOrDefault(e.getKey(), 0.0), hasMarkov))
                        .build())
                .collect(Collectors.toList());

        return ThreeZoneRatioPredictBo.builder()
                .candidates(candidates)
                .lastRatio(lastRatio)
                .basis(buildBasis(sampleSize, lastRatio, transitionTotal, hasMarkov))
                .build();
    }

    private static LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
                Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                        record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
                .blueBall(record.getSpecial()).build();
    }

    // ==================== 内部方法 ====================

    /**
     * 将样本统一为期号升序（最旧 → 最新）。
     * <p>上游 {@code getLatestRecords} 常按开奖日降序返回；马尔可夫转移依赖时间正序。
     * 优先按 period 字符串排序；period 缺失时保持原序并反转（假定输入为降序）。
     */
    private static List<LotteryAnalysisReqBo.DrawRecord> toAscending(List<LotteryAnalysisReqBo.DrawRecord> records) {
        boolean hasPeriod = records.stream().anyMatch(r -> r.getPeriod() != null && !r.getPeriod().isBlank());
        if (hasPeriod) {
            return records.stream()
                    .sorted(Comparator.comparing(LotteryAnalysisReqBo.DrawRecord::getPeriod, Comparator.nullsLast(String::compareTo)))
                    .toList();
        }
        List<LotteryAnalysisReqBo.DrawRecord> copy = new ArrayList<>(records);
        Collections.reverse(copy);
        return copy;
    }

    /**
     * 将一期红球转为三区比字符串，形如 "2:2:2"。
     */
    private static String toThreeZoneRatio(LotteryAnalysisReqBo.DrawRecord record) {
        int z1 = 0, z2 = 0, z3 = 0;
        if (record.getRedBalls() != null) {
            for (int b : record.getRedBalls()) {
                int zone = (b - 1) / RED_ZONE_SIZE;
                if (zone == 0) z1++;
                else if (zone == 1) z2++;
                else z3++;
            }
        }
        return z1 + ":" + z2 + ":" + z3;
    }

    private static String buildReason(String ratio, double freqP, double markovP, boolean hasMarkov) {
        if (hasMarkov && markovP > 0 && freqP > 0) {
            return String.format("频率先验 %.1f%% + 上期%s转移 %.1f%%", freqP * 100, "", markovP * 100);
        }
        if (hasMarkov && markovP > 0) {
            return "上期转移出现";
        }
        return String.format("历史频率 %.1f%%", freqP * 100);
    }

    private static String buildBasis(int sampleSize, String lastRatio, int transitionTotal, boolean hasMarkov) {
        if (hasMarkov) {
            return String.format(
                    "基于最近 %d 期样本，频率先验(权重 %.1f) + 马尔可夫转移(权重 %.1f)；"
                            + "最近一期三区比 %s，作为转移起点（历史中该起点出现 %d 次有后续）。",
                    sampleSize, config.getFreqWeight(), config.getMarkovWeight(),
                    lastRatio, transitionTotal);
        }
        return String.format(
                "基于最近 %d 期样本，纯频率先验（上期三区比 %s 在样本中无转移数据，回退频率先验）。",
                sampleSize, lastRatio);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static ThreeZoneRatioPredictBo emptyResult() {
        return ThreeZoneRatioPredictBo.builder()
                .candidates(List.of())
                .lastRatio(null)
                .basis("样本为空，未预测三区比")
                .build();
    }
}
