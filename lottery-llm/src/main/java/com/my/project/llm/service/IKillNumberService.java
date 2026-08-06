package com.my.project.llm.service;

import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;

import java.util.List;

/**
 * IKillNumberService
 *
 * <p>杀号计算服务。基于历史开奖样本，按多维度剔除置信度加权融合，
 * 产出硬杀 / 软杀两级清单，供调优阶段 LLM 过滤参考。
 *
 * @author 刘强
 * @version 2026/08/05 19:25
 **/
public interface IKillNumberService {

    /**
     * 计算杀号清单。
     *
     * @param records 历史样本（按期号升序，最近 N 期）
     * @return 杀号结果，含硬杀 / 软杀两级清单
     */
    KillNumberResultBo calculate(List<DrawRecord> records, DrawRecord defaultKillNumbers);
}
