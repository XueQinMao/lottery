package com.my.project.service.llm.impl;

import com.my.project.llm.bo.ColdHotAnalysisBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;
import com.my.project.service.llm.IColdHotAnalysisService;
import com.my.project.service.llm.config.ColdHotConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ColdHotAnalysisServiceImpl
 *
 * <p>基于历史开奖样本，按「频次初档 + 遗漏纠偏」将红球（1-33）/蓝球（1-16）分为热 / 温 / 冷三类。
 * 所有计算在 Java 侧完成，不依赖 LLM 的算术能力，保证结果可解释、可回测、可调参。
 *
 * <h2>一、前置：样本排序</h2>
 * 遗漏计算要求期号升序（最旧 → 最新）。有 {@code period} 则按期号排序；否则默认上游为降序并反转。
 *
 * <h2>二、两个基础量</h2>
 * <ul>
 *     <li>{@code count}：窗口内该号码一共开出几次（红球每期可累加多个；蓝球每期至多 1）</li>
 *     <li>{@code miss}：自最近一期向前，连续未开出的期数
 *         <ul>
 *             <li>最近一期开出 → miss = 0</li>
 *             <li>倒数第 k 期开出（最新为第 1 期往回数）→ miss = k - 1，
 *                 即 {@code miss = (sampleSize - 1) - lastAppearIndex}（升序下标）</li>
 *             <li>样本内从未开出 → miss = sampleSize</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h2>三、期望与频次门槛（默认 30 期）</h2>
 * <pre>
 * 红球期望 = sampleSize × 6 / 33 ≈ 5.45
 * 蓝球期望 = sampleSize × 1 / 16 ≈ 1.875
 *
 * 热门槛 = ceil(期望 × hotRatio)    红默认 1.3 → ≥8；蓝默认 2.0 → ≥4
 * 冷门槛 = floor(期望 × coldRatio)  红默认 0.4 → ≤2；蓝默认 0.5 → ≤0
 * </pre>
 *
 * <h2>四、两阶段分类</h2>
 * <ol>
 *     <li>频次初档：count ≥ 热门槛 → 热候选；count ≤ 冷门槛 → 冷候选；其余 → 温候选</li>
 *     <li>遗漏纠偏（默认阈值见 {@link ColdHotConfig}）：
 *         <ul>
 *             <li>冷候选且 miss ≤ recentMiss → 升为温（刚回补，不算当前冷）</li>
 *             <li>温候选且 miss ≥ deepMiss → 降为冷（近期长期未开）</li>
 *             <li>热候选且 miss ≥ coolDownMiss → 降为温（热号已冷却）</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <h2>五、红球速查表（30 期默认：recentMiss=1, deepMiss=10, coolDownMiss=6）</h2>
 * <pre>
 * count\miss |  0–1  |  2–5  |  6–9  | ≥10
 * -----------+-------+-------+-------+-----
 * ≥8（热候选）|  热   |  热   |  温   |  温
 * 3–7（温候选）|  温   |  温   |  温   |  冷
 * ≤2（冷候选）|  温   |  冷   |  冷   |  冷
 * </pre>
 *
 * <h2>六、举例（红球 20，近 30 期）</h2>
 * <pre>
 * 例1：count=2, miss=0（上一期刚开）
 *   频次 → 冷候选；miss≤1 → 纠偏为温号
 *
 * 例2：count=2, miss=3（近 3 期未开）
 *   频次 → 冷候选；miss&gt;1 → 保持冷号
 *
 * 例3：count=0, miss=30（样本内从未开出）
 *   频次 → 冷候选 → 冷号
 *
 * 例4：count=9, miss=0（高频且刚开）
 *   频次 → 热候选；miss&lt;6 → 热号
 *
 * 例5：count=9, miss=7（曾热但已 7 期未开）
 *   频次 → 热候选；miss≥6 → 降为温号
 *
 * 例6：count=5, miss=12（温频次但深遗漏）
 *   频次 → 温候选；miss≥10 → 降为冷号
 * </pre>
 *
 * <p>miss 只看「最近一次开出距今几期」；窗口内开了几次全由 count 负责。
 *
 * @author 刘强
 * @version 2026/08/10 11:37
 **/
