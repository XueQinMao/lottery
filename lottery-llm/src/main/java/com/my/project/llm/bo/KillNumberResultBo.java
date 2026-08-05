package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * KillNumberResultBo
 *
 * <p>杀号计算结果。由 {@code IKillNumberService} 基于历史样本统计得出，
 * 随 {@link LotteryAnalysisRespBo#getKillNumbers()} 一并序列化进
 * {@code analysisReportJson}，透传给调优阶段供 LLM 参考。
 *
 * <p>红球分硬杀 {@link #hardKillRed}（LLM 须遵守）与软杀 {@link #softKillRed}（仅参考）。
 * 蓝球分硬杀 {@link #hardKillBlue}（LLM 须遵守）与软杀 {@link #softKillBlue}（仅参考）。
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
     * 硬杀红球：按来源条数随机抽一部分进软杀，其余硬杀（均线/遗漏/指数规则不同）
     */
    private List<KillItemBo> hardKillRed;

    /**
     * 软杀红球：各来源随机抽出的号码，LLM 仅参考
     */
    private List<KillItemBo> softKillRed;

    /**
     * 硬杀蓝球：均线全杀；遗漏与指数仅当两者条数都 ≤2 时一并硬杀
     */
    private List<KillItemBo> hardKillBlue;

    /**
     * 软杀蓝球：遗漏或指数任一条数 &gt;2 时，两者都进软杀
     */
    private List<KillItemBo> softKillBlue;

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

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            KillItemBo that = (KillItemBo) o;
            return Objects.equals(ball, that.ball);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(ball);
        }
    }
}
