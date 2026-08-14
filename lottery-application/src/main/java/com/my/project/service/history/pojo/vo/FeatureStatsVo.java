package com.my.project.service.history.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 开奖形态统计（和值 / 差值 / 质合比 / 奇偶比），供前端折线图展示。
 *
 * @author 刘强
 * @version 2026/08/13
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureStatsVo {

    /** 期号列表（最旧 → 最新） */
    private List<String> periods;

    /** 红球和值序列 */
    private List<Integer> sumValues;

    /** 红球和值均值 */
    private Double sumAvg;

    /** 红球差值（跨度 = 最大号 − 最小号）序列 */
    private List<Integer> spanValues;

    /** 红球差值均值 */
    private Double spanAvg;

    /** 红球质数个数序列（质合比的数值轴） */
    private List<Integer> primeCounts;

    /** 红球质合比文案，如 "2:4" */
    private List<String> primeRatios;

    /** 红球质数个数均值 */
    private Double primeAvg;

    /** 红球奇数个数序列（奇偶比的数值轴） */
    private List<Integer> redOddCounts;

    /** 红球奇偶比文案，如 "3:3" */
    private List<String> redOddEvenRatios;

    /** 红球奇数个数均值 */
    private Double redOddAvg;

    /** 蓝球奇偶标记：1=奇，0=偶 */
    private List<Integer> blueOddFlags;

    /** 蓝球奇偶文案："奇" / "偶" */
    private List<String> blueOddEvenLabels;

    /** 蓝球为奇数的比例（均值） */
    private Double blueOddAvg;
}
