package com.my.project.service.support;

import com.my.project.persistence.entity.PredictHitRecord;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * WinningNumberAnalyzerUtils
 *
 * <p>基于历史命中记录的 totalScore，计算入库过滤用的 P5/P95 主体区间。
 * 分层抽样比例使用默认值（高/中/低 = 0.4/0.4/0.2）。
 *
 * @author 刘强
 * @version 2026/08/10 17:02
 */
@Slf4j
@Component
public class WinningNumberAnalyzerUtils {

    /** 概率过滤下界：历史命中分数的第 5 百分位 */
    private static final double PROB_LOWER_QUANTILE = 0.05;
    /** 概率过滤上界：历史命中分数的第 95 百分位 */
    private static final double PROB_UPPER_QUANTILE = 0.95;

    /**
     * 根据命中记录构建运行时配置（P5/P95 + 默认分层比例）。
     */
    public WeightConfigBo buildWeightConfig(List<PredictHitRecord> winningNumbers) {
        WeightConfigBo config = new WeightConfigBo();
        if (winningNumbers == null || winningNumbers.isEmpty()) {
            log.warn("没有中奖号码数据，返回默认配置（无概率过滤区间）");
            return config;
        }

        List<Double> scores = winningNumbers.stream()
            .map(PredictHitRecord::getTotalScore)
            .filter(Objects::nonNull)
            .map(v -> v.doubleValue())
            .sorted()
            .toList();

        if (scores.isEmpty()) {
            log.warn("命中记录均无有效 totalScore，跳过概率分位数计算");
            return config;
        }

        double p5 = percentile(scores, PROB_LOWER_QUANTILE);
        double p95 = percentile(scores, PROB_UPPER_QUANTILE);
        config.setProbabilityMin(p5);
        config.setProbabilityMax(p95);

        log.info("命中样本={}，概率过滤主体区间 P5/P95=[{}, {}]", scores.size(), p5, p95);
        return config;
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
}
