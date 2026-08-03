package com.my.project.service.predict;

import com.my.project.persistence.entity.PredictLog;

import java.time.LocalDate;

/**
 * 预测进度日志服务
 *
 * @author 刘强
 * @version 2026/01/06 14:22
 **/
public interface IPredictLogService {

    /**
     * 新增或更新当前预测进度（已完成组数）
     */
    void addOrUpdate(LocalDate openDate, Long position);

    PredictLog getByOpenDate(LocalDate openDate);

    /**
     * 是否已完成全部组合预测
     */
    boolean existsPredictLog(LocalDate openDate);
}
