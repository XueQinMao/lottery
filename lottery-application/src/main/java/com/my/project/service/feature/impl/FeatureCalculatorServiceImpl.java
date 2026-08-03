package com.my.project.service.feature.impl;

import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.selection.pojo.bo.SsqCombinationBo;
import com.my.project.service.feature.IFeatureCalculatorService;
import com.my.project.service.support.SsqCombinationUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


/**
 * FeatureCalculatorServiceImpl
 * 特征数据相关计算
 *
 * <p>注意：自优化版起，<b>特征工程权威实现迁移至 Python 侧 {@code ssq_features.py}</b>。
 * 本类仍负责生成 history.csv（Python 训练/预测的原始数据源）以及 ml_features /
 * sequence / markov 三个 CSV（用于人工分析与兼容旧链路）。新特征列（AC 值、遗漏值、
 * 重号、012 路、三区分布）与多目标标签已同步加入 ml_features.csv，与
 * {@code ssq_features.FEATURE_COLUMNS / LABEL_COLUMNS} 保持一致。</p>
 *
 * @author 刘强
 * @version 2025/10/23 16:25
 **/
@Service
@Primary
public class FeatureCalculatorServiceImpl implements IFeatureCalculatorService {

    private static final Set<Integer> PRIMES = new HashSet<>(Arrays.asList(
            2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31
    ));

    /** ml_features.csv 特征列（顺序与 ssq_features.FEATURE_COLUMNS 一致） */
    private static final List<String> FEATURE_COLUMNS = Arrays.asList(
            "sum_red", "span_red", "odd_count", "even_count",
            "big_count", "small_count", "hot_hits", "cold_hits",
            "blue_hot", "red_sum_last_diff", "red_max_last_diff",
            "consecutive_count", "same_tail_count",
            // 新增特征
            "ac_value", "repeat_from_last", "max_missing", "avg_missing",
            "blue_missing", "blue_012_road",
            "zone1_count", "zone2_count", "zone3_count"
    );

    /** 多目标标签列（与 ssq_features.LABEL_COLUMNS 一致） */
    private static final List<String> LABEL_COLUMNS = Arrays.asList(
            "label_sum", "label_span", "label_odd_even",
            "label_zone", "label_blue_odd", "label_blue_big"
    );

    private static final int[] SUM_TYPICAL = {90, 120};
    private static final int[] SPAN_TYPICAL = {16, 28};
    private static final Set<Integer> ODD_TYPICAL = new HashSet<>(Arrays.asList(2, 3, 4));

    private final IHistoryRecordRepository historyRecordRepository;

    private final LotteryModelConfig lotteryModelConfig;


    public FeatureCalculatorServiceImpl(IHistoryRecordRepository historyRecordRepository, LotteryModelConfig lotteryModelConfig) {
        this.historyRecordRepository = historyRecordRepository;
        this.lotteryModelConfig = lotteryModelConfig;
    }

    @Override
    public void calculateAndExportFeatures() throws IOException {
        List<HistoryRecord> historyRecords = historyRecordRepository.lambdaQuery().list();
        // 按期号升序，保证时序特征与遗漏值计算正确
        historyRecords.sort(Comparator.comparing(HistoryRecord::getPeriod));
        generateMLFeatures(historyRecords);
        generateSequenceFeatures(historyRecords, 5);
        generateMarkovFeatures(historyRecords);
        generateHistory(historyRecords);
    }

    @Override
    public Map<String, Object> calculateFeature(SsqCombinationBo ssqCombinationBo) {
        return calculateFeatures(ssqCombinationBo);
    }

