package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FeatureForecastBo
 *
 * <p>红球 11 维 + 蓝球 4 维下一期形态目标。默认由 Java 按命中间隔扩张/收缩评分产出
 * （稀有桶过滤、刚出不主推、红蓝自洽）；engine=llm 时先快照再逐维问 LLM，不合规则回退 Java。
 *
 * @author 刘强
 * @version 2026/08/17
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureForecastBo {

    /** 奇偶比，如 "3:3" */
    private FeatureForecastItem oddEven;
    /** 大小比，如 "3:3" */
    private FeatureForecastItem bigSmall;
    /** 质合比，如 "2:4"（走势图口径：01 为质） */
    private FeatureForecastItem primeComposite;
    /** 012路比，如 "2:2:2" */
    private FeatureForecastItem ratio012;
    /** 跨度，精确值如 "21" 或区间如 "20-24" */
    private FeatureForecastItem span;
    /** 和值区间，如 "97-102" 或合并区间 "91-108" */
    private FeatureForecastItem sumRange;
    /** 和值尾数，精确值如 "3" 或区间如 "2-5" */
    private FeatureForecastItem sumTail;
    /** 三区比，如 "2:2:2" */
    private FeatureForecastItem threeZone;
    /** 一区个数，如 "2" 或 "1-2" */
    private FeatureForecastItem zone1Count;
    /** 二区个数 */
    private FeatureForecastItem zone2Count;
    /** 三区个数 */
    private FeatureForecastItem zone3Count;
    /** 蓝球奇偶，如 "奇" / "偶" */
    private FeatureForecastItem blueOddEven;
    /** 蓝球大小，如 "大" / "小"（小=1-8，大=9-16） */
    private FeatureForecastItem blueBigSmall;
    /** 蓝球大小奇偶，如 "大奇" / "小偶" */
    private FeatureForecastItem blueBigSmallOddEven;
    /** 蓝球012路，如 "0路" / "1路" / "2路" */
    private FeatureForecastItem blueRatio012;
    /** 综合依据（200 字内） */
    private String basis;

    /**
     * 按形态维取对应推算项（供 LLM 失败回退等场景）。
     * code 与 {@code FeatureKind#getCode()} 一致。
     */
    public FeatureForecastItem itemOf(String featureCode) {
        if (featureCode == null) {
            return null;
        }
        return switch (featureCode) {
            case "oddEven" -> oddEven;
            case "bigSmall" -> bigSmall;
            case "primeComp" -> primeComposite;
            case "ratio012" -> ratio012;
            case "span" -> span;
            case "sumRange" -> sumRange;
            case "sumTail" -> sumTail;
            case "threeZone" -> threeZone;
            case "zone1Count" -> zone1Count;
            case "zone2Count" -> zone2Count;
            case "zone3Count" -> zone3Count;
            case "blueOddEven" -> blueOddEven;
            case "blueBigSmall" -> blueBigSmall;
            case "blueBigSmallOddEven" -> blueBigSmallOddEven;
            case "blueRatio012" -> blueRatio012;
            default -> null;
        };
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureForecastItem {
        /** 主推值或区间 */
        private String value;
        /** 备选 1-3 个 */
        private List<String> alternatives;
        /** 置信度 [0,1]（由 Top1/Top2 分差推导） */
        private Double confidence;
        /** 简要依据 */
        private String reason;
        /** heating / cooling / stable / unknown */
        private String gapTrend;
        /** 预计下次完整周期 Ĝ（期） */
        private Double predictedGap;
        /** 当前遗漏期数 */
        private Integer currentOmission;
        /** 距预计接入还剩几期：Ĝ − currentOmission */
        private Integer eta;
        /** eta∈{0,1} 时为接入窗口 */
        private Boolean dueWindow;
        /** 桶综合得分 */
        private Double score;
        /** 最近若干次命中间隔 */
        private List<Integer> recentGaps;
    }
}
