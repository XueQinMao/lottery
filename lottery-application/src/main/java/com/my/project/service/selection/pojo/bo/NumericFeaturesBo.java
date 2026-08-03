package com.my.project.service.selection.pojo.bo;

import lombok.Data;

/**
 * NumericFeaturesBo
 *
 * @author 刘强
 * @version 2025/12/22 19:34
 **/
@Data
public class NumericFeaturesBo {

    private int bigCount;           // 大号个数 (>16)
    private int oddCount;           // 奇数个数
    private int sum;                // 红球总和
    private int zone1;              // 区域1 (1-11)
    private int zone2;              // 区域2 (12-22)
    private int zone3;              // 区域3 (23-33)
    private int sameTailCount;      // 尾号相同个数
    private int consecutiveCount;   // 连号个数
    private int repeatFromLast;     // 与上期相同红球数量
}
