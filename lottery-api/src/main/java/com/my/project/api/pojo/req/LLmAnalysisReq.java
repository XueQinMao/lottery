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
     * 推荐号码组数量（仅 drawRecords 为空或不传时生效）。
     * <p>默认 2，上限 10。
     */
    private Integer count;

    @Data
    public static class DrawRecord {

        private List<Integer> redballs;

        private Integer blueball;
    }
}
