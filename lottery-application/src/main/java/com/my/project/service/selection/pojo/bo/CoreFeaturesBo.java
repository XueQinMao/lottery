package com.my.project.service.selection.pojo.bo;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * CoreFeaturesBo
 *
 * @author 刘强
 * @version 2026/01/06 14:50
 **/
@Data
public class CoreFeaturesBo {
    public int bigCount;           // 大号个数
    public int oddCount;           // 奇数个数
    public int sum;                // 总和
    public int[] zoneDistribution; // 三区分布 [zone1, zone2, zone3]
    public String zonePattern;     // 三区分布模式
    public Set<Integer> zones;     // 所在区域集合
    public Set<Integer> tails;     // 尾号集合
    public double avgHotness;      // 平均热度
    public List<Integer> gaps;     // 号码间隔
    public boolean coreBlueIsOdd;  // 核心蓝球是否奇数
    public boolean coreBlueIsBig;  // 核心蓝球是否大号
    public int coreBlueHotness;    // 核心蓝球热度
}
