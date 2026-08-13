package com.my.project.service.llm.impl;

import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;
import com.my.project.llm.bo.ThreeZoneRatioPredictBo;
import com.my.project.llm.bo.ThreeZoneRatioPredictBo.Candidate;
import com.my.project.service.llm.IThreeZoneRatioPredictService;
import com.my.project.service.llm.config.ThreeZoneRatioPredictConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ThreeZoneRatioPredictServiceImpl
 *
 * <p>下一期三区比预测：采用「频率先验 + 马尔可夫转移」混合模型。
 *
 * <h3>方法 1：频率先验</h3>
 * <p>统计近 N 期各三区比出现频率，作为基础概率：
 * <pre>P_freq(ratio) = count(ratio) / sampleSize</pre>
 *
 * <h3>方法 2：马尔可夫转移</h3>
 * <p>以最近一期三区比为起点，统计「上期 A → 下期 B」的转移概率：
 * <pre>P_markov(next | last) = count(last → next) / count(last → *)</pre>
 * 若上期三区比在样本中从未作为「上期」出现过（无转移数据），回退为纯频率先验。
 *
 * <h3>混合</h3>
 * <pre>P_final = freqWeight × P_freq + markovWeight × P_markov（归一化）</pre>
 *
 * <p>三区定义：一区(1-11) / 二区(12-22) / 三区(23-33)。
 * <p>入口会将样本统一为期号升序（最旧→最新），兼容上游降序传入。
 *
 * @author 刘强
 * @version 2026/08/10 11:06
 **/
@Slf4j
@Service
@AllArgsConstructor
public class ThreeZoneRatioPredictServiceImpl implements IThreeZoneRatioPredictService {

    private static final int RED_ZONE_SIZE = 11;

    private final ThreeZoneRatioPredictConfig config;

    @Override
    public ThreeZoneRatioPredictBo predict(List<DrawRecord> records) {
        if (records == null || records.isEmpty()) {
            log.warn("三区比预测样本为空，跳过");
            return emptyResult();
        }
        List<DrawRecord> drawRecords = records.subList(0, Math.min(30, records.size()));
        // 统一为期号升序（最旧→最新），兼容上游降序传入
        List<DrawRecord> chronological = toAscending(drawRecords);

        // 1. 将每期红球转为三区比字符串（按期号升序）
        List<String> ratioSequence = chronological.stream()
            .map(this::toThreeZoneRatio)
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
        List<Candidate> candidates = finalProb.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> Candidate.builder()
                .ratio(e.getKey())
                .probability(round(e.getValue()))
                .frequencyProb(round(freqProb.getOrDefault(e.getKey(), 0.0)))
                .markovProb(round(markovProb.getOrDefault(e.getKey(), 0.0)))
                .reason(buildReason(e.getKey(), freqProb.getOrDefault(e.getKey(), 0.0),
                    markovProb.getOrDefault(e.getKey(), 0.0), hasMarkov))
                .build())
            .collect(Collectors.toList());

        ThreeZoneRatioPredictBo result = ThreeZoneRatioPredictBo.builder()
            .candidates(candidates)
            .lastRatio(lastRatio)
            .basis(buildBasis(sampleSize, lastRatio, transitionTotal, hasMarkov))
            .build();

        log.info("三区比预测完成: 最近一期={}, 转移样本数={}, Top{}={}",
            lastRatio, transitionTotal, candidates.size(),
            candidates.stream().map(c -> c.getRatio() + "(" + c.getProbability() + ")")
                .collect(Collectors.joining(", ")));
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 将样本统一为期号升序（最旧 → 最新）。
     * <p>上游 {@code getLatestRecords} 常按开奖日降序返回；马尔可夫转移依赖时间正序。
     * 优先按 period 字符串排序；period 缺失时保持原序并反转（假定输入为降序）。
     */
    private List<DrawRecord> toAscending(List<DrawRecord> records) {
        boolean hasPeriod = records.stream().anyMatch(r -> r.getPeriod() != null && !r.getPeriod().isBlank());
        if (hasPeriod) {
            return records.stream()
                .sorted(Comparator.comparing(DrawRecord::getPeriod, Comparator.nullsLast(String::compareTo)))
                .toList();
        }
        List<DrawRecord> copy = new ArrayList<>(records);
        Collections.reverse(copy);
        return copy;
    }

    /**
     * 将一期红球转为三区比字符串，形如 "2:2:2"。
     */
    private String toThreeZoneRatio(DrawRecord record) {
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

    private String buildReason(String ratio, double freqP, double markovP, boolean hasMarkov) {
        if (hasMarkov && markovP > 0 && freqP > 0) {
            return String.format("频率先验 %.1f%% + 上期%s转移 %.1f%%", freqP * 100, "", markovP * 100);
        }
        if (hasMarkov && markovP > 0) {
            return "上期转移出现";
        }
        return String.format("历史频率 %.1f%%", freqP * 100);
    }

    private String buildBasis(int sampleSize, String lastRatio, int transitionTotal, boolean hasMarkov) {
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

    private double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private ThreeZoneRatioPredictBo emptyResult() {
        return ThreeZoneRatioPredictBo.builder()
            .candidates(List.of())
            .lastRatio(null)
            .basis("样本为空，未预测三区比")
            .build();
    }
}
