package com.my.project.service.history.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 形态遗漏 / 超额指数分析结果。
 *
 * @author 刘强
 * @version 2026/08/14
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternTrendVo {

    /** oddEven / bigSmall / ... / blueOddEven / blueBigSmall / blueBigSmallOddEven / blueRatio012 */
    private String feature;

    /** 奇偶比 / 012路比 / 跨度 等 */
    private String featureLabel;

    /** 当前分析的比例，如 1:5 */
    private String ratio;

    /** 期号列表（最旧 → 最新） */
    private List<String> periods;

    /** 每期是否命中该比例 */
    private List<Boolean> hits;

    /** 遗漏值序列 */
    private List<Integer> omissions;

    /** 累计超额指数序列（命中 +(1-p)，未命中 −p） */
    private List<Double> indexValues;

    /** 最近一期开奖（用于图头展示） */
    private String latestPeriod;

    /** 如 05 08 15 20 21 24 + 09 */
    private String latestWinning;

    /** 最近一期实际形态比例 */
    private String latestRatio;

    /** 每期实际形态（与 periods 对齐，最旧 → 最新） */
    private List<String> actuals;

    private Stats stats;

    /** 该形态全部比例的汇总，供下拉切换 */
    private List<RatioOption> ratioOptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Integer maxOmission;
        private Double avgOmission;
        private Integer currentOmission;
        private Integer hitCount;
        private Integer totalPeriods;
        private Double theoreticalProb;
        private Double theoreticalHits;
        /** 实际出现次数 − 理论出现次数 */
        private Double index;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatioOption {
        private String ratio;
        private Integer hitCount;
        private Double theoreticalProb;
        private Double theoreticalHits;
        private Double index;
        /** 当前遗漏（切到该比例时与页面 stats.currentOmission 一致） */
        private Integer currentOmission;
        /** 平均遗漏 */
        private Double avgOmission;
        /** 最大遗漏 */
        private Integer maxOmission;
        /** 该比例每期遗漏序列（与外层 periods 对齐，最旧 → 最新） */
        private List<Integer> omissions;
        /** 该比例累计超额指数序列（与 periods 对齐） */
        private List<Double> indexValues;
        /** 相邻两次命中的间隔（期），由近到远可看扩张/收缩 */
        private List<Integer> hitIntervals;
    }
}
