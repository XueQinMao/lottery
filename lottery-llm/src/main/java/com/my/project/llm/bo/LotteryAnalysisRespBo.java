package com.my.project.llm.bo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * LotteryAnalysisRespBo
 *
 * <p>Java 直方图 + LLM 形态推算的多维度特征分析结果。
 * <ul>
 *     <li>红球：奇偶比、大小比、质合比、012路比、跨度、和值区间、和值尾数、和值位数、
 *         三区比、一区个数、二区个数、三区个数、尾数、连号、邻狐传</li>
 *     <li>蓝球：奇偶比、大小比、质合比、012路比、尾数、尾数大小、尾数奇偶、
 *         尾数012路、分区（四分区）、邻狐传、任意N码</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/07/21 20:23
 **/
@Data
public class LotteryAnalysisRespBo {

    /** 样本概览 */
    private SampleOverview sampleOverview;

    // ==================== 红球维度 ====================

    /** 奇偶比：key 形如 "3:3"、"4:2"，value 为出现次数 */
    private Map<String, Integer> oddEvenRatio;

    /** 大小比：大号(17-33)与小号(1-16)的比例分布，key 形如 "3:3" */
    private Map<String, Integer> bigSmallRatio;

    /** 质合比：质数与合数的比例分布，key 形如 "3:3" */
    private Map<String, Integer> primeCompositeRatio;

    /** 012路比：0路(3,6,9..)、1路(1,4,7..)、2路(2,5,8..) 的比例分布，key 形如 "2:2:2" */
    private Map<String, Integer> ratio012;

    /** 跨度分布：最大红球 - 最小红球，key 为跨度值，value 为出现次数 */
    private Map<String, Integer> span;

    /** 和值区间分布：key 形如 "97-102"，value 为出现次数 */
    private Map<String, Integer> sumRange;

    /** 和值尾数分布：key 为和值个位 0-9，value 为出现次数 */
    private Map<String, Integer> sumTail;

    /** 和值位数分布：key 为和值的位数（2位/3位），value 为出现次数 */
    private Map<String, Integer> sumDigit;

    /** 三区比：一区(1-11)、二区(12-22)、三区(23-33) 的比例分布，key 形如 "2:2:2" */
    private Map<String, Integer> threeZoneRatio;

    /** 一区个数分布：key 为一区红球个数(0-6)，value 为出现次数 */
    private Map<String, Integer> zone1Count;

    /** 二区个数分布 */
    private Map<String, Integer> zone2Count;

    /** 三区个数分布 */
    private Map<String, Integer> zone3Count;

    /** 尾数分析 */
    private TailAnalysis tail;

    /** 连号分析 */
    private ConsecutiveAnalysis consecutive;

    /** 邻狐传分析（红球，相对样本中上一期） */
    private NeighborFoxTransmit neighborFoxTransmit;

    // ==================== 蓝球维度 ====================

    /** 蓝球综合分析 */
    private BlueAnalysis blue;

    /**
     * 杀号计算结果（可选）。
     * <p>仅当杀号功能启用时由 {@code IKillNumberService} 产出；为 {@code null} 表示未启用。
     * 随 {@code analysisReportJson} 序列化后透传给调优阶段供 LLM 参考。
     */
    private KillNumberResultBo killNumbers;

    /**
     * 冷热温号码分析结果（可选）。
     * <p>由 {@code IColdHotAnalysisService} 基于原始样本统计产出，将红球/蓝球分为
     * 热号 / 温号 / 冷号三类。随 {@code analysisReportJson} 序列化后透传给调优阶段，
     * 供 LLM 直接引用冷热分类，避免自行从频次推断。
     */
    private ColdHotAnalysisBo coldHotAnalysis;

    /**
     * 下一期三区比预测结果（可选）。
     * <p>由 {@code IThreeZoneRatioPredictService} 基于「频率先验 + 马尔可夫转移」
     * 混合模型预测，给出 Top-K 候选三区比及概率。随 {@code analysisReportJson} 序列化后
     * 透传给调优阶段，供 LLM 选号形态参考。
     */
    private ThreeZoneRatioPredictBo predictedThreeZoneRatio;

