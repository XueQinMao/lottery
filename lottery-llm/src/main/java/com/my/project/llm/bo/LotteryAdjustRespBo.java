package com.my.project.llm.bo;

import lombok.Data;

import java.util.List;

/**
 * LotteryAdjustRespBo
 *
 * <p>号码推荐结果，兼容已保存的旧版推荐包。
 *
 * @author 刘强
 * @version 2026/07/22 11:42
 **/
@Data
public class LotteryAdjustRespBo {

    /**
     * 各组预测号码的调整 / 推荐结果（仅单式），顺序与入参 tickets 一致（推荐模式为生成顺序）。
     */
    private List<AdjustedTicket> adjustedTickets;

    /**
     * 复式推荐及历史记录中的额外单式。
     */
    private FinalRecommendation finalRecommendation;

    /** 综合调优 / 选号说明 */
    private String conclusion;

    /**
     * 复式及旧版单式归档结构。
     */
    @Data
    public static class FinalRecommendation {
        /**
         * 旧版推荐记录中的额外单式。
         */
        private List<SingleTicket> singleTickets;
        /**
         * 最终推荐购买的复式玩法（唯一）。
         * <p>红球 7-10、蓝球 2-5，综合全部候选与特征报告生成。
         */
        private ComplexTicket complexTicket;
    }

    /**
     * 单组预测号码的调整 / 推荐结果（仅单式，不含复式）。
     */
    @Data
    public static class AdjustedTicket {
        /** 对应入参的组标识 */
        private String id;
        private TicketCategory category;
        /** 原始红球（升序）；推荐模式可为 null */
        private List<Integer> originalRedBalls;
        /** 原始蓝球；推荐模式可为 null */
        private Integer originalBlueBall;
        /** 调整后 / 推荐的红球（升序，6 个） */
        private List<Integer> adjustedRedBalls;
        /** 调整后 / 推荐的蓝球 */
        private Integer adjustedBlueBall;
        /** 本组调整 / 推荐理由 */
        private String reason;
    }

    public enum TicketCategory { OPTIMIZED, RECOMMENDED, FULL_MATCH }

    /**
     * 复式：红球 7-10 个，蓝球 2-5 个。
     */
    @Data
    public static class ComplexTicket {
        /** 复式名称/说明 */
        private String name;
        /** 复式红球（升序，7-10 个） */
        private List<Integer> redBalls;
        /** 复式蓝球（升序，2-5 个） */
        private List<Integer> blueBalls;
        /** 注数 = C(红球个数, 6) × 蓝球个数 */
        private Integer totalBets;
        /** 选号依据 */
        private String basis;
    }

    /**
     * 单式：红球 6 个 + 蓝球 1 个，共 1 注。
     */
    @Data
    public static class SingleTicket {
        /** 单式名称/说明（如"热温延续单式"、"温冷回冷单式"） */
        private String name;
        /** 红球（升序，恰好 6 个，1-33 互异） */
        private List<Integer> redBalls;
        /** 蓝球（恰好 1 个，1-16） */
        private Integer blueBall;
        /** 注数，恒为 1 */
        private Integer totalBets;
        /** 选号依据：说明本组针对的形态假设与冷热/分区/连号结构 */
        private String basis;
    }
}
