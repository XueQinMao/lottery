package com.my.project.service.selection.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BuyRecordVo
 *
 * @author 刘强
 * @version 2026/08/06 15:55
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BuyRecordVo {

    private String oriRedBalls;

    private String oriBlueBall;

    private String adjustedRedBalls;

    private String adjustedBlueBall;

    private String coreRedBalls;

    private String redBalls;

    private String blueBalls;
}
