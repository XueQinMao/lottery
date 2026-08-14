package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LotteryAdjustReqBo
 *
 * <p>大模型号码调优 / 推荐入参。包含：
 * <ol>
 *     <li>特征分析报告（{@link LotteryAnalysisRespBo} 的 JSON）</li>
 *     <li>若干组待调整的预测号码（可为空）</li>
 *     <li>推荐组数（仅当 tickets 为空时生效）</li>
 * </ol>
 *
 * <p>当 {@link #tickets} 非空时走「调优」；为空时走「推荐」：
 * 仅依据特征报告生成 {@link #recommendCount} 组号码，输出 Schema 与调优一致。
 *
 * @author 刘强
 * @version 2026/07/22 11:40
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotteryAdjustReqBo {

    /**
     * 特征分析报告 JSON 字符串。
     * <p>由 Java 直方图 + LLM 形态推算产出，直接透传给大模型作为调优 / 推荐依据。
     */
    private String analysisReportJson;

    /**
     * 待调整的预测号码组。
     * <p>非空 → 调优模式；空或 null → 推荐模式（按 {@link #recommendCount} 生成）。
     */
    private List<PredictTicket> tickets;

    /**
     * 推荐号码组数量（仅 tickets 为空时生效）。
     * <p>默认 3，上限由服务侧截断（如 ≤10）。
     */
    private Integer count;

    /**
     * 单注预测号码。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictTicket {
        /** 组标识，可选 */
        private String id;
        /** 6 个红球，升序，范围 1-33 */
        private List<Integer> redBalls;
        /** 蓝球，范围 1-16 */
        private Integer blueBall;
    }
}
