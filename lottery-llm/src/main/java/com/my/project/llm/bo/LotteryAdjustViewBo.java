package com.my.project.llm.bo;

import lombok.Data;

import java.util.List;

/**
 * LotteryAdjustViewBo
 *
 * <p>API 返回的推荐 / 调优视图：在大模型 {@link LotteryAdjustRespBo} 之上回填形态对照，
 * 不进入 ChatClient.entity Schema，避免污染 LLM 输出结构。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
@Data
public class LotteryAdjustViewBo {

    private List<LotteryAdjustRespBo.AdjustedTicket> adjustedTickets;
    private LotteryAdjustRespBo.FinalRecommendation finalRecommendation;
    private String conclusion;
    /** 本次推荐所依据的形态推算 */
    private FeatureForecastBo featureForecast;
    /** 与 {@link #adjustedTickets} 按下标对齐 */
    private List<FeatureHitSummary> adjustedTicketHits;
    /** 与 {@link LotteryAdjustRespBo.FinalRecommendation#getSingleTickets()} 按下标对齐 */
    private List<FeatureHitSummary> finalSingleHits;

    @Data
    public static class FeatureHitSummary {
        private List<FeatureHit> hits;
        private Integer mainHitCount;
        private Integer altHitCount;
        private Integer missCount;
    }

    @Data
    public static class FeatureHit {
        private String code;
        private String label;
        private String actual;
        private String mainValue;
        private List<String> alternatives;
        /** MAIN / ALT / MISS */
        private String hitType;
    }
}
