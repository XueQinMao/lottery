package com.my.project.service.predict.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预测命中记录展示对象
 *
 * @author 刘强
 * @version 2025/07/29
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictHitRecordVo {

    private Long id;

    private LocalDate openDate;

    private String redBalls;

    private Integer blueBall;

    private BigDecimal totalScore;

    private Integer level;

    private String explanation;

    private String source;

    private LocalDateTime createTime;
}
