package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LotteryAdjustReqBo
 *
 * <p>大模型号码调优入参。包含：
 * <ol>
 *     <li>特征分析报告（{@link LotteryAnalysisRespBo} 的 JSON）</li>
 *     <li>若干组待调整的预测号码</li>
 * </ol>
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
     * <p>由 {@code ILotteryAnalysisService.analyze} 产出，直接透传给大模型作为调优依据。
     */
    private String analysisReportJson;

    /** 待调整的预测号码组 */
    private List<PredictTicket> tickets;

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
