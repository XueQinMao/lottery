package com.my.project.service.predict;

import java.time.LocalDate;

/**
 * IPredictService
 *
 * @author 刘强
 * @version 2025/10/28 19:58
 **/
public interface IPredictService {

    /**
     *
     * @param openDate
     */
    void autoPredict(LocalDate openDate);
}
