package com.my.project.service.llm;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.service.llm.pojo.dto.LLmAdjustDto;

/**
 * ILotteryFeatureAnalysisService
 *
 * <p>应用层封装：拉取最近 N 期一等奖号码，调用大模型进行多维度特征分析。
 *
 * @author 刘强
 * @version 2026/07/21 20:35
 **/
public interface ILotteryFeatureAnalysisService {

    /**
     * 拉取最近 {@code sampleSize} 期一等奖号码并调用大模型分析。
     *
     * @param sampleSize 样本数（建议 100）
     * @return 结构化分析结果
     */
    LotteryAnalysisRespBo analyzeLatest(int sampleSize);

    /**
     * 调用大模型对预测号码组进行调优
     * @param dto
     * @return
     */
    LotteryAdjustRespBo adjust(LLmAdjustDto dto);

    /**
     * 从缓存中选取中奖率top count的组去预测号码
     * @param count
     * @return
     */
    LotteryAdjustRespBo adjust(Integer count, boolean isTopN);
}
