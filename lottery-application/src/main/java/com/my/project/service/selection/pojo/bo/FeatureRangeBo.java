package com.my.project.service.selection.pojo.bo;

import lombok.Data;

/**
 * 历史特征分位数区间（用于约束打分，替代 ARIMA 点预测）
 *
 * @author 刘强
 */
@Data
public class FeatureRangeBo {

    private int sumMin;
    private int sumMax;

    private int spanMin;
    private int spanMax;

    private int oddMin;
    private int oddMax;

    private int bigMin;
    private int bigMax;

    private int zone1Min;
    private int zone1Max;

    private int zone2Min;
    private int zone2Max;

    private int zone3Min;
    private int zone3Max;

    private int consecutiveMax;
    private int sameTailMax;

    /** 区间来源说明，如 P10-P90 */
    private String source;
}
