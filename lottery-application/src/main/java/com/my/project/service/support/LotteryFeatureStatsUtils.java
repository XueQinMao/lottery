package com.my.project.service.support;

import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo.AnyNAnalysis;
import com.my.project.llm.bo.LotteryAnalysisRespBo.AnyNCandidate;
import com.my.project.llm.bo.LotteryAnalysisRespBo.BlueAnalysis;
import com.my.project.llm.bo.LotteryAnalysisRespBo.BlueNeighborFoxTransmit;
import com.my.project.llm.bo.LotteryAnalysisRespBo.ConsecutiveAnalysis;
import com.my.project.llm.bo.LotteryAnalysisRespBo.NeighborFoxTransmit;
import com.my.project.llm.bo.LotteryAnalysisRespBo.SampleOverview;
import com.my.project.llm.bo.LotteryAnalysisRespBo.TailAnalysis;
import com.my.project.llm.bo.LotteryAnalysisRespBo.ThreeDAnalysis;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * LotteryFeatureStatsUtils
 *
 * <p>用 Java 计算特征分析报告中除杀号 / 冷热温 / 三区预测 / 趋势均线 / 形态推算以外的直方图。
 * 连号、邻狐传、蓝球由本工具计算；胆码不再统计。
 *
 * @author 刘强
 * @version 2026/08/13
 **/
public final class LotteryFeatureStatsUtils {

