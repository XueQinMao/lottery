package com.my.project.service.llm.config;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * ColdHotConfig
 *
 * <p>冷热温号码分析配置。对应配置项前缀 {@code lottery.llm.cold-hot}。
 *
 * <p>两阶段分类：
 * <ol>
 *     <li>频次初档：出现次数 ≥ hotRatio × 期望 → 热候选；≤ coldRatio × 期望 → 冷候选；其余 → 温候选</li>
 *     <li>遗漏纠偏：刚开过的冷候选升为温；深遗漏的温候选降为冷；冷却中的热候选降为温</li>
 * </ol>
 *
 * <p>红球期望 = sampleSize × 6/33；蓝球期望 = sampleSize × 1/16。
 * <p>30 期样本下：红球期望 ≈ 5.45，蓝球期望 ≈ 1.875。
 *
 * @author 刘强
 * @version 2026/08/10 11:35
 **/
@Component
@Data
public class ColdHotConfig {

    /** 红球冷热阈值 */
    private RedThreshold red = new RedThreshold();

    /** 蓝球冷热阈值 */
    private BlueThreshold blue = new BlueThreshold();

    /**
     * 红球冷热阈值（按比例自适应样本量 + 遗漏纠偏）
     */
    @Data
    public static class RedThreshold {
        /**
         * 热号比例：出现次数 ≥ hotRatio × 期望次数 → 热候选。
         * <p>30 期下期望 ≈ 5.45，默认 1.3 → ≥ 7.09 即 ≥8 次为热候选。
         */
        private double hotRatio = 1.3;
        /**
         * 冷号比例：出现次数 ≤ coldRatio × 期望次数 → 冷候选。
         * <p>30 期下期望 ≈ 5.45，默认 0.4 → ≤ 2.18 即 ≤2 次为冷候选。
         */
        private double coldRatio = 0.4;
        /**
         * 刚回补遗漏上限：冷候选且 miss ≤ recentMiss → 升为温号。
         * <p>默认 1（上期或近 2 期内开出，不算当前冷）。
         */
        private int recentMiss = 1;
        /**
         * 深冷遗漏下限：温候选且 miss ≥ deepMiss → 降为冷号。
         * <p>默认 10（约 1/3 样本未开）。
         */
        private int deepMiss = 10;
        /**
         * 热号冷却遗漏下限：热候选且 miss ≥ coolDownMiss → 降为温号。
         * <p>默认 6（约超过红球理论间隔 33/6≈5.5）。
         */
        private int coolDownMiss = 6;
    }

    /**
     * 蓝球冷热阈值（按比例自适应样本量 + 遗漏纠偏）
     */
    @Data
    public static class BlueThreshold {
        /**
         * 热号比例：出现次数 ≥ hotRatio × 期望次数 → 热候选。
         * <p>30 期下期望 ≈ 1.875，默认 2.0 → ≥ 3.75 即 ≥4 次为热候选。
         */
        private double hotRatio = 2.0;
        /**
         * 冷号比例：出现次数 ≤ coldRatio × 期望次数 → 冷候选。
         * <p>30 期下期望 ≈ 1.875，默认 0.5 → ≤ 0.94 即 0 次为冷候选。
         */
        private double coldRatio = 0.5;
        /**
         * 刚回补遗漏上限：冷候选且 miss ≤ recentMiss → 升为温号。
         * <p>默认 2（蓝球开出更稀疏，窗口略宽）。
         */
        private int recentMiss = 2;
        /**
         * 深冷遗漏下限：温候选且 miss ≥ deepMiss → 降为冷号。
         * <p>默认 16（约半样本 / 蓝球理论间隔）。
         */
        private int deepMiss = 16;
        /**
         * 热号冷却遗漏下限：热候选且 miss ≥ coolDownMiss → 降为温号。
         * <p>默认 9。
         */
        private int coolDownMiss = 9;
    }
}
