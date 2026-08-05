package com.my.project.service.support.analyse;

import com.my.project.llm.bo.ColdHotAnalysisBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.feature.config.ColdHotConfig;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 冷+热球分析工具
 */
public class ColdHotAnalysisUtils {

    private static final int RED_MIN = 1;
    private static final int RED_MAX = 33;
    private static final int BLUE_MIN = 1;
    private static final int BLUE_MAX = 16;
    private static final int RED_PICK = 6;
    private static final int BLUE_PICK = 1;

    private static final ColdHotConfig coldHotConfig = new ColdHotConfig();

    private enum Band {
        HOT, WARM, COLD
    }

    public static ColdHotAnalysisBo calculate(List<HistoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return emptyResult();
        }
        var statsRecords =
                records.subList(0, Math.min(30, records.size())).stream().map(ColdHotAnalysisUtils::toDrawRecord)
                        .toList();
        // 遗漏计算要求期号升序（最旧→最新），兼容上游降序传入
        List<LotteryAnalysisReqBo.DrawRecord> chronological = toAscending(statsRecords);
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
        IntStream.rangeClosed(RED_MIN, RED_MAX).forEach(b -> {
            Band band = classify(
                    redFreq.getOrDefault(b, 0),
                    redOmission.getOrDefault(b, sampleSize),
                    redHotThreshold, redColdThreshold,
                    redCfg.getRecentMiss(), redCfg.getDeepMiss(), redCfg.getCoolDownMiss());
            addByBand(b, band, redHot, redWarm, redCold);
        });

        List<Integer> blueHot = new ArrayList<>();
        List<Integer> blueWarm = new ArrayList<>();
        List<Integer> blueCold = new ArrayList<>();
        IntStream.rangeClosed(BLUE_MIN, BLUE_MAX).forEach(b -> {
            Band band = classify(
                    blueFreq.getOrDefault(b, 0),
                    blueOmission.getOrDefault(b, sampleSize),
                    blueHotThreshold, blueColdThreshold,
                    blueCfg.getRecentMiss(), blueCfg.getDeepMiss(), blueCfg.getCoolDownMiss());
            addByBand(b, band, blueHot, blueWarm, blueCold);
        });

        return ColdHotAnalysisBo.builder()
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
    }

    private static LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
                Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                        record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
                .blueBall(record.getSpecial()).build();
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
    private static Band classify(int count, int miss, int hotThreshold, int coldThreshold,
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

    private static void addByBand(int ball, Band band,
                                  List<Integer> hot, List<Integer> warm, List<Integer> cold) {
        switch (band) {
            case HOT -> hot.add(ball);
            case WARM -> warm.add(ball);
            case COLD -> cold.add(ball);
        }
    }

    // ==================== 频次 / 遗漏 ====================

    private static Map<Integer, Integer> countRedFrequency(List<LotteryAnalysisReqBo.DrawRecord> records) {
        return records.stream()
                .filter(r -> r.getRedBalls() != null)
                .flatMap(r -> r.getRedBalls().stream())
                .collect(Collectors.toMap(Function.identity(), b -> 1, Integer::sum, HashMap::new));
    }

    private static Map<Integer, Integer> countBlueFrequency(List<LotteryAnalysisReqBo.DrawRecord> records) {
        return records.stream()
                .filter(r -> r.getBlueBall() != null)
                .collect(Collectors.toMap(LotteryAnalysisReqBo.DrawRecord::getBlueBall, b -> 1, Integer::sum, HashMap::new));
    }

    /**
     * 计算每个红球自最近一期起向前连续未开出的期数。
     * <p>最近一期开出则为 0；样本内从未开出则为样本大小。
     */
    private static Map<Integer, Integer> calcRedOmission(List<LotteryAnalysisReqBo.DrawRecord> records) {
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
    private static Map<Integer, Integer> calcBlueOmission(List<LotteryAnalysisReqBo.DrawRecord> records) {
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

    private static String buildBasis(int sampleSize, double redExpected, double blueExpected,
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

    private static ColdHotAnalysisBo emptyResult() {
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
