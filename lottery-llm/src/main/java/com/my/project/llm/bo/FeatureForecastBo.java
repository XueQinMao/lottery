package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FeatureForecastBo
 *
 * <p>LLM 基于形态指数页同源的遗漏 / 超额指数推算下一期目标值或区间。
 * 含红球 11 维与蓝球 4 维（奇偶、大小、大小奇偶、012路）。
 * 挂到 {@link LotteryAnalysisRespBo#getFeatureForecast()}，随分析报告透传给调优与推荐。
 *
 * @author 刘强
 * @version 2026/08/14
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureForecastItem {
        /** 主推值或区间 */
        private String value;
        /** 备选 1-3 个 */
        private List<String> alternatives;
        /** 置信度 [0,1] */
        private Double confidence;
        /** 简要依据 */
        private String reason;
    }
}
