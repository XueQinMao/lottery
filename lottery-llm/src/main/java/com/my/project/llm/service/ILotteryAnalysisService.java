package com.my.project.llm.service;

import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;

import java.util.List;

/**
 * ILotteryAnalysisService
 *
 * <p>大模型号码特征分析服务。传入最近若干组一等奖号码，输出多维度统计分析结果。
 *
 * @author 刘强
 * @version 2026/07/21 20:28
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
