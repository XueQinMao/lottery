package com.my.project.service.support;

import cn.hutool.core.collection.CollectionUtil;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;

import java.util.*;

/**
 * OmissionUtils
 *
 * @author 刘强
 * @version 2026/08/25 20:15
 **/
public class OmissionUtils {

    /**
     * 指定指定球的遗漏趋势
     *
     * @param historyRecords 历史开间记录
     * @param type           rea or blue
     * @param ball           01 02 。。。
     * @return
     */
    public static TrendAnalysisVo omissionBallAnalyzer(List<HistoryRecord> historyRecords, String type, int ball) {
        // getRecordsEndingAt 为降序（截止期→更旧），趋势计算需要升序（最旧→最新）
        List<HistoryRecord> chronological = new ArrayList<>(historyRecords);
        Collections.reverse(chronological);
        List<String> periods = new ArrayList<>(chronological.size());
        List<Set<Integer>> draws = new ArrayList<>(chronological.size());
        for (HistoryRecord r : chronological) {
            periods.add(r.getPeriod());
            if ("red".equals(type)) {
                draws.add(Set.of(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6()));
            } else {
                draws.add(Set.of(r.getSpecial()));
            }
        }

        LotteryTrendUtils.TrendAnalysisResult result = LotteryTrendUtils.analyze(draws, ball);
        LotteryTrendUtils.TrendStats stats = result.getStats();
        return TrendAnalysisVo.builder().ballType(type).ball(ball).periods(periods).omissions(result.getOmissions())
            .indexValues(result.getIndexValues()).ma5(result.getMa5()).ma10(result.getMa10()).ma20(result.getMa20())
            .arrangement(result.getArrangement()).ma5Slope(result.getMa5Slope()).phase(result.getPhase()).stats(
                TrendAnalysisVo.Stats.builder().maxOmission(stats.getMaxOmission()).avgOmission(stats.getAvgOmission())
                    .currentOmission(stats.getCurrentOmission()).indexMean(stats.getIndexMean())
                    .hitCount(stats.getHitCount()).totalPeriods(stats.getTotalPeriods()).build()).build();
    }

    /**
     * 特征遗漏
     * @param feature
     * @param ratio
     * @param latestNewestFirst
     * @return
     */
    public static PatternTrendVo omissionFeatureTrendAnalyzer(String feature, String ratio,
        List<HistoryRecord> latestNewestFirst) {
        LotteryFeatureTrendUtils.FeatureKind kind = LotteryFeatureTrendUtils.FeatureKind.fromCode(feature);
        String normalizedRatio = LotteryFeatureTrendUtils.normalizeBucket(kind, ratio);
        if (CollectionUtil.isEmpty(latestNewestFirst)) {
            throw new IllegalStateException("无可用的历史开奖记录");
        }
        List<HistoryRecord> chronological = new ArrayList<>(latestNewestFirst);
        Collections.reverse(chronological);

        List<String> periods = new ArrayList<>(chronological.size());
        List<Boolean> hits = new ArrayList<>(chronological.size());
        List<String> actuals = new ArrayList<>(chronological.size());
        Map<String, Integer> ratioCounts = new LinkedHashMap<>();
        for (String bucket : LotteryFeatureTrendUtils.buckets(kind)) {
            ratioCounts.put(bucket, 0);
        }
        for (HistoryRecord r : chronological) {
            periods.add(r.getPeriod());
            String actual = LotteryFeatureTrendUtils.extract(redsOf(r), r.getSpecial(), kind);
            actuals.add(actual);
            hits.add(normalizedRatio.equals(actual));
            ratioCounts.merge(actual, 1, Integer::sum);
        }
        double p = LotteryFeatureTrendUtils.theoreticalProb(kind, normalizedRatio);
        LotteryPatternTrendUtils.PatternTrendResult result = LotteryPatternTrendUtils.analyze(hits, p);
        LotteryPatternTrendUtils.PatternTrendStats stats = result.getStats();

        HistoryRecord last = chronological.get(chronological.size() - 1);
        String lastRatio = actuals.get(actuals.size() - 1);

        List<PatternTrendVo.RatioOption> options = new ArrayList<>();
        for (String optRatio : LotteryFeatureTrendUtils.buckets(kind)) {
            int optHits = ratioCounts.getOrDefault(optRatio, 0);
            double optP = LotteryFeatureTrendUtils.theoreticalProb(kind, optRatio);
            double optTheory = chronological.size() * optP;
            List<Boolean> optHitFlags = new ArrayList<>(actuals.size());
            for (String actual : actuals) {
                optHitFlags.add(optRatio.equals(actual));
            }
            LotteryPatternTrendUtils.PatternTrendResult optResult = LotteryPatternTrendUtils.analyze(optHitFlags, optP);
            LotteryPatternTrendUtils.PatternTrendStats optStats = optResult.getStats();
            options.add(PatternTrendVo.RatioOption.builder().ratio(optRatio).hitCount(optHits)
                .theoreticalProb(LotteryPatternTrendUtils.round6(optP))
                .theoreticalHits(LotteryPatternTrendUtils.round2(optTheory))
                .index(LotteryPatternTrendUtils.round2(optHits - optTheory))
                .currentOmission(optStats.getCurrentOmission()).avgOmission(optStats.getAvgOmission())
                .maxOmission(optStats.getMaxOmission()).omissions(optResult.getOmissions())
                .indexValues(optResult.getIndexValues()).hitIntervals(hitIntervals(optHitFlags)).build());
        }

        return PatternTrendVo.builder().feature(kind.getCode()).featureLabel(kind.getLabel()).ratio(normalizedRatio)
            .periods(periods).hits(result.getHits()).omissions(result.getOmissions())
            .indexValues(result.getIndexValues()).latestPeriod(last.getPeriod()).latestWinning(formatWinning(last))
            .latestRatio(lastRatio).actuals(actuals).stats(
                PatternTrendVo.Stats.builder().maxOmission(stats.getMaxOmission()).avgOmission(stats.getAvgOmission())
                    .currentOmission(stats.getCurrentOmission()).hitCount(stats.getHitCount())
                    .totalPeriods(stats.getTotalPeriods()).theoreticalProb(stats.getTheoreticalProb())
                    .theoreticalHits(stats.getTheoreticalHits()).index(stats.getIndex()).build()).ratioOptions(options)
            .build();
    }

    private static List<Integer> hitIntervals(List<Boolean> hits) {
        List<Integer> intervals = new ArrayList<>();
        int lastHit = -1;
        for (int i = 0; i < hits.size(); i++) {
            if (!Boolean.TRUE.equals(hits.get(i))) {
                continue;
            }
            if (lastHit >= 0) {
                intervals.add(i - lastHit);
            }
            lastHit = i;
        }
        return intervals;
    }

    private static List<Integer> redsOf(HistoryRecord r) {
        return Arrays.asList(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
    }

    private static String formatWinning(HistoryRecord r) {
        return String.format("%02d %02d %02d %02d %02d %02d + %02d", r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(),
            r.getNum5(), r.getNum6(), r.getSpecial());
    }
}
