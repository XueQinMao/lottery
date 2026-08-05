package com.my.project.service.support.analyse;

import com.my.project.llm.bo.ColdHotAnalysisBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.feature.config.ColdHotConfig;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;
import com.my.project.service.support.OmissionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 冷+热球分析工具
 * <p>
 * 基于"近30期频次 + 遗漏趋势(当前/平均/最大遗漏 + 均线相位)"两阶段分类：
 * <ol>
 *     <li>频次初档：count ≥ hotRatio×期望 → 热候选；≤ coldRatio×期望 → 冷候选；其余 → 温候选</li>
 *     <li>遗漏趋势纠偏：结合 currentOmission/avgOmission/maxOmission + phase/arrangement 升降档</li>
 * </ol>
 */
public class ColdHotAnalysisUtils {

    private static final int RED_MIN = 1;
    private static final int RED_MAX = 33;
    private static final int BLUE_MIN = 1;
    private static final int BLUE_MAX = 16;
    private static final int RED_PICK = 6;
    private static final int BLUE_PICK = 1;
    private static final int SAMPLE_SIZE = 30;
    /** 接近历史最大遗漏的比例阈值，达到则视为极端冷却 */
    private static final double EXTREME_OMISSION_RATIO = 0.8;

    private static final ColdHotConfig coldHotConfig = new ColdHotConfig();

    private enum Band {
        HOT, WARM, COLD
    }

    public static ColdHotAnalysisBo calculate(List<HistoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return emptyResult();
        }
        List<HistoryRecord> historyRecords = records.subList(0, Math.min(SAMPLE_SIZE, records.size()));
        int sampleSize = historyRecords.size();

        // 近30期频次
        Map<Integer, Integer> readCountMap = new HashMap<>();
        Map<Integer, Integer> blueCountMap = new HashMap<>();
        historyRecords.forEach(h -> {
            readCountMap.merge(h.getNum1(), 1, Integer::sum);
            readCountMap.merge(h.getNum2(), 1, Integer::sum);
            readCountMap.merge(h.getNum3(), 1, Integer::sum);
            readCountMap.merge(h.getNum4(), 1, Integer::sum);
            readCountMap.merge(h.getNum5(), 1, Integer::sum);
            readCountMap.merge(h.getNum6(), 1, Integer::sum);
            blueCountMap.merge(h.getSpecial(), 1, Integer::sum);
        });

