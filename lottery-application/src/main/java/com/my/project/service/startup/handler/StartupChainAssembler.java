package com.my.project.service.startup.handler;

import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.persistence.entity.HistoryRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * StartupChainAssembler
 *
 * @author 刘强
 * @version 2026/02/05 19:42
 **/
@Component
public class StartupChainAssembler {

    @Resource
    private FetchHistoryRecordChain fetchHistoryRecordChain;

    @Resource
    private FeatureDataPreparationChain featureDataPreparationChain;

    @Resource
    private TrainModelChain trainModelChain;

    @Resource
    private InitPythonProcessChain initPythonProcessChain;

    @Resource
    private AutoPredictChain autoPredictChain;

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Resource
    private SmartSelectChain smartSelectChain;


    @Resource
    private CalculateWeightsChain calculateWeightsChain;

    public List<AbstractStartupChain> assembleChain(LocalDate localDate) {
        HistoryRecord lastRecord =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit 1").one();
        if(Objects.isNull(lastRecord)){
            return List.of(fetchHistoryRecordChain, featureDataPreparationChain, trainModelChain, initPythonProcessChain,
                calculateWeightsChain, autoPredictChain, smartSelectChain);
        }
        //取当天往前推的第一个开奖日期，如果比数据库里面最大的开奖日志都大说明需要拉取历史数据训练模型
        if (localDate.isBefore(lastRecord.getOpenDate()) || localDate.isEqual(
            lastRecord.getOpenDate()) || localDate.equals(LocalDate.now())) {
            return List.of(initPythonProcessChain, calculateWeightsChain, autoPredictChain, smartSelectChain);
        }
        return List.of(fetchHistoryRecordChain, featureDataPreparationChain, trainModelChain, initPythonProcessChain,
            calculateWeightsChain, autoPredictChain, smartSelectChain);
    }
}
