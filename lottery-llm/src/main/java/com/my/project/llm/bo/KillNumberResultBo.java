package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * KillNumberResultBo
 *
 * <p>杀号计算结果。由 {@code IKillNumberService} 基于历史样本统计得出，
 * 随 {@link LotteryAnalysisRespBo#getKillNumbers()} 一并序列化进
 * {@code analysisReportJson}，透传给调优阶段供 LLM 参考。
 *
 * <p>仅产出硬杀清单（{@link #hardKillRed} / {@link #hardKillBlue}），
 * LLM 须遵守，禁止出现在任何输出号码中。
 *
 * @author 刘强
 * @version 2026/08/05 19:22
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KillNumberResultBo {

    /**
     * 硬杀红球清单（综合得分 ≥ 硬杀阈值且排名靠前）
     */
    private List<KillItemBo> hardKillRed;

    /**
     * 硬杀蓝球清单
     */
    private List<KillItemBo> hardKillBlue;

    /**
     * 总体依据说明（用于透传给 LLM 作为上下文）
     */
    private String basis;

    /**
     * KillItemBo
     *
     * <p>单个号码的杀号明细。
     *
     * @author 刘强
     * @version 2026/08/05 19:22
     **/
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KillItemBo {
        /**
         * 号码（红球 1-33，蓝球 1-16）
         */
        private Integer ball;
        /**
         * 综合剔除置信度，区间 [0,1]
         */
        private Double score;
        /**
         * 来源：SCORE=加权硬杀，TREND=趋势杀，LAST=上期开出
         */
        private String source;
        /**
         * 文字依据（含各维度分、趋势相位、是否过阈值，便于回测对照）
         */
        private String reason;
    }
}
