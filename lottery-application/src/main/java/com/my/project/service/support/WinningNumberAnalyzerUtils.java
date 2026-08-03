package com.my.project.service.support;

import com.my.project.persistence.entity.PredictHitRecord;
import com.my.project.service.selection.pojo.bo.NumericFeaturesBo;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;
import com.my.project.service.selection.pojo.bo.WinningFeatureStatsBo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 中奖号码特征分析器 基于已知的中奖号码数据（一等奖、二等奖），反向计算最优权重配置
 *
 * 核心思路： 1. 分析所有中奖号码的特征分布（big_count, odd_count, sum等） 2. 计算特征的集中度（使用信息熵） 3. 集中度越高的特征，说明越重要，分配更高的权重 4.
 * 结合模型分数的分布情况，动态调整权重
 *
 * @author 刘强
 * @version 2025-11-26
 */
@Slf4j
@Component
public class WinningNumberAnalyzerUtils {

    /** 概率过滤下界：历史命中分数的第 5 百分位（主体区间） */
    private static final double PROB_LOWER_QUANTILE = 0.05;
    /** 概率过滤上界：历史命中分数的第 95 百分位（主体区间） */
    private static final double PROB_UPPER_QUANTILE = 0.95;

    /**
     * 分析中奖号码特征，计算最优权重配置
     *
     * @param winningNumbers 中奖号码列表（从数据库查询或number.json读取）
     * @return 基于中奖号码特征优化的权重配置
     */
    public WeightConfigBo buildWeightConfig(List<PredictHitRecord> winningNumbers) {

        log.info("========== 开始分析中奖号码特征 ==========");
        log.info("中奖号码总数: {}", winningNumbers.size());

        if (winningNumbers.isEmpty()) {
            log.warn("没有中奖号码数据，返回默认权重配置");
            return new WeightConfigBo();
        }

        // 1. 统计中奖号码的特征分布
        WinningFeatureStatsBo stats = analyzeWinningFeatures(winningNumbers);

        // 2. 计算各特征的重要性（基于信息熵和分布特征）
        calculateFeatureImportance(stats);

        // 3. 根据重要性构建最优权重配置
        WeightConfigBo optimizedConfig = buildOptimizedWeightConfig(stats);

        // 4. 生成分析报告
        String report = generateAnalysisReport(stats, optimizedConfig);
        stats.setAnalysisReport(report);

        // 【新增】将分析报告也设置到 WeightConfigBo
        optimizedConfig.setAnalysisReport(report);

        log.info("\n{}", report);

        log.info("========== 中奖号码分析完成 ==========");

        return optimizedConfig;
    }

