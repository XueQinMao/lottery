package com.my.project.api.pojo.req;

import lombok.Data;

import java.time.LocalDate;

/**
 * PredictRecordReq
 *
 * @author 刘强
 * @version 2025/08/01 15:53
 **/
@Data
public class PredictRecordReq {

  private LocalDate startDate;

  private LocalDate endDate;

  private LocalDate openDate;

  private PageReq page;
}
