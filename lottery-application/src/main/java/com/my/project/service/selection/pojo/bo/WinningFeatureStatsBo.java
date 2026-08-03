package com.my.project.service.selection.pojo.bo;

import com.my.project.persistence.entity.PredictHitRecord;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WinningFeatureStatsBo
 *
 * @author 刘强
 * @version 2026/01/06 14:55
 **/
@Data
public class WinningFeatureStatsBo {
    // 基础统计
    private int totalCount;                                   // 中奖号码总数
    private List<PredictHitRecord> winningNumbers;               // 所有中奖号码

    // 特征分布统计（用于计算集中度）
    private Map<Integer, Integer> bigCountDist = new HashMap<>();      // big_count分布
    private Map<Integer, Integer> oddCountDist = new HashMap<>();      // odd_count分布
    private Map<Integer, Integer> sumRangeDist = new HashMap<>();      // sum范围分布
    private Map<Integer, Integer> sameTailDist = new HashMap<>();      // same_tail分布

    // 模型分数统计
    private double avgModelScore;                             // 平均模型分数
    private double minModelScore;                             // 最低模型分数
    private double maxModelScore;                             // 最高模型分数
    private double modelScoreStdDev;                          // 模型分数标准差

    // 概率主体区间（P5/P95，用于入库过滤；minModelScore/maxModelScore 仍保留极值）
    private double probabilityMin;                            // P5
    private double probabilityMax;                            // P95
    private double probabilityRange;                          // P95 - P5
    private double probabilityConcentration;                  // 概率集中度 (0-1，越高越集中)
    /** 是否已根据有效命中分数算出 P5/P95 */
    private boolean probabilityBoundsReady;
    private Map<String, Integer> probabilityBucketDist = new HashMap<>();  // 概率区间分布

    // 特征重要性评分（0-1之间）
    private double numFeatureImportance;                      // 数值特征重要性
    private double catFeatureImportance;                      // 离散特征重要性
    private double modelScoreImportance;                      // 模型分数重要性

    // 详细说明
    private String analysisReport;                            // 分析报告
}