    /**
     * 统计中奖号码的特征分布
     */
    private WinningFeatureStatsBo analyzeWinningFeatures(List<PredictHitRecord> winningNumbers) {
        WinningFeatureStatsBo stats = new WinningFeatureStatsBo();
        stats.setWinningNumbers(winningNumbers);
        stats.setTotalCount(winningNumbers.size());

        double totalModelScore = 0;
        double minScore = Double.MAX_VALUE;
        double maxScore = Double.MIN_VALUE;
        List<Double> modelScores = new ArrayList<>();

        for (PredictHitRecord winning : winningNumbers) {
            List<Integer> reds = parseRedBalls(winning.getRedBalls());

            // 计算数值特征
            NumericFeaturesBo numFeatures = calculateNumericFeatures(reds);

            // 统计 big_count 分布
            stats.getBigCountDist().merge(numFeatures.getBigCount(), 1, Integer::sum);

            // 统计 odd_count 分布
            stats.getOddCountDist().merge(numFeatures.getOddCount(), 1, Integer::sum);

            // 统计 sum 范围分布（按10分段，如70-79=7, 80-89=8）
            int sumRange = numFeatures.getSum() / 10;
            stats.getSumRangeDist().merge(sumRange, 1, Integer::sum);

            // 统计 same_tail 分布
            stats.getSameTailDist().merge(numFeatures.getSameTailCount(), 1, Integer::sum);

            // 统计模型分数（即概率）；跳过空分避免污染分位数
            if (winning.getTotalScore() == null) {
                continue;
            }
            double score = winning.getTotalScore().doubleValue();
            totalModelScore += score;
            minScore = Math.min(minScore, score);
            maxScore = Math.max(maxScore, score);
            modelScores.add(score);

            // 概率分桶统计（按0.05区间分桶）
            String bucket = getProbabilityBucket(score);
            stats.getProbabilityBucketDist().merge(bucket, 1, Integer::sum);
        }

        if (modelScores.isEmpty()) {
            log.warn("命中记录均无有效 totalScore，跳过概率分位数计算");
            stats.setProbabilityBoundsReady(false);
            return stats;
        }

        stats.setAvgModelScore(totalModelScore / modelScores.size());
        stats.setMinModelScore(minScore);
        stats.setMaxModelScore(maxScore);
        stats.setModelScoreStdDev(calculateStdDev(modelScores, stats.getAvgModelScore()));

        // 主体区间：P5/P95（抗极端值），仍保留真实 min/max 供日志参考
        List<Double> sortedScores = new ArrayList<>(modelScores);
        Collections.sort(sortedScores);
        double p5 = percentile(sortedScores, PROB_LOWER_QUANTILE);
        double p95 = percentile(sortedScores, PROB_UPPER_QUANTILE);
        stats.setProbabilityMin(p5);
        stats.setProbabilityMax(p95);
        stats.setProbabilityRange(p95 - p5);
        stats.setProbabilityConcentration(
            calculateProbabilityConcentration(stats.getProbabilityRange(), stats.getAvgModelScore()));
        stats.setProbabilityBoundsReady(true);

        log.info("特征分布统计:");
        log.info("  big_count分布: {}", formatDistribution(stats.getBigCountDist()));
        log.info("  odd_count分布: {}", formatDistribution(stats.getOddCountDist()));
        log.info("  same_tail分布: {}", formatDistribution(stats.getSameTailDist()));
        log.info("  模型分数: avg={:.4f}, min={:.4f}, max={:.4f}, stdDev={:.4f}", stats.getAvgModelScore(),
            stats.getMinModelScore(), stats.getMaxModelScore(), stats.getModelScoreStdDev());

        log.info("概率主体区间(P5/P95):");
        log.info("  P5/P95 区间: [{:.4f}, {:.4f}] (极值=[{:.4f}, {:.4f}])", p5, p95, minScore, maxScore);
        log.info("  主体范围: {:.4f}", stats.getProbabilityRange());
        log.info("  概率集中度: {:.3f}", stats.getProbabilityConcentration());
        log.info("  概率分桶分布: {}", formatProbabilityDistribution(stats.getProbabilityBucketDist()));

        return stats;
    }

    /**
     * 计算各特征的重要性 基于信息熵（集中度）和统计特性
     */
    private void calculateFeatureImportance(WinningFeatureStatsBo stats) {
        log.info("开始计算特征重要性...");

        // 1. 数值特征重要性：基于分布的集中度（信息熵）
        // 熵越低，分布越集中，说明该特征越重要
        double bigCountEntropy = calculateEntropy(stats.getBigCountDist());
        double oddCountEntropy = calculateEntropy(stats.getOddCountDist());
        double sameTailEntropy = calculateEntropy(stats.getSameTailDist());

        // 平均熵（理论最大熵为 log(6)=1.79）
        double avgNumEntropy = (bigCountEntropy + oddCountEntropy + sameTailEntropy) / 3.0;

        // 熵越低，重要性越高（归一化到0-1）
        // 熵的范围：0（完全集中）到log(n)（完全均匀）
        double maxEntropy = Math.log(6); // 假设最多6种可能
        double numImportance = 1.0 - Math.min(avgNumEntropy / maxEntropy, 1.0);

        // 2. 离散特征重要性
        // 这里简化处理，根据中奖号码的奇偶、大小组合模式的稳定性评估
        // 从实际数据看，离散特征相对稳定，赋予中等重要性
        double catImportance = 0.5;

        // 3. 模型分数重要性：基于分数的集中度和变异系数【增强版】
        double scoreRange = stats.getMaxModelScore() - stats.getMinModelScore();
        double avgScore = stats.getAvgModelScore();
        double coefficientOfVariation = avgScore > 1e-12
            ? stats.getModelScoreStdDev() / avgScore
            : 1.0;

        // 基础重要性：如果分数集中（变异系数小），说明模型分数很重要
        // 变异系数越小，重要性越高
        double baseModelImportance = 1.0 - Math.min(coefficientOfVariation * 2, 1.0);

        // 【新增】概率集中度加成（最多20%）
        double probabilityBonus = stats.getProbabilityConcentration() * 0.2;

        // 最终模型重要性 = 基础重要性 + 概率集中度加成
        double modelImportance = Math.min(baseModelImportance + probabilityBonus, 0.95);

        // 特殊处理：如果所有中奖号码的分数都在一个窄区间（如0.15-0.25），
        // 说明模型对这个分数段很有信心，提升其重要性
        if (scoreRange < 0.1) {
            log.info("  检测到模型分数高度集中(range={:.4f})，已应用集中度加成", scoreRange);
        }

        // 【新增】如果概率高度集中在最佳区间（0.18-0.25），进一步提升
        if (stats.getProbabilityRange() < 0.08 && stats.getAvgModelScore() >= 0.18 && stats.getAvgModelScore() <= 0.25) {
            modelImportance = Math.min(modelImportance * 1.15, 0.95);
            log.info("  检测到概率集中在最佳区间 [{:.4f}, {:.4f}]，提升模型重要性15%", stats.getProbabilityMin(),
                stats.getProbabilityMax());
        }

        stats.setNumFeatureImportance(numImportance);
        stats.setCatFeatureImportance(catImportance);
        stats.setModelScoreImportance(modelImportance);

        log.info("特征重要性计算结果:");
        log.info("  数值特征重要性: {:.3f} (平均熵={:.3f}/{:.3f})", numImportance, avgNumEntropy, maxEntropy);
        log.info("  离散特征重要性: {:.3f}", catImportance);
        log.info("  模型分数重要性: {:.3f} (变异系数={:.3f}, 范围={:.4f}, 概率集中度={:.3f})", modelImportance,
            coefficientOfVariation, scoreRange, stats.getProbabilityConcentration());
    }

