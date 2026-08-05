package com.my.project.service.selection.pojo.bo;

import lombok.Data;

/**
 * WeightConfigBo
 *
 * <p>智能选号运行时配置。当前主路径仅使用：
 * <ul>
 *     <li>高/中/低分层抽样比例 → {@code percentileSample}</li>
 *     <li>命中分数 P5/P95 → 预测结果入库过滤</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/10 17:02
 **/
@Data
public class WeightConfigBo {

    // ========== 分位数分层抽样比例（高/中/低模型分） ==========
    private double highScoreRatio = 0.40;
    private double midScoreRatio = 0.40;
    private double lowScoreRatio = 0.20;

    // ========== 概率主体区间（来自命中分析的 P5/P95，供入库过滤） ==========
    /** 历史命中分数 P5（下界） */
    private Double probabilityMin;
    /** 历史命中分数 P95（上界） */
    private Double probabilityMax;
}
