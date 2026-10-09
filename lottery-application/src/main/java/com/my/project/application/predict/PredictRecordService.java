package com.my.project.application.predict;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.application.predict.vo.PredictRecordVo;

import java.time.LocalDate;
import java.util.Map;

/**
 * 预测记录服务
 *
 * @author 刘强
 * @version 2025/10/31 14:16
 **/
public interface PredictRecordService {

    void deleteByOpenDate(LocalDate openDate);

    IPage<PredictRecordVo> findPage(IPage<PredictRecordVo> page, LocalDate openDate);

    void saveBatch(Map<String, ModelPredictOutputBo> predictOutputMaps, LocalDate openDate);
}
