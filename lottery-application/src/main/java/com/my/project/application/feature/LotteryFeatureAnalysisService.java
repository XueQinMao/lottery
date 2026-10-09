package com.my.project.application.feature;

import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.application.feature.dto.LlmAdjustmentCommand;
import com.my.project.application.feature.dto.RecommendationCommand;


/**
 * LotteryFeatureAnalysisService
 *
 * <p>编排形态分析与号码推荐；大模型特征分析串行执行，报告文件由独立服务管理。
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

    /**
     * 调用大模型对预测号码组进行调优
     * @param command 待调优号码与推荐要求
     * @return 推荐结果
     */
    default LotteryAdjustViewBo adjust(LlmAdjustmentCommand command) {
        return recommend(new RecommendationCommand.Feature(command));
    }

    LotteryAdjustViewBo recommend(RecommendationCommand command);

    /**
     * 从缓存中选取中奖率top count的组去预测号码
     * @param count 组数
     * @param isTopN true=评分最高，false=随机抽取
     * @param userRequirement 用户附加要求提示词（可选）
     * @return 调优结果
     */
    default LotteryAdjustViewBo adjust(Integer count, boolean isTopN, String userRequirement) {
        return recommend(new RecommendationCommand.Cache(count, isTopN, userRequirement));
    }

}
