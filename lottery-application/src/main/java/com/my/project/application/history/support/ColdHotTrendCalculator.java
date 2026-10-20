package com.my.project.application.history.support;

import com.my.project.application.history.vo.ColdHotTrendVo;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.my.project.application.history.support.TrendStatistics.average;

public final class ColdHotTrendCalculator {
    private ColdHotTrendCalculator() { }

    public static ColdHotTrendVo calculate(List<HistoryRecord> latest, int size) {
        if (latest == null || latest.isEmpty() || latest.size() <= 30) {
            throw new IllegalStateException("历史开奖记录不足，至少需要 31 期才能计算冷温热趋势");
        }
        // latest 为降序（最新在前）。可展示的样本为 latest[0..size-1]，
        // 对下标 i 的期，其前 30 期为 latest[i+1..i+30]。
        int displayCount = Math.min(size, latest.size() - 30);

        // 按期号升序（最旧 → 最新）输出
        List<String> periods = new ArrayList<>(displayCount);
        List<Integer> redHotCounts = new ArrayList<>(displayCount);
        List<Integer> redWarmCounts = new ArrayList<>(displayCount);
        List<Integer> redColdCounts = new ArrayList<>(displayCount);
        List<Integer> blueBandFlags = new ArrayList<>(displayCount);
        List<String> blueBandLabels = new ArrayList<>(displayCount);
        String basis = null;

        // 从最旧的展示期向最新遍历：i 从 displayCount-1 递减到 0
        for (int i = displayCount - 1; i >= 0; i--) {
            List<HistoryRecord> prior30 = latest.subList(i + 1, i + 31);
            var bo = ColdHotAnalysisUtils.calculate(prior30);
            if (basis == null) {
                basis = bo.getBasis();
            }
            Set<Integer> redHot = new HashSet<>(bo.getRedHotBalls());
            Set<Integer> redWarm = new HashSet<>(bo.getRedWarmBalls());
            Set<Integer> redCold = new HashSet<>(bo.getRedColdBalls());
            Set<Integer> blueHot = new HashSet<>(bo.getBlueHotBalls());
            Set<Integer> blueWarm = new HashSet<>(bo.getBlueWarmBalls());

            HistoryRecord cur = latest.get(i);
            List<Integer> reds =
                Arrays.asList(cur.getNum1(), cur.getNum2(), cur.getNum3(), cur.getNum4(), cur.getNum5(), cur.getNum6());
            int hot = 0, warm = 0, cold = 0;
            for (Integer r : reds) {
                if (r == null) continue;
                if (redHot.contains(r)) hot++;
                else if (redWarm.contains(r)) warm++;
                else if (redCold.contains(r)) cold++;
            }
            int blueBand;
            String blueLabel;
            Integer blue = cur.getSpecial();
            if (blue != null && blueHot.contains(blue)) {
                blueBand = 2;
                blueLabel = "热";
            } else if (blue != null && blueWarm.contains(blue)) {
                blueBand = 1;
                blueLabel = "温";
            } else {
                blueBand = 0;
                blueLabel = "冷";
            }

            periods.add(cur.getPeriod());
            redHotCounts.add(hot);
            redWarmCounts.add(warm);
            redColdCounts.add(cold);
            blueBandFlags.add(blueBand);
            blueBandLabels.add(blueLabel);
        }

        return ColdHotTrendVo.builder()
            .periods(periods)
            .redHotCounts(redHotCounts).redWarmCounts(redWarmCounts).redColdCounts(redColdCounts)
            .redHotAvg(average(redHotCounts)).redWarmAvg(average(redWarmCounts)).redColdAvg(average(redColdCounts))
            .blueBandFlags(blueBandFlags).blueBandLabels(blueBandLabels)
            .blueHotAvg(average(blueBandFlags.stream().map(f -> f == 2 ? 1 : 0).toList()))
            .blueWarmAvg(average(blueBandFlags.stream().map(f -> f == 1 ? 1 : 0).toList()))
            .blueColdAvg(average(blueBandFlags.stream().map(f -> f == 0 ? 1 : 0).toList()))
            .basis(basis)
            .build();
    }

}
