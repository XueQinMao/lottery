package com.my.project.llm.service;

import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;

/**
 * ILotteryAnalysisService
 *
 * <p>直方图由 Java 本地统计；LLM 按单个形态推算下一期值或区间。
 *
 * @author 刘强
 * @version 2026/08/14
 **/
public interface ILotteryAnalysisService {

    /**
     * 已不再用于直方图。直方图由 {@code LotteryFeatureStatsUtils} 计算。
     */
    LotteryAnalysisRespBo analyze(LotteryAnalysisReqBo reqBo);

    /**
     * 基于单个形态快照推算下一期值或区间。
     *
     * @param featureLabel 形态中文名，如「奇偶比」
     * @param valueHint    取值格式说明
     * @param snapshotJson 该形态的 Java 快照
     */
    FeatureForecastItem forecastOne(String featureLabel, String valueHint, String snapshotJson);
}
