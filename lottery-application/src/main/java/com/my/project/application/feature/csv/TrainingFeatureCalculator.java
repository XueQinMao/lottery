package com.my.project.application.feature.csv;

import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TrainingFeatureCalculator {
    private TrainingFeatureCalculator() { }

    private static final Set<Integer> PRIMES = new HashSet<>(Arrays.asList(
            2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31
    ));

    private static final int[] SUM_TYPICAL = {90, 120};
    private static final int[] SPAN_TYPICAL = {16, 28};
    private static final Set<Integer> ODD_TYPICAL = new HashSet<>(Arrays.asList(2, 3, 4));

    /**
     * 计算 ML 特征（含新特征）。需要 prev 与 missing 上下文。
     */
    static Map<String, Integer> calculateMLFeatures(SsqCombinationBo current, SsqCombinationBo prev,
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

        int[] zones = calculateZones(redBalls);
        features.put("zone1_count", zones[0]);
        features.put("zone2_count", zones[1]);
        features.put("zone3_count", zones[2]);

        return features;
    }

    /**
     * 计算多目标标签
     */
    static Map<String, Integer> computeLabels(List<Integer> redBalls, int blueBall) {
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
    static Map<Integer, Integer> computeRedMissing(List<HistoryRecord> history, int upToIdx) {
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

    static Map<Integer, Integer> computeBlueMissing(List<HistoryRecord> history, int upToIdx) {
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

    static int[] calculateZones(List<Integer> redBalls) {
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

}
