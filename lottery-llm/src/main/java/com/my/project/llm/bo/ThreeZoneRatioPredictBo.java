package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ThreeZoneRatioPredictBo
 *
 * <p>下一期三区比预测结果。由 {@code IThreeZoneRatioPredictService} 基于历史样本
 * 通过「频率先验 + 马尔可夫转移」混合模型计算得出，给出 Top-K 候选三区比及概率。
 *
 * <p>预测方法：
 * <ol>
 *     <li>频率先验：统计近 N 期各三区比出现频率，作为基础概率</li>
 *     <li>马尔可夫转移：以最近一期三区比为起点，统计「上期 A → 下期 B」的转移概率</li>
 *     <li>混合：finalProb = freqWeight × 频率先验 + markovWeight × 转移概率（归一化）</li>
 * </ol>
 *
 * <p>随 {@link LotteryAnalysisRespBo#getPredictedThreeZoneRatio()} 一并序列化进
 * {@code analysisReportJson}，透传给调优阶段供 LLM 选号形态参考。
 *
 * @author 刘强
 * @version 2026/08/07 14:05
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreeZoneRatioPredictBo {

    /** 候选三区比列表（按概率降序），形如 "2:2:2"、"3:1:2" */
    private List<Candidate> candidates;

    /** 最近一期实际三区比（预测起点） */
    private String lastRatio;

    /** 总体依据说明（用于透传给 LLM 作为上下文） */
    private String basis;

    /**
     * 单个候选三区比。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        /** 三区比，形如 "2:2:2" */
        private String ratio;
        /** 综合概率，区间 [0,1]，已归一化 */
        private double probability;
        /** 频率先验概率（归一化前），区间 [0,1] */
        private double frequencyProb;
        /** 马尔可夫转移概率（归一化前），区间 [0,1]；无转移数据时为 0 */
        private double markovProb;
        /** 候选理由（简短说明） */
        private String reason;
    }
}
