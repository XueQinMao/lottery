package com.my.project.llm.bo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * LotteryAnalysisRespBo
 *
 * <p>新报告仅输出featureAnalysis、killNumbers、coldHotAnalysis、recentDrawRecords。
 * 历史兼容字段仅用于读取旧文件，不向API或推荐Prompt序列化。
 * <p>以下为旧结构说明：
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
@Builder
public class LotteryAnalysisRespBo {

    private List<PatternFeatureAnalysisBo> featureAnalysis;
    /** 大模型分支固定最近30期，最旧到最新；Java及旧报告按实际记录读取。 */
    private List<LotteryAnalysisReqBo.DrawRecord> recentDrawRecords;

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
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ThreeZoneRatioPredictBo predictedThreeZoneRatio;

    /**
     * 趋势均线分析结果（可选）。
     * <p>由 {@code MaAnalysisUtils} 同一份 MA5/MA10/MA20 按堆叠 + MA5 斜率产出 rising/rebounding/falling/cooling。
     */
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private TrendAnalysisBo trendAnalysis;

    /**
     * 遗漏均线抬头红球池（可选）。
     * <p>由 {@code OmissionUtils.omissionBallAnalyzer} 逐红球计算 phase，合并
     * {@code rising}（多头且斜率未下行）与 {@code rebounding}（空头/交叉但斜率向上）两类得到。
     * 选号时优先从该池中挑选，不足或与其他真硬冲突时再试其他号码。null 则忽略。
     */
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Integer> risingTrendRedBalls;

    /**
     * 形态推算（调优/推荐使用）。
     * <p>由 {@code lottery.llm.analysis.engine} 决定：java=indexValues 差值趋势本地计算，llm=逐维大模型选值。
     */
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private FeatureForecastBo featureForecast;

    /**
     * 趋势均线分析结果。
     * <p>由 {@code MaAnalysisUtils} 同一份反向指数 MA5/MA10/MA20 按「堆叠 + MA5 斜率」分组：
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