    private void generateHistory(List<HistoryRecord> historyRecords) throws IOException{
        File output = new File(lotteryModelConfig.getPath().concat(lotteryModelConfig.getCsv().getOrDefault("history", "history.csv")));
        output.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            writer.write("issue,red1,red2,red3,red4,red5,red6,blue\n");
            historyRecords.forEach(record -> {
                try {
                    writer.write(String.format("%s,%d,%d,%d,%d,%d,%d,%d\n",
                            record.getPeriod(),
                            record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(), record.getNum6(),
                            record.getSpecial()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        }
    }

    /**
     * 生成Markov状态转移特征文件
     */
    private void generateMarkovFeatures(List<HistoryRecord> history) throws IOException {

        File output = new File(lotteryModelConfig.getPath().concat(lotteryModelConfig.getCsv().getOrDefault("markov", "markov_features.csv")));
        output.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            writer.write("issue,state_id,prev_state_id,transition_type,");
            writer.write("zone1_count,zone2_count,zone3_count,");
            writer.write("blue_ball,blue_state,label\n");

            for (int i = 1; i < history.size(); i++) {
                HistoryRecord current = history.get(i);
                HistoryRecord prev = history.get(i - 1);

                List<Integer> currentRedBalls = List.of(current.getNum1(), current.getNum2(), current.getNum3(),
                                                        current.getNum4(), current.getNum5(), current.getNum6());
                List<Integer> prevRedBalls = List.of(prev.getNum1(), prev.getNum2(), prev.getNum3(),
                                                     prev.getNum4(), prev.getNum5(), prev.getNum6());

                String line = generateMarkovLine(current.getPeriod(), currentRedBalls, current.getSpecial(),
                                                prevRedBalls, prev.getSpecial(), 1);
                writer.write(line);
            }

            Random random = new Random(42);

            List<SsqCombinationBo> ssqCombinationBos = SsqCombinationUtils.generateRandomDraw(history, (history.size() * 5) + 200);
            for(int i=0;i<ssqCombinationBos.size()-1;i++){
                int idx = 1 + random.nextInt(history.size() - 1);
                HistoryRecord refCurrent = history.get(idx);
                SsqCombinationBo randomCurrent =ssqCombinationBos.get(i);
                SsqCombinationBo randomPrev =ssqCombinationBos.get(i+1);
                String line = generateMarkovLine(refCurrent.getPeriod(), randomCurrent.getRedBalls(),
                        randomCurrent.getBlueBall(), randomPrev.getRedBalls(),
                        randomPrev.getBlueBall(), 0);
                writer.write(line);
            }
        }
    }

    private String generateMarkovLine(String issue, List<Integer> currentRedBalls, int currentBlue,
                                     List<Integer> prevRedBalls, int prevBlue, int label) {
        int[] zones = calculateZones(currentRedBalls);
        int stateId = zones[0] * 100 + zones[1] * 10 + zones[2];

        int[] prevZones = calculateZones(prevRedBalls);
        int prevStateId = prevZones[0] * 100 + prevZones[1] * 10 + prevZones[2];

        int stateDiff = Math.abs(stateId - prevStateId);
        String transitionType;
        if (stateDiff == 0) {
            transitionType = "SAME";
        } else if (stateDiff < 50) {
            transitionType = "STABLE";
        } else if (stateDiff < 150) {
            transitionType = "MODERATE";
        } else {
            transitionType = "DRASTIC";
        }

        String blueState;
        if (currentBlue % 2 == 1) {
            blueState = currentBlue <= 8 ? "ODD_SMALL" : "ODD_BIG";
        } else {
            blueState = currentBlue <= 8 ? "EVEN_SMALL" : "EVEN_BIG";
        }

        return String.format("%s,%d,%d,%s,%d,%d,%d,%d,%s,%d\n",
                issue, stateId, prevStateId, transitionType,
                zones[0], zones[1], zones[2],
                currentBlue, blueState, label);
    }

    private int[] calculateZones(List<Integer> redBalls) {
        int zone1 = 0, zone2 = 0, zone3 = 0;
        for (int ball : redBalls) {
            if (ball >= 1 && ball <= 11) {
                zone1++;
            } else if (ball >= 12 && ball <= 22) {
                zone2++;
            } else if (ball >= 23 && ball <= 33) {
                zone3++;
            }
        }
        return new int[]{zone1, zone2, zone3};
    }

    /**
     * 生成序列特征文件（用于LSTM和Transformer）
     */
    private void generateSequenceFeatures(List<HistoryRecord> history, int lookback) throws IOException {
        File output = new File(lotteryModelConfig.getPath().concat(lotteryModelConfig.getCsv().getOrDefault("sequence", "sequence_features.csv")));
        output.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            StringBuilder header = new StringBuilder("issue");
            for (int i = 1; i <= 6; i++) {
                header.append(",red_").append(i);
            }
            header.append(",blue");
            for (int t = 1; t <= lookback; t++) {
                for (int i = 1; i <= 6; i++) {
                    header.append(",red_").append(i).append("_t").append(t);
                }
                header.append(",blue_t").append(t);
            }
            header.append(",label\n");
            writer.write(header.toString());

            for (int i = lookback; i < history.size(); i++) {
                HistoryRecord current = history.get(i);
                List<Integer> redBalls = List.of(current.getNum1(), current.getNum2(), current.getNum3(),
                                                 current.getNum4(), current.getNum5(), current.getNum6());

                String line = generateSequenceLine(current.getPeriod(), redBalls, current.getSpecial(),
                                                   history, i, lookback, 1);
                writer.write(line);
            }

            List<SsqCombinationBo> ssqCombinationBos = SsqCombinationUtils.generateRandomDraw(history, history.size() * 5);
            Random random = new Random(42);
            for(int i=0;i<ssqCombinationBos.size();i++){
                int idx = lookback + random.nextInt(history.size() - lookback);
                HistoryRecord refRecord = history.get(idx);
                SsqCombinationBo randomCombo = ssqCombinationBos.get(i);
                String line = generateSequenceLine(refRecord.getPeriod(), randomCombo.getRedBalls(),
                        randomCombo.getBlueBall(), history, idx, lookback, 0);
                writer.write(line);
            }
        }
    }

    private String generateSequenceLine(String issue, List<Integer> currentRedBalls, int currentBlue,
                                       List<HistoryRecord> history, int currentIdx, int lookback, int label) {
        StringBuilder line = new StringBuilder(issue);

        for (int red : currentRedBalls) {
            line.append(",").append(red);
        }
        line.append(",").append(currentBlue);

        for (int t = 1; t <= lookback; t++) {
            HistoryRecord prev = history.get(currentIdx - t);
            List<Integer> prevRedBalls = List.of(prev.getNum1(), prev.getNum2(), prev.getNum3(),
                                                 prev.getNum4(), prev.getNum5(), prev.getNum6());

            for (int red : prevRedBalls) {
                line.append(",").append(red);
            }
            line.append(",").append(prev.getSpecial());
        }

        line.append(",").append(label).append("\n");

        return line.toString();
    }


    /**
     * 生成ML特征文件（用于传统机器学习模型）
     * 含完整 22 维特征 + 6 个多目标标签（与 ssq_features.py 对齐）
     */
    private void generateMLFeatures(List<HistoryRecord> history) throws IOException {
        File output = new File(lotteryModelConfig.getPath().concat(lotteryModelConfig.getCsv().getOrDefault("ml", "ml_features.csv")));
        output.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            // 表头：特征列 + 标签列
            writer.write(String.join(",", FEATURE_COLUMNS));
            writer.write(",");
            writer.write(String.join(",", LABEL_COLUMNS));
            writer.write("\n");

            // 正样本：每期真实开奖，动态特征基于该期之前的 history 计算
            for (int i = 1; i < history.size(); i++) {
                HistoryRecord draw = history.get(i);
                HistoryRecord prevDraw = history.get(i - 1);
                List<Integer> redBalls = List.of(draw.getNum1(), draw.getNum2(), draw.getNum3(), draw.getNum4(), draw.getNum5(), draw.getNum6());
                List<Integer> prevDrawRedBalls = List.of(prevDraw.getNum1(), prevDraw.getNum2(), prevDraw.getNum3(), prevDraw.getNum4(), prevDraw.getNum5(), prevDraw.getNum6());
                Map<Integer, Integer> redMissing = computeRedMissing(history, i);
                Map<Integer, Integer> blueMissing = computeBlueMissing(history, i);
                Map<String, Integer> features = calculateMLFeatures(
                        SsqCombinationBo.of(redBalls, draw.getSpecial()),
                        SsqCombinationBo.of(prevDrawRedBalls, prevDraw.getSpecial()),
                        redMissing, blueMissing);
                Map<String, Integer> labels = computeLabels(redBalls, draw.getSpecial());
                writer.write(formatMLFeatureLine(features, labels));
            }

            // 负样本：随机号码
            List<SsqCombinationBo> ssqCombinationBos = SsqCombinationUtils.generateRandomDraw(history, (history.size() * 5)+200);
            for(int i=0;i<ssqCombinationBos.size()-1;i++){
                SsqCombinationBo ssqCombinationBo1 = ssqCombinationBos.get(i);
                SsqCombinationBo ssqCombinationBo2 = ssqCombinationBos.get(i+1);
                // 负样本动态特征借用一个随机历史位置的上下文
                int idx = 1 + new Random(42).nextInt(history.size() - 1);
                Map<Integer, Integer> redMissing = computeRedMissing(history, idx);
                Map<Integer, Integer> blueMissing = computeBlueMissing(history, idx);
                Map<String, Integer> features = calculateMLFeatures(ssqCombinationBo1, ssqCombinationBo2, redMissing, blueMissing);
                Map<String, Integer> labels = computeLabels(ssqCombinationBo1.getRedBalls(), ssqCombinationBo1.getBlueBall());
                writer.write(formatMLFeatureLine(features, labels));
            }
        }
    }


    /**
     * 计算 ML 特征（含新特征）。需要 prev 与 missing 上下文。
     */
    private Map<String, Integer> calculateMLFeatures(SsqCombinationBo current, SsqCombinationBo prev,
                                                     Map<Integer, Integer> redMissing,
                                                     Map<Integer, Integer> blueMissing) {
        Map<String, Integer> features = new HashMap<>();
        List<Integer> redBalls = current.getRedBalls();
        List<Integer> prevReadBalls = prev.getRedBalls();

        int sumRed = redBalls.stream().mapToInt(Integer::intValue).sum();
        features.put("sum_red", sumRed);
        features.put("span_red", Collections.max(redBalls) - Collections.min(redBalls));

        int oddCount = (int) redBalls.stream().filter(n -> n % 2 == 1).count();
        features.put("odd_count", oddCount);
        features.put("even_count", 6 - oddCount);

        int bigCount = (int) redBalls.stream().filter(n -> n >= 17).count();
        features.put("big_count", bigCount);
        features.put("small_count", 6 - bigCount);

        int hotHits = (int) redBalls.stream().filter(PRIMES::contains).count();
        features.put("hot_hits", hotHits);
        features.put("cold_hits", 6 - hotHits);

        features.put("blue_hot", (current.getBlueBall() % 2 == 0) ? 3 : 2);

        int prevSum = prevReadBalls.stream().mapToInt(Integer::intValue).sum();
        features.put("red_sum_last_diff", sumRed - prevSum);
        features.put("red_max_last_diff", Collections.max(redBalls) - Collections.max(prevReadBalls));

        features.put("consecutive_count", countConsecutive(redBalls));
        features.put("same_tail_count", countSameTail(redBalls));

        // ---- 新增特征 ----
        features.put("ac_value", computeAcValue(redBalls));
        features.put("repeat_from_last", countRepeatFromLast(redBalls, prevReadBalls));

        int maxMissing = redBalls.stream().mapToInt(b -> redMissing.getOrDefault(b, 0)).max().orElse(0);
        int avgMissing = (int) Math.round(redBalls.stream().mapToInt(b -> redMissing.getOrDefault(b, 0)).average().orElse(0));
        features.put("max_missing", maxMissing);
        features.put("avg_missing", avgMissing);
        features.put("blue_missing", blueMissing.getOrDefault(current.getBlueBall(), 0));
        features.put("blue_012_road", current.getBlueBall() % 3);

        int[] zones = calculateZones(redBalls);
        features.put("zone1_count", zones[0]);
        features.put("zone2_count", zones[1]);
        features.put("zone3_count", zones[2]);

        return features;
    }

    /**
     * 为单个号码组合计算特征（用于预测时 / 兼容旧调用）。
     * 注意：无历史上下文，动态特征（遗漏/重号）置 0。
     * 新协议下预测由 Python 内部计算完整动态特征，本方法仅作兼容。
     */
    public static Map<String, Object> calculateFeatures(SsqCombinationBo combination) {
        List<Integer> redBalls = combination.getRedBalls();
        int blueBall = combination.getBlueBall();

        Map<String, Object> features = new HashMap<>();

        int sumRed = redBalls.stream().mapToInt(Integer::intValue).sum();
        features.put("sum_red", sumRed);
        features.put("span_red", Collections.max(redBalls) - Collections.min(redBalls));

        long oddCount = redBalls.stream().filter(n -> n % 2 == 1).count();
        features.put("odd_count", (int) oddCount);
        features.put("even_count", 6 - (int) oddCount);

        long bigCount = redBalls.stream().filter(n -> n >= 17).count();
        features.put("big_count", (int) bigCount);
        features.put("small_count", 6 - (int) bigCount);

        long hotHits = redBalls.stream().filter(PRIMES::contains).count();
        features.put("hot_hits", (int) hotHits);
        features.put("cold_hits", 6 - (int) hotHits);

        features.put("blue_hot", (blueBall % 2 == 0) ? 3 : 2);
        features.put("red_sum_last_diff", 0);
        features.put("red_max_last_diff", 0);
        features.put("consecutive_count", countConsecutive(redBalls));
        features.put("same_tail_count", countSameTail(redBalls));

        // 新增静态特征
        features.put("ac_value", computeAcValue(redBalls));
        features.put("repeat_from_last", 0);
        features.put("max_missing", 0);
        features.put("avg_missing", 0);
        features.put("blue_missing", 0);
        features.put("blue_012_road", blueBall % 3);

        int[] zones = calculateZonesStatic(redBalls);
        features.put("zone1_count", zones[0]);
        features.put("zone2_count", zones[1]);
        features.put("zone3_count", zones[2]);

        return features;
    }

    /**
     * 计算多目标标签
     */
    private Map<String, Integer> computeLabels(List<Integer> redBalls, int blueBall) {
        Map<String, Integer> labels = new HashMap<>();
        int sumRed = redBalls.stream().mapToInt(Integer::intValue).sum();
        int spanRed = Collections.max(redBalls) - Collections.min(redBalls);
        int oddCount = (int) redBalls.stream().filter(n -> n % 2 == 1).count();
        int[] zones = calculateZones(redBalls);

        labels.put("label_sum", (sumRed >= SUM_TYPICAL[0] && sumRed <= SUM_TYPICAL[1]) ? 1 : 0);
        labels.put("label_span", (spanRed >= SPAN_TYPICAL[0] && spanRed <= SPAN_TYPICAL[1]) ? 1 : 0);
        labels.put("label_odd_even", ODD_TYPICAL.contains(oddCount) ? 1 : 0);
        labels.put("label_zone", (zones[0] >= 1 && zones[0] <= 4
                && zones[1] >= 1 && zones[1] <= 4
                && zones[2] >= 1 && zones[2] <= 4) ? 1 : 0);
        labels.put("label_blue_odd", (blueBall % 2 == 1) ? 1 : 0);
        labels.put("label_blue_big", (blueBall >= 9) ? 1 : 0);
        return labels;
    }

    /**
     * 计算红球遗漏字典：基于 history[0, upToIdx) 的出现情况
     */
    private Map<Integer, Integer> computeRedMissing(List<HistoryRecord> history, int upToIdx) {
        Map<Integer, Integer> lastSeen = new HashMap<>();
        for (int i = 0; i < upToIdx; i++) {
            HistoryRecord r = history.get(i);
            lastSeen.put(r.getNum1(), i);
            lastSeen.put(r.getNum2(), i);
            lastSeen.put(r.getNum3(), i);
            lastSeen.put(r.getNum4(), i);
            lastSeen.put(r.getNum5(), i);
            lastSeen.put(r.getNum6(), i);
        }
        Map<Integer, Integer> missing = new HashMap<>();
        for (int b = 1; b <= 33; b++) {
            int last = lastSeen.getOrDefault(b, -1);
            missing.put(b, upToIdx - 1 - last);
        }
        return missing;
    }

    private Map<Integer, Integer> computeBlueMissing(List<HistoryRecord> history, int upToIdx) {
        Map<Integer, Integer> lastSeen = new HashMap<>();
        for (int i = 0; i < upToIdx; i++) {
            lastSeen.put(history.get(i).getSpecial(), i);
        }
        Map<Integer, Integer> missing = new HashMap<>();
        for (int b = 1; b <= 16; b++) {
            int last = lastSeen.getOrDefault(b, -1);
            missing.put(b, upToIdx - 1 - last);
        }
        return missing;
    }

    /**
     * AC 值 = 不同差值个数 - (n-1)
     */
    private static int computeAcValue(List<Integer> redBalls) {
        List<Integer> sorted = new ArrayList<>(redBalls);
        Collections.sort(sorted);
        Set<Integer> diffs = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                diffs.add(sorted.get(j) - sorted.get(i));
            }
        }
        return Math.max(diffs.size() - (6 - 1), 0);
    }

    private static int countRepeatFromLast(List<Integer> redBalls, List<Integer> prevRedBalls) {
        if (prevRedBalls == null || prevRedBalls.isEmpty()) {
            return 0;
        }
        Set<Integer> prevSet = new HashSet<>(prevRedBalls);
        return (int) redBalls.stream().filter(prevSet::contains).count();
    }

    private static int[] calculateZonesStatic(List<Integer> redBalls) {
        int zone1 = 0, zone2 = 0, zone3 = 0;
        for (int ball : redBalls) {
            if (ball >= 1 && ball <= 11) {
                zone1++;
            } else if (ball >= 12 && ball <= 22) {
                zone2++;
            } else if (ball >= 23 && ball <= 33) {
                zone3++;
            }
        }
        return new int[]{zone1, zone2, zone3};
    }

    private static int countConsecutive(List<Integer> balls) {
        List<Integer> sorted = new ArrayList<>(balls);
        Collections.sort(sorted);

        int count = 0;
        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i + 1) - sorted.get(i) == 1) {
                count++;
            }
        }
        return count;
    }

    private static int countSameTail(List<Integer> balls) {
        Map<Integer, Integer> tailCount = new HashMap<>();
        for (int ball : balls) {
            int tail = ball % 10;
            tailCount.put(tail, tailCount.getOrDefault(tail, 0) + 1);
        }

        int pairs = 0;
        for (int count : tailCount.values()) {
            if (count > 1) {
                pairs += count - 1;
            }
        }
        return pairs;
    }

    /**
     * 格式化 ML 特征行（特征 + 多目标标签）
     */
    private String formatMLFeatureLine(Map<String, Integer> features, Map<String, Integer> labels) {
        StringBuilder sb = new StringBuilder();
        for (String col : FEATURE_COLUMNS) {
            sb.append(features.getOrDefault(col, 0)).append(",");
        }
        for (int i = 0; i < LABEL_COLUMNS.size(); i++) {
            sb.append(labels.getOrDefault(LABEL_COLUMNS.get(i), 0));
            if (i < LABEL_COLUMNS.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

}
