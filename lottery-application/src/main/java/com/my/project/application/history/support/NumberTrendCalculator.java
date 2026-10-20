package com.my.project.application.history.support;

import com.my.project.application.feature.support.RedTrendPatterns;
import com.my.project.application.history.vo.NumberTrendVo;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import com.my.project.application.history.vo.NumberTrendVo.Row;
import com.my.project.application.history.vo.NumberTrendVo.NumberStats;

public final class NumberTrendCalculator {
    private NumberTrendCalculator() { }

    public static NumberTrendVo calculate(List<HistoryRecord> oldestFirst, int sampleSize) {
        if (sampleSize < 1 || sampleSize > 100) throw new IllegalArgumentException("展示期数应为 1–100");
        int firstDisplay = Math.max(0, oldestFirst.size() - sampleSize);
        int[] redOmissions = new int[33];
        int[] blueOmissions = new int[16];
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < oldestFirst.size(); index++) {
            HistoryRecord record = oldestFirst.get(index);
            Set<Integer> reds = Set.of(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(), record.getNum6());
            advance(redOmissions, reds);
            advance(blueOmissions, Set.of(record.getSpecial()));
            if (index >= firstDisplay) {
                rows.add(new Row(record.getPeriod(), Objects.toString(record.getOpenDate(), ""),
                    reds.stream().sorted().toList(), record.getSpecial(),
                    Arrays.stream(redOmissions).boxed().toList(), Arrays.stream(blueOmissions).boxed().toList()));
            }
        }
        var redCandidates = RedTrendPatterns.candidateNumbers(rows.stream().map(Row::reds).toList());
        return new NumberTrendVo(rows, statistics(rows, 33, Row::redOmissions), statistics(rows, 16, Row::blueOmissions),
            redCandidates);
    }

    private static void advance(int[] omissions, Set<Integer> winning) {
        for (int index = 0; index < omissions.length; index++) {
            omissions[index] = winning.contains(index + 1) ? 0 : omissions[index] + 1;
        }
    }

    private static List<NumberStats> statistics(List<Row> rows, int count, Function<Row, List<Integer>> omissions) {
        return IntStream.range(0, count).mapToObj(index -> {
            int hits = 0, maxOmission = 0, maxConsecutive = 0, streak = 0, current = 0;
            for (Row row : rows) {
                current = omissions.apply(row).get(index);
                if (current == 0) hits++;
                streak = current == 0 ? streak + 1 : 0;
                maxConsecutive = Math.max(maxConsecutive, streak);
                maxOmission = Math.max(maxOmission, current);
            }
            Double average = hits == 0 ? null : (double) (rows.size() - hits) / hits;
            return new NumberStats(index + 1, hits, average, maxOmission, maxConsecutive, current);
        }).toList();
    }
}
