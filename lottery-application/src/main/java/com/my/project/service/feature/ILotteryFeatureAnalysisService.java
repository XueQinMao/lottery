package com.my.project.service.feature;

import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.service.feature.pojo.dto.LLmAdjustDto;
import com.my.project.service.feature.pojo.vo.AdjustHistoryFileVo;
import com.my.project.service.record.pojo.vo.PredictFileRecordVo;

import java.util.List;

/**
 * ILotteryFeatureAnalysisService
 *
 * <p>应用层封装：拉取最近 N 期一等奖号码，Java 统计直方图并并发推算下一期形态。
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
    LotteryAdjustViewBo adjust(LLmAdjustDto dto);

    /**
     * 从缓存中选取中奖率top count的组去预测号码
     * @param count 组数
     * @param isTopN true=评分最高，false=随机抽取
     * @param userRequirement 用户附加要求提示词（可选）
     * @return 调优结果
     */
    LotteryAdjustViewBo adjust(Integer count, boolean isTopN, String userRequirement);

    /**
     * 列出最近的推荐结果文件名（按修改时间倒序）。
     */
    List<AdjustHistoryFileVo> listAdjustHistory(int limit);

    /**
     * 按文件名读取推荐结果详情。
     */
    LotteryAdjustViewBo loadAdjustHistory(String fileName);

    /**
     * 按类型查询预测/特征结果文件历史记录（按创建时间倒序）。
     *
     * @param type 类型 code：RECOMMEND=号码推荐，ANALYSIS=特征预测；为空查全部
     * @param limit 最多返回条数
     * @return 文件记录列表
     */
    List<PredictFileRecordVo> listFileHistory(String type, int limit);

    /**
     * 按文件名读取特征预测结果详情。
     */
    LotteryAnalysisRespBo loadAnalysisHistory(String fileName);
}
