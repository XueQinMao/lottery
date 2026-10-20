package com.my.project.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;

import java.util.Optional;

/**
 * HistoryRecordRepository
 * @author 刘强 
 * @version 2025/10/23 16:14
**/
public interface HistoryRecordRepository extends IService<HistoryRecord> {
    Optional<String> findNextPeriod(String sourcePeriod);
}
