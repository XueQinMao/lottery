package com.my.project.service.predict.pojo.vo;

import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测记录展示对象
 *
 * @author 刘强
 * @version 2025/10/31 16:51
 **/
@Data
public class PredictRecordVo {

    private Long id;

    private List<Pair<Integer, Boolean>> redContrasts;

    private List<Pair<Integer, Boolean>> blueContrasts;

    private List<Integer> winningRedNumbers;

    private List<Integer> winningBlueNumbers;

    private LocalDateTime recommendedDate;

    private LocalDate openDate;

    private String explanation;

    private BigDecimal hitRate;

    private Long hitCount;

    private BigDecimal totalScore;
}
