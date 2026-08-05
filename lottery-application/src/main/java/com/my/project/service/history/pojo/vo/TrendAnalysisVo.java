package com.my.project.service.history.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 号码遗漏趋势分析结果（供前端图表展示）。
 *
 * @author 刘强
 * @version 2026/08/12
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendAnalysisVo {

    /** 球类型：red / blue */
    private String ballType;

    /** 目标号码 */
    private Integer ball;

    /** 期号列表（最旧→最新） */
    private List<String> periods;

    /** 遗漏值序列 */
    private List<Integer> omissions;

    /** 反向指数序列 */
    private List<Double> indexValues;

    /** 指数 5 期均线 */
    private List<Double> ma5;

    /** 指数 10 期均线 */
    private List<Double> ma10;

    /** 指数 20 期均线 */
    private List<Double> ma20;

    /** 统计指标 */
    private Stats stats;

    /** 均线排列：1=多头, -1=空头, 0=交叉 */
    private Integer arrangement;

    /** MA5 近 3 期斜率 */
    private Double ma5Slope;

    /** rising / rebounding / falling / cooling / neutral */
    private String phase;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Integer maxOmission;
        private Double avgOmission;
        private Integer currentOmission;
        private Double indexMean;
        private Integer hitCount;
        private Integer totalPeriods;
    }
}
