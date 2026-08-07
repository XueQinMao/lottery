package com.my.project.service.llm.config;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * ColdHotConfig
 *
 * <p>冷热温号码分析配置。对应配置项前缀 {@code lottery.llm.cold-hot}。
 *
 * <p>采用「比例阈值」自适应样本量：以单号期望出现次数为基准，
 * 出现次数 ≥ hotRatio × 期望 → 热号；≤ coldRatio × 期望 → 冷号；之间 → 温号。
 *
 * <p>红球期望 = sampleSize × 6/33；蓝球期望 = sampleSize × 1/16。
 * <p>30 期样本下：红球期望 ≈ 5.45，蓝球期望 ≈ 1.875。
 *
 * @author 刘强
 * @version 2026/08/06 19:45
 **/
@Component
@Data
public class ColdHotConfig {

    /** 红球冷热阈值 */
    private RedThreshold red = new RedThreshold();

    /** 蓝球冷热阈值 */
    private BlueThreshold blue = new BlueThreshold();

    /**
     * 红球冷热阈值（按比例自适应样本量）
     */
    @Data
    public static class RedThreshold {
        /**
         * 热号比例：出现次数 ≥ hotRatio × 期望次数 → 热号。
         * <p>30 期下期望 ≈ 5.45，默认 1.8 → ≥ 9.8 即 ≥10 次为热号（约 2 倍期望）。
         */
        private double hotRatio = 1.3;
        /**
         * 冷号比例：出现次数 ≤ coldRatio × 期望次数 → 冷号。
         * <p>30 期下期望 ≈ 5.45，默认 0.4 → ≤ 2.2 即 ≤2 次为冷号（约 0.4 倍期望）。
         */
        private double coldRatio = 0.4;
    }

    /**
     * 蓝球冷热阈值（按比例自适应样本量）
     */
    @Data
    public static class BlueThreshold {
        /**
         * 热号比例：出现次数 ≥ hotRatio × 期望次数 → 热号。
         * <p>30 期下期望 ≈ 1.875，默认 2.0 → ≥ 3.75 即 ≥4 次为热号（约 2 倍期望）。
         */
        private double hotRatio = 2.0;
        /**
         * 冷号比例：出现次数 ≤ coldRatio × 期望次数 → 冷号。
         * <p>30 期下期望 ≈ 1.875，默认 0.5 → ≤ 0.94 即 0 次为冷号（未开出）。
         */
        private double coldRatio = 0.5;
    }
}
