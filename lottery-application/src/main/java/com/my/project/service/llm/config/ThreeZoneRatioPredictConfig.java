package com.my.project.service.llm.config;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * ThreeZoneRatioPredictConfig
 *
 * <p>三区比预测配置。对应配置项前缀 {@code lottery.llm.three-zone-predict}。
 *
 * <p>混合模型：finalProb = freqWeight × 频率先验 + markovWeight × 马尔可夫转移概率，
 * 权重无需归一化（计算时会自动归一）。当无马尔可夫转移数据（上期三区比未在样本中出现过）
 * 时，自动回退为纯频率先验。
 *
 * @author 刘强
 * @version 2026/08/07 14:05
 **/
@Component
@Data
public class ThreeZoneRatioPredictConfig {

    /** 频率先验权重（基础概率） */
    private double freqWeight = 0.4;

    /** 马尔可夫转移权重（上期→下期） */
    private double markovWeight = 0.6;

    /** 输出候选 Top-K 数量 */
    private int topK = 4;
}
