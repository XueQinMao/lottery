package com.my.project.llm.bo;

import lombok.Data;

import java.util.List;

/**
 * LotteryAnalysisRespBo
 *
 * <p>特征分析报告（透传调优 / 推荐 Prompt）。仅保留选号约束实际用到的模块：
 * <ul>
     *     <li>{@code featureForecast}：下一期形态目标（engine=java 或 llm）</li>
 *     <li>{@code killNumbers}：硬杀清单</li>
 *     <li>{@code coldHotAnalysis}：冷热温分档</li>
 *     <li>{@code predictedThreeZoneRatio}：三区比预测</li>
 *     <li>{@code trendAnalysis}：趋势相位补号倾向</li>
 * </ul>
 * 历史直方图 / 样本概览 / 尾数 / 蓝球 anyN 等已移除（形态页与 feature-stats 另有接口）。
 *
 * @author 刘强
 * @version 2026/08/17
 **/
@Data
public class LotteryAnalysisRespBo {

    /**
     * 杀号计算结果（可选）。
     * <p>仅当杀号功能启用时由 {@code IKillNumberService} 产出；为 {@code null} 表示未启用。
     */
    private KillNumberResultBo killNumbers;

    /**
     * 冷热温号码分析结果（可选）。
     * <p>由 {@code IColdHotAnalysisService} 基于原始样本统计产出。
     */
    private ColdHotAnalysisBo coldHotAnalysis;

    /**
     * 下一期三区比预测结果（可选）。
     * <p>由 {@code IThreeZoneRatioPredictService} 基于「频率先验 + 马尔可夫转移」混合模型预测。
     */
    private ThreeZoneRatioPredictBo predictedThreeZoneRatio;

    /**
     * 趋势均线分析结果（可选）。
     * <p>由 {@code LotteryTrendUtils} 基于反向指数 MA + 斜率相位产出 rising/rebounding/falling/cooling。
     */
    private TrendAnalysisBo trendAnalysis;

    /**
     * 形态推算（调优/推荐使用）。
     * <p>由 {@code lottery.llm.analysis.engine} 决定：java=indexValues 差值趋势本地计算，llm=逐维大模型选值。
     */
    private FeatureForecastBo featureForecast;

    /**
     * 趋势均线分析结果。
     * <p>基于反向指数 MA5/MA10/MA20 的「堆叠 + MA5 斜率」相位：
     * <ul>
     *     <li>rising：多头且斜率未下行 → 趋热，补号优先</li>
     *     <li>rebounding：空头/交叉但斜率向上 → 回暖，优先于 rising，禁止因空头回避</li>
     *     <li>falling：空头且斜率未上行 → 真趋冷，可谨慎</li>
     *     <li>cooling：多头但斜率下行 → 转弱</li>
     * </ul>
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrendAnalysisBo {
        /** 多头且斜率未下行 */
        private List<Integer> risingRedBalls;
        /** 空头/交叉但斜率向上（回暖） */
        private List<Integer> reboundingRedBalls;
        /** 空头且斜率未上行（真趋冷） */
        private List<Integer> fallingRedBalls;
        /** 多头但斜率下行 */
        private List<Integer> coolingRedBalls;
        private List<Integer> risingBlueBalls;
        private List<Integer> reboundingBlueBalls;
        private List<Integer> fallingBlueBalls;
        private List<Integer> coolingBlueBalls;
    }
}
