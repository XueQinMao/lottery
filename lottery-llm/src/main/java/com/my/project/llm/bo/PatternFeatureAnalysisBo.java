package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** 单项形态的事实、中文现象总结和下一期假设；候选池不是命中概率或硬约束。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternFeatureAnalysisBo {
    private String code;
    private String label;
    /** OK / UNAVAILABLE；source 为 llm-pattern 或 java。 */
    private String status;
    private String source;
    /** 本次形态观察的实际期数；旧报告可能为空。 */
    private Integer sampleSize;
    /** 内部事实校验使用；兼容读取旧报告，但不写入文件、接口和推荐模型输入。 */
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private List<Observation> observations;
    /** 分析不可用时的实际失败原因，不能将失败包装成预测。 */
    private String failureReason;
    private String patternSummary;
    private String nextPeriodInference;
    private List<String> likelyValues;
    private List<String> unlikelyValues;
    private String likelyReason;
    private String unlikelyReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Observation {
        private Integer position;
        private String period;
        private List<Integer> redBalls;
        private Integer blueBall;
        /** 尾数为 0尾:1尾:...:9尾 的十项计数；冷温热为 热:温:冷。 */
        private String value;
        /** 冷温热特征专用：仅用该期开奖之前的30期计算。 */
        private ColdHotAnalysisBo priorColdHot;
    }
}
