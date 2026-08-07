package com.my.project.llm.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ColdHotAnalysisBo
 *
 * <p>冷热温号码分析结果。由 {@code IColdHotAnalysisService} 基于历史样本统计得出，
 * 随 {@link LotteryAnalysisRespBo#getColdHotAnalysis()} 一并序列化进
 * {@code analysisReportJson}，透传给调优阶段供 LLM 直接使用。
 *
 * <p>分类规则（按样本量自适应，以红球期望出现次数为基准）：
 * <ul>
 *     <li>热号：出现次数 ≥ hotRatio × 期望次数</li>
 *     <li>冷号：出现次数 ≤ coldRatio × 期望次数</li>
 *     <li>温号：介于两者之间</li>
 * </ul>
 *
 * <p>红球期望 = sampleSize × 6/33；蓝球期望 = sampleSize × 1/16。
 *
 * @author 刘强
 * @version 2026/08/06 19:45
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColdHotAnalysisBo {

    /** 红球热号清单（出现次数 ≥ hotRatio × 期望），按号码升序 */
    private List<Integer> redHotBalls;

    /** 红球温号清单（介于冷热阈值之间），按号码升序 */
    private List<Integer> redWarmBalls;

    /** 红球冷号清单（出现次数 ≤ coldRatio × 期望），按号码升序 */
    private List<Integer> redColdBalls;

    /** 蓝球热号清单，按号码升序 */
    private List<Integer> blueHotBalls;

    /** 蓝球温号清单，按号码升序 */
    private List<Integer> blueWarmBalls;

    /** 蓝球冷号清单，按号码升序 */
    private List<Integer> blueColdBalls;

    /**
     * 总体依据说明（用于透传给 LLM 作为上下文），
     * 包含样本量、期望次数、冷热阈值等。
     */
    private String basis;
}
