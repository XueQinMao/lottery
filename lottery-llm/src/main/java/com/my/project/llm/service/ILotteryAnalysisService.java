package com.my.project.llm.service;

import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;

/**
 * ILotteryAnalysisService
 *
 * <p>红球 11 维 + 蓝球 4 维：各开一条线程，基于 Java 间隔节奏快照调用 LLM 推算下一期值/区间。
 *
 * @author 刘强
 * @version 2026/08/17
 **/
public interface ILotteryAnalysisService {

    /**
     * 已废弃：特征报告直方图已移除，请勿调用。
     */
    LotteryAnalysisRespBo analyze(LotteryAnalysisReqBo reqBo);

    /**
     * 基于单个形态的间隔节奏快照推算下一期值或区间。
     *
     * @param featureLabel 形态中文名，如「奇偶比」「蓝球大小奇偶」
     * @param valueHint    取值格式说明
     * @param snapshotJson {@code FeatureIntervalForecastUtils.buildSnapshot} 产出的 JSON
     */
    FeatureForecastItem forecastOne(String featureLabel, String valueHint, String snapshotJson);
}