        // 遗漏趋势分析（当前遗漏 / 平均遗漏 / 最大遗漏 / 均线相位）
        Map<Integer, TrendAnalysisVo> redOmissionMaps = IntStream.rangeClosed(RED_MIN, RED_MAX).boxed()
            .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(historyRecords, "red", ball)))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        Map<Integer, TrendAnalysisVo> blueOmissionMaps = IntStream.rangeClosed(BLUE_MIN, BLUE_MAX).boxed()
            .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(historyRecords, "blue", ball)))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // 期望次数与热/冷门槛
        double redExpected = (double)sampleSize * RED_PICK / (RED_MAX - RED_MIN + 1);
        double blueExpected = (double)sampleSize * BLUE_PICK / (BLUE_MAX - BLUE_MIN + 1);
        ColdHotConfig.RedThreshold redCfg = coldHotConfig.getRed();
        ColdHotConfig.BlueThreshold blueCfg = coldHotConfig.getBlue();
        int redHotThreshold = (int)Math.ceil(redExpected * redCfg.getHotRatio());
        int redColdThreshold = (int)Math.floor(redExpected * redCfg.getColdRatio());
        int blueHotThreshold = (int)Math.ceil(blueExpected * blueCfg.getHotRatio());
        int blueColdThreshold = (int)Math.floor(blueExpected * blueCfg.getColdRatio());

        // 分类：频次初档 + 遗漏趋势纠偏
        List<Integer> redHot = new ArrayList<>();
        List<Integer> redWarm = new ArrayList<>();
        List<Integer> redCold = new ArrayList<>();
        IntStream.rangeClosed(RED_MIN, RED_MAX).forEach(b -> {
            Band band =
                classify(readCountMap.getOrDefault(b, 0), redOmissionMaps.get(b), redHotThreshold, redColdThreshold,
                    redCfg.getRecentMiss(), redCfg.getDeepMiss(), redCfg.getCoolDownMiss());
            addByBand(b, band, redHot, redWarm, redCold);
        });

        List<Integer> blueHot = new ArrayList<>();
        List<Integer> blueWarm = new ArrayList<>();
        List<Integer> blueCold = new ArrayList<>();
        IntStream.rangeClosed(BLUE_MIN, BLUE_MAX).forEach(b -> {
            Band band =
                classify(blueCountMap.getOrDefault(b, 0), blueOmissionMaps.get(b), blueHotThreshold, blueColdThreshold,
                    blueCfg.getRecentMiss(), blueCfg.getDeepMiss(), blueCfg.getCoolDownMiss());
            addByBand(b, band, blueHot, blueWarm, blueCold);
        });

        return ColdHotAnalysisBo.builder().redHotBalls(redHot).redWarmBalls(redWarm).redColdBalls(redCold)
            .blueHotBalls(blueHot).blueWarmBalls(blueWarm).blueColdBalls(blueCold).basis(
                buildBasis(sampleSize, redExpected, blueExpected, redHotThreshold, redColdThreshold, blueHotThreshold,
                    blueColdThreshold, redCfg, blueCfg)).build();
    }

    /**
     * 频次初档 + 遗漏趋势纠偏。
     *
     * <pre>
     * 阶段一·频次初档：
     *   count ≥ hotThreshold → 热候选
     *   count ≤ coldThreshold → 冷候选
     *   其余 → 温候选
     *
     * 阶段二·遗漏趋势纠偏（结合 currentOmission / avgOmission / maxOmission + phase + arrangement）：
     *   反向指数 = avgOmission / max(omission,1)，遗漏越大指数越小；故
     *     arrangement=1（多头）= 指数走高 = 遗漏下降 = 变热
     *     arrangement=-1（空头）= 指数走低 = 遗漏上升 = 变冷
     *   phase：rising/rebounding=变热趋势，falling/cooling=变冷趋势
     *
     *   热候选：
     *     currentOmission ≥ maxOmission×0.8 且趋势向下 → 冷（极端冷却）
     *     currentOmission ≥ avgOmission 且趋势向下 → 温（跌破平均遗漏）
     *     currentOmission ≥ coolDownMiss 且 cooling → 温（达冷却阈值）
     *     否则 → 热
     *   冷候选：
     *     currentOmission ≤ recentMiss 且趋势向上 → 热（强势回暖）
     *     currentOmission ≤ recentMiss 或 (趋势向上 且 ≤ avgOmission) → 温（回暖）
     *     否则 → 冷
     *   温候选：
     *     currentOmission ≥ maxOmission×0.8 且趋势向下 → 冷（接近历史极值）
     *     currentOmission ≥ deepMiss 且趋势不向上 → 冷（深冷）
     *     count ≥ hotThreshold-1 且趋势向上且多头 → 热（温升热）
     *     否则 → 温
     * </pre>
     */
    private static Band classify(int count, TrendAnalysisVo trend, int hotThreshold, int coldThreshold, int recentMiss,
        int deepMiss, int coolDownMiss) {
        int currentOmission = trend.getStats().getCurrentOmission();
        double avgOmission = trend.getStats().getAvgOmission();
        int maxOmission = trend.getStats().getMaxOmission();
        String phase = trend.getPhase();
        int arrangement = trend.getArrangement();

        boolean warming = "rising" .equals(phase) || "rebounding" .equals(phase);
        boolean cooling = "falling" .equals(phase) || "cooling" .equals(phase);
        boolean bullish = arrangement == 1;   // 多头=反向指数走高=遗漏下降=变热
        boolean bearish = arrangement == -1; // 空头=反向指数走低=遗漏上升=变冷
        boolean downTrend = cooling || bearish;
        boolean upTrend = warming || bullish;

        // 频次初档
        Band initial;
        if (count >= hotThreshold) {
            initial = Band.HOT;
        } else if (count <= coldThreshold) {
            initial = Band.COLD;
        } else {
            initial = Band.WARM;
        }

        return switch (initial) {
            // 热候选：冷却则降档
            case HOT -> {
                if (maxOmission > 0 && currentOmission >= maxOmission * EXTREME_OMISSION_RATIO && downTrend) {
                    yield Band.COLD; // 极端冷却：接近历史最大遗漏且趋势向下
                }
                if (currentOmission >= avgOmission && downTrend) {
                    yield Band.WARM; // 跌破平均遗漏且趋势向下
                }
                if (currentOmission >= coolDownMiss && cooling) {
                    yield Band.WARM; // 达冷却阈值且正在冷却
                }
                yield Band.HOT;
            }
            // 冷候选：回暖则升档
            case COLD -> {
                if (currentOmission <= recentMiss && upTrend) {
                    yield Band.HOT; // 刚回补且趋势向上 → 强势回暖
                }
                if (currentOmission <= recentMiss || (upTrend && currentOmission <= avgOmission)) {
                    yield Band.WARM; // 刚回补 或 趋势向上且已回到平均线以下
                }
                yield Band.COLD;
            }
            // 温候选：双向调整
            case WARM -> {
                if (maxOmission > 0 && currentOmission >= maxOmission * EXTREME_OMISSION_RATIO && downTrend) {
                    yield Band.COLD; // 接近历史极值且趋势向下
                }
                if (currentOmission >= deepMiss && !upTrend) {
                    yield Band.COLD; // 深冷且趋势不向上
                }
                if (count >= hotThreshold - 1 && upTrend && bullish) {
                    yield Band.HOT; // 频次接近热且趋势向上多头
                }
                yield Band.WARM;
            }
        };
    }

    private static void addByBand(int ball, Band band, List<Integer> hot, List<Integer> warm, List<Integer> cold) {
        switch (band) {
            case HOT -> hot.add(ball);
            case WARM -> warm.add(ball);
            case COLD -> cold.add(ball);
        }
    }

    // ==================== 工具 ====================

    private static String buildBasis(int sampleSize, double redExpected, double blueExpected, int redHotThreshold,
        int redColdThreshold, int blueHotThreshold, int blueColdThreshold, ColdHotConfig.RedThreshold redCfg,
        ColdHotConfig.BlueThreshold blueCfg) {
        return String.format(
            "基于最近 %d 期样本，频次初档+遗漏趋势纠偏(currentOmission/avgOmission/maxOmission+phase/arrangement)。" + "红球期望 %.2f（热候选≥%d、冷候选≤%d；" + "热且≥max×%.1f%%且趋势向下→冷，热且≥avg且趋势向下→温，热且≥%d且cooling→温；" + "冷且≤%d且趋势向上→热，冷且≤%d或(趋势向上且≤avg)→温；" + "温且≥max×%.1f%%且趋势向下→冷，温且≥%d且趋势不向上→冷，温且≥hot-1且趋势向上多头→热）；" + "蓝球期望 %.2f（热候选≥%d、冷候选≤%d；同上规则，recentMiss=%d、deepMiss=%d、coolDownMiss=%d）。",
            sampleSize, redExpected, redHotThreshold, redColdThreshold, EXTREME_OMISSION_RATIO * 100,
            redCfg.getCoolDownMiss(), redCfg.getRecentMiss(), redCfg.getRecentMiss(), EXTREME_OMISSION_RATIO * 100,
            redCfg.getDeepMiss(), blueExpected, blueHotThreshold, blueColdThreshold, blueCfg.getRecentMiss(),
            blueCfg.getDeepMiss(), blueCfg.getCoolDownMiss());
    }

    private static ColdHotAnalysisBo emptyResult() {
        return ColdHotAnalysisBo.builder().redHotBalls(List.of()).redWarmBalls(List.of()).redColdBalls(List.of())
            .blueHotBalls(List.of()).blueWarmBalls(List.of()).blueColdBalls(List.of()).basis("样本为空，未计算冷热温")
            .build();
    }
}
