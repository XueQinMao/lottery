package com.my.project.persistence.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.my.project.persistence.mapper.HistoryRecordMapper;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.persistence.entity.HistoryRecord;
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
@Primary
@Transactional
public class HistoryRecordRepositoryImpl extends ServiceImpl<HistoryRecordMapper, HistoryRecord> implements
    IHistoryRecordRepository {
}
