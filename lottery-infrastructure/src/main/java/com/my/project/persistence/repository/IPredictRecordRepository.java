package com.my.project.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.my.project.persistence.entity.PredictRecord;

/**
 * IHistoryRecordRepository
 * @author 刘强 
 * @version 2025/10/23 16:14
**/
public interface IPredictRecordRepository extends IService<PredictRecord> {
    void optimizeTable();
}
