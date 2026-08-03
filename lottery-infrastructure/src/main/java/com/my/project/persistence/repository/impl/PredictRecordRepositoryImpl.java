package com.my.project.persistence.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.my.project.persistence.mapper.PredictRecordMapper;
import com.my.project.persistence.repository.IPredictRecordRepository;
import com.my.project.persistence.entity.PredictRecord;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HistoryRecordRepositoryImpl
 *
 * @author 刘强
 * @version 2025/10/23 16:15
 **/
@Service
@Transactional
@Primary
public class PredictRecordRepositoryImpl extends ServiceImpl<PredictRecordMapper, PredictRecord> implements
    IPredictRecordRepository {
    @Override
    public void optimizeTable() {
        this.getBaseMapper().optimizeTable();
    }
}