    /**
     * 根据特征重要性构建最优权重配置
     */
    private WeightConfigBo buildOptimizedWeightConfig(WinningFeatureStatsBo stats) {

        log.info("开始构建最优权重配置...");

        WeightConfigBo config = new WeightConfigBo();

        // 归一化重要性（总和为1.0）
        double totalImportance =
            stats.getNumFeatureImportance() + stats.getCatFeatureImportance() + stats.getModelScoreImportance();

        if (totalImportance == 0) {
            log.warn("总重要性为0，使用默认权重配置");
            return config;
        }

        // 按重要性比例分配权重
        double numWeight = stats.getNumFeatureImportance() / totalImportance;
        double catWeight = stats.getCatFeatureImportance() / totalImportance;
        double modelWeight = stats.getModelScoreImportance() / totalImportance;

        // 保底权重：每个特征至少保留5%的权重，避免完全忽略某个特征
        double minWeight = 0.05;
        numWeight = Math.max(numWeight, minWeight);
        catWeight = Math.max(catWeight, minWeight);
        modelWeight = Math.max(modelWeight, minWeight);

        // 再次归一化（因为加了保底权重）
        double sum = numWeight + catWeight + modelWeight;
        numWeight = numWeight / sum;
        catWeight = catWeight / sum;
        modelWeight = modelWeight / sum;

        // 设置权重
        config.setNumFeatureWeight(numWeight);
        config.setCatFeatureWeight(catWeight);
        config.setModelScoreWeight(modelWeight);

        // 新主路径：区间约束权重 = 数值+离散重要性的综合
        config.setRangeConstraintWeight(Math.max(0.15, (numWeight + catWeight) * 0.55));
        // 模型分与区间约束再归一化到合理量级（打分时会再除以总和）
        double modelAndRange = config.getModelScoreWeight() + config.getRangeConstraintWeight();
        if (modelAndRange > 0) {
            double scale = 0.75 / modelAndRange;
            config.setModelScoreWeight(config.getModelScoreWeight() * scale);
            config.setRangeConstraintWeight(config.getRangeConstraintWeight() * scale);
        }

        // 蓝球权重和加成权重
        config.setBlueHotWeight(0.10);
        config.setBlueGapWeight(0.05);
        config.setRangeBonus(0.20);
        config.setModerateBonus(0.10);
        config.setZoneBalanceBonus(0.05);
        config.setHotNumberBonus(0.05);

        // 分位数分层默认比例（真正被 SmartSelect 使用）
        config.setHighScoreRatio(0.40);
        config.setMidScoreRatio(0.40);
        config.setLowScoreRatio(0.20);
        config.setDiversityThreshold(0.50);
        config.setRangeLowerQuantile(0.10);
        config.setRangeUpperQuantile(0.90);

        // 仅在算出有效 P5/P95 时写入，避免默认 0 导致入库全被过滤
        if (stats.isProbabilityBoundsReady()) {
            config.setProbabilityMin(stats.getProbabilityMin());
            config.setProbabilityMax(stats.getProbabilityMax());
            config.setProbabilityRange(stats.getProbabilityRange());
            config.setProbabilityConcentration(stats.getProbabilityConcentration());
            config.setProbabilityBucketDistribution(formatProbabilityDistribution(stats.getProbabilityBucketDist()));
            log.info("概率过滤主体区间 P5/P95=[{:.4f}, {:.4f}]",
                config.getProbabilityMin(), config.getProbabilityMax());
        } else {
            log.warn("未写入概率过滤区间（无有效命中分数），监听器将默认放行入库");
        }

        log.info("最优权重配置: modelScore={:.3f}, rangeConstraint={:.3f}, numFeature={:.3f}, catFeature={:.3f}",
            config.getModelScoreWeight(), config.getRangeConstraintWeight(), numWeight, catWeight);

        return config;
    }

