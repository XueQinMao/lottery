package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternFeatureAnalysisReqBo {
    public static final int HISTORY_SIZE = 30;
    private String code;
    private String label;
    private String definition;
    private List<String> allowedValues;
    /** 恰好30期，按最旧到最新排序。value 为 Java 计算的模型输入事实及输出校验基准。 */
    private List<PatternFeatureAnalysisBo.Observation> observations;
}
