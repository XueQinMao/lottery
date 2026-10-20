package com.my.project.application.feature;

import com.my.project.llm.bo.LotteryAnalysisRespBo;


/**
 * LotteryFeatureAnalysisService
 *
 * <p>编排形态分析，报告文件由独立服务管理。
 *
 * @author 刘强
 * @version 2026/07/21 20:35
 **/
public interface LotteryFeatureAnalysisService {

    /**
     * 拉取最近 {@code sampleSize} 期一等奖号码并调用大模型分析。
     *
     * @param sampleSize 样本数（建议 100）
     * @return 结构化分析结果
     */
    LotteryAnalysisRespBo analyzeLatest(int sampleSize);

}
