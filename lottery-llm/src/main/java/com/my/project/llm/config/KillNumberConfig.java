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
 * <p>红球使用 {@link #weights}（含 frequency/omission/zone/tail/rebound 五维），
 * 蓝球使用独立的 {@link #blueWeights}（仅 frequency/omission 两维），避免被红球 rebound
 * 权重稀释分母。硬杀阈值 {@link #hardThreshold} 红蓝共用。
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
     * <p>30 期样本下，红球"极冷+长期遗漏"核心冷信号得分 ≈ 0.588，"极冷+rebound触发"得分 ≈ 0.446。
     * 取 0.55 介于两者之间：杀核心冷信号、保护极值遗漏号。
     */
    private double hardThreshold = 0.55;

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
 *     <li>rebound：冷号回补保护（极值遗漏即将解冻，给负分对冲）</li>
     * </ul>
 * <p>30 期样本下统计波动较大，冷热与遗漏为主导（0.8），三区与尾数为辅助（0.2）。
 * <p>新增 rebound 维度作为「冷号回补」对冲：当遗漏期数占样本比例超过极值阈值时，
 * 该号码被认为即将解冻，给负分降低综合剔除置信度，避免极冷号被误杀。
 */
    private Map<String, Double> weights = new HashMap<>();

    {
        // 默认权重：冷热 0.5 / 遗漏 0.3 / 三区 0.1 / 尾数 0.1 / 回补保护 0.2
        weights.put("frequency", 0.5);
        weights.put("omission", 0.3);
        weights.put("zone", 0.1);
        weights.put("tail", 0.1);
        weights.put("rebound", 0.2);
    }

    /**
     * 蓝球专用维度权重。蓝球仅参与 frequency + omission 两维度（无 zone/tail/rebound），
     * 单独配置避免被红球 rebound 权重稀释分母。
     * <p>默认 frequency 0.5 / omission 0.3，与红球前两维同比例，归一化后 0.625 / 0.375。
     */
    private Map<String, Double> blueWeights = new HashMap<>();

    {
        blueWeights.put("frequency", 0.5);
        blueWeights.put("omission", 0.3);
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
     * 冷号回补保护维度阈值：当号码遗漏期数占样本比例超过极值阈值时，
     * 认为该号码即将「解冻」回补，给负分对冲冷热/遗漏的杀号得分。
     * <p>该维度是「冷号继续冷」理论的对冲，避免极冷号被误杀。
     */
    private ReboundThreshold rebound = new ReboundThreshold();

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

    /**
     * 冷号回补保护维度阈值
     */
    @Data
    public static class ReboundThreshold {
        /**
         * 极值遗漏比例，区间 (0,1)。
         * <p>当号码遗漏期数 / 样本大小 > 此值时，认为该号码即将解冻回补，触发保护。
         * <p>30 期样本下默认 0.9（即遗漏 >27 期触发），需小于 extremeOmissionWhitelistRatio(0.85)
         * 以保证 rebound 先降分、白名单再兜底的正确层次。
         */
        private double extremeRatio = 0.9;
        /**
         * 回补保护分（负分，对冲冷热/遗漏的杀号得分）。
         * <p>取负值，加权后降低综合剔除置信度。默认 -0.85，与冷热/遗漏高分同量级。
         */
        private double protectScore = -0.85;
    }

    /**
     * 极值遗漏白名单比例，区间 (0,1)。
     * <p>在 pickTop 筛选硬杀清单时，遗漏期数 / 样本大小 ≥ 此值的号码直接排除，
     * 不进入硬杀清单（兜底保护，与 rebound 维度双保险）。
     * <p>30 期样本下默认 0.85（即遗漏 ≥ 26 期的号码绝不硬杀）。
     */
    private double extremeOmissionWhitelistRatio = 0.85;
}
