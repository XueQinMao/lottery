package com.my.project.service.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LotteryPatternTrendUtils
 *
 * <p>形态（奇偶比 / 大小比 / 质合比）遗漏与超额指数工具。
 * <ul>
 *   <li>遗漏值：该形态距上次出现的期数，出现当期为 0</li>
 *   <li>平均遗漏 = (总期数 - 出现次数) / 出现次数</li>
 *   <li>理论概率 p = C(池A, k) × C(池B, 6-k) / C(33, 6)</li>
 *   <li>指数（当期）= 实际出现次数 − 理论出现次数（n × p）</li>
 *   <li>指数序列：命中 +(1-p)，未命中 −p，从样本起点累计</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/14
 **/
public final class LotteryPatternTrendUtils {

    public static final int RED_TOTAL = 33;
    public static final int RED_DRAW = 6;
    /** C(33, 6) */
    public static final long C_33_6 = 1_107_568L;

    public static final int ODD_POOL = 17;
    public static final int EVEN_POOL = 16;
    public static final int BIG_POOL = 17;
    public static final int SMALL_POOL = 16;
    public static final int PRIME_POOL = 12;
    public static final int COMPOSITE_POOL = 21;

    /**
     * 红球质数（走势图口径：01 计为质数）。
     * <p>双色球质合走势把 01、02、03、05、07、11、13、17、19、23、29、31 共 12 个号算质数，
     * 其余 21 个为合数。这与数论上「1 不是质数」不同，但与常见走势图一致。
     */
    public static final Set<Integer> RED_PRIMES = Set.of(1, 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31);

    private LotteryPatternTrendUtils() {
    }

    public enum FeatureType {
        ODD_EVEN("oddEven", "奇偶比", ODD_POOL, EVEN_POOL),
        BIG_SMALL("bigSmall", "大小比", BIG_POOL, SMALL_POOL),
        PRIME_COMP("primeComp", "质合比", PRIME_POOL, COMPOSITE_POOL);

        private final String code;
        private final String label;
        private final int poolA;
        private final int poolB;