@Slf4j
@Service
@AllArgsConstructor
public class ColdHotAnalysisServiceImpl implements IColdHotAnalysisService {

    private static final int RED_MIN = 1;
    private static final int RED_MAX = 33;
    private static final int BLUE_MIN = 1;
    private static final int BLUE_MAX = 16;
    private static final int RED_PICK = 6;
    private static final int BLUE_PICK = 1;

    private final ColdHotConfig coldHotConfig;

    private enum Band {
        HOT, WARM, COLD
    }

    @Override
    public ColdHotAnalysisBo calculate(List<DrawRecord> records) {
        if (records == null || records.isEmpty()) {
            log.warn("冷热温分析样本为空，跳过");
            return emptyResult();
        }
        List<DrawRecord> drawRecords = records.subList(0, Math.min(30, records.size()));
        // 遗漏计算要求期号升序（最旧→最新），兼容上游降序传入
        List<DrawRecord> chronological = toAscending(drawRecords);
        int sampleSize = chronological.size();

        Map<Integer, Integer> redFreq = countRedFrequency(chronological);
        Map<Integer, Integer> blueFreq = countBlueFrequency(chronological);
        Map<Integer, Integer> redOmission = calcRedOmission(chronological);
        Map<Integer, Integer> blueOmission = calcBlueOmission(chronological);

        double redExpected = (double) sampleSize * RED_PICK / (RED_MAX - RED_MIN + 1);
        double blueExpected = (double) sampleSize * BLUE_PICK / (BLUE_MAX - BLUE_MIN + 1);

        ColdHotConfig.RedThreshold redCfg = coldHotConfig.getRed();
        ColdHotConfig.BlueThreshold blueCfg = coldHotConfig.getBlue();
        int redHotThreshold = (int) Math.ceil(redExpected * redCfg.getHotRatio());
        int redColdThreshold = (int) Math.floor(redExpected * redCfg.getColdRatio());
        int blueHotThreshold = (int) Math.ceil(blueExpected * blueCfg.getHotRatio());
        int blueColdThreshold = (int) Math.floor(blueExpected * blueCfg.getColdRatio());

        List<Integer> redHot = new ArrayList<>();
        List<Integer> redWarm = new ArrayList<>();
        List<Integer> redCold = new ArrayList<>();
        for (int b = RED_MIN; b <= RED_MAX; b++) {
            Band band = classify(
                redFreq.getOrDefault(b, 0),
                redOmission.getOrDefault(b, sampleSize),
                redHotThreshold, redColdThreshold,
                redCfg.getRecentMiss(), redCfg.getDeepMiss(), redCfg.getCoolDownMiss());
            addByBand(b, band, redHot, redWarm, redCold);
        }

        List<Integer> blueHot = new ArrayList<>();
        List<Integer> blueWarm = new ArrayList<>();
        List<Integer> blueCold = new ArrayList<>();
        for (int b = BLUE_MIN; b <= BLUE_MAX; b++) {
            Band band = classify(
                blueFreq.getOrDefault(b, 0),
                blueOmission.getOrDefault(b, sampleSize),
                blueHotThreshold, blueColdThreshold,
                blueCfg.getRecentMiss(), blueCfg.getDeepMiss(), blueCfg.getCoolDownMiss());
            addByBand(b, band, blueHot, blueWarm, blueCold);
        }

        ColdHotAnalysisBo result = ColdHotAnalysisBo.builder()
            .redHotBalls(redHot)
            .redWarmBalls(redWarm)
            .redColdBalls(redCold)
            .blueHotBalls(blueHot)
            .blueWarmBalls(blueWarm)
            .blueColdBalls(blueCold)
            .basis(buildBasis(sampleSize, redExpected, blueExpected,
                redHotThreshold, redColdThreshold, blueHotThreshold, blueColdThreshold,
                redCfg, blueCfg))
            .build();

        log.info("冷热温分析完成: 红球 热={}, 温={}, 冷={}; 蓝球 热={}, 温={}, 冷={}",
            redHot.size(), redWarm.size(), redCold.size(),
            blueHot.size(), blueWarm.size(), blueCold.size());
        return result;
    }

