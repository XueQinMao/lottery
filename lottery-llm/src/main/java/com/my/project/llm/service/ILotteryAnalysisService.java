package com.my.project.llm.service;

import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;

/**
 * ILotteryAnalysisService
 *
 * <p>单形态大模型推算：入参为 application 已压缩的候选表 JSON，本模块只负责 ChatClient 调用。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
public interface ILotteryAnalysisService {

    /**
     * 已废弃：特征报告直方图已移除，请勿调用。
     */
    LotteryAnalysisRespBo analyze(LotteryAnalysisReqBo reqBo);

    /**
     * 调用大模型从压缩候选表中选出下一期形态值或区间。
     *
     * @param featureLabel 形态中文名，如「奇偶比」「蓝球大小奇偶」
     * @param valueHint    取值格式说明
     * @param snapshotJson application 层 {@code compactForLlm} 后的候选表
     */
    FeatureForecastItem forecastOne(String featureLabel, String valueHint, String snapshotJson);
}