        FeatureType(String code, String label, int poolA, int poolB) {
            this.code = code;
            this.label = label;
            this.poolA = poolA;
            this.poolB = poolB;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public static FeatureType fromCode(String code) {
            if (code == null || code.isBlank()) {
                return ODD_EVEN;
            }
            String normalized = code.trim();
            for (FeatureType type : values()) {
                if (type.code.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("feature 仅支持 oddEven / bigSmall / primeComp");
        }
    }

    /**
     * 解析比例字符串，返回左侧个数 k（如 "1:5" → 1）。两侧之和必须为 6。
     */
    public static int parseRatioLeft(String ratio) {
        if (ratio == null || ratio.isBlank()) {
            throw new IllegalArgumentException("ratio 不能为空，格式如 1:5");
        }
        String[] parts = ratio.trim().replace('：', ':').split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("ratio 格式错误，应为 k:(6-k)，如 1:5");
        }
        int left;
        int right;
        try {
            left = Integer.parseInt(parts[0].trim());
            right = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ratio 必须为整数，如 1:5");
        }
        if (left < 0 || right < 0 || left + right != RED_DRAW) {
            throw new IllegalArgumentException("ratio 两侧之和必须为 6，且均 ≥ 0");
        }
        return left;
    }

    public static String toRatio(int left) {
        return left + ":" + (RED_DRAW - left);
    }

    public static long combination(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        if (k == 0 || k == n) {
            return 1;
        }
        int r = Math.min(k, n - k);
        long result = 1;
        for (int i = 0; i < r; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /**
     * 理论概率：从池 A 抽 k 个、从池 B 抽 6-k 个。
     */
    public static double theoreticalProb(FeatureType type, int leftCount) {
        int right = RED_DRAW - leftCount;
        if (leftCount < 0 || leftCount > RED_DRAW) {
            return 0;
        }
        if (leftCount > type.poolA || right > type.poolB) {
            return 0;
        }
        long ways = combination(type.poolA, leftCount) * combination(type.poolB, right);
        return (double) ways / C_33_6;
    }

    public static int countFeature(List<Integer> reds, FeatureType type) {
        if (reds == null) {
            return 0;
        }
        int count = 0;
        for (Integer ball : reds) {
            if (ball == null) {
                continue;
            }
            if (matchesLeftPool(ball, type)) {
                count++;
            }
        }
        return count;
    }

    public static boolean matchesLeftPool(int ball, FeatureType type) {
        return switch (type) {
            case ODD_EVEN -> ball % 2 == 1;
            case BIG_SMALL -> ball >= 17;
            case PRIME_COMP -> RED_PRIMES.contains(ball);
        };
    }

    /**
     * 每期是否命中指定比例（最旧 → 最新）。
     */
    public static List<Boolean> matchSequence(List<List<Integer>> draws, FeatureType type, int leftCount) {
        List<Boolean> hits = new ArrayList<>(draws.size());
        for (List<Integer> reds : draws) {
            hits.add(countFeature(reds, type) == leftCount);
        }
        return hits;
    }

    /**
     * 各比例在样本中的出现次数（key 为 "k:(6-k)"）。
     */
    public static Map<String, Integer> countRatios(List<List<Integer>> draws, FeatureType type) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int k = 0; k <= RED_DRAW; k++) {
            counts.put(toRatio(k), 0);
        }
        for (List<Integer> reds : draws) {
            String ratio = toRatio(countFeature(reds, type));
            counts.merge(ratio, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 遗漏序列：命中当期为 0；窗口内尚未出现过则从 1 累加。
     */
    public static List<Integer> calcOmissionSequence(List<Boolean> hits) {
        List<Integer> omissions = new ArrayList<>(hits.size());
        int lastHitIndex = -1;
        for (int i = 0; i < hits.size(); i++) {
            if (Boolean.TRUE.equals(hits.get(i))) {
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
     * 累计超额指数：命中 +(1-p)，未命中 −p。
     */
    public static List<Double> calcExcessIndexSequence(List<Boolean> hits, double p) {
        List<Double> indexValues = new ArrayList<>(hits.size());
        double acc = 0;
        double hitDelta = 1 - p;
        for (Boolean hit : hits) {
            acc += Boolean.TRUE.equals(hit) ? hitDelta : -p;
            indexValues.add(round4(acc));
        }
        return indexValues;
    }

    /**
     * 完整分析：遗漏 + 超额指数（不含 5/10/20 期均线）。
     *
     * @param hits 每期是否命中（最旧 → 最新）
     * @param p    该形态的理论概率
     */
    public static PatternTrendResult analyze(List<Boolean> hits, double p) {
        if (hits == null || hits.isEmpty()) {
            throw new IllegalArgumentException("命中序列不能为空");
        }
        double prob = p < 0 ? 0 : p;
        List<Integer> omissions = calcOmissionSequence(hits);
        List<Double> indexValues = calcExcessIndexSequence(hits, prob);
        int total = hits.size();
        int hitCount = 0;
        for (Boolean hit : hits) {
            if (Boolean.TRUE.equals(hit)) {
                hitCount++;
            }
        }
        int maxOmission = 0;
        for (int o : omissions) {
            if (o > maxOmission) {
                maxOmission = o;
            }
        }
        int currentOmission = omissions.get(total - 1);
        double avgOmission = hitCount > 0 ? (double) (total - hitCount) / hitCount : 0;
        double theoreticalHits = total * prob;
        double index = hitCount - theoreticalHits;

        PatternTrendStats stats = new PatternTrendStats();
        stats.setMaxOmission(maxOmission);
        stats.setAvgOmission(round2(avgOmission));
        stats.setCurrentOmission(currentOmission);
        stats.setHitCount(hitCount);
        stats.setTotalPeriods(total);
        stats.setTheoreticalProb(round6(prob));
        stats.setTheoreticalHits(round2(theoreticalHits));
        stats.setIndex(round2(index));

        PatternTrendResult result = new PatternTrendResult();
        result.setHits(hits);
        result.setOmissions(omissions);
        result.setIndexValues(indexValues);
        result.setStats(stats);
        return result;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    public static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    @Data
    public static class PatternTrendStats {
        private int maxOmission;
        private double avgOmission;
        private int currentOmission;
        private int hitCount;
        private int totalPeriods;
        /** 理论概率 p */
        private double theoreticalProb;
        /** n × p */
        private double theoreticalHits;
        /** 实际次数 − 理论次数 */
        private double index;
    }

    @Data
    public static class PatternTrendResult {
        private List<Boolean> hits;
        private List<Integer> omissions;
        private List<Double> indexValues;
        private PatternTrendStats stats;
    }
}
