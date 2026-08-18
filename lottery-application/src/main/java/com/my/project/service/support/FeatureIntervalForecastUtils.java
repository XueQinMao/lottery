package com.my.project.service.support;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo.DrawRecord;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FeatureIntervalForecastUtils
 *
 * <p>基于形态「相邻两次命中间隔」扩张/收缩评分：
 * <ul>
 *   <li>Java 主路径 {@link #forecast}：选桶 + 稀有过滤 + 刚出不主推 + 红/蓝自洽</li>
 *   <li>LLM 快照 {@link #buildSnapshot}；回填/纠偏 {@link #enrichFromSnapshot}</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/18
 **/
public final class FeatureIntervalForecastUtils {

    private static final int RECENT_GAP_WINDOW = 5;
    private static final int MIN_GAPS_FOR_TREND = 3;
    private static final int MIN_GAPS_FOR_VALUE = 2;
    private static final double SLOPE_RATIO_THRESHOLD = 0.15;
    private static final double MIN_PRIOR_RATIO = 0.15;
    private static final int MAX_ALTERNATIVES = 2;
    private static final int SNAPSHOT_TOP_N = 12;

    private static final Set<String> RARE_RATIO = Set.of("0:6", "6:0");
    private static final Set<String> RARE_SUM_RANGE = Set.of(
        "21-60", "61-66", "67-72", "133-138", "139-144", "145-183");
    private static final Set<String> RARE_ZONE_COUNT = Set.of("0", "5", "6");

    private FeatureIntervalForecastUtils() {
    }

    /**
     * 构建单形态间隔节奏快照（JSON），供 LLM forecastOne 使用。
     */
    public static String buildSnapshot(FeatureKind kind, List<DrawRecord> recordsNewestFirst) {
        if (recordsNewestFirst == null || recordsNewestFirst.isEmpty()) {
            throw new IllegalArgumentException("开奖样本不能为空");
        }
        List<DrawRecord> chronological = toChronological(recordsNewestFirst);
        List<BucketScore> scores = scoreAllBuckets(kind, chronological);
        String lastValue = LotteryFeatureTrendUtils.extract(
            chronological.getLast().getRedBalls(), chronological.getLast().getBlueBall(), kind);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("feature", kind.getCode());
        root.put("label", kind.getLabel());
        root.put("sampleSize", chronological.size());
        root.put("lastValue", lastValue);
        root.put("method", "adjacent-hit-gap：间隔扩张=走冷(cooling)，收缩=走热(heating)；"
            + "G=预计下次完整周期，eta=G-currentOmission；eta为0或1且非刚出且间隔样本充足才算接入窗口");

        List<Map<String, Object>> candidates = new ArrayList<>();
        int limit = Math.min(SNAPSHOT_TOP_N, scores.size());
        for (int i = 0; i < limit; i++) {
            BucketScore b = scores.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ratio", b.ratio());
            row.put("rank", i + 1);
            row.put("score", round3(b.score()));
            row.put("prior", round3(b.prior()));
            row.put("gapTrend", b.gapTrend());
            row.put("recentGaps", b.recentGaps());
            row.put("predictedGap", round3(b.predictedGap()));
            row.put("currentOmission", b.currentOmission());
            row.put("eta", b.eta());
            row.put("dueWindow", b.dueWindow());
            row.put("isLast", b.ratio().equals(lastValue));
            row.put("eligibleValue", eligibleForValue(kind, b));
            row.put("rare", isRareBucket(kind, b.ratio(), b.prior()));
            candidates.add(row);
        }
        root.put("candidates", candidates);
        root.put("hint",
            "value 必须选 eligibleValue=true 的桶；禁止刚出(isLast/currentOmission=0)、"
                + "rare=true、gapTrend=unknown 且间隔不足的桶作为主推；"
                + "优先 dueWindow=true 且 heating/stable；跨度/尾数/区个数可将相邻高分桶合并为闭区间");
        return JSON.toJSONString(root);
    }

    /**
     * 回填间隔字段；若 LLM 主推不合规（稀有/刚出/样本不足），替换为 Java 选桶。
     */
    public static FeatureForecastItem enrichFromSnapshot(FeatureKind kind, FeatureForecastItem llmItem,
        List<DrawRecord> recordsNewestFirst) {
        if (llmItem == null || recordsNewestFirst == null || recordsNewestFirst.isEmpty()) {
            return llmItem;
        }
        List<DrawRecord> chronological = toChronological(recordsNewestFirst);
        List<BucketScore> scores = scoreAllBuckets(kind, chronological);
        BucketScore matched = matchBucket(kind, llmItem.getValue(), scores);
        if (matched == null || !eligibleForValue(kind, matched)) {
            FeatureForecastItem javaItem = forecastOne(kind, chronological);
            String llmVal = llmItem.getValue();
            javaItem.setReason("LLM主推「" + llmVal + "」不合规已替换。" + javaItem.getReason());
            return javaItem;
        }
        fillItem(llmItem, matched, llmItem.getValue(), scores);
        return llmItem;
    }

    public static void reconcileBlueItems(EnumMap<FeatureKind, FeatureForecastItem> items) {
        reconcileBlue(items);
    }

    public static void reconcileRedItems(EnumMap<FeatureKind, FeatureForecastItem> items,
        List<DrawRecord> recordsNewestFirst) {
        if (items == null || recordsNewestFirst == null || recordsNewestFirst.isEmpty()) {
            return;
        }
        reconcileRed(items, toChronological(recordsNewestFirst));
    }

    public static FeatureForecastItem forecastOneKind(FeatureKind kind, List<DrawRecord> recordsNewestFirst) {
        if (recordsNewestFirst == null || recordsNewestFirst.isEmpty()) {
            throw new IllegalArgumentException("开奖样本不能为空");
        }
        return forecastOne(kind, toChronological(recordsNewestFirst));
    }

    /**
     * 纯 Java 间隔节奏预测（红 11 + 蓝 4，含过滤与自洽）。
     */
    public static FeatureForecastBo forecast(List<DrawRecord> recordsNewestFirst) {
        if (recordsNewestFirst == null || recordsNewestFirst.isEmpty()) {
            throw new IllegalArgumentException("开奖样本不能为空");
        }
        List<DrawRecord> chronological = toChronological(recordsNewestFirst);

        EnumMap<FeatureKind, FeatureForecastItem> items = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            items.put(kind, forecastOne(kind, chronological));
        }
        reconcileBlue(items);
        reconcileRed(items, chronological);

        FeatureForecastBo forecast = toBo(items);
        forecast.setBasis(String.format(Locale.ROOT,
            "Java间隔评分（最近%d期）：走冷/走热+eta；禁止刚出重复主推、稀有桶与间隔样本不足进主推；"
                + "红球三区对齐区个数，和值与跨度互斥时回退常态和值；蓝球以大小奇偶对齐奇偶/大小。",
            chronological.size()));
        return forecast;
    }

    public static FeatureForecastBo toBo(EnumMap<FeatureKind, FeatureForecastItem> items) {
        FeatureForecastBo forecast = new FeatureForecastBo();
        forecast.setOddEven(items.get(FeatureKind.ODD_EVEN));
        forecast.setBigSmall(items.get(FeatureKind.BIG_SMALL));
        forecast.setPrimeComposite(items.get(FeatureKind.PRIME_COMP));
        forecast.setRatio012(items.get(FeatureKind.RATIO_012));
        forecast.setSpan(items.get(FeatureKind.SPAN));
        forecast.setSumRange(items.get(FeatureKind.SUM_RANGE));
        forecast.setSumTail(items.get(FeatureKind.SUM_TAIL));
        forecast.setThreeZone(items.get(FeatureKind.THREE_ZONE));
        forecast.setZone1Count(items.get(FeatureKind.ZONE1_COUNT));
        forecast.setZone2Count(items.get(FeatureKind.ZONE2_COUNT));
        forecast.setZone3Count(items.get(FeatureKind.ZONE3_COUNT));
        forecast.setBlueOddEven(items.get(FeatureKind.BLUE_ODD_EVEN));
        forecast.setBlueBigSmall(items.get(FeatureKind.BLUE_BIG_SMALL));
        forecast.setBlueBigSmallOddEven(items.get(FeatureKind.BLUE_BIG_SMALL_ODD_EVEN));
        forecast.setBlueRatio012(items.get(FeatureKind.BLUE_RATIO_012));
        return forecast;
    }

    private static List<DrawRecord> toChronological(List<DrawRecord> recordsNewestFirst) {
        List<DrawRecord> chronological = new ArrayList<>(recordsNewestFirst);
        java.util.Collections.reverse(chronological);
        return chronological;
    }

    private static List<BucketScore> scoreAllBuckets(FeatureKind kind, List<DrawRecord> chronological) {
        List<BucketScore> scores = new ArrayList<>();
        for (String bucket : LotteryFeatureTrendUtils.buckets(kind)) {
            scores.add(scoreBucket(kind, bucket, chronological));
        }
        scores.sort(Comparator.comparingDouble(BucketScore::score).reversed()
            .thenComparing(Comparator.comparingDouble(BucketScore::prior).reversed())
            .thenComparing(BucketScore::ratio));
        return scores;
    }

    static boolean eligibleForValue(FeatureKind kind, BucketScore b) {
        if (b == null) {
            return false;
        }
        if (b.currentOmission() == 0) {
            return false;
        }
        if (isRareBucket(kind, b.ratio(), b.prior())) {
            return false;
        }
        if (b.recentGaps() == null || b.recentGaps().size() < MIN_GAPS_FOR_VALUE) {
            return false;
        }
        return true;
    }

    static boolean isRareBucket(FeatureKind kind, String ratio, double prior) {
        if (ratio == null) {
            return true;
        }
        double maxPrior = maxPrior(kind);
        if (maxPrior > 0 && prior < MIN_PRIOR_RATIO * maxPrior) {
            return true;
        }
        return switch (kind) {
            case ODD_EVEN, BIG_SMALL -> RARE_RATIO.contains(ratio);
            case PRIME_COMP -> "0:6".equals(ratio) || "6:0".equals(ratio);
            case SUM_RANGE -> RARE_SUM_RANGE.contains(ratio);
            case ZONE1_COUNT, ZONE2_COUNT, ZONE3_COUNT -> RARE_ZONE_COUNT.contains(ratio);
            case SPAN -> {
                try {
                    int s = Integer.parseInt(ratio);
                    yield s < 14 || s > 30;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case THREE_ZONE, RATIO_012 -> isExtremeTriple(ratio);
            default -> false;
        };
    }

    private static boolean isExtremeTriple(String ratio) {
        String[] p = ratio.split(":");
        if (p.length != 3) {
            return false;
        }
        try {
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            int c = Integer.parseInt(p[2]);
            return a >= 5 || b >= 5 || c >= 5 || a + b + c != 6;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static double maxPrior(FeatureKind kind) {
        double max = 0;
        for (String b : LotteryFeatureTrendUtils.buckets(kind)) {
            max = Math.max(max, LotteryFeatureTrendUtils.theoreticalProb(kind, b));
        }
        return max;
    }

    private static BucketScore matchBucket(FeatureKind kind, String value, List<BucketScore> scores) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (BucketScore s : scores) {
            if (v.equals(s.ratio())) {
                return s;
            }
        }
        if (v.matches("\\d+-\\d+")
            && (kind == FeatureKind.SPAN || kind == FeatureKind.SUM_TAIL
            || kind == FeatureKind.ZONE1_COUNT || kind == FeatureKind.ZONE2_COUNT
            || kind == FeatureKind.ZONE3_COUNT)) {
            int dash = v.indexOf('-');
            try {
                int low = Integer.parseInt(v.substring(0, dash));
                int high = Integer.parseInt(v.substring(dash + 1));
                BucketScore best = null;
                for (BucketScore s : scores) {
                    try {
                        int n = Integer.parseInt(s.ratio());
                        if (n >= low && n <= high && (best == null || s.score() > best.score())) {
                            best = s;
                        }
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
                return best;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static FeatureForecastItem forecastOne(FeatureKind kind, List<DrawRecord> chronological) {
        List<BucketScore> scores = scoreAllBuckets(kind, chronological);
        List<BucketScore> eligible = scores.stream().filter(s -> eligibleForValue(kind, s)).toList();
        BucketScore top = eligible.isEmpty()
            ? scores.stream().filter(s -> s.currentOmission() > 0 && !isRareBucket(kind, s.ratio(), s.prior()))
                .findFirst().orElse(scores.getFirst())
            : eligible.getFirst();

        List<String> alternatives = new ArrayList<>();
        for (BucketScore s : eligible) {
            if (!s.ratio().equals(top.ratio()) && alternatives.size() < MAX_ALTERNATIVES) {
                alternatives.add(s.ratio());
            }
        }
        if (alternatives.size() < MAX_ALTERNATIVES) {
            for (BucketScore s : scores) {
                if (!s.ratio().equals(top.ratio()) && !alternatives.contains(s.ratio())
                    && !isRareBucket(kind, s.ratio(), s.prior())
                    && alternatives.size() < MAX_ALTERNATIVES) {
                    alternatives.add(s.ratio());
                }
            }
        }

        String value = maybeMergeAdjacent(kind, top, eligible.isEmpty() ? scores : eligible);
        FeatureForecastItem item = new FeatureForecastItem();
        fillItem(item, top, value, scores);
        item.setAlternatives(alternatives);
        if (eligible.isEmpty()) {
            item.setConfidence(round3(Math.min(item.getConfidence() == null ? 0.3 : item.getConfidence(), 0.35)));
            item.setReason("无合格间隔样本，回退次优常态桶。" + item.getReason());
        }
        return item;
    }

    private static void fillItem(FeatureForecastItem item, BucketScore top, String value, List<BucketScore> scores) {
        double second = 0;
        for (BucketScore s : scores) {
            if (!s.ratio().equals(top.ratio())) {
                second = s.score();
                break;
            }
        }
        double confidence = top.score() <= 1e-9
            ? 0.0
            : Math.min(1.0, Math.max(0.0, (top.score() - second) / top.score()));
        if (!top.dueWindow()) {
            confidence = Math.min(confidence, 0.45);
        }
        item.setValue(value);
        item.setConfidence(round3(confidence));
        item.setGapTrend(top.gapTrend());
        item.setPredictedGap(round3(top.predictedGap()));
        item.setCurrentOmission(top.currentOmission());
        item.setEta(top.eta());
        item.setDueWindow(top.dueWindow());
        item.setScore(round3(top.score()));
        item.setRecentGaps(top.recentGaps());
        item.setReason(buildReason(top, value));
    }

    private static BucketScore scoreBucket(FeatureKind kind, String bucket, List<DrawRecord> chronological) {
        List<Boolean> hits = new ArrayList<>(chronological.size());
        for (DrawRecord r : chronological) {
            String actual = LotteryFeatureTrendUtils.extract(r.getRedBalls(), r.getBlueBall(), kind);
            hits.add(bucket.equals(actual));
        }
        List<Integer> hitIndexes = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            if (Boolean.TRUE.equals(hits.get(i))) {
                hitIndexes.add(i);
            }
        }
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < hitIndexes.size(); i++) {
            gaps.add(hitIndexes.get(i) - hitIndexes.get(i - 1));
        }
        List<Integer> recentGaps = tail(gaps, RECENT_GAP_WINDOW);

        int currentOmission;
        if (hitIndexes.isEmpty()) {
            currentOmission = chronological.size();
        } else {
            currentOmission = chronological.size() - 1 - hitIndexes.getLast();
        }

        double prior = LotteryFeatureTrendUtils.theoreticalProb(kind, bucket);
        String gapTrend = classifyTrend(recentGaps);
        double predictedGap = estimatePredictedGap(recentGaps, gapTrend, chronological.size(), prior);
        int eta = (int) Math.round(predictedGap) - currentOmission;
        boolean sampleOk = recentGaps.size() >= MIN_GAPS_FOR_VALUE;
        boolean justAppeared = currentOmission == 0;
        boolean dueWindow = sampleOk && !justAppeared && (eta == 0 || eta == 1);

        double due = dueScore(currentOmission, predictedGap, eta, gapTrend, sampleOk, justAppeared);
        double heat = switch (gapTrend) {
            case "heating" -> 1.15;
            case "cooling" -> 0.85;
            default -> 1.0;
        };
        double priorFactor = 0.5 + 0.5 * normalizePrior(prior, kind);
        double score = due * heat * priorFactor;
        if (justAppeared) {
            score *= 0.35;
        }
        if (!sampleOk) {
            score *= 0.5;
        }
        if (isRareBucket(kind, bucket, prior)) {
            score *= 0.25;
        }

        return new BucketScore(bucket, score, prior, gapTrend, predictedGap, currentOmission, eta,
            dueWindow, recentGaps);
    }

    static String classifyTrend(List<Integer> recentGaps) {
        if (recentGaps == null || recentGaps.size() < MIN_GAPS_FOR_TREND) {
            return "unknown";
        }
        double slope = gapSlope(recentGaps);
        double avg = recentGaps.stream().mapToInt(Integer::intValue).average().orElse(1);
        double threshold = Math.max(0.5, avg * SLOPE_RATIO_THRESHOLD);
        if (slope > threshold) {
            return "cooling";
        }
        if (slope < -threshold) {
            return "heating";
        }
        return "stable";
    }

    static double gapSlope(List<Integer> gaps) {
        int n = gaps.size();
        if (n < 2) {
            return 0;
        }
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = gaps.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = n * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-9) {
            return 0;
        }
        return (n * sumXY - sumX * sumY) / denom;
    }

    static double estimatePredictedGap(List<Integer> recentGaps, String gapTrend, int sampleSize, double prior) {
        if (recentGaps == null || recentGaps.isEmpty()) {
            double theory = prior > 1e-6 ? 1.0 / prior : sampleSize;
            return clamp(theory, 3, sampleSize);
        }
        double median = median(recentGaps);
        int last = recentGaps.getLast();
        double slope = recentGaps.size() >= 2 ? gapSlope(recentGaps) : 0;
        double predicted = switch (gapTrend) {
            case "heating" -> Math.max(1, last + slope);
            case "cooling" -> last + Math.max(0, slope);
            default -> median;
        };
        return clamp(predicted, 1, sampleSize);
    }

    static double dueScore(int currentOmission, double predictedGap, int eta, String gapTrend,
        boolean sampleOk, boolean justAppeared) {
        if (justAppeared || !sampleOk) {
            return 0.15;
        }
        double g = Math.max(predictedGap, 1);
        if (eta < 0) {
            double overdue = currentOmission / g;
            double bonus = clamp(overdue, 0.5, 1.2);
            if ("cooling".equals(gapTrend)) {
                bonus *= 0.85;
            } else if ("heating".equals(gapTrend)) {
                bonus *= 1.05;
            }
            return bonus;
        }
        return clamp(1.0 - Math.abs(currentOmission - g) / g, 0.05, 1.0);
    }

    private static double normalizePrior(double prior, FeatureKind kind) {
        double max = maxPrior(kind);
        if (max <= 0) {
            return 0;
        }
        return clamp(prior / max, 0, 1);
    }

    private static String maybeMergeAdjacent(FeatureKind kind, BucketScore top, List<BucketScore> ranked) {
        if (ranked.size() < 2) {
            return top.ratio();
        }
        if (kind != FeatureKind.SPAN && kind != FeatureKind.SUM_TAIL
            && kind != FeatureKind.ZONE1_COUNT && kind != FeatureKind.ZONE2_COUNT
            && kind != FeatureKind.ZONE3_COUNT) {
            return top.ratio();
        }
        BucketScore second = null;
        for (BucketScore s : ranked) {
            if (!s.ratio().equals(top.ratio())) {
                second = s;
                break;
            }
        }
        if (second == null || second.score() < top.score() * 0.85) {
            return top.ratio();
        }
        try {
            int a = Integer.parseInt(top.ratio());
            int b = Integer.parseInt(second.ratio());
            if (Math.abs(a - b) == 1) {
                return Math.min(a, b) + "-" + Math.max(a, b);
            }
        } catch (NumberFormatException ignored) {
            // 非纯数字桶不合并
        }
        return top.ratio();
    }

    private static void reconcileBlue(EnumMap<FeatureKind, FeatureForecastItem> items) {
        FeatureForecastItem compound = items.get(FeatureKind.BLUE_BIG_SMALL_ODD_EVEN);
        if (compound == null || compound.getValue() == null) {
            return;
        }
        String v = compound.getValue();
        if (!v.contains("奇") && !v.contains("偶")) {
            return;
        }
        boolean big = v.startsWith("大");
        boolean odd = v.endsWith("奇");
        alignSimple(items.get(FeatureKind.BLUE_ODD_EVEN), odd ? "奇" : "偶",
            "与蓝球大小奇偶「" + v + "」对齐");
        alignSimple(items.get(FeatureKind.BLUE_BIG_SMALL), big ? "大" : "小",
            "与蓝球大小奇偶「" + v + "」对齐");
    }

    private static void reconcileRed(EnumMap<FeatureKind, FeatureForecastItem> items,
        List<DrawRecord> chronological) {
        FeatureForecastItem threeZone = items.get(FeatureKind.THREE_ZONE);
        if (threeZone != null && threeZone.getValue() != null && threeZone.getValue().matches("\\d:\\d:\\d")) {
            String[] p = threeZone.getValue().split(":");
            alignZoneCount(items, FeatureKind.ZONE1_COUNT, p[0], chronological, threeZone.getValue());
            alignZoneCount(items, FeatureKind.ZONE2_COUNT, p[1], chronological, threeZone.getValue());
            alignZoneCount(items, FeatureKind.ZONE3_COUNT, p[2], chronological, threeZone.getValue());
        }
        reconcileSumAndSpan(items, chronological);
    }

    private static void alignZoneCount(EnumMap<FeatureKind, FeatureForecastItem> items, FeatureKind kind,
        String count, List<DrawRecord> chronological, String threeZone) {
        FeatureForecastItem item = items.get(kind);
        if (item == null) {
            return;
        }
        if (covers(item.getValue(), count)) {
            return;
        }
        List<BucketScore> scores = scoreAllBuckets(kind, chronological);
        BucketScore matched = matchBucket(kind, count, scores);
        if (matched == null) {
            alignSimple(item, count, "与三区比「" + threeZone + "」对齐");
            return;
        }
        String old = item.getValue();
        fillItem(item, matched, count, scores);
        item.setAlternatives(prependAlt(old, item.getAlternatives()));
        item.setReason("与三区比「" + threeZone + "」对齐为「" + count + "」。" + item.getReason());
    }

    private static void reconcileSumAndSpan(EnumMap<FeatureKind, FeatureForecastItem> items,
        List<DrawRecord> chronological) {
        FeatureForecastItem spanItem = items.get(FeatureKind.SPAN);
        FeatureForecastItem sumItem = items.get(FeatureKind.SUM_RANGE);
        if (sumItem == null) {
            return;
        }
        Integer spanMid = parseMid(spanItem == null ? null : spanItem.getValue());
        Integer sumMid = parseMid(sumItem.getValue());
        String sumHead = sumRangeHead(sumItem.getValue());
        boolean rareSum = sumHead != null && isRareBucket(FeatureKind.SUM_RANGE, sumHead,
            LotteryFeatureTrendUtils.theoreticalProb(FeatureKind.SUM_RANGE, sumHead));
        boolean incompatible = spanMid != null && sumMid != null
            && ((spanMid >= 16 && sumMid < 70) || (spanMid <= 28 && sumMid > 140));
        if (!rareSum && !incompatible) {
            return;
        }
        List<BucketScore> scores = scoreAllBuckets(FeatureKind.SUM_RANGE, chronological);
        BucketScore pick = null;
        for (BucketScore s : scores) {
            if (!eligibleForValue(FeatureKind.SUM_RANGE, s)) {
                continue;
            }
            Integer mid = parseMid(s.ratio());
            if (mid != null && mid >= 85 && mid <= 120) {
                pick = s;
                break;
            }
        }
        if (pick == null) {
            pick = scores.stream()
                .filter(s -> eligibleForValue(FeatureKind.SUM_RANGE, s))
                .findFirst()
                .orElse(null);
        }
        if (pick == null) {
            return;
        }
        String old = sumItem.getValue();
        fillItem(sumItem, pick, pick.ratio(), scores);
        sumItem.setAlternatives(prependAlt(old, sumItem.getAlternatives()));
        sumItem.setReason("和值与跨度/稀有档冲突，回退常态区间「" + pick.ratio() + "」。" + sumItem.getReason());
    }

    private static void alignSimple(FeatureForecastItem item, String value, String why) {
        if (item == null || value.equals(item.getValue())) {
            return;
        }
        item.setAlternatives(prependAlt(item.getValue(), item.getAlternatives()));
        item.setValue(value);
        item.setReason((item.getReason() == null ? "" : item.getReason() + "；") + why + "为「" + value + "」");
    }

    private static List<String> prependAlt(String old, List<String> alts) {
        List<String> next = new ArrayList<>();
        if (old != null && !old.isBlank()) {
            next.add(old);
        }
        if (alts != null) {
            for (String a : alts) {
                if (a != null && !a.equals(old) && !next.contains(a) && next.size() < MAX_ALTERNATIVES) {
                    next.add(a);
                }
            }
        }
        return next.stream().limit(MAX_ALTERNATIVES).toList();
    }

    private static boolean covers(String value, String exact) {
        if (value == null) {
            return false;
        }
        if (value.equals(exact)) {
            return true;
        }
        if (value.matches("\\d+-\\d+")) {
            int dash = value.indexOf('-');
            try {
                int lo = Integer.parseInt(value.substring(0, dash));
                int hi = Integer.parseInt(value.substring(dash + 1));
                int v = Integer.parseInt(exact);
                return v >= lo && v <= hi;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static Integer parseMid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.matches("\\d+-\\d+")) {
                int dash = value.indexOf('-');
                int lo = Integer.parseInt(value.substring(0, dash));
                int hi = Integer.parseInt(value.substring(dash + 1));
                return (lo + hi) / 2;
            }
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String sumRangeHead(String value) {
        if (value == null) {
            return null;
        }
        if (RARE_SUM_RANGE.contains(value) || value.matches("\\d+-\\d+")) {
            if (value.matches("\\d+-\\d+") && !RARE_SUM_RANGE.contains(value)) {
                Integer mid = parseMid(value);
                if (mid != null && mid < 70) {
                    return "21-60";
                }
            }
            return value.contains("-") && value.length() <= 7 ? value : value;
        }
        return value;
    }

    private static String buildReason(BucketScore top, String value) {
        String trendCn = switch (top.gapTrend()) {
            case "heating" -> "走热(间隔缩短)";
            case "cooling" -> "走冷(间隔拉长)";
            case "stable" -> "间隔平稳";
            default -> "间隔样本不足";
        };
        String gaps = top.recentGaps() == null || top.recentGaps().isEmpty()
            ? "无"
            : top.recentGaps().stream().map(String::valueOf).collect(Collectors.joining(","));
        String due = Boolean.TRUE.equals(top.dueWindow()) ? "接入窗口内" : "非窗口";
        return String.format(Locale.ROOT,
            "主推%s：近间隔[%s]→%s，G=%.1f，当前遗漏%d，eta=%d（%s），score=%.3f",
            value, gaps, trendCn, top.predictedGap(),
            top.currentOmission(), top.eta(), due, top.score());
    }

    private static List<Integer> tail(List<Integer> list, int n) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, list.size() - n);
        return new ArrayList<>(list.subList(from, list.size()));
    }

    private static double median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    record BucketScore(
        String ratio,
        double score,
        double prior,
        String gapTrend,
        double predictedGap,
        int currentOmission,
        int eta,
        boolean dueWindow,
        List<Integer> recentGaps
    ) {
    }
}
