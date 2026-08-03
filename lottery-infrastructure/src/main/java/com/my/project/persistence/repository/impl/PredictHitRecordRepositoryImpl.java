package com.my.project.persistence.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.my.project.persistence.mapper.PredictHitRecordMapper;
import com.my.project.persistence.repository.IPredictHitRecordRepository;
import com.my.project.persistence.entity.PredictHitRecord;
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
public class PredictHitRecordRepositoryImpl extends ServiceImpl<PredictHitRecordMapper, PredictHitRecord> implements
    IPredictHitRecordRepository {
}