    /**
     * 频次初档 + 遗漏纠偏。
     *
     * <pre>
     * count≥hot → 热候选；count≤cold → 冷候选；其余 → 温候选
     * 冷候选且 miss≤recentMiss → 温
     * 温候选且 miss≥deepMiss   → 冷
     * 热候选且 miss≥coolDown   → 温
     * </pre>
     */
    private Band classify(int count, int miss, int hotThreshold, int coldThreshold,
                          int recentMiss, int deepMiss, int coolDownMiss) {
        Band initial;
        if (count >= hotThreshold) {
            initial = Band.HOT;
        } else if (count <= coldThreshold) {
            initial = Band.COLD;
        } else {
            initial = Band.WARM;
        }

        return switch (initial) {
            case COLD -> miss <= recentMiss ? Band.WARM : Band.COLD;
            case WARM -> miss >= deepMiss ? Band.COLD : Band.WARM;
            case HOT -> miss >= coolDownMiss ? Band.WARM : Band.HOT;
        };
    }

    private void addByBand(int ball, Band band,
                           List<Integer> hot, List<Integer> warm, List<Integer> cold) {
        switch (band) {
            case HOT -> hot.add(ball);
            case WARM -> warm.add(ball);
            case COLD -> cold.add(ball);
        }
    }

    // ==================== 频次 / 遗漏 ====================

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

    private Map<Integer, Integer> countBlueFrequency(List<DrawRecord> records) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (DrawRecord r : records) {
            if (r.getBlueBall() != null) {
                freq.merge(r.getBlueBall(), 1, Integer::sum);
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

    /**
     * 计算每个蓝球自最近一期起向前连续未开出的期数。
     */
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

    // ==================== 工具 ====================

    /**
     * 转为期号升序（最旧→最新）。有 period 按 period 排序；否则默认上游为降序并反转。
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

    private String buildBasis(int sampleSize, double redExpected, double blueExpected,
                              int redHotThreshold, int redColdThreshold,
                              int blueHotThreshold, int blueColdThreshold,
                              ColdHotConfig.RedThreshold redCfg,
                              ColdHotConfig.BlueThreshold blueCfg) {
        return String.format(
            "基于最近 %d 期样本，频次初档+遗漏纠偏。"
                + "红球期望 %.2f（热候选≥%d、冷候选≤%d；冷且miss≤%d→温，温且miss≥%d→冷，热且miss≥%d→温）；"
                + "蓝球期望 %.2f（热候选≥%d、冷候选≤%d；冷且miss≤%d→温，温且miss≥%d→冷，热且miss≥%d→温）。",
            sampleSize,
            redExpected, redHotThreshold, redColdThreshold,
            redCfg.getRecentMiss(), redCfg.getDeepMiss(), redCfg.getCoolDownMiss(),
            blueExpected, blueHotThreshold, blueColdThreshold,
            blueCfg.getRecentMiss(), blueCfg.getDeepMiss(), blueCfg.getCoolDownMiss());
    }

    private ColdHotAnalysisBo emptyResult() {
        return ColdHotAnalysisBo.builder()
            .redHotBalls(List.of())
            .redWarmBalls(List.of())
            .redColdBalls(List.of())
            .blueHotBalls(List.of())
            .blueWarmBalls(List.of())
            .blueColdBalls(List.of())
            .basis("样本为空，未计算冷热温")
            .build();
    }
}
