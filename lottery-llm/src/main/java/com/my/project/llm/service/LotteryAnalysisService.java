package com.my.project.llm.service;

import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;

/**
 * LotteryAnalysisService
 *
 * <p>基于原始开奖逐项调用大模型，并严格校验形态事实与候选池。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
public interface LotteryAnalysisService {

    PatternFeatureAnalysisBo analyzePattern(PatternFeatureAnalysisReqBo request);

}
