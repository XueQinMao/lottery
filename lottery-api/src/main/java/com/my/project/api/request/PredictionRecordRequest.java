package com.my.project.api.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * PredictionRecordRequest
 *
 * @author 刘强
 * @version 2025/08/01 15:53
 **/
@Data
public class PredictionRecordRequest {

  private LocalDate startDate;

  private LocalDate endDate;

  private LocalDate openDate;

  private PageRequest page;
}
