package com.my.project.llm.bo;

import lombok.Data;

import java.util.List;

/**
 * LotteryAdjustRespBo
 *
 * <p>大模型号码调优 / 推荐结果：
 * <ul>
 *     <li>各组：仅单式调整结果 {@link #adjustedTickets}（不含组内复式）</li>
 *     <li>全局：综合后的最终推荐包 {@link #finalRecommendation}
 *         （3 胆码 + 2 组单式 + 1 组复式）</li>
 * </ul>
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
     * 基于整体调优 / 推荐结果凝练的最终可购买方案（唯一）。
     */
    private FinalRecommendation finalRecommendation;

    /** 综合调优 / 选号说明 */
    private String conclusion;

    /**
     * 最终推荐包：3 胆码 + 2 组单式 + 1 组复式。
     */
    @Data
    public static class FinalRecommendation {
        /**
         * 三个胆码（红球，升序，恰好 3 个，1-33 互异）。
         * <p>须被两组单式与复式红球同时包含。
         */
        private List<Integer> danBalls;
        /** 胆码选号依据 */
        private String danBasis;
        /**
         * 最终推荐购买的单式玩法（恰好 2 组）。
         * <p>每组红球 6 个 + 蓝球 1 个，共 1 注；两组分别针对不同形态假设。
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
        /** 原始红球（升序）；推荐模式可为 null */
        private List<Integer> originalRedBalls;
        /** 原始蓝球；推荐模式可为 null */
        private Integer originalBlueBall;
        /** 被替换的红球及替换说明；推荐模式为空数组 */
        private List<RedReplacement> redReplacements;
        /** 被替换的蓝球及替换说明；推荐模式可为 null */
        private BlueReplacement blueReplacement;
        /** 调整后 / 推荐的红球（升序，6 个） */
        private List<Integer> adjustedRedBalls;
        /** 调整后 / 推荐的蓝球 */
        private Integer adjustedBlueBall;
        /** 本组调整 / 推荐理由 */
        private String reason;
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
