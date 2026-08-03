package com.my.project.service.selection;

import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;

import java.time.LocalDate;
import java.util.Map;

/**
 * 智能选号服务
 *
 * @author 刘强
 * @version 2025/11/07 16:31
 **/
public interface ISmartSelectService {

    /**
     * 智能选号并扩展为复式方案
     *
     * @param openDate 开奖日期
     * @param budget   预算注数档位
     * @return 复式方案列表
     */
    void recommendFushi(LocalDate openDate, int budget);

    /**
     * 重新计算并缓存权重配置
     */
    void refreshWeightConfig();

    /**
     * 获取默认权重配置
     */
    WeightConfigBo getWeightConfig();

    /**
     * 分层抽样
     * @param config
     * @param quantity
     * @return
     */
    Map<String, ModelPredictOutputBo> percentileSample(int quantity);
}
