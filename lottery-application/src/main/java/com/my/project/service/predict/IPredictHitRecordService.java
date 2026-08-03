package com.my.project.service.predict;

import com.my.project.service.enums.PrizeLevelEnum;
import com.my.project.service.predict.pojo.vo.PredictHitRecordVo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 预测命中记录服务
 *
 * @author 刘强
 * @version 2025/10/31 14:16
 **/
public interface IPredictHitRecordService {

    void archiveHits(LocalDate openDate);

    Map<PrizeLevelEnum, List<PredictHitRecordVo>> getHitsByPrizeLevel(LocalDate openDate);

}