    /**
     * 趋势均线分析结果（可选）。
     * <p>由 {@code LotteryTrendUtils} 基于反向指数的 MA5/MA10/MA20 均线排列计算，
     * 将红球/蓝球分为「趋势上升」（指数多头排列，号码趋热）和「趋势下降」
     * （指数空头排列，号码趋冷）两类。随 {@code analysisReportJson} 序列化后
     * 透传给调优阶段，供 LLM 补号时优先选择趋势上升的号码。
     */
    private TrendAnalysisBo trendAnalysis;

    /**
     * 形态推算（可选）。
     * <p>红球：奇偶/大小/质合/012路/跨度/和值区间/和尾/三区/分区个数；
     * 蓝球：奇偶/大小/大小奇偶/012路。指数快照与形态指数页同源，
     * LLM 或 Java 最高频推算下一期值。调优与推荐须优先落入这些目标。
     */
    private FeatureForecastBo featureForecast;

    /** 综合结论与选号建议 */
    private String conclusion;

    @Data
    public static class SampleOverview {
        /** 样本总数 */
        private Integer totalCount;
        /** 平均和值 */
        private Double avgSum;
        /** 平均跨度 */
        private Double avgSpan;
        /** 平均奇偶比 */
        private String avgOddEven;
        /** 平均大小比 */
        private String avgBigSmall;
    }

    @Data
    public static class TailAnalysis {
        /** 尾数值分布：key 为尾数(0-9)，value 为出现次数 */
        private Map<String, Integer> tailValue;
        /** 同尾组数分布：key 为同尾组数(0-3)，value 为出现次数 */
        private Map<String, Integer> sameTailGroupCount;
        /** 3D分析：对个位、十位、百位三档的尾数分布分析 */
        private ThreeDAnalysis threeD;
    }

    @Data
    public static class ThreeDAnalysis {
        /** 个位尾数分布 */
        private Map<String, Integer> onesDigit;
        /** 十位尾数分布 */
        private Map<String, Integer> tensDigit;
        /** 百位尾数分布 */
        private Map<String, Integer> hundredsDigit;
    }

    @Data
    public static class ConsecutiveAnalysis {
        /** 连号类型分布：key 形如 "无连号"、"2连号"、"3连号"、"2组2连号"，value 为出现次数 */
        private Map<String, Integer> consecutiveType;
        /** 连号号码热度：key 为连号组合(如 "12,13")，value 为出现次数 */
        private Map<String, Integer> hotConsecutive;
    }

    /**
     * 邻狐传分析（红球）。
     *
     * <p>以样本中相邻两期为单位，本期相对上一期：
     * <ul>
     *     <li>邻号：与上一期号码相差 1 的号码</li>
     *     <li>狐号：与上一期号码既不相邻也不重复的号码</li>
     *     <li>传号/重号：与上一期完全相同的号码</li>
     * </ul>
     */
    @Data
    public static class NeighborFoxTransmit {
        /** 邻狐传比分布：key 形如 "2:3:1"（邻:狐:传），value 为出现次数 */
        private Map<String, Integer> neighborFoxTransmitRatio;
        /** 邻号个数分布：key 为邻号个数(0-6)，value 为出现次数 */
        private Map<String, Integer> neighborCount;
        /** 狐号个数分布：key 为狐号个数(0-6)，value 为出现次数 */
        private Map<String, Integer> foxCount;
        /** 重号个数分布：key 为重号个数(0-6)，value 为出现次数 */
        private Map<String, Integer> repeatCount;
    }

