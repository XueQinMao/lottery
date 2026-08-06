package com.my.project.llm.service.impl;

import com.my.project.llm.bo.ColdHotAnalysisBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;
import com.my.project.llm.config.ColdHotConfig;
import com.my.project.llm.service.IColdHotAnalysisService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ColdHotAnalysisServiceImpl
 *
 * <p>基于历史开奖样本，按出现频次将红球（1-33）/蓝球（1-16）分为热号 / 温号 / 冷号三类。
 *
 * <p>分类采用「比例阈值」自适应样本量：以单号期望出现次数为基准，
 * 出现次数 ≥ hotRatio × 期望 → 热号；≤ coldRatio × 期望 → 冷号；之间 → 温号。
 * <ul>
 *     <li>红球期望 = sampleSize × 6/33</li>
 *     <li>蓝球期望 = sampleSize × 1/16</li>
 * </ul>
 *
 * <p>所有计算在 Java 侧完成，不依赖 LLM 的算术能力，保证结果可解释、可回测、可调参。
 *
 * @author 刘强
 * @version 2026/08/06 19:45
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

    @Override
    public ColdHotAnalysisBo calculate(List<DrawRecord> records) {
        if (records == null || records.isEmpty()) {
            log.warn("冷热温分析样本为空，跳过");
            return emptyResult();
        }

        int sampleSize = records.size();
        Map<Integer, Integer> redFreq = countRedFrequency(records);
        Map<Integer, Integer> blueFreq = countBlueFrequency(records);

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
            int count = redFreq.getOrDefault(b, 0);
            if (count >= redHotThreshold) {
                redHot.add(b);
            } else if (count <= redColdThreshold) {
                redCold.add(b);
            } else {
                redWarm.add(b);
            }
        }

        List<Integer> blueHot = new ArrayList<>();
        List<Integer> blueWarm = new ArrayList<>();
        List<Integer> blueCold = new ArrayList<>();
        for (int b = BLUE_MIN; b <= BLUE_MAX; b++) {
            int count = blueFreq.getOrDefault(b, 0);
            if (count >= blueHotThreshold) {
                blueHot.add(b);
            } else if (count <= blueColdThreshold) {
                blueCold.add(b);
            } else {
                blueWarm.add(b);
            }
        }

        ColdHotAnalysisBo result = ColdHotAnalysisBo.builder()
            .redHotBalls(redHot)
            .redWarmBalls(redWarm)
            .redColdBalls(redCold)
            .blueHotBalls(blueHot)
            .blueWarmBalls(blueWarm)
            .blueColdBalls(blueCold)
            .basis(buildBasis(sampleSize, redExpected, blueExpected,
                redHotThreshold, redColdThreshold, blueHotThreshold, blueColdThreshold))
            .build();

        log.info("冷热温分析完成: 红球 热={}, 温={}, 冷={}; 蓝球 热={}, 温={}, 冷={}",
            redHot.size(), redWarm.size(), redCold.size(),
            blueHot.size(), blueWarm.size(), blueCold.size());
        return result;
    }

    // ==================== 频次统计 ====================

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

    // ==================== 工具 ====================

    private String buildBasis(int sampleSize, double redExpected, double blueExpected,
                              int redHotThreshold, int redColdThreshold,
                              int blueHotThreshold, int blueColdThreshold) {
        return String.format(
            "基于最近 %d 期样本，红球单号期望 %.2f 次（热≥%d、冷≤%d、温居中）；"
                + "蓝球单号期望 %.2f 次（热≥%d、冷≤%d、温居中）。",
            sampleSize, redExpected, redHotThreshold, redColdThreshold,
            blueExpected, blueHotThreshold, blueColdThreshold);
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
