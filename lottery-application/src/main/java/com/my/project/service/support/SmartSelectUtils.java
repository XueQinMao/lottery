package com.my.project.service.support;

import com.github.signaflo.timeseries.TimeSeries;
import com.github.signaflo.timeseries.model.arima.Arima;
import com.github.signaflo.timeseries.model.arima.ArimaOrder;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.entity.PredictRecord;
import com.my.project.service.selection.pojo.bo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 双色球智能复试选号工具类
 *
 * <p>优化后主路径：历史分位数特征区间约束 + 模型分统一语义 + Jaccard 多样性选 Top-N。
 * ARIMA/马尔可夫点预测仍保留为兼容实现，但不再作为主打分依据。</p>
 *
 * @author 刘强
 * @version 2025/11/03
 */
public class SmartSelectUtils {

    private static final Logger logger = LoggerFactory.getLogger(SmartSelectUtils.class);


    /**
     * 提取历史特征
     */
    public static List<PeriodFeaturesBo> extractHistoricalFeatures(List<HistoryRecord> history) {
        List<PeriodFeaturesBo> features = new ArrayList<>();
        
        for (int i = 0; i < history.size(); i++) {
            HistoryRecord record = history.get(i);
            List<Integer> redBalls = Arrays.asList(
                    record.getNum1(), record.getNum2(), record.getNum3(),
                    record.getNum4(), record.getNum5(), record.getNum6()
            );
            
            PeriodFeaturesBo pf = new PeriodFeaturesBo();
            pf.setNumeric(calculateNumericFeatures(redBalls, 
                    i > 0 ? getRedBalls(history.get(i - 1)) : null));
            pf.setCategorical(calculateCategoricalFeatures(redBalls));
            pf.setBlue(calculateBlueFeatures(record.getSpecial(), history, i));
            
            features.add(pf);
        }
        
        return features;
    }