    /**
     * 蓝球综合分析（基于样本中所有蓝球，范围 1-16）。
     */
    @Data
    public static class BlueAnalysis {
        /** 奇偶比：奇:偶 出现次数分布，key 形如 "奇"、"偶" */
        private Map<String, Integer> oddEvenRatio;
        /** 大小比：大(9-16):小(1-8) 出现次数分布 */
        private Map<String, Integer> bigSmallRatio;
        /** 质合比：质数(2,3,5,7,11,13):合数 出现次数分布 */
        private Map<String, Integer> primeCompositeRatio;
        /** 012路比：按蓝球除以 3 的余数分类，出现次数分布 */
        private Map<String, Integer> ratio012;
        /** 大小奇偶：小奇/小偶/大奇/大偶 出现次数分布 */
        private Map<String, Integer> bigSmallOddEvenRatio;
        /** 尾数分布：key 为尾数(0-6)，value 为出现次数 */
        private Map<String, Integer> tailValue;
        /** 尾数大小分布：尾数 0-4 为小，5-9 为大 */
        private Map<String, Integer> tailBigSmall;
        /** 尾数奇偶分布 */
        private Map<String, Integer> tailOddEven;
        /** 尾数012路分布 */
        private Map<String, Integer> tailRatio012;
        /** 分区（四分区）分布：一区(1-4)、二区(5-8)、三区(9-12)、四区(13-16) */
        private Map<String, Integer> fourZone;
        /** 蓝球邻狐传：相对样本中上一期蓝球 */
        private BlueNeighborFoxTransmit neighborFoxTransmit;
        /** 任意N码热度：1码/2码/3码/4码/5码 出现频率 Top 榜 */
        private AnyNAnalysis anyN;
    }

    @Data
    public static class BlueNeighborFoxTransmit {
        /** 邻狐传比分布：key 形如 "邻"、"狐"、"传" */
        private Map<String, Integer> neighborFoxTransmitRatio;
        /** 邻号出现次数：与上一期蓝球相差 1 */
        private Integer neighborCount;
        /** 狐号出现次数：与上一期蓝球相差 >=2 */
        private Integer foxCount;
        /** 重号出现次数：与上一期蓝球相同 */
        private Integer repeatCount;
    }

    @Data
    public static class AnyNAnalysis {
        /** 任意1码：出现频率 Top10 的单个蓝球 */
        private List<AnyNCandidate> any1;
        /** 任意2码：出现频率 Top10 的 2 蓝球组合 */
        private List<AnyNCandidate> any2;
        /** 任意3码：出现频率 Top10 的 3 蓝球组合 */
        private List<AnyNCandidate> any3;
        /** 任意4码：出现频率 Top10 的 4 蓝球组合 */
        private List<AnyNCandidate> any4;
        /** 任意5码：出现频率 Top10 的 5 蓝球组合 */
        private List<AnyNCandidate> any5;
    }

    @Data
    public static class AnyNCandidate {
        /** 号码组合（单码为单个数字，多码为逗号分隔） */
        private String balls;
        /** 出现次数 */
        private Integer count;
        /** 出现频率（0-1） */
        private Double frequency;
    }

    /**
     * 趋势均线分析结果。
     * <p>基于反向指数（平均遗漏 / max(遗漏, 1)）的 MA5/MA10/MA20 均线排列：
     * <ul>
     *     <li>多头排列（MA5 > MA10 > MA20）→ 指数上升 → 遗漏减少 → 号码趋热 → 优先补充</li>
     *     <li>空头排列（MA5 < MA10 < MA20）→ 指数下降 → 遗漏增大 → 号码趋冷 → 谨慎使用</li>
     * </ul>
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrendAnalysisBo {
        /** 趋势上升红球（指数均线多头排列，号码趋热，补号时优先选择） */
        private List<Integer> risingRedBalls;
        /** 趋势下降红球（指数均线空头排列，号码趋冷，补号时谨慎使用） */
        private List<Integer> fallingRedBalls;
        /** 趋势上升蓝球 */
        private List<Integer> risingBlueBalls;
        /** 趋势下降蓝球 */
        private List<Integer> fallingBlueBalls;
    }
}