    /**
     * 生成分析报告
     */
    private String generateAnalysisReport(WinningFeatureStatsBo stats, WeightConfigBo config) {
        StringBuilder report = new StringBuilder();
        report.append("\n========== 中奖号码特征分析报告 ==========\n");
        report.append(String.format("样本数量: %d 组中奖号码\n", stats.getTotalCount()));

        report.append("\n【特征分布】\n");
        report.append(String.format("  big_count: %s\n", formatDistribution(stats.getBigCountDist())));
        report.append(String.format("  odd_count: %s\n", formatDistribution(stats.getOddCountDist())));
        report.append(String.format("  same_tail: %s\n", formatDistribution(stats.getSameTailDist())));
        report.append(String.format("  模型分数: 均值=%.4f, 区间=[%.4f, %.4f], 标准差=%.4f\n", stats.getAvgModelScore(),
            stats.getMinModelScore(), stats.getMaxModelScore(), stats.getModelScoreStdDev()));

        // 概率主体区间（P5/P95）
        report.append("\n【概率主体区间(P5/P95)】\n");
        report.append(String.format("  过滤区间: [%.4f, %.4f] (P5~P95，非极值)\n",
            stats.getProbabilityMin(), stats.getProbabilityMax()));
        report.append(String.format("  极值参考: [%.4f, %.4f]\n",
            stats.getMinModelScore(), stats.getMaxModelScore()));
        report.append(String.format("  主体范围: %.4f\n", stats.getProbabilityRange()));
        report.append(String.format("  概率集中度: %.3f (越高越集中)\n", stats.getProbabilityConcentration()));
        report.append(
            String.format("  概率分桶: %s\n", formatProbabilityDistribution(stats.getProbabilityBucketDist())));

        report.append("\n【特征重要性】\n");
        report.append(String.format("  数值特征: %.1f%% (集中度越高，越重要)\n", stats.getNumFeatureImportance() * 100));
        report.append(String.format("  离散特征: %.1f%%\n", stats.getCatFeatureImportance() * 100));
        report.append(String.format("  模型分数: %.1f%% (变异系数越小、概率越集中，越重要)\n",
            stats.getModelScoreImportance() * 100));

        report.append("\n【最优权重配置】\n");
        report.append(String.format("  numFeatureWeight  = %.3f (%.1f%%)\n", config.getNumFeatureWeight(),
            config.getNumFeatureWeight() * 100));
        report.append(String.format("  catFeatureWeight  = %.3f (%.1f%%)\n", config.getCatFeatureWeight(),
            config.getCatFeatureWeight() * 100));
        report.append(String.format("  modelScoreWeight  = %.3f (%.1f%%)\n", config.getModelScoreWeight(),
            config.getModelScoreWeight() * 100));

        report.append("\n【建议】\n");
        if (stats.getProbabilityConcentration() > 0.7) {
            report.append("  ✓ 概率高度集中，模型对该概率区间很有信心\n");
        }
        if (stats.getAvgModelScore() >= 0.18 && stats.getAvgModelScore() <= 0.25) {
            report.append("  ✓ 平均概率处于理想区间(0.18-0.25)，预测质量较高\n");
        }
        if (config.getModelScoreWeight() > 0.4) {
            report.append("  ✓ 模型分数权重较高，说明XGBoost模型对中奖号码预测很有信心\n");
        }
        if (config.getNumFeatureWeight() > 0.3) {
            report.append("  ✓ 数值特征权重较高，big_count、odd_count等特征对中奖很重要\n");
        }
        report.append("  ✓ 该权重配置基于实际中奖数据优化，建议使用\n");
        report.append("=========================================\n");

        return report.toString();
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算信息熵（用于评估分布集中度） 熵越低，分布越集中，说明特征越重要
     */
    private double calculateEntropy(Map<Integer, Integer> distribution) {
        if (distribution.isEmpty())
            return 0;

        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        double entropy = 0;

        for (int count : distribution.values()) {
            if (count > 0) {
                double p = (double)count / total;
                entropy -= p * Math.log(p);
            }
        }

        return entropy;
    }

    /**
     * 计算标准差
     */
    private double calculateStdDev(List<Double> values, double mean) {
        if (values.isEmpty())
            return 0;

        double sumSquaredDiff = 0;
        for (double value : values) {
            sumSquaredDiff += Math.pow(value - mean, 2);
        }

        return Math.sqrt(sumSquaredDiff / values.size());
    }

    /**
     * 线性插值分位数。{@code p} 取值 0~1，例如 0.05=P5、0.95=P95。
     * {@code sorted} 必须已升序排序。
     */
    public static double percentile(List<Double> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) {
            return 0;
        }
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        double clamped = Math.max(0.0, Math.min(1.0, p));
        double index = clamped * (sorted.size() - 1);
        int lo = (int) Math.floor(index);
        int hi = (int) Math.ceil(index);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double weight = index - lo;
        return sorted.get(lo) * (1.0 - weight) + sorted.get(hi) * weight;
    }