    /**
     * 计算数值型特征
     */
    private static NumericFeaturesBo calculateNumericFeatures(List<Integer> redBalls,
                                                             List<Integer> lastPeriodBalls) {
        NumericFeaturesBo nf = new NumericFeaturesBo();
        
        // 大号个数 (>16)
        nf.setBigCount((int) redBalls.stream().filter(n -> n > 16).count());
        
        // 奇数个数
        nf.setOddCount((int) redBalls.stream().filter(n -> n % 2 == 1).count());
        
        // 总和
        nf.setSum(redBalls.stream().mapToInt(Integer::intValue).sum());
        
        // 三区分布
        nf.setZone1((int) redBalls.stream().filter(n -> n <= 11).count());
        nf.setZone2((int) redBalls.stream().filter(n -> n >= 12 && n <= 22).count());
        nf.setZone3((int) redBalls.stream().filter(n -> n >= 23).count());
        
        // 尾号相同个数
        Map<Integer, Long> tailCount = redBalls.stream()
                .collect(Collectors.groupingBy(n -> n % 10, Collectors.counting()));
        nf.setSameTailCount((int) tailCount.values().stream().filter(c -> c > 1).mapToLong(Long::longValue).sum());
        
        // 连号个数
        List<Integer> sorted = new ArrayList<>(redBalls);
        Collections.sort(sorted);
        int consecutive = 0;
        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i + 1) - sorted.get(i) == 1) {
                consecutive++;
            }
        }
        nf.setConsecutiveCount(consecutive);
        
        // 与上期相同红球数量
        if (lastPeriodBalls != null) {
            Set<Integer> intersection = new HashSet<>(redBalls);
            intersection.retainAll(lastPeriodBalls);
            nf.setRepeatFromLast(intersection.size());
        } else {
            nf.setRepeatFromLast(0);
        }
        
        return nf;
    }

    /**
     * 计算离散型特征
     */
    private static CategoricalFeaturesBo calculateCategoricalFeatures(List<Integer> redBalls) {
        CategoricalFeaturesBo cf = new CategoricalFeaturesBo();
        
        int oddCount = (int) redBalls.stream().filter(n -> n % 2 == 1).count();
        int evenCount = 6 - oddCount;
        cf.setOddEvenPattern(oddCount + "奇" + evenCount + "偶");
        
        int bigCount = (int) redBalls.stream().filter(n -> n > 16).count();
        int smallCount = 6 - bigCount;
        cf.setBigSmallPattern(bigCount + "大" + smallCount + "小");
        
        int zone1 = (int) redBalls.stream().filter(n -> n <= 11).count();
        int zone2 = (int) redBalls.stream().filter(n -> n >= 12 && n <= 22).count();
        int zone3 = (int) redBalls.stream().filter(n -> n >= 23).count();
        cf.setZonePattern(zone1 + "-" + zone2 + "-" + zone3);
        
        return cf;
    }

    /**
     * 计算蓝球特征
     */
    private static BlueFeaturesBo calculateBlueFeatures(int blueBall,
                                                       List<HistoryRecord> history, 
                                                       int currentIndex) {
        BlueFeaturesBo bf = new BlueFeaturesBo();
        
        bf.setBig(blueBall > 8);
        bf.setOdd(blueBall % 2 == 1);
        
        // 热度：历史出现次数
        int hotness = 0;
        for (HistoryRecord record : history) {
            if (record.getSpecial() == blueBall) {
                hotness++;
            }
        }
        bf.setHotness(hotness);
        
        // 距离上次出现的期数
        int gap = 0;
        for (int i = currentIndex - 1; i >= 0; i--) {
            gap++;
            if (history.get(i).getSpecial() == blueBall) {
                break;
            }
        }
        bf.setRecentGap(gap);
        
        return bf;
    }

    // ==================== 2. 特征区间（替代 ARIMA 点预测） ====================

    /**
     * 构建下一期特征约束：历史分位数区间 + 蓝球概率
     */
    public static PredictedFeaturesBo buildPredictedFeatures(List<HistoryRecord> history,
                                                             WeightConfigBo config) {
        List<HistoryRecord> chrono = chronological(history);
        PredictedFeaturesBo pf = new PredictedFeaturesBo();
        pf.setFeatureRange(buildFeatureRanges(chrono,
            config.getRangeLowerQuantile(), config.getRangeUpperQuantile()));
        pf.setBlueProbability(predictBlueProbability(chrono));
        // 兼容旧字段：用区间中点填充，避免空指针
        NumericFeaturesBo mid = new NumericFeaturesBo();
        FeatureRangeBo range = pf.getFeatureRange();
        mid.setSum((range.getSumMin() + range.getSumMax()) / 2);
        mid.setOddCount((range.getOddMin() + range.getOddMax()) / 2);
        mid.setBigCount((range.getBigMin() + range.getBigMax()) / 2);
        mid.setZone1((range.getZone1Min() + range.getZone1Max()) / 2);
        mid.setZone2((range.getZone2Min() + range.getZone2Max()) / 2);
        mid.setZone3((range.getZone3Min() + range.getZone3Max()) / 2);
        mid.setSameTailCount(0);
        mid.setConsecutiveCount(0);
        mid.setRepeatFromLast(0);
        pf.setNumeric(mid);
        CategoricalFeaturesBo cat = new CategoricalFeaturesBo();
        cat.setOddEvenPattern(mid.getOddCount() + "奇" + (6 - mid.getOddCount()) + "偶");
        cat.setBigSmallPattern(mid.getBigCount() + "大" + (6 - mid.getBigCount()) + "小");
        cat.setZonePattern(mid.getZone1() + "-" + mid.getZone2() + "-" + mid.getZone3());
        pf.setCategorical(cat);
        return pf;
    }

    /**
     * 兼容旧调用：内部转发到区间约束构建
     */
    public static PredictedFeaturesBo predictNextFeatures(List<PeriodFeaturesBo> historicalFeatures,
                                                          List<HistoryRecord> history,
                                                          WeightConfigBo config) {
        return buildPredictedFeatures(history, config);
    }

    /**
     * 从历史开奖计算特征分位数区间（chrono: oldest -> newest）
     */
    public static FeatureRangeBo buildFeatureRanges(List<HistoryRecord> chrono,
                                                    double lowerQ, double upperQ) {
        FeatureRangeBo range = new FeatureRangeBo();
        if (chrono == null || chrono.isEmpty()) {
            // 合理默认区间
            range.setSumMin(70);
            range.setSumMax(130);
            range.setSpanMin(16);
            range.setSpanMax(30);
            range.setOddMin(2);
            range.setOddMax(4);
            range.setBigMin(2);
            range.setBigMax(4);
            range.setZone1Min(1);
            range.setZone1Max(3);
            range.setZone2Min(1);
            range.setZone2Max(3);
            range.setZone3Min(1);
            range.setZone3Max(3);
            range.setConsecutiveMax(2);
            range.setSameTailMax(2);
            range.setSource("default");
            return range;
        }

        List<Integer> sums = new ArrayList<>();
        List<Integer> spans = new ArrayList<>();
        List<Integer> odds = new ArrayList<>();
        List<Integer> bigs = new ArrayList<>();
        List<Integer> z1 = new ArrayList<>();
        List<Integer> z2 = new ArrayList<>();
        List<Integer> z3 = new ArrayList<>();
        List<Integer> cons = new ArrayList<>();
        List<Integer> tails = new ArrayList<>();

        for (int i = 0; i < chrono.size(); i++) {
            List<Integer> reds = getRedBalls(chrono.get(i));
            List<Integer> prev = i > 0 ? getRedBalls(chrono.get(i - 1)) : null;
            NumericFeaturesBo nf = calculateNumericFeatures(reds, prev);
            sums.add(nf.getSum());
            spans.add(Collections.max(reds) - Collections.min(reds));
            odds.add(nf.getOddCount());
            bigs.add(nf.getBigCount());
            z1.add(nf.getZone1());
            z2.add(nf.getZone2());
            z3.add(nf.getZone3());
            cons.add(nf.getConsecutiveCount());
            tails.add(nf.getSameTailCount());
        }

        range.setSumMin(quantileInt(sums, lowerQ));
        range.setSumMax(quantileInt(sums, upperQ));
        range.setSpanMin(quantileInt(spans, lowerQ));
        range.setSpanMax(quantileInt(spans, upperQ));
        range.setOddMin(quantileInt(odds, lowerQ));
        range.setOddMax(quantileInt(odds, upperQ));
        range.setBigMin(quantileInt(bigs, lowerQ));
        range.setBigMax(quantileInt(bigs, upperQ));
        range.setZone1Min(quantileInt(z1, lowerQ));
        range.setZone1Max(quantileInt(z1, upperQ));
        range.setZone2Min(quantileInt(z2, lowerQ));
        range.setZone2Max(quantileInt(z2, upperQ));
        range.setZone3Min(quantileInt(z3, lowerQ));
        range.setZone3Max(quantileInt(z3, upperQ));
        range.setConsecutiveMax(quantileInt(cons, upperQ));
        range.setSameTailMax(quantileInt(tails, upperQ));
        range.setSource(String.format("P%.0f-P%.0f", lowerQ * 100, upperQ * 100));

        logger.info("特征区间 {}: sum=[{},{}] span=[{},{}] odd=[{},{}] big=[{},{}] zone1=[{},{}] zone2=[{},{}] zone3=[{},{}]",
            range.getSource(), range.getSumMin(), range.getSumMax(),
            range.getSpanMin(), range.getSpanMax(),
            range.getOddMin(), range.getOddMax(),
            range.getBigMin(), range.getBigMax(),
            range.getZone1Min(), range.getZone1Max(),
            range.getZone2Min(), range.getZone2Max(),
            range.getZone3Min(), range.getZone3Max());
        return range;
    }

    private static int quantileInt(List<Integer> values, double q) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double idx = q * (sorted.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double w = idx - lo;
        return (int) Math.round(sorted.get(lo) * (1 - w) + sorted.get(hi) * w);
    }

    /** 将 DESC 历史转为 oldest -> newest */
    public static List<HistoryRecord> chronological(List<HistoryRecord> history) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        List<HistoryRecord> copy = new ArrayList<>(history);
        copy.sort(Comparator.comparing(HistoryRecord::getOpenDate,
            Comparator.nullsLast(Comparator.naturalOrder())));
        return copy;
    }

    /**
     * 使用ARIMA预测数值型特征（兼容保留，主路径不再调用）
     */
    private static NumericFeaturesBo predictNumericFeaturesWithArima(List<PeriodFeaturesBo> historicalFeatures,
                                                                    WeightConfigBo config) {
        NumericFeaturesBo predicted = new NumericFeaturesBo();
        
        try {
            // 提取时间序列数据
            double[] bigCountSeries = historicalFeatures.stream()
                    .mapToDouble(f -> f.getNumeric().getBigCount()).toArray();
            double[] oddCountSeries = historicalFeatures.stream()
                    .mapToDouble(f -> f.getNumeric().getOddCount()).toArray();
            double[] sumSeries = historicalFeatures.stream()
                    .mapToDouble(f -> f.getNumeric().getSum()).toArray();
            double[] zone1Series = historicalFeatures.stream()
                    .mapToDouble(f -> f.getNumeric().getZone1()).toArray();
            double[] zone2Series = historicalFeatures.stream()
                    .mapToDouble(f -> f.getNumeric().getZone2()).toArray();
            double[] zone3Series = historicalFeatures.stream()
                    .mapToDouble(f -> f.getNumeric().getZone3()).toArray();
            
            // ARIMA参数
            ArimaOrder order = ArimaOrder.order(config.getArimaP(), config.getArimaD(), config.getArimaQ());
            
            // 使用ARIMA预测各个特征
            predicted.setBigCount((int) Math.round(predictWithArima(bigCountSeries, order)));
            predicted.setOddCount((int) Math.round(predictWithArima(oddCountSeries, order)));
            predicted.setSum((int) Math.round(predictWithArima(sumSeries, order)));
            predicted.setZone1((int) Math.round(predictWithArima(zone1Series, order)));
            predicted.setZone2((int) Math.round(predictWithArima(zone2Series, order)));
            predicted.setZone3((int) Math.round(predictWithArima(zone3Series, order)));
            
            // 其他特征使用移动平均（数据波动较大，ARIMA效果可能不佳）
            predicted.setSameTailCount((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getSameTailCount())));
            predicted.setConsecutiveCount((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getConsecutiveCount())));
            predicted.setRepeatFromLast((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getRepeatFromLast())));
            
            logger.debug("ARIMA预测数值特征: bigCount={}, oddCount={}, sum={}, zone1={}, zone2={}, zone3={}",
                    predicted.getBigCount(), predicted.getOddCount(), predicted.getSum(),
                    predicted.getZone1(), predicted.getZone2(), predicted.getZone3());
            
        } catch (Exception e) {
            logger.warn("ARIMA预测失败，使用移动平均替代: {}", e.getMessage());
            // 如果ARIMA失败，回退到移动平均
            predicted.setBigCount((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getBigCount())));
            predicted.setOddCount((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getOddCount())));
            predicted.setSum((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getSum())));
            predicted.setZone1((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getZone1())));
            predicted.setZone2((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getZone2())));
            predicted.setZone3((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getZone3())));
            predicted.setSameTailCount((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getSameTailCount())));
            predicted.setConsecutiveCount((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getConsecutiveCount())));
            predicted.setRepeatFromLast((int) Math.round(movingAverage(historicalFeatures, 10,
                    f -> (double) f.getNumeric().getRepeatFromLast())));
        }
        
        return predicted;
    }

    /**
     * ARIMA单变量预测
     * @param data 历史时间序列数据
     * @param order ARIMA参数(p,d,q)
     * @return 预测值
     */
    private static double predictWithArima(double[] data, ArimaOrder order) {
        if (data.length < 10) {
            // 数据点太少，使用简单平均
            return Arrays.stream(data).average().orElse(0.0);
        }
        
        try {
            // 创建时间序列
            TimeSeries timeSeries = TimeSeries.from(data);
            
            // 训练ARIMA模型
            Arima model = Arima.model(timeSeries, order);
            
            // 预测下一步
            com.github.signaflo.timeseries.forecast.Forecast forecast = model.forecast(1);
            
            return forecast.pointEstimates().at(0);
            
        } catch (Exception e) {
            logger.warn("ARIMA模型拟合失败，使用最后一个值: {}", e.getMessage());
            return data[data.length - 1];
        }
    }

    /**
     * 移动平均
     */
    private static double movingAverage(List<PeriodFeaturesBo> features, int window,
                                        java.util.function.Function<PeriodFeaturesBo, Double> extractor) {
        int start = Math.max(0, features.size() - window);
        return features.subList(start, features.size()).stream()
                .mapToDouble(extractor::apply)
                .average()
                .orElse(0.0);
    }

    /**
     * 使用马尔可夫链预测离散型特征
     */
    private static CategoricalFeaturesBo predictCategoricalFeatures(List<PeriodFeaturesBo> historicalFeatures) {
        CategoricalFeaturesBo predicted = new CategoricalFeaturesBo();
        
        // 奇偶组合预测
        predicted.setOddEvenPattern(markovPredict(historicalFeatures.stream()
                .map(f -> f.getCategorical().getOddEvenPattern())
                .collect(Collectors.toList())));
        
        // 大小组合预测
        predicted.setBigSmallPattern(markovPredict(historicalFeatures.stream()
                .map(f -> f.getCategorical().getBigSmallPattern())
                .collect(Collectors.toList())));
        
        // 三区分布预测
        predicted.setZonePattern(markovPredict(historicalFeatures.stream()
                .map(f -> f.getCategorical().getZonePattern())
                .collect(Collectors.toList())));
        
        logger.debug("马尔可夫链预测离散特征: oddEvenPattern={}, bigSmallPattern={}, zonePattern={}",
                predicted.getOddEvenPattern(), predicted.getBigSmallPattern(), predicted.getZonePattern());
        
        return predicted;
    }

    /**
     * 马尔可夫链预测（一阶）
     * @param sequence 状态序列
     * @return 预测的下一个状态
     */
    private static String markovPredict(List<String> sequence) {
        if (sequence.isEmpty()) return "";
        if (sequence.size() == 1) return sequence.get(0);
        
        // 构建转移矩阵
        Map<String, Map<String, Integer>> transitions = new HashMap<>();
        for (int i = 0; i < sequence.size() - 1; i++) {
            String current = sequence.get(i);
            String next = sequence.get(i + 1);
            
            transitions.putIfAbsent(current, new HashMap<>());
            transitions.get(current).merge(next, 1, Integer::sum);
        }
        
        // 从最后一个状态预测
        String lastState = sequence.get(sequence.size() - 1);
        Map<String, Integer> nextStates = transitions.get(lastState);
        
        if (nextStates == null || nextStates.isEmpty()) {
            // 没有转移记录，返回最高频状态
            return sequence.stream()
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(lastState);
        }
        
        // 返回概率最大的下一个状态
        return nextStates.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(lastState);
    }

    /**
     * 预测蓝球概率（基于热度和间隔）
     */
    private static Map<Integer, Double> predictBlueProbability(List<HistoryRecord> history) {
        Map<Integer, Double> probabilities = new HashMap<>();
        Map<Integer, Integer> hotness = new HashMap<>();
        Map<Integer, Integer> lastGap = new HashMap<>();
        
        // 统计热度
        for (HistoryRecord record : history) {
            int blue = record.getSpecial();
            hotness.merge(blue, 1, Integer::sum);
        }
        
        // 统计最近间隔
        for (int blue = 1; blue <= 16; blue++) {
            int gap = 0;
            boolean found = false;
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).getSpecial() == blue) {
                    found = true;
                    break;
                }
                gap++;
            }
            lastGap.put(blue, found ? gap : history.size());
        }
        
        // 计算综合概率
        int totalHotness = hotness.values().stream().mapToInt(Integer::intValue).sum();
        for (int blue = 1; blue <= 16; blue++) {
            double hotnessProb = hotness.getOrDefault(blue, 0) / (double) totalHotness;
            double gapProb = lastGap.get(blue) / (double) history.size();  // 间隔越大，概率越高
            
            // 热度权重0.6，间隔权重0.4
            double prob = 0.6 * hotnessProb + 0.4 * gapProb;
            probabilities.put(blue, prob);
        }
        
        logger.debug("蓝球概率预测完成，top3: {}", probabilities.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + "=" + String.format("%.3f", e.getValue()))
                .collect(Collectors.joining(", ")));
        
        return probabilities;
    }

    // ==================== 3. 综合评分 ====================



    /**
     * 统一语义打分：模型分 + 特征区间约束 + 蓝球 + 轻量加成
     * 分层抽样与最终排序共用同一套分数逻辑中的模型分部分。
     */
    public static ScoredCandidateBo scorePrediction(PredictRecord prediction,
                                                 PredictedFeaturesBo predictedFeatures,
                                                 List<HistoryRecord> history,
                                                 WeightConfigBo config) {
        List<HistoryRecord> chrono = chronological(history);
        List<Integer> redBalls = parseRedBalls(prediction.getRedBalls());
        int blueBall = prediction.getBlueBall();

        List<Integer> lastReds = chrono.isEmpty() ? null : getRedBalls(chrono.get(chrono.size() - 1));
        NumericFeaturesBo actualNumeric = calculateNumericFeatures(redBalls, lastReds);

        double modelScore = prediction.getTotalScore() != null
            ? prediction.getTotalScore().doubleValue()
            : 0.5;

        FeatureRangeBo range = predictedFeatures.getFeatureRange();
        if (range == null) {
            range = buildFeatureRanges(chrono, config.getRangeLowerQuantile(), config.getRangeUpperQuantile());
        }
        double rangeScore = calculateRangeConstraintScore(actualNumeric, redBalls, range);

        Map<Integer, Double> blueProb = predictedFeatures.getBlueProbability();
        if (blueProb == null) {
            blueProb = predictBlueProbability(chrono);
        }
        double blueHotScore = blueProb.getOrDefault(blueBall, 0.0);

        BlueFeaturesBo blueFeature = calculateBlueFeatures(blueBall, chrono, chrono.size());
        double blueGapScore = chrono.isEmpty() ? 0.0
            : blueFeature.getRecentGap() / (double) Math.min(chrono.size(), 50);

        double zoneBalanceScore = calculateZoneBalance(actualNumeric);
        double hotNumberScore = calculateHotNumberBonus(redBalls, chrono);

        // 权重：优先用新字段；若 analyzer 只写了旧字段则回退融合
        double modelW = config.getModelScoreWeight();
        double rangeW = config.getRangeConstraintWeight() > 0
            ? config.getRangeConstraintWeight()
            : (config.getNumFeatureWeight() + config.getCatFeatureWeight()) * 0.5;
        double blueHotW = config.getBlueHotWeight();
        double blueGapW = config.getBlueGapWeight();
        double zoneW = config.getZoneBalanceBonus();
        double hotW = config.getHotNumberBonus();

        double weightSum = modelW + rangeW + blueHotW + blueGapW + zoneW + hotW;
        if (weightSum <= 0) {
            weightSum = 1.0;
        }

        double finalScore = (
            modelW * modelScore
                + rangeW * rangeScore
                + blueHotW * blueHotScore
                + blueGapW * Math.min(blueGapScore, 1.0)
                + zoneW * zoneBalanceScore
                + hotW * Math.min(hotNumberScore / 0.30, 1.0)
        ) / weightSum;

        String explanation = String.format(
            "统一分[%.4f]: 模型=%.4f(w=%.2f), 区间约束=%.3f(w=%.2f,%s), 蓝热=%.3f, 蓝隔=%.3f, 均衡=%.3f, 热号=%.3f | "
                + "实际: sum=%d odd=%d big=%d zone=%d-%d-%d span=%d",
            finalScore, modelScore, modelW, rangeScore, rangeW, range.getSource(),
            blueHotScore, blueGapScore, zoneBalanceScore, hotNumberScore,
            actualNumeric.getSum(), actualNumeric.getOddCount(), actualNumeric.getBigCount(),
            actualNumeric.getZone1(), actualNumeric.getZone2(), actualNumeric.getZone3(),
            Collections.max(redBalls) - Collections.min(redBalls)
        );

        ScoredCandidateBo result = new ScoredCandidateBo();
        result.setResult(prediction);
        result.setFinalScore(finalScore);
        result.setExplanation(explanation);
        return result;
    }

    /**
     * 特征值落在历史分位数区间内得满分，偏离则按距离线性衰减
     */
    private static double calculateRangeConstraintScore(NumericFeaturesBo features,
                                                        List<Integer> redBalls,
                                                        FeatureRangeBo range) {
        int span = Collections.max(redBalls) - Collections.min(redBalls);
        double s = 0.0;
        s += inRangeScore(features.getSum(), range.getSumMin(), range.getSumMax(), 40);
        s += inRangeScore(span, range.getSpanMin(), range.getSpanMax(), 10);
        s += inRangeScore(features.getOddCount(), range.getOddMin(), range.getOddMax(), 3);
        s += inRangeScore(features.getBigCount(), range.getBigMin(), range.getBigMax(), 3);
        s += inRangeScore(features.getZone1(), range.getZone1Min(), range.getZone1Max(), 3);
        s += inRangeScore(features.getZone2(), range.getZone2Min(), range.getZone2Max(), 3);
        s += inRangeScore(features.getZone3(), range.getZone3Min(), range.getZone3Max(), 3);
        s += features.getConsecutiveCount() <= range.getConsecutiveMax() ? 1.0
            : Math.max(0, 1.0 - (features.getConsecutiveCount() - range.getConsecutiveMax()) / 3.0);
        s += features.getSameTailCount() <= range.getSameTailMax() ? 1.0
            : Math.max(0, 1.0 - (features.getSameTailCount() - range.getSameTailMax()) / 3.0);
        return s / 9.0;
    }

    private static double inRangeScore(int value, int min, int max, int softMargin) {
        if (value >= min && value <= max) {
            return 1.0;
        }
        int dist = value < min ? (min - value) : (value - max);
        return Math.max(0.0, 1.0 - dist / (double) Math.max(softMargin, 1));
    }

    /**
     * 改进3: 计算特征范围加成（兼容旧逻辑，主路径已改用 calculateRangeConstraintScore）
     */
    private static double calculateRangeBonus(NumericFeaturesBo features, List<Integer> redBalls) {
        double bonus = 0.0;

        if (features.getBigCount() >= 2 && features.getBigCount() <= 4) {
            bonus += 0.20;
        }
        if (features.getOddCount() >= 3 && features.getOddCount() <= 5) {
            bonus += 0.20;
        }
        if (features.getSum() >= 70 && features.getSum() <= 110) {
            bonus += 0.15;
        }
        int span = Collections.max(redBalls) - Collections.min(redBalls);
        if (span >= 20 && span <= 32) {
            bonus += 0.15;
        }
        if (features.getSameTailCount() >= 0 && features.getSameTailCount() <= 2) {
            bonus += 0.15;
        }
        if (features.getConsecutiveCount() <= 2) {
            bonus += 0.15;
        }
        return bonus;
    }
    
    /**
     * 改进5: 计算中庸度评分（越接近理想范围分数越高，降低极端值惩罚）
     * 根据中奖号码特征调整理想值范围
     */
    private static double calculateModerateScore(NumericFeaturesBo features) {
        double score = 0.0;
        
        // big_count: 理想值 2-4（从单一值2.5扩大到范围2-4）
        if (features.getBigCount() >= 2 && features.getBigCount() <= 4) {
            score += 1.0; // 在理想范围内直接给满分
        } else {
            // 不在理想范围时，按距离中心值3的距离计算，降低惩罚力度
            score += 1.0 - Math.min(1.0, Math.abs(3.0 - features.getBigCount()) / 4.0);
        }
        
        // odd_count: 理想值 3-5（从单一值3.5扩大到范围3-5）
        if (features.getOddCount() >= 3 && features.getOddCount() <= 5) {
            score += 1.0; // 在理想范围内直接给满分
        } else {
            // 不在理想范围时，按距离中心值4的距离计算
            score += 1.0 - Math.min(1.0, Math.abs(4.0 - features.getOddCount()) / 4.0);
        }
        
        // sum: 理想值 70-110（从92.5大幅扩大到70-110范围）
        if (features.getSum() >= 70 && features.getSum() <= 110) {
            score += 1.0; // 在理想范围内直接给满分
        } else {
            // 不在理想范围时，按距离中心值90的距离计算，大幅降低惩罚力度
            score += 1.0 - Math.min(1.0, Math.abs(90.0 - features.getSum()) / 100.0);
        }
        
        // same_tail_count: 理想值 0-2（从1.5扩大到0-2范围）
        if (features.getSameTailCount() >= 0 && features.getSameTailCount() <= 2) {
            score += 1.0; // 在理想范围内直接给满分
        } else {
            // 不在理想范围时，按距离中心值1的距离计算
            score += 1.0 - Math.min(1.0, Math.abs(1.0 - features.getSameTailCount()) / 4.0);
        }
        
        return score / 4.0; // 归一化到 0-1
    }
    
    /**
     * 改进6: 计算区间均衡性评分（三区分布越均衡分数越高）
     */
    private static double calculateZoneBalance(NumericFeaturesBo features) {
        int zone1 = features.getZone1();
        int zone2 = features.getZone2();
        int zone3 = features.getZone3();
        
        // 计算方差（方差越小越均衡）
        double mean = 2.0; // 平均每区2个
        double variance = (Math.pow(zone1 - mean, 2) + 
                          Math.pow(zone2 - mean, 2) + 
                          Math.pow(zone3 - mean, 2)) / 3.0;
        
        // 转换为得分（方差越小分数越高）
        // 最大方差约为 4（极端情况如 6-0-0），归一化
        return Math.max(0, 1.0 - variance / 4.0);
    }
    
    /**
     * 改进4: 计算热号加成
     */
    private static double calculateHotNumberBonus(List<Integer> redBalls, List<HistoryRecord> history) {
        // 统计最近20期的热号（至少出现5次）
        Map<Integer, Integer> hotNumbers = calculateHotNumbers(history, Math.min(20, history.size()));
        
        int hotMatchCount = 0;
        for (int red : redBalls) {
            if (hotNumbers.getOrDefault(red, 0) >= 5) {
                hotMatchCount++;
            }
        }
        
        // 每个热号加成5%
        return hotMatchCount * 0.05;
    }
    
    /**
     * 统计热号
     */
    private static Map<Integer, Integer> calculateHotNumbers(List<HistoryRecord> history, int recentPeriods) {
        Map<Integer, Integer> hotNumbers = new HashMap<>();
        
        int startIndex = Math.max(0, history.size() - recentPeriods);
        for (int i = startIndex; i < history.size(); i++) {
            HistoryRecord record = history.get(i);
            hotNumbers.merge(record.getNum1(), 1, Integer::sum);
            hotNumbers.merge(record.getNum2(), 1, Integer::sum);
            hotNumbers.merge(record.getNum3(), 1, Integer::sum);
            hotNumbers.merge(record.getNum4(), 1, Integer::sum);
            hotNumbers.merge(record.getNum5(), 1, Integer::sum);
            hotNumbers.merge(record.getNum6(), 1, Integer::sum);
        }
        
        return hotNumbers;
    }

    /**
     * 计算数值型特征相似度
     */
    private static double calculateNumericSimilarity(NumericFeaturesBo actual, NumericFeaturesBo predicted) {
        double bigCountSim = 1.0 - Math.abs(actual.getBigCount() - predicted.getBigCount()) / 6.0;
        double oddCountSim = 1.0 - Math.abs(actual.getOddCount() - predicted.getOddCount()) / 6.0;
        double sumSim = 1.0 - Math.abs(actual.getSum() - predicted.getSum()) / 198.0;  // 最大差值198
        double zone1Sim = 1.0 - Math.abs(actual.getZone1() - predicted.getZone1()) / 6.0;
        double zone2Sim = 1.0 - Math.abs(actual.getZone2() - predicted.getZone2()) / 6.0;
        double zone3Sim = 1.0 - Math.abs(actual.getZone3() - predicted.getZone3()) / 6.0;
        
        return (bigCountSim + oddCountSim + sumSim + zone1Sim + zone2Sim + zone3Sim) / 6.0;
    }

    /**
     * 计算离散型特征匹配度
     */
    private static double calculateCategoricalMatch(CategoricalFeaturesBo actual, CategoricalFeaturesBo predicted) {
        double oddEvenMatch = actual.getOddEvenPattern().equals(predicted.getOddEvenPattern()) ? 1.0 : 0.3;
        double bigSmallMatch = actual.getBigSmallPattern().equals(predicted.getBigSmallPattern()) ? 1.0 : 0.3;
        double zoneMatch = actual.getZonePattern().equals(predicted.getZonePattern()) ? 1.0 : 0.3;
        
        return (oddEvenMatch + bigSmallMatch + zoneMatch) / 3.0;
    }

    /**
     * 按 finalScore 降序贪心选取，强制红球 Jaccard 多样性
     */
    public static List<ScoredCandidateBo> selectDiverseTop(List<ScoredCandidateBo> scoredResults,
                                                        int buyCount,
                                                        double diversityThreshold) {
        if (scoredResults == null || scoredResults.isEmpty() || buyCount <= 0) {
            return Collections.emptyList();
        }
        List<ScoredCandidateBo> sorted = scoredResults.stream()
            .sorted(Comparator.comparing(ScoredCandidateBo::getFinalScore).reversed())
            .toList();

        List<ScoredCandidateBo> selected = new ArrayList<>();
        for (ScoredCandidateBo candidate : sorted) {
            if (selected.size() >= buyCount) {
                break;
            }
            Set<Integer> candReds = new HashSet<>(parseRedBalls(candidate.getResult().getRedBalls()));
            boolean tooSimilar = false;
            for (ScoredCandidateBo picked : selected) {
                Set<Integer> pickedReds = new HashSet<>(parseRedBalls(picked.getResult().getRedBalls()));
                if (jaccard(candReds, pickedReds) >= diversityThreshold) {
                    tooSimilar = true;
                    break;
                }
            }
            if (!tooSimilar) {
                selected.add(candidate);
            }
        }
        // 若多样性过严导致不足，用高分补齐
        if (selected.size() < buyCount) {
            for (ScoredCandidateBo candidate : sorted) {
                if (selected.size() >= buyCount) {
                    break;
                }
                if (!selected.contains(candidate)) {
                    selected.add(candidate);
                }
            }
        }
        logger.info("多样性选号: 候选={}, 阈值={}, 选出={}",
            sorted.size(), diversityThreshold, selected.size());
        return selected;
    }

    private static double jaccard(Set<Integer> a, Set<Integer> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<Integer> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<Integer> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    /**
     * 兼容旧接口：按分数取 Top（无多样性）
     */
    public static List<PredictRecord> selectTopCombinations(List<ScoredCandidateBo> scoredResults, int buyCount) {
        return selectDiverseTop(scoredResults, buyCount, 1.0).stream().map(scored -> {
            PredictRecord result = scored.getResult();
            result.setTotalScore(BigDecimal.valueOf(scored.getFinalScore()).setScale(4, RoundingMode.HALF_UP));
            result.setExplanation(scored.getExplanation());
            return result;
        }).toList();
    }

    // ==================== 辅助方法 ====================

    /**
     * 从HistoryRecord提取红球
     */
    private static List<Integer> getRedBalls(HistoryRecord record) {
        return Arrays.asList(
                record.getNum1(), record.getNum2(), record.getNum3(),
                record.getNum4(), record.getNum5(), record.getNum6()
        );
    }

    /**
     * 解析红球字符串
     */
    private static List<Integer> parseRedBalls(String redBalls) {
        if (redBalls == null || redBalls.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(redBalls.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

}

