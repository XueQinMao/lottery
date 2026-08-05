package com.my.project.service.feature.pojo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * LLmAdjustDto
 *
 * @author 刘强
 * @version 2026/07/24 17:36
 **/
@Data
@Builder
public class LLmAdjustDto {

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
     * 推荐号码组数量（仅 drawRecords 为空时生效，透传给 LotteryAdjustReqBo.count）。
     */
    private Integer count;

    @Data
    @Builder
    public static class DrawRecord {

        private List<Integer> redballs;

        private Integer blueball;
    }
}
