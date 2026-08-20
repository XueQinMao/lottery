package com.my.project.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MorphologySnapshotForecast
 *
 * <p>应用层纯计算：主推以理论先验（众数）为主，指数差值 / 间隔只做轻量加减分。
 * 理论众数刚出仍可主推（不因「刚出一律禁止」躲开 3:3）；低频刚出与热度断档不得主推。
 * 跨度 / 和尾 / 区个数 / 和值相邻高分桶合并为闭区间以覆盖下一期。
 * 长冷回补必须进入 value 或 alternatives。
 * <p>{@link #forecast} 为 Java 主路径；{@link #compactForLlm} / {@link #applyGuard} 供 engine=llm 压缩候选并硬校验。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
public final class MorphologySnapshotForecast {

    private static final int RECENT_GAP_WINDOW = 5;
    /** 参与差值趋势的最近指数点数（含最早一端） */
    private static final int INDEX_DIFF_WINDOW = 8;
    private static final int MIN_GAPS_FOR_TREND = 3;
    private static final int MIN_GAPS_FOR_VALUE = 2;
    private static final int MAX_ALTERNATIVES = 3;
    private static final int CANDIDATE_LIMIT = 10;
    private static final double SLOPE_RATIO_THRESHOLD = 0.15;
    private static final double MIN_PRIOR_RATIO = 0.15;
    /** 先验 ≥ 该比例×最大先验，视为理论众数/近众数，刚出仍可主推 */
    private static final double MODE_PRIOR_RATIO = 0.70;
    private static final double HIT_PULSE = 0.45;
    private static final Set<String> MERGEABLE_FEATURES = Set.of(
        "span", "sumTail", "zone1Count", "zone2Count", "zone3Count");

    private MorphologySnapshotForecast() {
    }

    /**
     * 压缩快照：只保留已算好的差值趋势与窗口字段。
     */
    public static String compactForLlm(String snapshotJson) {
        JSONObject root = parseRoot(snapshotJson);
        List<BucketScore> scores = scoreAll(root);
        String lastValue = root.getString("lastValue");

        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("feature", root.getString("feature"));
        compact.put("label", root.getString("label"));
        compact.put("sampleSize", root.get("sampleSize"));
        compact.put("lastPeriod", root.get("lastPeriod"));
        compact.put("lastValue", lastValue);
        compact.put("hint",
            "主推优先理论先验最高的桶；指数差值只做轻量加减分，禁止用到期(eta)把低频桶抬成主推。"
                + "value 只能选自 eligibleValue=true 且 forbiddenAsValue=false 的候选；"
                + "禁止低频刚出/热度断档主推；理论众数刚出或黏性连出(clusterContinue)允许再主推；"
                + "reboundWindow 必须进 value 或 alternatives；"
                + "predictedGap/eta/dueWindow/recentGaps/gapTrend/score 必须抄所选候选，禁止自造。");

        List<String> forbidden = new ArrayList<>();
        List<String> rebound = new ArrayList<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        int n = 0;
        for (BucketScore b : scores) {
            boolean mustKeep = b.reboundWindow || b.ratio.equals(lastValue);
            if (n >= CANDIDATE_LIMIT && !mustKeep) {
                continue;
            }
            n++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ratio", b.ratio);
            row.put("rank", candidates.size() + 1);
            row.put("javaScore", round3(b.score));
            row.put("eligibleValue", eligibleForValue(b));
            row.put("forbiddenAsValue", forbiddenAsValue(b));
            row.put("heatBroken", b.heatBroken);
            row.put("reboundWindow", b.reboundWindow);
            row.put("clusterContinue", b.clusterContinue);
            row.put("highPrior", b.highPrior);
            row.put("justAppeared", b.currentOmission == 0);
            row.put("gapTrend", b.gapTrend);
            row.put("predictedGap", round3(b.predictedGap));
            row.put("currentOmission", b.currentOmission);
            row.put("eta", b.eta);
            row.put("dueWindow", b.dueWindow);
            row.put("recentGaps", b.recentGaps);
            row.put("indexDiffTrend", b.indexDiffTrend);
            row.put("indexDeltaGap", round3(b.indexDeltaGap));
            row.put("recentIndexDeltas", b.recentIndexDeltas);
            row.put("indexTail", b.indexTail);
            row.put("hitPulse", b.hitPulse);
            row.put("prior", round3(b.prior));
            candidates.add(row);
            if (forbiddenAsValue(b)) {
                forbidden.add(b.ratio);
            }
            if (b.reboundWindow) {
                rebound.add(b.ratio);
            }
        }
        compact.put("candidates", candidates);
        compact.put("forbiddenAsValue", forbidden);
        compact.put("reboundMustInclude", rebound);
        return JSON.toJSONString(compact);
    }

    /**
     * 按 indexValues 差值趋势确定性选出主推（不经过 LLM）。
     */
    public static FeatureForecastItem forecast(String snapshotJson) {
        JSONObject root = parseRoot(snapshotJson);
        List<BucketScore> scores = scoreAll(root);
        String feature = root.getString("feature");
        List<BucketScore> eligible = scores.stream()
            .filter(MorphologySnapshotForecast::eligibleForValue)
            .toList();
        BucketScore top = eligible.isEmpty()
            ? scores.stream().filter(s -> s.currentOmission > 0 && !s.rare).findFirst().orElse(scores.getFirst())
            : eligible.getFirst();
        String value = maybeMergeAdjacent(feature, top, eligible.isEmpty() ? scores : eligible);
        List<String> alternatives = mergeAlternatives(feature, null, top, eligible, scores, null, value);
        FeatureForecastItem item = new FeatureForecastItem();
        fillItem(item, top, value, scores, alternatives);
        return item;
    }

    /**
     * 纠正 LLM 违规主推，回填 Java 计算的间隔字段，并保证长冷回补进入备选。
     */
    public static FeatureForecastItem applyGuard(FeatureForecastItem llmItem, String snapshotJson) {
        JSONObject root = parseRoot(snapshotJson);
        List<BucketScore> scores = scoreAll(root);
        String feature = root.getString("feature");
        List<BucketScore> eligible = scores.stream()
            .filter(MorphologySnapshotForecast::eligibleForValue)
            .toList();
        BucketScore fallback = eligible.isEmpty()
            ? scores.stream().filter(s -> s.currentOmission > 0 && !s.rare).findFirst().orElse(scores.getFirst())
            : eligible.getFirst();

        FeatureForecastItem item = llmItem == null ? new FeatureForecastItem() : llmItem;
        String rawValue = item.getValue() == null ? "" : item.getValue().trim();
        String llmReason = item.getReason();
        BucketScore matched = matchBucket(feature, rawValue, scores);
        String reasonPrefix = "";
        BucketScore top = matched;
        if (matched == null || forbiddenAsValue(matched) || !eligibleForValue(matched)) {
            reasonPrefix = "LLM主推「" + (rawValue.isEmpty() ? "空" : rawValue) + "」不合规已替换。";
            top = fallback;
        }
        String value = maybeMergeAdjacent(feature, top, eligible.isEmpty() ? scores : eligible);
        List<String> alternatives = mergeAlternatives(feature, item.getAlternatives(), top, eligible, scores, rawValue, value);
        fillItem(item, top, value, scores, alternatives);
        if (!reasonPrefix.isEmpty()) {
            item.setReason(reasonPrefix + item.getReason());
        } else if (llmReason != null && !llmReason.isBlank()) {
            item.setReason(llmReason.trim() + "。" + item.getReason());
        }
        return item;
    }

    static List<BucketScore> scoreAll(JSONObject root) {
        JSONArray options = root.getJSONArray("ratioOptions");
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("形态快照缺少 ratioOptions");
        }
        double maxPrior = 0;
        List<JSONObject> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            JSONObject row = options.getJSONObject(i);
            rows.add(row);
            maxPrior = Math.max(maxPrior, row.getDoubleValue("theoreticalProb"));
        }
        List<BucketScore> scores = new ArrayList<>();
        for (JSONObject row : rows) {
            scores.add(scoreBucket(row, maxPrior));
        }
        scores.sort(Comparator.comparingDouble(BucketScore::score).reversed()
            .thenComparing(Comparator.comparingDouble(BucketScore::prior).reversed())
            .thenComparing(BucketScore::ratio));
        return scores;
    }

    static BucketScore scoreBucket(JSONObject row, double maxPrior) {
        String ratio = row.getString("ratio");
        double prior = row.getDoubleValue("theoreticalProb");
        int currentOmission = row.getIntValue("currentOmission");
        double avgOmission = row.getDoubleValue("avgOmission");
        int maxOmission = row.getIntValue("maxOmission");
        int hitCount = row.getIntValue("hitCount");
        List<Integer> allGaps = toIntList(row.getJSONArray("hitIntervals"));
        List<Double> indexValues = toDoubleList(row.getJSONArray("indexValues"));
        List<Integer> recentGaps = tail(allGaps, RECENT_GAP_WINDOW);
        List<Double> indexTail = tail(indexValues, INDEX_DIFF_WINDOW);

        DroughtContext drought = detectDrought(allGaps, avgOmission, maxOmission);
        List<Integer> rhythmGaps = drought.outlierLast() ? drought.typicalGaps() : recentGaps;
        String intervalTrend = classifyTrend(rhythmGaps.size() >= MIN_GAPS_FOR_TREND ? rhythmGaps : recentGaps);
        IndexDiffSignal indexDiff = readIndexDiff(indexValues);
        String gapTrend = !"unknown".equals(indexDiff.trend()) ? indexDiff.trend() : intervalTrend;
        boolean reboundWindow = drought.outlierLast() && currentOmission >= 1 && currentOmission <= 2
            && drought.canCluster();
        boolean clusterContinue = clusterContinue(currentOmission, recentGaps, avgOmission,
            indexDiff.trend(), false);

        double predictedGap = estimatePredictedGap(recentGaps, rhythmGaps,
            "stable".equals(gapTrend) ? "stable" : intervalTrend, drought, prior, avgOmission);
        if ("heating".equals(indexDiff.trend()) && !drought.outlierLast()) {
            predictedGap = Math.max(1, predictedGap * 0.85);
        } else if ("cooling".equals(indexDiff.trend()) && currentOmission > 0 && !reboundWindow) {
            predictedGap = Math.min(80, predictedGap * 1.15);
        }
        if (clusterContinue) {
            predictedGap = Math.max(1, Math.min(predictedGap, 2));
        }
        int eta = (int) Math.round(predictedGap) - currentOmission;
        boolean sampleOk = allGaps.size() >= MIN_GAPS_FOR_VALUE || (drought.outlierLast() && !rhythmGaps.isEmpty());
        boolean heatBroken = heatBroken(currentOmission, allGaps, intervalTrend);
        boolean hitPulse = hasRecentHitPulse(indexValues);
        boolean rare = isRare(ratio, prior, maxPrior);
        boolean highPrior = !rare && maxPrior > 1e-9 && prior >= MODE_PRIOR_RATIO * maxPrior;
        clusterContinue = clusterContinue && !rare;
        boolean coolingBlocked = "cooling".equals(indexDiff.trend()) && !reboundWindow
            && !clusterContinue && !highPrior;
        boolean dueWindow = sampleOk && !coolingBlocked
            && (clusterContinue || highPrior
            || (currentOmission > 0 && (eta == 0 || eta == 1 || reboundWindow)));

        double due = dueScore(currentOmission, predictedGap, eta, gapTrend, sampleOk,
            currentOmission == 0, reboundWindow, clusterContinue, heatBroken, avgOmission, highPrior);
        double indexFactor = switch (indexDiff.trend()) {
            case "heating" -> 1.08;
            case "cooling" -> reboundWindow || highPrior ? 1.0 : 0.88;
            case "stable" -> 1.0;
            default -> 1.0;
        };
        double priorFactor = 0.20 + 0.80 * (maxPrior <= 0 ? 0 : clamp(prior / maxPrior, 0, 1));
        double score = due * indexFactor * priorFactor;
        if (currentOmission == 0) {
            if (clusterContinue) {
                score *= 1.18;
            } else if (highPrior) {
                score *= 0.95;
            } else {
                score *= drought.outlierLast() && drought.canCluster() ? 0.55 : 0.28;
            }
        }
        if (reboundWindow) {
            score *= 1.15;
        }
        if (heatBroken && !reboundWindow) {
            score *= 0.52;
        }
        if (!sampleOk) {
            score *= 0.5;
        }
        if (rare || hitCount == 0) {
            score *= 0.2;
        }
        if (avgOmission > 1e-6 && currentOmission > 0) {
            double rel = currentOmission / Math.max(avgOmission, predictedGap);
            if (rel < 0.35 && !reboundWindow) {
                score *= 0.62;
            } else if (rel > 2.2 && coolingBlocked) {
                score *= 0.55;
            }
        }

        return new BucketScore(ratio, score, prior, gapTrend, predictedGap, currentOmission, eta,
            dueWindow, recentGaps, reboundWindow, clusterContinue, heatBroken, rare, sampleOk,
            indexTail, hitPulse, indexDiff.trend(), indexDiff.deltaGap(), indexDiff.recentDeltas(),
            highPrior);
    }

    static boolean eligibleForValue(BucketScore b) {
        if (b == null || b.rare) {
            return false;
        }
        if (forbiddenAsValue(b)) {
            return false;
        }
        return b.reboundWindow || b.clusterContinue || b.highPrior || b.sampleOk;
    }

    static boolean forbiddenAsValue(BucketScore b) {
        if (b == null || b.rare) {
            return true;
        }
        if (b.currentOmission == 0) {
            return !(b.clusterContinue || b.highPrior);
        }
        return b.heatBroken && !b.reboundWindow;
    }

    /**
     * 高频黏性连出：均漏短、近间隔多为 1～2，且指数差值未走冷。
     * 例如大小比 3:3（均漏≈1.4）刚出后仍可主推，避免被「刚出一律不主推」漏掉连开。
     */
    static boolean clusterContinue(int currentOmission, List<Integer> recentGaps, double avgOmission,
        String indexDiffTrend, boolean rare) {
        if (rare || currentOmission != 0 || "cooling".equals(indexDiffTrend)) {
            return false;
        }
        if (recentGaps == null || recentGaps.size() < 2) {
            return false;
        }
        if (avgOmission > 2.5) {
            return false;
        }
        double med = median(recentGaps);
        long shortHits = recentGaps.stream().filter(g -> g != null && g <= 2).count();
        return med <= 2.0 && shortHits >= Math.max(2, (recentGaps.size() + 1) / 2);
    }

    static DroughtContext detectDrought(List<Integer> allGaps, double avgOmission, int maxOmission) {
        if (allGaps == null || allGaps.size() < 2) {
            return DroughtContext.none();
        }
        int lastGap = allGaps.getLast();
        List<Integer> earlier = allGaps.subList(0, allGaps.size() - 1);
        double earlierMedian = median(earlier);
        boolean outlier = lastGap >= Math.max(12, earlierMedian * 2.0)
            && lastGap >= Math.max(avgOmission * 1.6, maxOmission * 0.75);
        boolean canCluster = minOf(earlier) <= 2;
        return new DroughtContext(outlier, canCluster, lastGap, new ArrayList<>(earlier));
    }

    static boolean heatBroken(int currentOmission, List<Integer> allGaps, String gapTrend) {
        if (currentOmission <= 0 || allGaps == null || allGaps.isEmpty()) {
            return false;
        }
        int lastGap = allGaps.getLast();
        if (currentOmission <= lastGap) {
            return false;
        }
        return "heating".equals(gapTrend) || currentOmission >= lastGap * 1.5;
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

    static double estimatePredictedGap(List<Integer> recentGaps, List<Integer> rhythmGaps,
        String gapTrend, DroughtContext drought, double prior, double avgOmission) {
        List<Integer> base = (rhythmGaps != null && !rhythmGaps.isEmpty()) ? rhythmGaps : recentGaps;
        if (base == null || base.isEmpty()) {
            double theory = prior > 1e-6 ? 1.0 / prior : Math.max(avgOmission, 8);
            return clamp(theory, 2, 80);
        }
        if (drought.outlierLast() && drought.canCluster()) {
            double cluster = 0.5 * minOf(drought.typicalGaps()) + 0.5 * median(drought.typicalGaps());
            return clamp(cluster, 1, 6);
        }
        double median = median(base);
        int last = base.getLast();
        double slope = base.size() >= 2 ? gapSlope(base) : 0;
        double predicted = switch (gapTrend) {
            case "heating" -> Math.max(1, last + slope);
            case "cooling" -> last + Math.max(0, slope);
            default -> median;
        };
        return clamp(predicted, 1, 80);
    }

    static double dueScore(int currentOmission, double predictedGap, int eta, String gapTrend,
        boolean sampleOk, boolean justAppeared, boolean reboundWindow, boolean clusterContinue,
        boolean heatBroken, double avgOmission, boolean highPrior) {
        if (clusterContinue) {
            return 1.08;
        }
        if (justAppeared) {
            if (highPrior) {
                return 0.92;
            }
            return reboundWindow ? 0.35 : 0.12;
        }
        if (!sampleOk) {
            return 0.15;
        }
        if (reboundWindow) {
            return currentOmission == 1 ? 1.15 : 1.05;
        }
        double g = Math.max(predictedGap, 1);
        if (eta < 0) {
            double overdue = currentOmission / g;
            double bonus = clamp(overdue, 0.45, 1.05);
            if ("cooling".equals(gapTrend) || heatBroken) {
                bonus *= 0.7;
            }
            if (avgOmission > 1e-6 && currentOmission > avgOmission * 1.8) {
                bonus *= 0.75;
            }
            return bonus;
        }
        return clamp(1.0 - Math.abs(currentOmission - g) / g, 0.08, 1.0);
    }

    /**
     * indexValues[0] 最早、末项最新。比较近端差值均值相对前半段：变小=收缩走热，变大=扩张走冷，接近=平稳。
     */
    static IndexDiffSignal readIndexDiff(List<Double> indexValues) {
        if (indexValues == null || indexValues.size() < 4) {
            return IndexDiffSignal.unknown();
        }
        List<Double> series = tail(indexValues, INDEX_DIFF_WINDOW);
        List<Double> deltas = new ArrayList<>(series.size() - 1);
        for (int i = 1; i < series.size(); i++) {
            deltas.add(round4(series.get(i) - series.get(i - 1)));
        }
        if (deltas.size() < 3) {
            return IndexDiffSignal.unknown();
        }
        int mid = Math.max(1, deltas.size() / 2);
        double first = mean(deltas.subList(0, mid));
        double second = mean(deltas.subList(mid, deltas.size()));
        double gap = second - first;
        double absAvg = 0;
        for (double d : deltas) {
            absAvg += Math.abs(d);
        }
        absAvg /= deltas.size();
        double threshold = Math.max(0.06, absAvg * 0.30);
        String trend;
        if (gap < -threshold) {
            trend = "heating";
        } else if (gap > threshold) {
            trend = "cooling";
        } else {
            trend = "stable";
        }
        return new IndexDiffSignal(trend, gap, deltas);
    }

    static boolean hasRecentHitPulse(List<Double> indexValues) {
        if (indexValues == null || indexValues.size() < 2) {
            return false;
        }
        int from = Math.max(0, indexValues.size() - 4);
        for (int i = from + 1; i < indexValues.size(); i++) {
            if (indexValues.get(i) - indexValues.get(i - 1) >= HIT_PULSE) {
                return true;
            }
        }
        return false;
    }

    static boolean isRare(String ratio, double prior, double maxPrior) {
        if (ratio == null) {
            return true;
        }
        if (maxPrior > 0 && prior < MIN_PRIOR_RATIO * maxPrior) {
            return true;
        }
        return isExtremeTriple(ratio);
    }

    private static boolean isExtremeTriple(String ratio) {
        String[] p = ratio.split(":");
        if (p.length != 3) {
            return false;
        }
        try {
            int a = Integer.parseInt(p[0].trim());
            int b = Integer.parseInt(p[1].trim());
            int c = Integer.parseInt(p[2].trim());
            return a >= 5 || b >= 5 || c >= 5 || a + b + c != 6;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static BucketScore matchBucket(String feature, String value, List<BucketScore> scores) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (BucketScore s : scores) {
            if (v.equals(s.ratio)) {
                return s;
            }
        }
        if (v.matches("\\d+-\\d+") && feature != null && MERGEABLE_FEATURES.contains(feature)) {
            int dash = v.indexOf('-');
            try {
                int low = Integer.parseInt(v.substring(0, dash));
                int high = Integer.parseInt(v.substring(dash + 1));
                BucketScore best = null;
                for (BucketScore s : scores) {
                    try {
                        int n = Integer.parseInt(s.ratio);
                        if (n >= low && n <= high && (best == null || s.score > best.score)) {
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

    private static List<String> mergeAlternatives(String feature, List<String> llmAlts, BucketScore top,
        List<BucketScore> eligible, List<BucketScore> scores, String rawValue, String mergedValue) {
        String cover = mergedValue == null || mergedValue.isBlank() ? top.ratio : mergedValue;
        List<String> alternatives = new ArrayList<>();
        double maxPrior = 0;
        for (BucketScore s : scores) {
            maxPrior = Math.max(maxPrior, s.prior);
        }

        if (llmAlts != null) {
            for (String a : llmAlts) {
                tryAddAlt(alternatives, a, cover, scores, false);
            }
        }
        if (rawValue != null && !rawValue.isBlank()) {
            BucketScore rejected = matchBucket(feature, rawValue, scores);
            if (rejected != null && !rejected.rare) {
                tryAddAlt(alternatives, rejected.ratio, cover, scores, true);
            }
        }
        BucketScore mode = highestPrior(scores);
        if (mode != null) {
            tryAddAlt(alternatives, mode.ratio, cover, scores, true);
        }
        if ("ratio012".equals(feature) || "threeZone".equals(feature)) {
            tryAddAlt(alternatives, "2:2:2", cover, scores, true);
        }
        List<BucketScore> byPrior = new ArrayList<>(scores);
        byPrior.sort(Comparator.comparingDouble(BucketScore::prior).reversed()
            .thenComparing(BucketScore::ratio));
        for (BucketScore s : byPrior) {
            if (s.rare || (maxPrior > 1e-9 && s.prior < 0.20 * maxPrior && !s.highPrior && !s.reboundWindow)) {
                continue;
            }
            tryAddAlt(alternatives, s.ratio, cover, scores, false);
        }
        for (BucketScore s : eligible) {
            tryAddAlt(alternatives, s.ratio, cover, scores, false);
        }
        for (BucketScore s : scores) {
            if (s.reboundWindow) {
                tryAddAlt(alternatives, s.ratio, cover, scores, true);
            }
        }
        alternatives.removeIf(a -> coveredBy(cover, a));
        for (BucketScore s : byPrior) {
            if (s.rare || (maxPrior > 1e-9 && s.prior < 0.20 * maxPrior && !s.highPrior && !s.reboundWindow)) {
                continue;
            }
            tryAddAlt(alternatives, s.ratio, cover, scores, false);
        }
        ensureClosedSetAlts(feature, cover, alternatives);
        alternatives.removeIf(a -> coveredBy(cover, a));
        return alternatives;
    }

    private static BucketScore highestPrior(List<BucketScore> scores) {
        BucketScore mode = null;
        for (BucketScore s : scores) {
            if (!s.rare && (mode == null || s.prior > mode.prior)) {
                mode = s;
            }
        }
        return mode;
    }

    private static void tryAddAlt(List<String> alternatives, String ratio, String cover,
        List<BucketScore> scores, boolean prepend) {
        if (ratio == null || ratio.isBlank() || coveredBy(cover, ratio) || alternatives.contains(ratio)) {
            return;
        }
        BucketScore b = matchBucket(null, ratio, scores);
        if (b != null && b.rare) {
            return;
        }
        if (prepend) {
            alternatives.add(0, ratio);
        } else if (alternatives.size() < MAX_ALTERNATIVES) {
            alternatives.add(ratio);
            return;
        } else {
            return;
        }
        if (alternatives.size() > MAX_ALTERNATIVES) {
            alternatives.subList(MAX_ALTERNATIVES, alternatives.size()).clear();
        }
    }

    private static boolean coveredBy(String cover, String candidate) {
        if (cover == null || candidate == null) {
            return false;
        }
        if (cover.equals(candidate)) {
            return true;
        }
        return FeatureForecastHitUtils.matches(cover, candidate);
    }

    private static void ensureClosedSetAlts(String feature, String value, List<String> alternatives) {
        if (feature == null || value == null) {
            return;
        }
        List<String> universe = switch (feature) {
            case "blueOddEven" -> List.of("奇", "偶");
            case "blueBigSmall" -> List.of("大", "小");
            case "blueRatio012" -> List.of("0路", "1路", "2路");
            case "blueBigSmallOddEven" -> List.of("小奇", "小偶", "大奇", "大偶");
            default -> List.of();
        };
        if (universe.isEmpty()) {
            return;
        }
        for (String v : universe) {
            if (coveredBy(value, v) || alternatives.contains(v)) {
                continue;
            }
            if (alternatives.size() < MAX_ALTERNATIVES) {
                alternatives.add(v);
            } else {
                alternatives.set(alternatives.size() - 1, v);
            }
        }
    }

    private static String maybeMergeAdjacent(String feature, BucketScore top, List<BucketScore> ranked) {
        if (feature == null || ranked.isEmpty()) {
            return top.ratio;
        }
        if ("sumRange".equals(feature)) {
            return maybeMergeSumRanges(top, ranked);
        }
        if (!MERGEABLE_FEATURES.contains(feature)) {
            return top.ratio;
        }
        int topN;
        try {
            topN = Integer.parseInt(top.ratio);
        } catch (NumberFormatException e) {
            return top.ratio;
        }
        int lo = topN;
        int hi = topN;
        int span = "sumTail".equals(feature) ? 10 : Integer.MAX_VALUE / 4;
        int neighbor = "span".equals(feature) ? 3 : 2;
        for (BucketScore s : ranked) {
            if (s.ratio.equals(top.ratio) || s.score < top.score * 0.50) {
                continue;
            }
            try {
                int n = Integer.parseInt(s.ratio);
                int dist = "sumTail".equals(feature)
                    ? Math.min(Math.abs(n - topN), span - Math.abs(n - topN))
                    : Math.abs(n - topN);
                if (dist <= neighbor && dist > 0) {
                    if ("sumTail".equals(feature) && Math.abs(n - topN) > neighbor) {
                        continue;
                    }
                    lo = Math.min(lo, n);
                    hi = Math.max(hi, n);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return lo == hi ? top.ratio : lo + "-" + hi;
    }

    private static String maybeMergeSumRanges(BucketScore top, List<BucketScore> ranked) {
        int[] topR = parseClosedRange(top.ratio);
        if (topR == null) {
            return top.ratio;
        }
        int lo = topR[0];
        int hi = topR[1];
        int merged = 1;
        for (BucketScore s : ranked) {
            if (s.ratio.equals(top.ratio) || s.score < top.score * 0.55) {
                continue;
            }
            int[] r = parseClosedRange(s.ratio);
            if (r == null) {
                continue;
            }
            if (r[1] + 1 >= lo && r[0] - 1 <= hi) {
                lo = Math.min(lo, r[0]);
                hi = Math.max(hi, r[1]);
                merged++;
            }
        }
        return merged < 2 ? top.ratio : lo + "-" + hi;
    }

    private static int[] parseClosedRange(String s) {
        if (s == null || !s.matches("\\d+-\\d+")) {
            return null;
        }
        int dash = s.indexOf('-');
        try {
            int a = Integer.parseInt(s.substring(0, dash));
            int b = Integer.parseInt(s.substring(dash + 1));
            return new int[] {Math.min(a, b), Math.max(a, b)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void fillItem(FeatureForecastItem item, BucketScore top, String value,
        List<BucketScore> scores, List<String> alternatives) {
        double second = 0;
        for (BucketScore s : scores) {
            if (!s.ratio.equals(top.ratio)) {
                second = s.score;
                break;
            }
        }
        double confidence = top.score <= 1e-9 ? 0.0 : Math.min(1.0, Math.max(0.0, (top.score - second) / top.score));
        if (!top.dueWindow) {
            confidence = Math.min(confidence, 0.45);
        }
        item.setValue(value);
        item.setAlternatives(alternatives);
        item.setConfidence(round3(confidence));
        item.setGapTrend(top.gapTrend);
        item.setPredictedGap(round3(top.predictedGap));
        item.setCurrentOmission(top.currentOmission);
        item.setEta(top.eta);
        item.setDueWindow(top.dueWindow);
        item.setScore(round3(top.score));
        item.setRecentGaps(top.recentGaps);
        item.setReason(buildReason(top, value));
    }

    private static String buildReason(BucketScore top, String value) {
        String trendCn = switch (top.gapTrend) {
            case "heating" -> "走热(指数差值收缩，未来倾向命中)";
            case "cooling" -> "走冷(指数差值扩张，开出概率低)";
            case "stable" -> "指数差值/间隔平稳，按节奏择时";
            default -> "指数差值样本不足";
        };
        String gaps = top.recentGaps == null || top.recentGaps.isEmpty()
            ? "无"
            : top.recentGaps.stream().map(String::valueOf).collect(Collectors.joining(","));
        String deltas = top.recentIndexDeltas == null || top.recentIndexDeltas.isEmpty()
            ? "无"
            : top.recentIndexDeltas.stream()
                .map(d -> String.format(Locale.ROOT, "%.3f", d))
                .collect(Collectors.joining(","));
        String due = Boolean.TRUE.equals(top.dueWindow) ? "接入窗口内" : "非窗口";
        String extra = "";
        if (top.reboundWindow) {
            extra += "；长冷后短间隔回补窗口";
        }
        if (top.clusterContinue) {
            extra += "；高频黏性连出(均漏短且近间隔1-2，允许刚出再主推)";
        } else if (top.highPrior && top.currentOmission == 0) {
            extra += "；理论众数刚出仍主推";
        }
        if (top.heatBroken) {
            extra += "；近端热度已断档(当前遗漏超过末次间隔)";
        }
        return String.format(Locale.ROOT,
            "主推%s：近指数差值[%s]→%s，近间隔[%s]，G=%.1f，当前遗漏%d，eta=%d（%s），score=%.3f%s",
            value, deltas, trendCn, gaps, top.predictedGap, top.currentOmission, top.eta, due, top.score, extra);
    }

    private static JSONObject parseRoot(String snapshotJson) {
        JSONObject root = JSON.parseObject(snapshotJson);
        if (root == null) {
            throw new IllegalArgumentException("形态快照不是合法 JSON");
        }
        return root;
    }

    static double gapSlope(List<Integer> gaps) {
        List<Double> ys = new ArrayList<>(gaps.size());
        for (Integer g : gaps) {
            ys.add(g.doubleValue());
        }
        return doubleSlope(ys);
    }

    static double doubleSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) {
            return 0;
        }
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i);
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

    private static double mean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static double median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static int minOf(List<Integer> values) {
        int min = Integer.MAX_VALUE;
        for (int v : values) {
            min = Math.min(min, v);
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static <T> List<T> tail(List<T> list, int n) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, list.size() - n);
        return new ArrayList<>(list.subList(from, list.size()));
    }

    private static List<Integer> toIntList(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            out.add(arr.getIntValue(i));
        }
        return out;
    }

    private static List<Double> toDoubleList(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<Double> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            out.add(arr.getDoubleValue(i));
        }
        return out;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    record DroughtContext(boolean outlierLast, boolean canCluster, int lastGap, List<Integer> typicalGaps) {
        static DroughtContext none() {
            return new DroughtContext(false, false, 0, List.of());
        }
    }

    record IndexDiffSignal(String trend, double deltaGap, List<Double> recentDeltas) {
        static IndexDiffSignal unknown() {
            return new IndexDiffSignal("unknown", 0, List.of());
        }
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
        List<Integer> recentGaps,
        boolean reboundWindow,
        boolean clusterContinue,
        boolean heatBroken,
        boolean rare,
        boolean sampleOk,
        List<Double> indexTail,
        boolean hitPulse,
        String indexDiffTrend,
        double indexDeltaGap,
        List<Double> recentIndexDeltas,
        boolean highPrior
    ) {
    }
}
