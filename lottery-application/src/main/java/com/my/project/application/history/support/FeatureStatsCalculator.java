package com.my.project.application.history.support;

import com.my.project.application.history.vo.FeatureStatsVo;
import com.my.project.application.support.LotteryFeatureTrendUtils;
import com.my.project.application.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.my.project.application.history.support.TrendStatistics.average;

public final class FeatureStatsCalculator {
    /** 历史走势图的质合口径将 1 归入合数。 */
    private static final Set<Integer> PRIMES = Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31);

    private FeatureStatsCalculator() { }

    public static FeatureStatsVo calculate(List<HistoryRecord> latest) {
        List<HistoryRecord> chronological = latest.reversed();

        List<String> periods = new ArrayList<>(chronological.size());
        List<Integer> sums = new ArrayList<>(chronological.size());
        List<Integer> spans = new ArrayList<>(chronological.size());
        List<Integer> primeCounts = new ArrayList<>(chronological.size());
        List<String> primeRatios = new ArrayList<>(chronological.size());
        List<Integer> redOddCounts = new ArrayList<>(chronological.size());
        List<String> redOddEvenRatios = new ArrayList<>(chronological.size());
        List<Integer> blueOddFlags = new ArrayList<>(chronological.size());
        List<String> blueOddEvenLabels = new ArrayList<>(chronological.size());
        List<List<Integer>> tailCounts = new ArrayList<>(10);
        for (int t = 0; t < 10; t++) {
            tailCounts.add(new ArrayList<>(chronological.size()));
        }

        for (HistoryRecord r : chronological) {
            List<Integer> reds =
                Arrays.asList(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
            int sum = reds.stream().mapToInt(Integer::intValue).sum();
            int span = Collections.max(reds) - Collections.min(reds);
            int primeCount = primeCount(reds);
            int oddCount = (int)reds.stream().filter(n -> n % 2 == 1).count();
            boolean blueOdd = r.getSpecial() != null && r.getSpecial() % 2 == 1;
            int[] tails = new int[10];
            for (Integer ball : reds) {
                if (ball != null) {
                    tails[Math.floorMod(ball, 10)]++;
                }
            }

            periods.add(r.getPeriod());
            sums.add(sum);
            spans.add(span);
            primeCounts.add(primeCount);
            primeRatios.add(primeCount + ":" + (6 - primeCount));
            redOddCounts.add(oddCount);
            redOddEvenRatios.add(oddCount + ":" + (6 - oddCount));
            blueOddFlags.add(blueOdd ? 1 : 0);
            blueOddEvenLabels.add(blueOdd ? "奇" : "偶");
            for (int t = 0; t < 10; t++) {
                tailCounts.get(t).add(tails[t]);
            }
        }

        List<Integer> sumDeltas = new ArrayList<>(sums.size());
        for (int i = 0; i < sums.size(); i++) {
            if (i == 0) {
                sumDeltas.add(null);
            } else {
                sumDeltas.add(sums.get(i) - sums.get(i - 1));
            }
        }

        return FeatureStatsVo.builder().periods(periods).sumValues(sums).sumAvg(average(sums))
            .sumDeltaValues(sumDeltas).sumDeltaAvg(average(sumDeltas)).spanValues(spans)
            .spanAvg(average(spans)).primeCounts(primeCounts).primeRatios(primeRatios).primeAvg(average(primeCounts))
            .redOddCounts(redOddCounts).redOddEvenRatios(redOddEvenRatios).redOddAvg(average(redOddCounts))
            .blueOddFlags(blueOddFlags).blueOddEvenLabels(blueOddEvenLabels).blueOddAvg(average(blueOddFlags))
            .tailCounts(tailCounts)
            .tailAvgs(tailCounts.stream().map(TrendStatistics::average).toList())
            .occurrenceFeatures(buildOccurrenceFeatures(chronological)).build();
    }

    public static int primeCount(List<Integer> reds) {
        return (int) reds.stream().filter(PRIMES::contains).count();
    }

    private static List<FeatureStatsVo.OccurrenceFeature> buildOccurrenceFeatures(List<HistoryRecord> records) {
        return Arrays.stream(FeatureKind.values()).filter(kind -> !kind.isBlue())
            .map(kind -> occurrenceFeature(records, kind))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static FeatureStatsVo.OccurrenceFeature occurrenceFeature(List<HistoryRecord> records, FeatureKind kind) {
        List<String> actualValues = records.stream().map(record -> LotteryFeatureTrendUtils.extract(
            List.of(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(),
                record.getNum5(), record.getNum6()), kind)).toList();
        var series = LotteryFeatureTrendUtils.buckets(kind).stream()
            .map(bucket -> {
                List<Integer> hits = actualValues.stream().map(value -> bucket.equals(value) ? 1 : 0).toList();
                int hitCount = hits.stream().mapToInt(Integer::intValue).sum();
                return FeatureStatsVo.OccurrenceSeries.builder().value(bucket).hits(hits)
                    .hitCount(hitCount).hitRate((double) hitCount / records.size()).build();
            })
            .collect(Collectors.toCollection(ArrayList::new));
        return FeatureStatsVo.OccurrenceFeature.builder().code(kind.getCode()).label(kind.getLabel())
            .series(series).build();
    }
}
