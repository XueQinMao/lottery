package com.my.project.service.support;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * LotteryTrendUtils
 *
 * <p>彩票号码遗漏与趋势分析工具类。
 * <ul>
 *   <li>遗漏值：某号距上次开出的期数，开出当期为 0，未开出逐期 +1</li>
 *   <li>平均遗漏 = (总期数 - 出现次数) / 出现次数</li>
 *   <li>指数（反向）= 平均遗漏 / max(遗漏值, 1)，遗漏越大指数越小</li>
 *   <li>MA5/MA10/MA20 = 指数序列的 5/10/20 期简单移动平均</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/12 16:45
 **/
@Slf4j
@Component
public class LotteryTrendUtils {

    /**
     * 计算某号在指定开奖序列中的遗漏值序列。
     *
     * @param draws        每期开出的号码集合列表（最旧→最新）
     * @param targetNumber 要分析的目标号码
     * @return 遗漏值列表，索引对应期号顺序
     */
    public static List<Integer> calcOmissionSequence(List<Set<Integer>> draws, int targetNumber) {
        List<Integer> omissions = new ArrayList<>(draws.size());
        int lastHitIndex = -1;
        for (int i = 0; i < draws.size(); i++) {
            if (draws.get(i).contains(targetNumber)) {
                omissions.add(0);
                lastHitIndex = i;
            } else if (lastHitIndex < 0) {
                omissions.add(i + 1);
            } else {
                omissions.add(i - lastHitIndex);
            }
        }
        return omissions;
    }

    /**
     * 计算反向指数序列：指数 = 平均遗漏 / max(遗漏值, 1)。
     * 遗漏为 0 时指数 = 平均遗漏（最高，表示刚出过最热）。
     *
     * @param omissions    遗漏值序列
     * @param avgOmission  平均遗漏
     * @return 指数序列（与输入等长）
     */
    public static List<Double> calcIndexValues(List<Integer> omissions, double avgOmission) {
        double avg = avgOmission > 0 ? avgOmission : 1;
        List<Double> indexValues = new ArrayList<>(omissions.size());
        for (int o : omissions) {
            indexValues.add(o == 0 ? avg : avg / o);
        }
        return indexValues;
    }

    /**
     * 计算简单移动平均（SMA），输入为 Integer 序列。窗口不足时返回 null。
     */
    public static List<Double> calcSMA(List<Integer> values, int window) {
        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (i < window - 1) {
                result.add(null);
            } else {
                double sum = 0;
                for (int j = i - window + 1; j <= i; j++) {
                    sum += values.get(j);
                }
                result.add(sum / window);
            }
        }
        return result;
    }

    /**
     * 计算简单移动平均（SMA），输入为 Double 序列。窗口不足时返回 null。
     */
    public static List<Double> calcSMADouble(List<Double> values, int window) {
        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (i < window - 1) {
                result.add(null);
            } else {
                double sum = 0;
                for (int j = i - window + 1; j <= i; j++) {
                    sum += values.get(j);
                }
                result.add(sum / window);
            }
        }
        return result;
    }

    /**
     * 计算趋势统计指标。
     *<ul>
     *   <li>平均遗漏 = (总期数 - 出现次数) / 出现次数</li>
     *   <li>指数均值 = 从首次出现起遗漏值的算术平均</li>
     * </ul>
     */
    public static TrendStats calcTrendStats(List<Integer> omissions) {
        TrendStats stats = new TrendStats();
        int total = omissions.size();
        int hitCount = (int) omissions.stream().filter(o -> o == 0).count();
        int maxOmission = omissions.stream().mapToInt(Integer::intValue).max().orElse(0);
        int currentOmission = omissions.get(omissions.size() - 1);

        double avgOmission = hitCount > 0 ? (double) (total - hitCount) / hitCount : 0;

        int firstHitIndex = -1;
        for (int i = 0; i < omissions.size(); i++) {
            if (omissions.get(i) == 0) {
                firstHitIndex = i;
                break;
            }
        }
        double indexMean = 0;
        if (firstHitIndex >= 0) {
            int count = total - firstHitIndex;
            double sum = 0;
            for (int i = firstHitIndex; i < total; i++) {
                sum += omissions.get(i);
            }
            indexMean = count > 0 ? sum / count : 0;
        }

        stats.setMaxOmission(maxOmission);
        stats.setAvgOmission(avgOmission);
        stats.setCurrentOmission(currentOmission);
        stats.setIndexMean(indexMean);
        stats.setHitCount(hitCount);
        stats.setTotalPeriods(total);
        return stats;
    }

    /**
     * 判断均线排列：多头（MA5>MA10>MA20）返回 1，空头（MA5<MA10<MA20）返回 -1，否则 0。
     */
    public static int calcArrangement(List<Double> ma5, List<Double> ma10, List<Double> ma20) {
        int i = ma5.size() - 1;
        if (i < 0 || ma5.get(i) == null || ma10.get(i) == null || ma20.get(i) == null) {
            return 0;
        }
        double v5 = ma5.get(i), v10 = ma10.get(i), v20 = ma20.get(i);
        if (v5 > v10 && v10 > v20) {
            return 1;
        }
        if (v5 < v10 && v10 < v20) {
            return -1;
        }
        return 0;
    }

    /**
     * 完整趋势分析：遗漏序列 → 反向指数 → SMA → 统计指标 → 均线排列。
     * 一次调用返回全部结果，供前端/服务直接使用。
     *
     * @param draws        每期开出的号码集合列表（最旧→最新）
     * @param targetNumber 要分析的目标号码
     * @return 趋势分析结果
     */
    public static TrendAnalysisResult analyze(List<Set<Integer>> draws, int targetNumber) {
        List<Integer> omissions = calcOmissionSequence(draws, targetNumber);
        TrendStats stats = calcTrendStats(omissions);
        List<Double> indexValues = calcIndexValues(omissions, stats.getAvgOmission());
        List<Double> ma5 = calcSMADouble(indexValues, 5);
        List<Double> ma10 = calcSMADouble(indexValues, 10);
        List<Double> ma20 = calcSMADouble(indexValues, 20);
        int arrangement = calcArrangement(ma5, ma10, ma20);

        TrendAnalysisResult result = new TrendAnalysisResult();
        result.setOmissions(omissions);
        result.setIndexValues(indexValues);
        result.setMa5(ma5);
        result.setMa10(ma10);
        result.setMa20(ma20);
        result.setStats(stats);
        result.setArrangement(arrangement);
        return result;
    }

    @Data
    public static class TrendStats {
        private int maxOmission;
        private double avgOmission;
        private int currentOmission;
        /** 从首次出现起遗漏值的算术平均（原图黄色参考线） */
        private double indexMean;
        private int hitCount;
        private int totalPeriods;
    }

    @Data
    public static class TrendAnalysisResult {
        private List<Integer> omissions;
        private List<Double> indexValues;
        private List<Double> ma5;
        private List<Double> ma10;
        private List<Double> ma20;
        private TrendStats stats;
        /** 1=多头, -1=空头, 0=交叉 */
        private int arrangement;
    }
}
