package com.my.project.llm.service;

import com.my.project.llm.bo.LotteryAdjustReqBo;
import com.my.project.llm.bo.LotteryAdjustRespBo;

/**
 * ILotteryAdjustService
 *
 * <p>大模型号码调优 / 推荐服务。
 * <ul>
 *     <li>调优：tickets 非空 — 每组输出单式调整 + 最终推荐包（3 胆 + 2 单式 + 1 复式）</li>
 *     <li>推荐：tickets 为空 — 按特征报告生成 recommendCount 组单式，输出 Schema 与调优一致</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/17
 **/
public interface ILotteryAdjustService {

    /**
     * 基于特征报告对预测号码组进行调优，或在无候选号码时按报告推荐号码组。
     *
     * @param reqBo 入参（含特征报告 JSON；tickets 非空为调优，为空则按 recommendCount 推荐）
     * @return 结果：各组单式 adjustedTickets + 全局 finalRecommendation
     */
    LotteryAdjustRespBo adjust(LotteryAdjustReqBo reqBo);
}
