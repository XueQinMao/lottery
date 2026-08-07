package com.my.project.service.llm;

import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;
import com.my.project.llm.bo.ThreeZoneRatioPredictBo;

import java.util.List;

/**
 * IThreeZoneRatioPredictService
 *
 * <p>下一期三区比预测服务。基于历史开奖样本，采用「频率先验 + 马尔可夫转移」
 * 混合模型给出 Top-K 候选三区比及概率，供调优阶段 LLM 选号形态参考。
 *
 * @author 刘强
 * @version 2026/08/07 14:05
 **/
public interface IThreeZoneRatioPredictService {

    /**
     * 预测下一期三区比候选列表。
     *
     * @param records 历史样本（按期号升序，最近 N 期）
     * @return 预测结果，含 Top-K 候选三区比、最近一期三区比与依据说明
     */
    ThreeZoneRatioPredictBo predict(List<DrawRecord> records);
}
