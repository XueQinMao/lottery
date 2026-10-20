package com.my.project.application.history;

import com.my.project.application.history.support.NumberTrendCalculator;
import com.my.project.application.support.LotteryTrendUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NumberTrendCalculatorTest {
    @Test
    void preservesPriorOmissionsWhenChangingTheDisplayedWindow() {
        var records = List.of(record("2026001", 16, 1, 2, 3, 4, 5, 33),
            record("2026002", 1, 2, 3, 4, 5, 6, 7),
            record("2026003", 1, 2, 3, 4, 5, 6, 7),
            record("2026004", 16, 33, 1, 2, 3, 4, 5));
        var full = NumberTrendCalculator.calculate(records, 4);
        var shortWindow = NumberTrendCalculator.calculate(records, 2);
        assertEquals(List.of("2026003", "2026004"), shortWindow.rows().stream().map(r -> r.period()).toList());
        assertEquals(full.rows().subList(2, 4), shortWindow.rows());
        assertEquals(List.of(1, 2, 3, 4, 5, 33), shortWindow.rows().getLast().reds());
        assertEquals(2, shortWindow.rows().getFirst().redOmissions().getFirst());
        assertEquals(2, shortWindow.rows().getFirst().blueOmissions().getLast());
        assertEquals(0, shortWindow.rows().getLast().blueOmissions().getLast());
        assertEquals(4, shortWindow.rows().getLast().redOmissions().get(31));
        assertEquals(33, full.redStats().size());
        assertEquals(16, full.blueStats().size());
        assertEquals(2, full.blueStats().getFirst().hitCount());
        assertEquals(2, full.blueStats().getFirst().maxConsecutive());
        assertEquals(1.0, full.blueStats().getFirst().averageOmission());
        assertNull(full.redStats().get(31).averageOmission());
        assertEquals(4, full.redStats().get(31).currentOmission());
    }

    @Test
    void matchesExistingOmissionRulesForEveryBallAndNeverLeaksLaterDraws() {
        var records = new ArrayList<HistoryRecord>();
        for (int i = 0; i < 80; i++) {
            int start = i % 28 + 1;
            records.add(record(String.valueOf(2026001 + i), i % 16 + 1, start, start + 1, start + 2, start + 3, start + 4, start + 5));
        }
        var result = NumberTrendCalculator.calculate(records, 30);
        var redDraws = records.stream().map(r -> Set.of(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6())).toList();
        var blueDraws = records.stream().map(r -> Set.of(r.getSpecial())).toList();
        for (int ball = 1; ball <= 33; ball++) {
            var expected = LotteryTrendUtils.calcOmissionSequence(redDraws, ball).subList(50, 80);
            int index = ball - 1;
            assertEquals(expected, result.rows().stream().map(r -> r.redOmissions().get(index)).toList());
        }
        for (int ball = 1; ball <= 16; ball++) {
            var expected = LotteryTrendUtils.calcOmissionSequence(blueDraws, ball).subList(50, 80);
            int index = ball - 1;
            assertEquals(expected, result.rows().stream().map(r -> r.blueOmissions().get(index)).toList());
        }
        var prefix = NumberTrendCalculator.calculate(new ArrayList<>(records.subList(0, 60)), 60);
        var all = NumberTrendCalculator.calculate(records, 80);
        assertEquals(prefix.rows(), all.rows().subList(0, 60));
    }

    @Test
    void handlesEmptyAndSingleDrawWithoutInvalidAverages() {
        var empty = NumberTrendCalculator.calculate(List.of(), 100);
        assertTrue(empty.rows().isEmpty());
        var single = NumberTrendCalculator.calculate(List.of(record("2026001", 16, 1, 2, 3, 4, 5, 6)), 100);
        assertEquals(1, single.blueStats().getLast().hitCount());
        assertEquals(0.0, single.blueStats().getLast().averageOmission());
        assertEquals(1, single.redStats().getLast().maxOmission());
        assertThrows(IllegalArgumentException.class, () -> NumberTrendCalculator.calculate(List.of(), 0));
    }

    private static HistoryRecord record(String period, int blue, int... reds) {
        return HistoryRecord.builder().period(period).special(blue).num1(reds[0]).num2(reds[1]).num3(reds[2])
            .num4(reds[3]).num5(reds[4]).num6(reds[5]).build();
    }
}
