package com.my.project.service.selection.pojo.bo;

import lombok.Data;

import java.util.Map;

/**
 * PredictedFeaturesBo
 *
 * @author 刘强
 * @version 2025/12/22 19:40
 **/
@Data
public class PredictedFeaturesBo {
    /** @deprecated 点预测已弱化，主路径改用 featureRange 区间约束 */
    private NumericFeaturesBo numeric;
    /** @deprecated 点预测已弱化，主路径改用 featureRange 区间约束 */
    private CategoricalFeaturesBo categorical;
    private Map<Integer, Double> blueProbability;  // 蓝球号码 -> 概率
    /** 历史分位数特征区间（主打分依据） */
    private FeatureRangeBo featureRange;
}
