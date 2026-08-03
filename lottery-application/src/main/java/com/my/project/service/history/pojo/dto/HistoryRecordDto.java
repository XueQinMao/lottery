package com.my.project.service.history.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 历史开奖记录传输对象（与 Entity 解耦）
 *
 * @author 刘强
 * @version 2025/07/17 20:11
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryRecordDto {

    private Long id;

    private String type;

    private String period;

    private LocalDate openDate;

    private Integer num1;

    private Integer num2;

    private Integer num3;

    private Integer num4;

    private Integer num5;

    private Integer num6;

    private Integer special;

    private LocalDateTime createTime;
}
