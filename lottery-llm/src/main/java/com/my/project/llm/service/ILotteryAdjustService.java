package com.my.project.llm.service;

import com.my.project.llm.bo.LotteryAdjustReqBo;
import com.my.project.llm.bo.LotteryAdjustRespBo;

/**
 * ILotteryAdjustService
 *
 * <p>大模型号码调优服务。基于特征报告对若干组预测号码调优：
 * 每组输出单式调整 + 组内复式（{@code complexTicket}），
 * 并额外输出一组最终可购买复式（{@code finalComplexTicket}）。
 *
 * @author 刘强
 * @version 2026/07/22 11:38
 **/
public interface ILotteryAdjustService {

    /**
     * 基于特征报告对预测号码组进行调优。
     *
     * @param reqBo 调优入参（含特征报告 JSON 与待调整号码组）
     * @return 调优结果：各组 complexTicket + 全局 finalComplexTicket
     */
    LotteryAdjustRespBo adjust(LotteryAdjustReqBo reqBo);
}
