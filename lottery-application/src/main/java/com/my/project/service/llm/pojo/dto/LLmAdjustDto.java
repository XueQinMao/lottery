package com.my.project.service.llm.pojo.dto;

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


    @Data
    @Builder
    public static class DrawRecord {

        private List<Integer> redballs;

        private Integer blueball;
    }
}
