package com.my.project.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * KillNumberConfig
 *
 * <p>杀号功能配置。对应配置项前缀 {@code lottery.llm.kill-number}。
 *
 * <p>杀号（Kill Number）是双色球选号中的排除法技巧：基于历史样本对每个候选号码
 * 计算一个 [0,1] 的「剔除置信度」，超过阈值即判定为应杀号，从而缩小选号范围。
 * 本配置控制开关、阈值、权重与每期最多杀号个数。
 *
 * <p>所有默认阈值按 <b>30 期样本</b> 校准：红球单号期望出现约 5.45 次，
 * 蓝球单号期望出现约 1.875 次。仅产出硬杀清单（LLM 须遵守）。
 *
 * @author 刘强
 * @version 2026/08/05 19:20
 **/
@Component
@Data
public class KillNumberConfig {


    // ==================== 硬杀阈值 ====================

    /**
     * 硬杀阈值：综合得分 ≥ 此值且排名靠前者进入硬杀清单（LLM 须遵守）
     */
    private double hardThreshold = 0.7;

    /**
     * 每期最多硬杀红球个数
     */
    private int maxHardKillRed = 8;

    /**
     * 每期最多硬杀蓝球个数
     */
    private int maxHardKillBlue = 4;

    // ==================== 维度权重 ====================

    /**
     * 各维度权重。key 为维度标识，value 为权重（无需归一化，计算时会自动归一）。
     * <ul>
     *     <li>frequency：冷热频次（极冷/超热回冷风险）</li>
     *     <li>omission：遗漏期数（长期未开出）</li>
     *     <li>zone：三区失衡（某区整体偏冷）</li>
     *     <li>tail：尾数过密（某尾数显著高于均值）</li>
     * </ul>
     * <p>30 期样本下统计波动较大，冷热与遗漏为主导（0.8），三区与尾数为辅助（0.2）。
     */
    private Map<String, Double> weights = new HashMap<>();

    {
        // 默认权重：冷热 0.5 / 遗漏 0.3 / 三区 0.1 / 尾数 0.1
        weights.put("frequency", 0.5);
        weights.put("omission", 0.3);
        weights.put("zone", 0.1);
        weights.put("tail", 0.1);
    }

    // ==================== 维度阈值 ====================

    /**
     * 冷热频次维度阈值（按 30 期校准：红球单号期望 ≈ 5.45 次）
     */
    private FrequencyThreshold frequency = new FrequencyThreshold();

    /**
     * 遗漏维度阈值（按 30 期校准：遗漏上限 = 样本大小 30）
     */
    private OmissionThreshold omission = new OmissionThreshold();

    /**
     * 三区失衡维度阈值：某区总出现次数低于均值 × (1 - ratio) 时，该区号码剔除置信度提升。
     * <p>30 期波动大，ratio 调高至 0.5 以减少误判。
     */
    private ZoneThreshold zone = new ZoneThreshold();

    /**
     * 尾数过密维度阈值：某尾数总次数高于均值 × (1 + ratio) 时，该尾数号码剔除置信度提升。
     * <p>30 期波动大，ratio 调高至 0.6 以减少误判。
     */
    private TailThreshold tail = new TailThreshold();

    /**
     * 冷热频次维度阈值
     */
    @Data
    public static class FrequencyThreshold {
        /**
         * 极冷：近 N 期出现次数 ≤ 此值 → 高剔除置信度。
         * <p>30 期下红球单号期望 ≈ 5.45，≤1 次即约 1/5 均值，判定极冷。
         */
        private int coldCount = 1;
        /**
         * 超热：近 N 期出现次数 ≥ 此值 → 回冷剔除置信度。
         * <p>30 期下 ≥10 次约 2 倍均值，判定超热。
         */
        private int hotCount = 10;
        /**
         * 极冷对应得分
         */
        private double coldScore = 0.9;
        /**
         * 超热对应得分
         */
        private double hotScore = 0.7;
    }

    /**
     * 遗漏维度阈值
     */
    @Data
    public static class OmissionThreshold {
        /**
         * 遗漏期数 > 此值 → 高剔除置信度。
         * <p>30 期下 >20 期（2/3 样本未开）判定长期遗漏。
         */
        private int longOmission = 20;
        /**
         * 遗漏期数 > 此值 → 中剔除置信度。
         * <p>30 期下 >12 期（40% 样本未开）判定中期遗漏。
         */
        private int midOmission = 12;
        /**
         * 长期遗漏得分
         */
        private double longScore = 0.85;
        /**
         * 中期遗漏得分
         */
        private double midScore = 0.6;
    }

    /**
     * 三区失衡维度阈值
     */
    @Data
    public static class ZoneThreshold {
        /**
         * 失衡比例，区间 (0,1)，越大越敏感。
         * <p>30 期波动大，默认 0.5（某区低于均值一半才判失衡）。
         */
        private double imbalanceRatio = 0.5;
        /**
         * 失衡时该区号码得分
         */
        private double imbalanceScore = 0.8;
    }

    /**
     * 尾数过密维度阈值
     */
    @Data
    public static class TailThreshold {
        /**
         * 过密比例，区间 (0,∞)，越大越不敏感。
         * <p>30 期波动大，默认 0.6（某尾数高于均值 60% 才判过密）。
         */
        private double denseRatio = 0.6;
        /**
         * 过密时该尾数号码得分
         */
        private double denseScore = 0.6;
    }
}
