package com.my.project.application.predict;

import java.time.LocalDate;

/**
 * PredictService
 *
 * @author 刘强
 * @version 2025/10/28 19:58
 **/
public interface PredictService {

    /**
     *
     * @param openDate
     */
    void autoPredict(LocalDate openDate);
}
