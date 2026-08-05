package com.my.project.api.pojo.req;

import lombok.Data;

import java.util.List;

/**
 * LLmAnalysisReq
 *
 * @author 刘强
 * @version 2026/07/24 17:11
 **/
@Data
public class LLmAnalysisReq {

    private List<DrawRecord> drawRecords;

    /**
     * 上一期开奖红球（升序，1-33，共 6 个）；为空时由服务侧自动拉取最近一期。
     */
    private List<Integer> lastDrawRedBalls;

    /**
     * 上一期开奖蓝球（1-16）；为空时由服务侧自动拉取最近一期。
     */
    private Integer lastDrawBlueBall;

    /**
     * 推荐号码组数量（仅 drawRecords 为空或不传时生效）。
     * <p>默认 2，上限 10。
     */
    private Integer count;

    /**
     * 用户附加要求提示词（可选）。
     * <p>拼入调优 / 推荐 Prompt；与和值/跨度安全网等真硬约束求交后执行，不得越界。
     */
    private String userRequirement;

    @Data
    public static class DrawRecord {

        private List<Integer> redballs;

        private Integer blueball;
    }
}
