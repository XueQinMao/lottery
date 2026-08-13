package com.my.project.llm.service;

import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;

import java.util.List;

/**
 * ILotteryAnalysisService
 *
 * <p>号码特征分析服务。直方图可由 Java 本地统计或 LLM 生成（见 {@code lottery.llm.analysis.engine}）。
 *
 * @author 刘强
 * @version 2026/08/13
 **/
public interface ILotteryAnalysisService {

    /**
     * 基于最近的中奖号码样本进行多维度特征分析。
     *
     * @param reqBo 分析请求（包含样本号码）
     * @return 结构化分析结果
     */
    LotteryAnalysisRespBo analyze(LotteryAnalysisReqBo reqBo);
}
