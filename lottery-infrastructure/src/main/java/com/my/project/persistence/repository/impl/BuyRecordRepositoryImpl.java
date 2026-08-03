package com.my.project.persistence.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.my.project.persistence.mapper.BuyRecordMapper;
import com.my.project.persistence.repository.IBuyRecordRepository;
import com.my.project.persistence.entity.BuyRecord;
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
public class BuyRecordRepositoryImpl extends ServiceImpl<BuyRecordMapper, BuyRecord>
    implements IBuyRecordRepository {
}
