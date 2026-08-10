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
 * <p>分类规则（频次初档 + 遗漏纠偏）：
 * <ul>
 *     <li>频次：≥ hotRatio×期望 → 热候选；≤ coldRatio×期望 → 冷候选；其余 → 温候选</li>
 *     <li>遗漏：冷且刚开过 → 温；温且深遗漏 → 冷；热且已冷却 → 温</li>
 * </ul>
 *
 * <p>红球期望 = sampleSize × 6/33；蓝球期望 = sampleSize × 1/16。
 *
 * @author 刘强
 * @version 2026/08/10 11:35
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColdHotAnalysisBo {

    /** 红球热号清单，按号码升序 */
    private List<Integer> redHotBalls;

    /** 红球温号清单，按号码升序 */
    private List<Integer> redWarmBalls;

    /** 红球冷号清单，按号码升序 */
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
