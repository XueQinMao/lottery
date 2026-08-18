package com.my.project.service.llm.impl;

import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.llm.bo.KillNumberResultBo.KillItemBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;
import com.my.project.service.llm.IKillNumberService;
import com.my.project.service.llm.config.KillNumberConfig;
import com.my.project.service.support.LotteryTrendUtils;
import com.my.project.service.support.LotteryTrendUtils.TrendAnalysisResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * KillNumberServiceImpl
 *
 * <p>基于历史开奖样本计算双色球杀号清单。采用「多维度剔除置信度 + 加权融合」模型：
 * <ol>
 *     <li>frequency：冷热频次（极冷 / 超热回冷）</li>
 *     <li>omission：遗漏期数（长期未开出）</li>
 *     <li>zone：三区失衡（某区整体偏冷）</li>
 *     <li>tail：尾数过密（某尾数显著高于均值）</li>
 *     <li>rebound：冷号回补保护（仅红球，极值遗漏即将解冻，给负分对冲，避免极冷号被误杀）</li>
 * </ol>
 * 每个号码在每个维度得到一个 [0,1] 的得分（rebound 为负分），按 {@link KillNumberConfig#getWeights()} 加权
 * 融合为综合得分，再按硬杀阈值筛选为硬杀清单（LLM 须遵守）。
 *
 * <p>极值遗漏白名单兜底（仅红球）：遗漏期数占样本比例 ≥
 * {@link KillNumberConfig#getExtremeOmissionWhitelistRatio()} 的号码，
 * 即使综合得分达标也不进入硬杀清单，与 rebound 维度形成双保险。
 *
 * <p>所有阈值按 30 期样本校准，仅产出硬杀清单。所有计算在 Java 侧完成，
 * 不依赖 LLM 的算术能力，保证结果可解释、可回测、可调参。
 *
 * @author 刘强
 * @version 2026/08/05 19:28
 **/
@Slf4j
@Service
@AllArgsConstructor
public class KillNumberServiceImpl implements IKillNumberService {

    private static final int RED_MIN = 1;
    private static final int RED_MAX = 33;
    private static final int BLUE_MIN = 1;
    private static final int BLUE_MAX = 16;
    private static final int RED_ZONE_SIZE = 11;

    private final KillNumberConfig killNumberConfig;

    @Override
    public KillNumberResultBo calculate(List<DrawRecord> records, DrawRecord defaultKillNumbers) {
        if (records == null || records.isEmpty()) {
            log.warn("杀号计算样本为空，跳过");
            return emptyResult();
        }
        List<DrawRecord> drawRecords = records.subList(0, Math.min(30, records.size()));
        // 遗漏计算要求期号升序（最旧→最新），兼容上游降序传入
        List<DrawRecord> chronological = toAscending(drawRecords);

        Map<Integer, Integer> redOmission = calcRedOmission(chronological);
        Map<Integer, Integer> blueOmission = calcBlueOmission(chronological);
        Map<Integer, Double> redScores = calculateRedScores(chronological, redOmission);
        Map<Integer, Double> blueScores = calculateBlueScores(chronological, blueOmission);

        int sampleSize = chronological.size();
//        List<KillItemBo> hardKillRed = pickTop(redScores, redOmission, sampleSize,
//            killNumberConfig.getMaxHardKillRed(), killNumberConfig.getHardThreshold(), Set.of(), true);
//        List<KillItemBo> hardKillBlue = pickTop(blueScores, blueOmission, sampleSize,
//            killNumberConfig.getMaxHardKillBlue(), killNumberConfig.getHardThreshold(), Set.of(), false);
        List<KillItemBo> hardKillRed = new ArrayList<>();
        List<KillItemBo> hardKillBlue = new ArrayList<>();
        // 趋势均线直接杀号：不参与加权融合，达标直接进硬杀清单（须用升序样本）
        Set<Integer> alreadyRed = ballsOf(hardKillRed);
        Set<Integer> alreadyBlue = ballsOf(hardKillBlue);
        List<KillItemBo> trendKillRed = calcTrendKills(chronological, true, alreadyRed, killNumberConfig.getTrend());
        List<KillItemBo> trendKillBlue = calcTrendKills(chronological, false, alreadyBlue, killNumberConfig.getTrend());
        hardKillRed.addAll(trendKillRed);
        hardKillBlue.addAll(trendKillBlue);

        //默认吧最近一期的都杀掉
        hardKillBlue.add(
            KillItemBo.builder().ball(defaultKillNumbers.getBlueBall()).score(1.0).reason("上期开出").build());
        defaultKillNumbers.getRedBalls().stream()
            .map(redball -> KillItemBo.builder().ball(redball).score(1.0).reason("上期开出").build())
            .forEach(hardKillRed::add);

        KillNumberResultBo result = KillNumberResultBo.builder().hardKillRed(hardKillRed).hardKillBlue(hardKillBlue)
            .basis(buildBasis(sampleSize)).build();

        log.info("杀号计算完成: 硬杀红={}, 硬杀蓝={}", ballsOf(result.getHardKillRed()), ballsOf(result.getHardKillBlue()));
        return result;
    }

    @Override
    public KillNumberResultBo calculate(List<DrawRecord> records, DrawRecord defaultKillNumbers, Integer killNumber) {
        if (records == null || records.isEmpty()) {
            log.warn("杀号计算样本为空，跳过");
            return emptyResult();
        }
        List<DrawRecord> drawRecords = records.subList(0, Math.min(30, records.size()));
        // 遗漏计算要求期号升序（最旧→最新），兼容上游降序传入
        List<DrawRecord> chronological = toAscending(drawRecords);

        Map<Integer, Integer> redOmission = calcRedOmission(chronological);
        Map<Integer, Integer> blueOmission = calcBlueOmission(chronological);
        Map<Integer, Double> redScores = calculateRedScores(chronological, redOmission);
        Map<Integer, Double> blueScores = calculateBlueScores(chronological, blueOmission);

        int sampleSize = chronological.size();
        List<KillItemBo> hardKillRed = pickTop(redScores, redOmission, sampleSize,
            killNumberConfig.getMaxHardKillRed(), killNumberConfig.getHardThreshold(), Set.of(), true);
        List<KillItemBo> hardKillBlue = pickTop(blueScores, blueOmission, sampleSize,
            killNumberConfig.getMaxHardKillBlue(), killNumberConfig.getHardThreshold(), Set.of(), false);

        // 趋势均线直接杀号：不参与加权融合，达标直接进硬杀清单（须用升序样本）
        Set<Integer> alreadyRed = ballsOf(hardKillRed);
        Set<Integer> alreadyBlue = ballsOf(hardKillBlue);
        KillNumberConfig.TrendThreshold trend = killNumberConfig.getTrend();
        trend.setMaxTrendKillBlue(trend.getMaxTrendKillBlue()+killNumber);
        trend.setMaxTrendKillRed(trend.getMaxTrendKillRed()+killNumber);
        List<KillItemBo> trendKillRed = calcTrendKills(chronological, true, alreadyRed, trend);
        List<KillItemBo> trendKillBlue = calcTrendKills(chronological, false, alreadyBlue, trend);
        hardKillRed.addAll(trendKillRed);
        hardKillBlue.addAll(trendKillBlue);

        //默认吧最近一期的都杀掉
        hardKillBlue.add(
            KillItemBo.builder().ball(defaultKillNumbers.getBlueBall()).score(1.0).reason("上期开出").build());
        defaultKillNumbers.getRedBalls().stream()
            .map(redball -> KillItemBo.builder().ball(redball).score(1.0).reason("上期开出").build())
            .forEach(hardKillRed::add);

        KillNumberResultBo result = KillNumberResultBo.builder().hardKillRed(hardKillRed).hardKillBlue(hardKillBlue)
            .basis(buildBasis(sampleSize)).build();

        log.info("杀号计算完成: 硬杀红={}, 硬杀蓝={}", ballsOf(result.getHardKillRed()), ballsOf(result.getHardKillBlue()));
        return result;
    }


    // ==================== 红球得分 ====================

    private Map<Integer, Double> calculateRedScores(List<DrawRecord> records, Map<Integer, Integer> omission) {
        Map<Integer, Integer> frequency = countRedFrequency(records);
        Map<Integer, Double> zoneScores = calcZoneScores(frequency);
        Map<Integer, Double> tailScores = calcTailScores(frequency);

        int sampleSize = records.size();
        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (int b = RED_MIN; b <= RED_MAX; b++) {
            Map<String, Double> dims = new HashMap<>();
            dims.put("frequency", frequencyScore(frequency.getOrDefault(b, 0)));//出现频率
            dims.put("omission", omissionScore(omission.getOrDefault(b, sampleSize)));//遗漏频率
            dims.put("zone", zoneScores.getOrDefault(b, 0.0));//出现区间
            dims.put("tail", tailScores.getOrDefault(b, 0.0));//尾部
            dims.put("rebound", reboundScore(omission.getOrDefault(b, sampleSize), sampleSize));//冷号回补保护
            scores.put(b, weightedScore(dims));
        }
        return scores;
    }

    private Map<Integer, Integer> countRedFrequency(List<DrawRecord> records) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (DrawRecord r : records) {
            if (r.getRedBalls() == null) {
                continue;
            }
            for (int b : r.getRedBalls()) {
                freq.merge(b, 1, Integer::sum);
            }
        }
        return freq;
    }

    /**
     * 计算每个红球自最近一期起向前连续未开出的期数。
     * <p>最近一期开出则为 0；样本内从未开出则为样本大小。
     */
    private Map<Integer, Integer> calcRedOmission(List<DrawRecord> records) {
        Map<Integer, Integer> omission = new HashMap<>();
        for (int b = RED_MIN; b <= RED_MAX; b++) {
            omission.put(b, records.size());
        }
        // records 升序，从最后一期向前找首次出现位置
        for (int i = records.size() - 1; i >= 0; i--) {
            List<Integer> reds = records.get(i).getRedBalls();
            if (reds == null) {
                continue;
            }
            for (int b : reds) {
                if (omission.get(b) == records.size()) {
                    omission.put(b, records.size() - 1 - i);
                }
            }
        }
        return omission;
    }

    private Map<Integer, Double> calcZoneScores(Map<Integer, Integer> frequency) {
        int[] zoneTotals = new int[3];
        for (int b = RED_MIN; b <= RED_MAX; b++) {
            int zone = (b - 1) / RED_ZONE_SIZE;
            zoneTotals[zone] += frequency.getOrDefault(b, 0);
        }
        double avg = (zoneTotals[0] + zoneTotals[1] + zoneTotals[2]) / 3.0;
        double threshold = avg * (1 - killNumberConfig.getZone().getImbalanceRatio());

        Map<Integer, Double> scores = new HashMap<>();
        for (int z = 0; z < 3; z++) {
            if (zoneTotals[z] < threshold) {
                for (int b = z * RED_ZONE_SIZE + 1; b <= (z + 1) * RED_ZONE_SIZE; b++) {
                    scores.put(b, killNumberConfig.getZone().getImbalanceScore());
                }
            }
        }
        return scores;
    }

    private Map<Integer, Double> calcTailScores(Map<Integer, Integer> frequency) {
        int[] tailTotals = new int[10];
        for (Map.Entry<Integer, Integer> e : frequency.entrySet()) {
            tailTotals[e.getKey() % 10] += e.getValue();
        }
        double avg = IntStream.of(tailTotals).average().orElse(0);
        double threshold = avg * (1 + killNumberConfig.getTail().getDenseRatio());

        Map<Integer, Double> scores = new HashMap<>();
        for (int t = 0; t < 10; t++) {
            if (tailTotals[t] > threshold) {
                for (int b = RED_MIN; b <= RED_MAX; b++) {
                    if (b % 10 == t) {
                        scores.put(b, killNumberConfig.getTail().getDenseScore());
                    }
                }
            }
        }
        return scores;
    }

    // ==================== 蓝球得分 ====================

    private Map<Integer, Double> calculateBlueScores(List<DrawRecord> records, Map<Integer, Integer> omission) {
        Map<Integer, Integer> frequency = countBlueFrequency(records);

        int sampleSize = records.size();
        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (int b = BLUE_MIN; b <= BLUE_MAX; b++) {
            Map<String, Double> dims = new HashMap<>();
            dims.put("frequency", blueFrequencyScore(frequency.getOrDefault(b, 0)));
            dims.put("omission", blueOmissionScore(omission.getOrDefault(b, sampleSize)));
            scores.put(b, blueWeightedScore(dims));
        }
        return scores;
    }

    private Map<Integer, Integer> countBlueFrequency(List<DrawRecord> records) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (DrawRecord r : records) {
            if (r.getBlueBall() != null) {
                freq.merge(r.getBlueBall(), 1, Integer::sum);
            }
        }
        return freq;
    }

    private Map<Integer, Integer> calcBlueOmission(List<DrawRecord> records) {
        Map<Integer, Integer> omission = new HashMap<>();
        for (int b = BLUE_MIN; b <= BLUE_MAX; b++) {
            omission.put(b, records.size());
        }
        for (int i = records.size() - 1; i >= 0; i--) {
            Integer blue = records.get(i).getBlueBall();
            if (blue != null && omission.get(blue) == records.size()) {
                omission.put(blue, records.size() - 1 - i);
            }
        }
        return omission;
    }

    // ==================== 维度得分函数 ====================

    private double frequencyScore(int count) {
        KillNumberConfig.FrequencyThreshold f = killNumberConfig.getFrequency();
        if (count <= f.getColdCount()) {
            return f.getColdScore();
        }
        if (count >= f.getHotCount()) {
            return f.getHotScore();
        }
        return 0.0;
    }

    private double omissionScore(int miss) {
        KillNumberConfig.OmissionThreshold o = killNumberConfig.getOmission();
        if (miss > o.getLongOmission()) {
            return o.getLongScore();
        }
        if (miss > o.getMidOmission()) {
            return o.getMidScore();
        }
        return 0.0;
    }

    /**
     * 蓝球冷热频次得分（按 30 期校准：单号期望 ≈ 1.875 次）。
     * <p>30 期下约 2-3 个蓝球会出现 0 次，count==0 给 0.95，配合长期遗漏才进硬杀。
     */
    private double blueFrequencyScore(int count) {
        if (count == 0) {
            return 0.95;
        }
        if (count >= 6) {
            return 0.7;
        }
        return 0.0;
    }

    /**
     * 蓝球遗漏得分（按 30 期校准：遗漏上限 = 30）。
     */
    private double blueOmissionScore(int miss) {
        if (miss > 25) {
            return 0.85;
        }
        if (miss > 18) {
            return 0.6;
        }
        return 0.0;
    }

    /**
     * 冷号回补保护得分。
     * <p>当号码遗漏期数占样本比例超过极值阈值（默认 0.8）时，认为该号码即将「解冻」回补，
     * 给负分对冲冷热/遗漏维度的杀号得分，避免极冷号被误杀。
     * <p>该维度是「冷号继续冷」理论的对冲：极值遗漏的号码反而最有可能开出。
     *
     * @param miss       号码遗漏期数
     * @param sampleSize 样本大小
     * @return 负分（保护分），未触发时返回 0.0
     */
    private double reboundScore(int miss, int sampleSize) {
        if (sampleSize <= 0) {
            return 0.0;
        }
        KillNumberConfig.ReboundThreshold r = killNumberConfig.getRebound();
        double ratio = (double) miss / sampleSize;
        if (ratio > r.getExtremeRatio()) {
            return r.getProtectScore();
        }
        return 0.0;
    }

    // ==================== 趋势均线维度 ====================

    /**
     * 趋势均线直接杀号：计算所有号码的趋势得分，达标者直接生成杀号项。
     * <p>不参与加权融合，独立判断后直接进入硬杀清单。
     *
     * @param records  开奖记录（最旧 → 最新；与 {@link LotteryTrendUtils#analyze} 约定一致）
     * @param isRed    true=红球(1-33)，false=蓝球(1-16)
     * @param exclude  已在硬杀清单中的号码，跳过避免重复
     * @return 趋势杀号清单
     */
    private List<KillItemBo> calcTrendKills(List<DrawRecord> records, boolean isRed, Set<Integer> exclude, KillNumberConfig.TrendThreshold trendCfg) {
        List<Set<Integer>> drawSets = new ArrayList<>();
        for (DrawRecord r : records) {
            Set<Integer> set = new HashSet<>();
            if (isRed) {
                if (r.getRedBalls() != null) {
                    set.addAll(r.getRedBalls());
                }
            } else {
                if (r.getBlueBall() != null) {
                    set.add(r.getBlueBall());
                }
            }
            drawSets.add(set);
        }
        int min = isRed ? RED_MIN : BLUE_MIN;
        int max = isRed ? RED_MAX : BLUE_MAX;
//        KillNumberConfig.TrendThreshold trendCfg = killNumberConfig.getTrend();
        double killThreshold = trendCfg.getKillThreshold();
        int topN = isRed ? trendCfg.getMaxTrendKillRed() : trendCfg.getMaxTrendKillBlue();

        // 候选：仅「真趋冷 falling」可进趋势杀；回暖/多头抬头不杀
        List<TrendKillCandidate> candidates = new ArrayList<>();
        for (int b = min; b <= max; b++) {
            if (exclude.contains(b)) {
                continue;
            }
            TrendAnalysisResult result = LotteryTrendUtils.analyze(drawSets, b);
            String phase = result.getPhase();
            // rebounding / rising / cooling / neutral：禁止因空头堆叠误杀回暖号
            if (!"falling".equals(phase)) {
                continue;
            }
            double score = trendScore(result);
            if (score < killThreshold) {
                continue;
            }
            candidates.add(new TrendKillCandidate(b, score, calcBearishSpread(result),
                currentIndex(result), result.getStats().getCurrentOmission(),
                KillItemBo.builder()
                    .ball(b)
                    .score(round(score))
                    .reason(buildTrendReason(result, score))
                    .build()));
        }

        candidates.sort(Comparator
            .comparingDouble(TrendKillCandidate::score).reversed()
            .thenComparing(Comparator.comparingDouble(TrendKillCandidate::spread).reversed())
            .thenComparingDouble(TrendKillCandidate::currentIndex)
            .thenComparing(Comparator.comparingInt(TrendKillCandidate::currentOmission).reversed())
            .thenComparingInt(TrendKillCandidate::ball));

        List<KillItemBo> kills = candidates.stream()
            .limit(Math.max(topN, 0))
            .map(TrendKillCandidate::item)
            .collect(Collectors.toList());

        if (!kills.isEmpty()) {
            log.info("趋势杀号({} Top{}): {}", isRed ? "红球" : "蓝球", topN,
                kills.stream().map(k -> String.format("%02d(%.3f:%s)", k.getBall(), k.getScore(), k.getReason()))
                    .collect(Collectors.joining(", ")));
        }
        return kills;
    }

    /**
     * 空头开口：(MA20 - MA5) / MA20，越大表示空头排列越陡，同分时优先杀。
     */
    private double calcBearishSpread(TrendAnalysisResult result) {
        List<Double> ma5 = result.getMa5();
        List<Double> ma20 = result.getMa20();
        int last = ma5.size() - 1;
        if (last < 0 || ma5.get(last) == null || ma20.get(last) == null) {
            return 0.0;
        }
        double v5 = ma5.get(last);
        double v20 = ma20.get(last);
        return (v20 - v5) / Math.max(v20, 0.01);
    }

    private double currentIndex(TrendAnalysisResult result) {
        List<Double> indexValues = result.getIndexValues();
        if (indexValues == null || indexValues.isEmpty()) {
            return Double.MAX_VALUE;
        }
        return indexValues.get(indexValues.size() - 1);
    }

    private record TrendKillCandidate(int ball, double score, double spread, double currentIndex,
                                      int currentOmission, KillItemBo item) {
    }

    /**
     * 构建趋势杀号原因说明。
     */
    private String buildTrendReason(TrendAnalysisResult result, double score) {
        List<Double> ma5 = result.getMa5();
        List<Double> ma10 = result.getMa10();
        List<Double> ma20 = result.getMa20();
        int last = ma5.size() - 1;
        List<String> reasons = new ArrayList<>();

        if (last >= 0 && ma5.get(last) != null && ma10.get(last) != null && ma20.get(last) != null) {
            double v5 = ma5.get(last), v10 = ma10.get(last), v20 = ma20.get(last);
            if (v5 < v10 && v10 < v20) {
                reasons.add("指数均线空头排列");
            }
        }
        if (last >= 1 && ma5.get(last - 1) != null && ma20.get(last - 1) != null) {
            if (ma5.get(last - 1) >= ma20.get(last - 1) && ma5.get(last) < ma20.get(last)) {
                reasons.add("短期均线下穿长期均线");
            }
        }
        if (reasons.isEmpty()) {
            reasons.add("趋势走弱");
        }
        return String.join(" + ", reasons) + String.format(" 置信度%.3f", score);
    }

    /**
     * 根据趋势分析结果计算趋势维度得分。
     * <p>规则：
     * <ol>
     *     <li>空头排列（MA5 < MA10 < MA20）→ 指数持续下跌 → 遗漏增大 → 杀号</li>
     *     <li>大幅上涨后空头排列 → 额外加分（将继续下跌）</li>
     *     <li>短期均线下穿长期均线 + 长期均线疲软 + 夹角够大 → 杀号</li>
     * </ol>
     *
     * @param result 趋势分析结果（含指数序列、均线、统计）
     * @return 趋势得分 [0,1]
     */
    private double trendScore(TrendAnalysisResult result) {
        List<Double> indexValues = result.getIndexValues();
        List<Double> ma5 = result.getMa5();
        List<Double> ma10 = result.getMa10();
        List<Double> ma20 = result.getMa20();

        int last = indexValues.size() - 1;
        // MA20 需要至少 20 期数据
        if (last < 20 || ma5.get(last) == null || ma10.get(last) == null || ma20.get(last) == null) {
            return 0.0;
        }

        KillNumberConfig.TrendThreshold t = killNumberConfig.getTrend();
        // 斜率已抬头（回暖）→ 不参与趋势硬杀打分
        if (result.getMa5Slope() > 0 || "rebounding".equals(result.getPhase())
            || "rising".equals(result.getPhase())) {
            return 0.0;
        }
        double score = 0.0;

        double v5 = ma5.get(last);
        double v10 = ma10.get(last);
        double v20 = ma20.get(last);

        // 1. 空头排列：MA5 < MA10 < MA20（指数均线空头排列，将继续下跌）
        boolean bearish = v5 < v10 && v10 < v20;
        if (bearish) {
            score = Math.max(score, t.getBearishScore());

            // 2. 大幅上涨后空头排列：近期峰值 / 当前值 > riseRatio
            int lookback = Math.min(t.getRiseLookback(), indexValues.size() - 1);
            double recentMax = 0;
            for (int i = last - lookback; i <= last; i++) {
                if (i >= 0 && indexValues.get(i) > recentMax) {
                    recentMax = indexValues.get(i);
                }
            }
            double currentIdx = Math.max(indexValues.get(last), 0.01);
            if (recentMax / currentIdx > t.getRiseRatio()) {
                score = Math.max(score, t.getBearishScore() + t.getPostRiseBonus());
            }
        }

        // 3. 短期均线下穿长期均线 + 夹角够大 → 杀号
        if (last >= 1 && ma5.get(last - 1) != null && ma20.get(last - 1) != null) {
            boolean crossBelow = ma5.get(last - 1) >= ma20.get(last - 1) && v5 < v20;
            if (crossBelow) {
                double angle = Math.abs(v5 - v20) / Math.max(v20, 0.01);
                if (angle > t.getAngleThreshold()) {
                    score = Math.max(score, t.getCrossScore());
                }
            }
        }

        // 4. 长期均线走势疲软（MA20 斜率 ≤ 0）+ 短期在长期下方 + 夹角够大
        int slopeLookback = Math.min(t.getMaSlopeLookback(), ma20.size() - 1);
        if (slopeLookback > 0 && ma20.get(last - slopeLookback) != null) {
            double ma20Slope = v20 - ma20.get(last - slopeLookback);
            boolean ma20Weak = ma20Slope <= 0;
            boolean shortBelowLong = v5 < v20;
            if (ma20Weak && shortBelowLong) {
                double angle = Math.abs(v5 - v20) / Math.max(v20, 0.01);
                if (angle > t.getAngleThreshold()) {
                    score = Math.max(score, t.getCrossScore() * 0.8);
                }
            }
        }

        return Math.min(score, 1.0);
    }

    // ==================== 加权融合 ====================

    private double weightedScore(Map<String, Double> dims) {
        Map<String, Double> weights = killNumberConfig.getWeights();
        double weightSum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (weightSum <= 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            sum += e.getValue() * dims.getOrDefault(e.getKey(), 0.0);
        }
        return sum / weightSum;
    }

    /**
     * 蓝球专用加权融合：仅按 {@link KillNumberConfig#getBlueWeights()} 中存在的维度归一化，
     * 避免被红球 rebound/zone/tail 权重稀释分母。
     * <p>蓝球只参与 frequency + omission，归一化分母 = 这两维权重之和。
     */
    private double blueWeightedScore(Map<String, Double> dims) {
        Map<String, Double> weights = killNumberConfig.getBlueWeights();
        double weightSum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (weightSum <= 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            sum += e.getValue() * dims.getOrDefault(e.getKey(), 0.0);
        }
        return sum / weightSum;
    }

    // ==================== 清单筛选 ====================

    /**
     * 按综合得分筛选硬杀清单：得分 ≥ threshold、不在 exclude 中、按得分降序取前 maxSize 个。
     *
     * <p>极值遗漏白名单保护（仅红球启用）：号码遗漏期数 / 样本大小 ≥
     * {@link KillNumberConfig#getExtremeOmissionWhitelistRatio()} 时，即使综合得分达标也
     * 不进入硬杀清单，避免极冷即将解冻的号码被误杀（与 rebound 维度双保险）。
     *
     * @param omission        各号码遗漏期数
     * @param sampleSize      样本大小
     * @param exclude         需排除的号码集合（预留扩展，当前硬杀为空集）
     * @param applyWhitelist  是否启用极值遗漏白名单保护（红球 true，蓝球 false）
     */
    private List<KillItemBo> pickTop(Map<Integer, Double> scores, Map<Integer, Integer> omission,
                                     int sampleSize, int maxSize, double threshold,
                                     Set<Integer> exclude, boolean applyWhitelist) {
        int whitelistMiss = applyWhitelist
            ? (int) Math.ceil(sampleSize * killNumberConfig.getExtremeOmissionWhitelistRatio())
            : Integer.MAX_VALUE;
        return scores.entrySet().stream()
            .filter(e -> e.getValue() >= threshold)
            .filter(e -> !exclude.contains(e.getKey()))
            // 极值遗漏白名单（仅红球）：遗漏期数 ≥ whitelistMiss 的号码不进硬杀清单
            .filter(e -> omission.getOrDefault(e.getKey(), sampleSize) < whitelistMiss)
            .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
            .limit(maxSize)
            .map(e -> KillItemBo.builder()
                .ball(e.getKey())
                .score(round(e.getValue()))
                .reason(buildReason(e.getKey(), e.getValue()))
                .build())
            .collect(Collectors.toList());
    }

    // ==================== 工具 ====================

    /**
     * 将样本统一为期号升序（最旧 → 最新）。
     * <p>遗漏期数从「最近一期」向前回溯，依赖时间正序。
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

    private String buildBasis(int sampleSize) {
        KillNumberConfig.TrendThreshold trend = killNumberConfig.getTrend();
        return String.format(
            "基于最近 %d 期样本，按 frequency(冷热)/omission(遗漏)/zone(三区)/tail(尾数)/rebound(冷号回补保护) "
                + "五维度加权融合，硬杀阈值 %.2f；红球极值遗漏(≥%.0f%%样本)号码白名单保护不进硬杀。"
                + "趋势均线仅对相位=falling（空头且斜率未抬头）独立杀号；回暖/上升不进趋势硬杀；"
                + "趋势杀上限红 Top%d / 蓝 Top%d（按趋势分→空头开口→当前指数→遗漏排序）。",
            sampleSize, killNumberConfig.getHardThreshold(),
            killNumberConfig.getExtremeOmissionWhitelistRatio() * 100,
            trend.getMaxTrendKillRed(), trend.getMaxTrendKillBlue());
    }

    private String buildReason(int ball, double score) {
        return String.format("号码 %02d 综合剔除置信度 %.3f", ball, score);
    }

    private double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private Set<Integer> ballsOf(List<KillItemBo> items) {
        if (items == null) {
            return Set.of();
        }
        return items.stream().map(KillItemBo::getBall).collect(Collectors.toSet());
    }

    private KillNumberResultBo emptyResult() {
        return KillNumberResultBo.builder()
            .hardKillRed(List.of())
            .hardKillBlue(List.of())
            .basis("样本为空，未计算杀号")
            .build();
    }
}
