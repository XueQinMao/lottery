package com.my.project.service.llm;

import com.my.project.llm.bo.ColdHotAnalysisBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;

import java.util.List;

/**
 * IColdHotAnalysisService
 *
 * <p>冷热温号码分析服务。基于历史开奖样本，按出现频次将红球/蓝球分为
 * 热号 / 温号 / 冷号三类，供调优阶段 LLM 直接引用，避免 LLM 自行推断冷热。
 *
 * @author 刘强
 * @version 2026/08/06 19:45
 **/
public interface IColdHotAnalysisService {

    /**
     * 计算冷热温号码清单。
     *
     * @param records 历史样本（按期号升序，最近 N 期）
     * @return 冷热温分析结果，含红/蓝球三类清单与依据说明
     */
    ColdHotAnalysisBo calculate(List<DrawRecord> records);
}
