package com.my.project.service.predict.impl;

import com.my.project.persistence.entity.PredictLog;
import com.my.project.persistence.repository.IPredictLogRepository;
import com.my.project.service.predict.IPredictLogService;
import com.my.project.service.support.SsqCombinationUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * PredictLogServiceImpl
 *
 * @author 刘强
 * @version 2026/01/06 14:23
 **/
@Service
@Primary
public class PredictLogServiceImpl implements IPredictLogService {

    private final IPredictLogRepository predictLogRepository;

    public PredictLogServiceImpl(IPredictLogRepository predictLogRepository) {
        this.predictLogRepository = predictLogRepository;
    }

    @Override
    @Transactional
    public void addOrUpdate(LocalDate openDate, Long position) {
        synchronized (openDate.toString().intern()){
            if (Objects.isNull(position)) {
                return;
            }
            PredictLog predictLog = predictLogRepository.lambdaQuery().eq(PredictLog::getOpenDate, openDate).one();
            LocalDateTime now = LocalDateTime.now();
            if (Objects.isNull(predictLog)) {
                predictLog = new PredictLog();
                predictLog.setOpenDate(openDate);
                predictLog.setPosition(position);
                predictLog.setCreateTime(now);
                predictLog.setUpdateTime(now);
                predictLogRepository.save(predictLog);
                return;
            }
            // 只向前推进进度，避免并发乱序回写更小的 position
            if (predictLog.getPosition() == null || position > predictLog.getPosition()) {
                predictLogRepository.lambdaUpdate().set(PredictLog::getPosition, position)
                    .set(PredictLog::getUpdateTime, now).eq(PredictLog::getOpenDate, openDate)
                    .eq(PredictLog::getPosition, predictLog.getPosition()).update();
            }
        }
    }

    @Override
    public PredictLog getByOpenDate(LocalDate openDate) {
        return predictLogRepository.lambdaQuery().eq(PredictLog::getOpenDate, openDate).one();
    }

    @Override
    public boolean existsPredictLog(LocalDate openDate) {
        PredictLog predictLog = getByOpenDate(openDate);
        return predictLog != null
            && predictLog.getPosition() != null
            && predictLog.getPosition() >= SsqCombinationUtils.TOTAL_COMBINATIONS;
    }
}