    private static final Set<Integer> RED_PRIMES = Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31);
    private static final Set<Integer> BLUE_PRIMES = Set.of(2, 3, 5, 7, 11, 13);
    private static final int TOP_N = 10;


    public static LotteryAnalysisRespBo analyze(List<DrawRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("分析样本不能为空");
        }
        List<List<Integer>> redSets = new ArrayList<>(records.size());
        List<Integer> blues = new ArrayList<>(records.size());
        for (DrawRecord r : records) {
            List<Integer> reds = sortedReds(r);
            if (reds.size() != 6 || r.getBlueBall() == null) {
                continue;
            }
            redSets.add(reds);
            blues.add(r.getBlueBall());
        }
        if (redSets.isEmpty()) {
            throw new IllegalArgumentException("有效开奖样本为空");
        }

        LotteryAnalysisRespBo result = new LotteryAnalysisRespBo();
        fillRedStats(result, redSets);
        result.setBlue(buildBlue(blues));
        result.setConclusion(buildConclusion(result, redSets.size()));
        return result;
    }

    private static void fillRedStats(LotteryAnalysisRespBo result, List<List<Integer>> redSets) {
        int n = redSets.size();
        Map<String, Integer> oddEven = new HashMap<>();
        Map<String, Integer> bigSmall = new HashMap<>();
        Map<String, Integer> primeComp = new HashMap<>();
        Map<String, Integer> ratio012 = new HashMap<>();
        Map<String, Integer> spanMap = new HashMap<>();
        Map<String, Integer> sumRange = new HashMap<>();
        Map<String, Integer> sumTail = initCountMap(0, 9);
        Map<String, Integer> sumDigit = new HashMap<>();
        Map<String, Integer> threeZone = new HashMap<>();
        Map<String, Integer> zone1 = initCountMap(0, 6);
        Map<String, Integer> zone2 = initCountMap(0, 6);
        Map<String, Integer> zone3 = initCountMap(0, 6);
        Map<String, Integer> tailValue = initCountMap(0, 9);
        Map<String, Integer> sameTail = initCountMap(0, 3);
        Map<String, Integer> ones = initCountMap(0, 9);
        Map<String, Integer> tens = initCountMap(0, 3);
        Map<String, Integer> hundreds = initCountMap(0, 3);
        Map<String, Integer> consecType = new HashMap<>();
        Map<String, Integer> hotConsec = new HashMap<>();

        int sumTotal = 0;
        int spanTotal = 0;
        int oddTotal = 0;
        int bigTotal = 0;

        for (List<Integer> reds : redSets) {
            int min = reds.getFirst();
            int max = reds.getLast();
            int span = max - min;
            int sum = 0;
            int odd = 0;
            int big = 0;
            int prime = 0;
            int r0 = 0;
            int r1 = 0;
            int r2 = 0;
            int z1 = 0;
            int z2 = 0;
            int z3 = 0;
            Map<Integer, Integer> tailGroups = new HashMap<>();

            for (int ball : reds) {
                sum += ball;
                if (ball % 2 == 1) {
                    odd++;
                }
                if (ball >= 17) {
                    big++;
                }
                if (RED_PRIMES.contains(ball)) {
                    prime++;
                }
                int mod = ball % 3;
                if (mod == 0) {
                    r0++;
                } else if (mod == 1) {
                    r1++;
                } else {
                    r2++;
                }
                if (ball <= 11) {
                    z1++;
                } else if (ball <= 22) {
                    z2++;
                } else {
                    z3++;
                }
                inc(tailValue, String.valueOf(ball % 10));
                inc(ones, String.valueOf(ball % 10));
                inc(tens, String.valueOf((ball / 10) % 10));
                inc(hundreds, String.valueOf(ball / 100));
                tailGroups.merge(ball % 10, 1, Integer::sum);
            }

            sumTotal += sum;
            spanTotal += span;
            oddTotal += odd;
            bigTotal += big;

            inc(oddEven, odd + ":" + (6 - odd));
            inc(bigSmall, big + ":" + (6 - big));
            inc(primeComp, prime + ":" + (6 - prime));
            inc(ratio012, r0 + ":" + r1 + ":" + r2);
            inc(spanMap, String.valueOf(span));
            inc(sumRange, LotteryFeatureTrendUtils.extract(reds, FeatureKind.SUM_RANGE));
            inc(sumTail, String.valueOf(sum % 10));
            inc(sumDigit, sum < 100 ? "2位" : "3位");
            inc(threeZone, z1 + ":" + z2 + ":" + z3);
            inc(zone1, String.valueOf(z1));
            inc(zone2, String.valueOf(z2));
            inc(zone3, String.valueOf(z3));

            int sameTailGroups = 0;
            for (int c : tailGroups.values()) {
                if (c >= 2) {
                    sameTailGroups++;
                }
            }
            inc(sameTail, String.valueOf(Math.min(sameTailGroups, 3)));

            inc(consecType, classifyConsecutive(reds, hotConsec));
        }

        NeighborFoxTransmit nft = calcRedNeighborFox(redSets);

        SampleOverview overview = new SampleOverview();
        overview.setTotalCount(n);
        overview.setAvgSum(round2(sumTotal * 1.0 / n));
        overview.setAvgSpan(round2(spanTotal * 1.0 / n));
        overview.setAvgOddEven(round2(oddTotal * 1.0 / n) + ":" + round2((n * 6 - oddTotal) * 1.0 / n));
        overview.setAvgBigSmall(round2(bigTotal * 1.0 / n) + ":" + round2((n * 6 - bigTotal) * 1.0 / n));
        result.setSampleOverview(overview);

        result.setOddEvenRatio(sortedByCount(oddEven));
        result.setBigSmallRatio(sortedByCount(bigSmall));
        result.setPrimeCompositeRatio(sortedByCount(primeComp));
        result.setRatio012(sortedByCount(ratio012));
        result.setSpan(new TreeMap<>(spanMap));
        TreeMap<String, Integer> sortedSumRange = new TreeMap<>(Comparator.comparingInt(LotteryFeatureStatsUtils::rangeStart));
        sortedSumRange.putAll(sumRange);
        result.setSumRange(sortedSumRange);
        result.setSumTail(sortedKey(sumTail));
        result.setSumDigit(sortedByCount(sumDigit));
        result.setThreeZoneRatio(sortedByCount(threeZone));
        result.setZone1Count(sortedKey(zone1));
        result.setZone2Count(sortedKey(zone2));
        result.setZone3Count(sortedKey(zone3));

        TailAnalysis tail = new TailAnalysis();
        tail.setTailValue(sortedKey(tailValue));
        tail.setSameTailGroupCount(sortedKey(sameTail));
        ThreeDAnalysis threeD = new ThreeDAnalysis();
        threeD.setOnesDigit(sortedKey(ones));
        threeD.setTensDigit(sortedKey(tens));
        threeD.setHundredsDigit(sortedKey(hundreds));
        tail.setThreeD(threeD);
        result.setTail(tail);

        ConsecutiveAnalysis consecutive = new ConsecutiveAnalysis();
        consecutive.setConsecutiveType(sortedByCount(consecType));
        consecutive.setHotConsecutive(topMap(hotConsec, TOP_N));
        result.setConsecutive(consecutive);

        result.setNeighborFoxTransmit(nft);
    }

    /**
     * 红球邻狐传必须按时间正序（最旧→最新），本期相对上一期。
     * 上游样本通常是降序，这里只对副本 reverse，算完丢弃，不改动原 list。
     */
    private static NeighborFoxTransmit calcRedNeighborFox(List<List<Integer>> redSets) {
        List<List<Integer>> chronological = new ArrayList<>(redSets);
        Collections.reverse(chronological);

        Map<String, Integer> nftRatio = new HashMap<>();
        Map<String, Integer> neighborCnt = initCountMap(0, 6);
        Map<String, Integer> foxCnt = initCountMap(0, 6);
        Map<String, Integer> repeatCnt = initCountMap(0, 6);
        List<Integer> prev = null;
        for (List<Integer> reds : chronological) {
            if (prev != null) {
                int neighbor = 0;
                int fox = 0;
                int repeat = 0;
                for (int ball : reds) {
                    if (prev.contains(ball)) {
                        repeat++;
                    } else if (isNeighbor(ball, prev)) {
                        neighbor++;
                    } else {
                        fox++;
                    }
                }
                inc(nftRatio, neighbor + ":" + fox + ":" + repeat);
                inc(neighborCnt, String.valueOf(neighbor));
                inc(foxCnt, String.valueOf(fox));
                inc(repeatCnt, String.valueOf(repeat));
            }
            prev = reds;
        }

        NeighborFoxTransmit nft = new NeighborFoxTransmit();
        nft.setNeighborFoxTransmitRatio(sortedByCount(nftRatio));
        nft.setNeighborCount(sortedKey(neighborCnt));
        nft.setFoxCount(sortedKey(foxCnt));
        nft.setRepeatCount(sortedKey(repeatCnt));
        return nft;
    }

    private static BlueAnalysis buildBlue(List<Integer> blues) {
        int n = blues.size();
        Map<String, Integer> oddEven = new HashMap<>();
        Map<String, Integer> bigSmall = new HashMap<>();
        Map<String, Integer> bigSmallOddEven = new HashMap<>();
        Map<String, Integer> primeComp = new HashMap<>();
        Map<String, Integer> ratio012 = new HashMap<>();
        Map<String, Integer> tailValue = initCountMap(0, 9);
        Map<String, Integer> tailBigSmall = new HashMap<>();
        Map<String, Integer> tailOddEven = new HashMap<>();
        Map<String, Integer> tail012 = new HashMap<>();
        Map<String, Integer> fourZone = new HashMap<>();
        Map<String, Integer> any1 = new HashMap<>();

        int neighbor = 0;
        int fox = 0;
        int repeat = 0;
        Integer prev = null;
        for (int b : blues) {
            inc(oddEven, b % 2 == 1 ? "奇" : "偶");
            inc(bigSmall, b >= 9 ? "大" : "小");
            inc(bigSmallOddEven, LotteryFeatureTrendUtils.extractBlue(b, FeatureKind.BLUE_BIG_SMALL_ODD_EVEN));
            inc(primeComp, BLUE_PRIMES.contains(b) ? "质" : "合");
            int mod = b % 3;
            inc(ratio012, mod == 0 ? "0路" : (mod == 1 ? "1路" : "2路"));
            int tail = b % 10;
            inc(tailValue, String.valueOf(tail));
            inc(tailBigSmall, tail <= 4 ? "小" : "大");
            inc(tailOddEven, tail % 2 == 1 ? "奇" : "偶");
            int tMod = tail % 3;
            inc(tail012, tMod == 0 ? "0路" : (tMod == 1 ? "1路" : "2路"));
            String zone;
            if (b <= 4) {
                zone = "一区";
            } else if (b <= 8) {
                zone = "二区";
            } else if (b <= 12) {
                zone = "三区";
            } else {
                zone = "四区";
            }
            inc(fourZone, zone);
            inc(any1, String.valueOf(b));

            if (prev != null) {
                int diff = Math.abs(b - prev);
                if (diff == 0) {
                    repeat++;
                } else if (diff == 1) {
                    neighbor++;
                } else {
                    fox++;
                }
            }
            prev = b;
        }

        BlueNeighborFoxTransmit nft = new BlueNeighborFoxTransmit();
        Map<String, Integer> nftRatio = new LinkedHashMap<>();
        nftRatio.put("邻", neighbor);
        nftRatio.put("狐", fox);
        nftRatio.put("传", repeat);
        nft.setNeighborFoxTransmitRatio(nftRatio);
        nft.setNeighborCount(neighbor);
        nft.setFoxCount(fox);
        nft.setRepeatCount(repeat);

        AnyNAnalysis anyN = new AnyNAnalysis();
        anyN.setAny1(topAnyN(any1, n, TOP_N));
        anyN.setAny2(topWindowCombos(blues, 2, TOP_N));
        anyN.setAny3(topWindowCombos(blues, 3, TOP_N));
        anyN.setAny4(topWindowCombos(blues, 4, TOP_N));
        anyN.setAny5(topWindowCombos(blues, 5, TOP_N));

        BlueAnalysis blue = new BlueAnalysis();
        blue.setOddEvenRatio(sortedByCount(oddEven));
        blue.setBigSmallRatio(sortedByCount(bigSmall));
        blue.setBigSmallOddEvenRatio(sortedByCount(bigSmallOddEven));
        blue.setPrimeCompositeRatio(sortedByCount(primeComp));
        blue.setRatio012(sortedByCount(ratio012));
        blue.setTailValue(sortedKey(tailValue));
        blue.setTailBigSmall(sortedByCount(tailBigSmall));
        blue.setTailOddEven(sortedByCount(tailOddEven));
        blue.setTailRatio012(sortedByCount(tail012));
        blue.setFourZone(sortedByCount(fourZone));
        blue.setNeighborFoxTransmit(nft);
        blue.setAnyN(anyN);
        return blue;
    }

    private static String classifyConsecutive(List<Integer> reds, Map<String, Integer> hotConsec) {
        List<List<Integer>> runs = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        current.add(reds.getFirst());
        for (int i = 1; i < reds.size(); i++) {
            if (reds.get(i) == reds.get(i - 1) + 1) {
                current.add(reds.get(i));
            } else {
                if (current.size() >= 2) {
                    runs.add(current);
                }
                current = new ArrayList<>();
                current.add(reds.get(i));
            }
        }
        if (current.size() >= 2) {
            runs.add(current);
        }
        for (List<Integer> run : runs) {
            for (int i = 0; i < run.size() - 1; i++) {
                inc(hotConsec, run.get(i) + "," + run.get(i + 1));
            }
            if (run.size() >= 3) {
                for (int i = 0; i <= run.size() - 3; i++) {
                    inc(hotConsec, run.get(i) + "," + run.get(i + 1) + "," + run.get(i + 2));
                }
            }
        }
        if (runs.isEmpty()) {
            return "无连号";
        }
        List<Integer> lens = runs.stream().map(List::size).sorted(Comparator.reverseOrder()).toList();
        if (lens.size() == 1) {
            return switch (lens.getFirst()) {
                case 2 -> "2连号";
                case 3 -> "3连号";
                case 4 -> "4连号";
                case 5 -> "5连号";
                default -> "6连号";
            };
        }
        if (lens.size() == 2 && lens.get(0) == 3 && lens.get(1) == 2) {
            return "3连号+2连号";
        }
        if (lens.size() == 2 && lens.get(0) == 2 && lens.get(1) == 2) {
            return "2组2连号";
        }
        if (lens.size() == 3 && lens.get(0) == 2 && lens.get(1) == 2 && lens.get(2) == 2) {
            return "3组2连号";
        }
        return "其他连号";
    }

    private static boolean isNeighbor(int ball, List<Integer> prev) {
        for (int p : prev) {
            if (Math.abs(ball - p) == 1) {
                return true;
            }
        }
        return false;
    }

    private static List<AnyNCandidate> topWindowCombos(List<Integer> blues, int window, int topN) {
        if (blues.size() < window) {
            return List.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i <= blues.size() - window; i++) {
            List<Integer> slice = new ArrayList<>(blues.subList(i, i + window));
            Collections.sort(slice);
            inc(counts, slice.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        int denom = blues.size() - window + 1;
        return topAnyN(counts, denom, topN);
    }

    private static List<AnyNCandidate> topAnyN(Map<String, Integer> counts, int denom, int topN) {
        double d = Math.max(denom, 1);
        return counts.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(topN)
            .map(e -> {
                AnyNCandidate c = new AnyNCandidate();
                c.setBalls(e.getKey());
                c.setCount(e.getValue());
                c.setFrequency(round4(e.getValue() / d));
                return c;
            })
            .toList();
    }

    private static String buildConclusion(LotteryAnalysisRespBo r, int n) {
        SampleOverview o = r.getSampleOverview();
        String odd = topKey(r.getOddEvenRatio());
        String zone = topKey(r.getThreeZoneRatio());
        String blueHot = "-";
        if (r.getBlue() != null && r.getBlue().getAnyN() != null
            && r.getBlue().getAnyN().getAny1() != null && !r.getBlue().getAnyN().getAny1().isEmpty()) {
            blueHot = r.getBlue().getAnyN().getAny1().getFirst().getBalls();
        }
        return String.format(
            "样本%d期，红球均和值%s、均跨度%s；奇偶比以%s为主，三区比以%s为主；蓝球高频%s。形态推算/杀号/冷热温见外层计算结果。",
            n, o.getAvgSum(), o.getAvgSpan(), odd, zone, blueHot);
    }

    private static String topKey(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "-";
        }
        return map.entrySet().iterator().next().getKey();
    }

    private static List<Integer> sortedReds(DrawRecord r) {
        if (r.getRedBalls() == null) {
            return List.of();
        }
        List<Integer> copy = new ArrayList<>(r.getRedBalls());
        Collections.sort(copy);
        return copy;
    }

    private static Map<String, Integer> initCountMap(int from, int to) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = from; i <= to; i++) {
            map.put(String.valueOf(i), 0);
        }
        return map;
    }

    private static void inc(Map<String, Integer> map, String key) {
        map.merge(key, 1, Integer::sum);
    }

    private static Map<String, Integer> sortedByCount(Map<String, Integer> src) {
        return src.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, Integer> sortedKey(Map<String, Integer> src) {
        return src.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, Integer> topMap(Map<String, Integer> src, int topN) {
        return src.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(topN)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static int rangeStart(String range) {
        int dash = range.indexOf('-');
        return dash > 0 ? Integer.parseInt(range.substring(0, dash)) : 0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
