package com.my.project.service.selection.pojo.bo;

import lombok.Data;

/**
 * WeightConfigBo
 * 智能选号权重与抽样配置
 *
 * @author 刘强
 * @version 2025/12/22 19:33
 **/
@Data
public class WeightConfigBo {

    // ========== 打分权重（归一化后参与综合分） ==========
    /** 模型分数权重（与分层抽样共用同一分数语义） */
    private double modelScoreWeight = 0.45;
    /** 特征区间约束权重（替代 ARIMA 点匹配） */
    private double rangeConstraintWeight = 0.30;
    /** 蓝球热度权重 */
    private double blueHotWeight = 0.10;
    /** 蓝球间隔权重 */
    private double blueGapWeight = 0.05;
    /** 三区均衡加成权重 */
    private double zoneBalanceBonus = 0.05;
    /** 热号加成（每个热号） */
    private double hotNumberBonus = 0.05;

    // 兼容旧字段（WinningNumberAnalyzerUtils 仍会写入）
    private double numFeatureWeight = 0.20;
    private double catFeatureWeight = 0.15;
    private double rangeBonus = 0.20;
    private double moderateBonus = 0.10;

    // ========== 分位数分层抽样比例（高/中/低模型分） ==========
    private double highScoreRatio = 0.40;
    private double midScoreRatio = 0.40;
    private double lowScoreRatio = 0.20;

    // ARIMA 参数（兼容保留，主路径不再依赖）
    private int arimaP = 1;
    private int arimaD = 1;
    private int arimaQ = 1;

    /** 多样性阈值：红球 Jaccard 相似度超过此值视为过相似，跳过 */
    private double diversityThreshold = 0.5;

    /** 特征区间下分位（如 0.10 = P10） */
    private double rangeLowerQuantile = 0.10;
    /** 特征区间上分位（如 0.90 = P90） */
    private double rangeUpperQuantile = 0.90;

    // ========== 概率主体区间（来自命中分析的 P5/P95，供入库过滤） ==========
    /** 历史命中分数 P5（下界） */
    private Double probabilityMin;
    /** 历史命中分数 P95（上界） */
    private Double probabilityMax;
    /** 主体区间宽度 = P95 - P5 */
    private Double probabilityRange;
    private Double probabilityConcentration;
    private String probabilityBucketDistribution;
    private String analysisReport;
}
