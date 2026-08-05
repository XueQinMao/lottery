package com.my.project.persistence.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.my.project.persistence.entity.PredictFileRecord;
import com.my.project.persistence.mapper.PredictFileRecordMapper;
import com.my.project.persistence.repository.IPredictFileRecordRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PredictFileRecordRepositoryImpl
 *
 * @author 刘强
 * @version 2026/09/11
 **/
@Service
@Primary
@Transactional
public class PredictFileRecordRepositoryImpl
        extends ServiceImpl<PredictFileRecordMapper, PredictFileRecord>
        implements IPredictFileRecordRepository {
}
