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

    @Data
    public static class DrawRecord {

        private List<Integer> redballs;

        private Integer blueball;
    }
}