    /**
     * 格式化分布显示
     */
    private String formatDistribution(Map<Integer, Integer> dist) {
        return dist.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> String.format("%d:%d", e.getKey(), e.getValue())).collect(Collectors.joining(", "));
    }

    /**
     * 格式化概率分布显示（按桶排序）
     */
    private String formatProbabilityDistribution(Map<String, Integer> dist) {
        return dist.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> String.format("%s:%d", e.getKey(), e.getValue())).collect(Collectors.joining(", "));
    }

    /**
     * 获取概率所属区间桶 例如：0.15-0.20, 0.20-0.25, 0.25-0.30
     */
    private String getProbabilityBucket(double probability) {
        double bucketSize = 0.05;  // 每个桶的大小
        double lowerBound = Math.floor(probability / bucketSize) * bucketSize;
        double upperBound = lowerBound + bucketSize;
        return String.format("%.2f-%.2f", lowerBound, upperBound);
    }

    /**
     * 计算概率集中度
     *
     * @param range    概率范围
     * @param avgScore 平均分数
     * @return 集中度 (0-1，越接近1越集中)
     */
    private double calculateProbabilityConcentration(double range, double avgScore) {
        // 方法1：基于范围归一化
        // 假设中奖号码的概率理论上应该在 0.15-0.30 之间（根据过滤规则）
        double theoreticalMaxRange = 0.15;  // 0.30 - 0.15
        double concentration = 1.0 - Math.min(range / theoreticalMaxRange, 1.0);

        // 方法2：考虑平均分数的位置（越接近0.20-0.25越好）
        double idealCenter = 0.225;  // (0.15 + 0.30) / 2
        double centerDeviation = Math.abs(avgScore - idealCenter);
        double centerBonus = 1.0 - Math.min(centerDeviation / 0.075, 1.0);

        // 综合评分：70%看集中度，30%看中心位置
        return concentration * 0.7 + centerBonus * 0.3;
    }

    /**
     * 解析红球字符串
     */
    private List<Integer> parseRedBalls(String redBalls) {
        if (redBalls == null || redBalls.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(redBalls.split(",")).map(String::trim).map(Integer::parseInt).collect(Collectors.toList());
    }

    /**
     * 计算数值特征（简化版，只计算基本特征）
     */
    private NumericFeaturesBo calculateNumericFeatures(List<Integer> redBalls) {
        NumericFeaturesBo nf = new NumericFeaturesBo();

        // 大号个数 (>16)
        nf.setBigCount((int)redBalls.stream().filter(n -> n > 16).count());

        // 奇数个数
        nf.setOddCount((int)redBalls.stream().filter(n -> n % 2 == 1).count());

        // 总和
        nf.setSum(redBalls.stream().mapToInt(Integer::intValue).sum());

        // 尾号相同个数
        Map<Integer, Long> tailCount =
            redBalls.stream().collect(Collectors.groupingBy(n -> n % 10, Collectors.counting()));
        nf.setSameTailCount((int)tailCount.values().stream().filter(c -> c > 1).mapToLong(Long::longValue).sum());

        return nf;
    }
}
