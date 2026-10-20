package com.my.project.application.feature.csv;

import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.application.support.SsqCombinationUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.my.project.application.feature.csv.TrainingFeatureCalculator.*;

@Component
@RequiredArgsConstructor
public class FeatureCsvExporter {
    private final LotteryModelProperties modelProperties;

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

    public void export(List<HistoryRecord> historyRecords) throws IOException {
        generateMLFeatures(historyRecords);
        generateSequenceFeatures(historyRecords, 5);
        generateMarkovFeatures(historyRecords);
        generateHistory(historyRecords);
    }

    private void generateHistory(List<HistoryRecord> historyRecords) throws IOException{
        File output = new File(modelProperties.getPath().concat(modelProperties.getCsv().getOrDefault("history", "history.csv")));
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

        File output = new File(modelProperties.getPath().concat(modelProperties.getCsv().getOrDefault("markov", "markov_features.csv")));
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

    /**
     * 生成序列特征文件（用于LSTM和Transformer）
     */
    private void generateSequenceFeatures(List<HistoryRecord> history, int lookback) throws IOException {
        File output = new File(modelProperties.getPath().concat(modelProperties.getCsv().getOrDefault("sequence", "sequence_features.csv")));
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
        File output = new File(modelProperties.getPath().concat(modelProperties.getCsv().getOrDefault("ml", "ml_features.csv")));
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


    private String formatMLFeatureLine(Map<String, Integer> features, Map<String, Integer> labels) {
        return Stream.concat(FEATURE_COLUMNS.stream().map(column -> features.getOrDefault(column, 0)),
                LABEL_COLUMNS.stream().map(column -> labels.getOrDefault(column, 0)))
            .map(String::valueOf).collect(Collectors.joining(",", "", "\n"));
    }
}
