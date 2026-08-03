package com.my.project.llm.bo;

import lombok.Data;

import java.util.List;

/**
 * LotteryAdjustRespBo
 *
 * <p>大模型号码调优结果：
 * <ul>
 *     <li>各组：单式调整 + 该组对应复式 {@link AdjustedTicket#complexTicket}</li>
 *     <li>全局：综合后的<strong>一组最终可购买复式</strong> {@link #finalComplexTicket}</li>
 *     <li>全局：综合后的<strong>两组最终可购买单式</strong> {@link #finalSingleTickets}</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/07/22 11:42
 **/
@Data
public class LotteryAdjustRespBo {

    /**
     * 各组预测号码的调整结果（含该组复式），顺序与入参 tickets 一致。
     */
    private List<AdjustedTicket> adjustedTickets;

    /**
     * 最终推荐购买的复式玩法（唯一）。
     * <p>红球 7-10、蓝球 2-5，综合全部候选与特征报告生成。
     */
    private ComplexTicket finalComplexTicket;

    /**
     * 最终推荐购买的单式玩法（恰好 2 组）。
     * <p>每组红球 6 个 + 蓝球 1 个，共 1 注；用于低成本对冲复式风险。
     * 两组分别针对不同形态假设（如热温延续 / 温冷回冷）做精准聚焦。
     */
    private List<SingleTicket> finalSingleTickets;

    /** 综合调优 / 选号说明 */
    private String conclusion;

    /**
     * 单组预测号码的调整结果，以及基于该组生成的复式。
     */
    @Data
    public static class AdjustedTicket {
        /** 对应入参的组标识 */
        private String id;
        /** 原始红球（升序） */
        private List<Integer> originalRedBalls;
        /** 原始蓝球 */
        private Integer originalBlueBall;
        /** 被替换的红球及替换说明 */
        private List<RedReplacement> redReplacements;
        /** 被替换的蓝球及替换说明 */
        private BlueReplacement blueReplacement;
        /** 调整后的红球（升序，6 个） */
        private List<Integer> adjustedRedBalls;
        /** 调整后的蓝球 */
        private Integer adjustedBlueBall;
        /** 本组调整理由 */
        private String reason;
        /**
         * 基于本组调整后单式 + 特征报告生成的复式（与本组一一对应）。
         */
        private ComplexTicket complexTicket;
    }

    @Data
    public static class RedReplacement {
        private Integer from;
        private Integer to;
        private String basis;
    }

    @Data
    public static class BlueReplacement {
        private Integer from;
        private Integer to;
        private String basis;
    }

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
     * <p>用于在最终复式之外，提供低成本、聚焦特定形态假设的可购买单组号码。
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
