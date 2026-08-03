package com.my.project.service.selection.pojo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BuyRecordDto
 *
 * @author 刘强
 * @version 2026/07/22 20:19
 **/
@Data
@Builder
public class BuyRecordDto {

    private Long id;

    /**
     * manual 和 auto 两种
     */
    private String type;

    /**
     * 开奖日期
     */
    private LocalDate openDate;

    private String oriRedBalls;

    private String oriBlueBall;

    private String adjustedRedBalls;

    private String adjustedBlueBall;

    private String redBalls;

    private String blueBalls;

    private String reason;

    private String coreRedBalls;

    private Integer totalBets;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
