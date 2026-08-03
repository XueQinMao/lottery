package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LotteryAnalysisReqBo
 *
 * <p>大模型号码特征分析入参。{@code records} 为最近 N 组一等奖号码样本，
 * 每条记录包含 6 个红球（升序）与 1 个蓝球。
 *
 * @author 刘强
 * @version 2026/07/21 20:22
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotteryAnalysisReqBo {

    /** 样本条数（建议 100） */
    private Integer sampleSize;

    /** 一等奖号码样本 */
    private List<DrawRecord> records;

    /**
     * 单注号码
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrawRecord {
        /** 期号，可选 */
        private String period;
        /** 6 个红球，升序，范围 1-33 */
        private List<Integer> redBalls;
        /** 蓝球，范围 1-16 */
        private Integer blueBall;
    }
}
