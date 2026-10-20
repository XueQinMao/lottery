package com.my.project.application.feature;

import com.my.project.application.support.analyse.KillBallUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueBallKillUtilsTest {
    private List<HistoryRecord> history(int latestBlue) {
        var history = IntStream.range(0, 60).mapToObj(i -> HistoryRecord.builder()
            .period("P" + (200 - i)).openDate(LocalDate.of(2026, 10, 18).minusDays(i))
            .num1(3).num2(17).num3(18).num4(19).num5(20).num6(21).special(8).build()).toList();
        // 每个号码都在50期以内出现，避免原有长遗漏规则掩盖新增规则。
        IntStream.rangeClosed(1, 16).forEach(ball -> history.get(10 + ball).setSpecial(ball));
        history.getFirst().setSpecial(latestBlue);
        return history;
    }

    @Test
    void addsSixPeriodRepeatAndThirtyFivePeriodOmissionToMainAggregation() {
        var history = history(1);
        history.get(16).setSpecial(8);
        history.get(4).setSpecial(6);
        history.get(5).setSpecial(6);
        history.get(22).setSpecial(8);
        history.get(35).setSpecial(12);
        history.get(23).setSpecial(8);
        history.get(34).setSpecial(13);
        // 原近10期至少3次规则依然生效，但9不满足近6期重复。
        IntStream.rangeClosed(6, 8).forEach(i -> history.get(i).setSpecial(9));
        var result = KillBallUtils.calculate(history);
        var blue = result.getHardKillBlue();
        assertTrue(blue.stream().anyMatch(item -> item.getBall() == 6 && item.getSource().equals("近6期至少出现2次")));
        assertTrue(blue.stream().anyMatch(item -> item.getBall() == 12 && item.getSource().equals("遗漏35期")));
        assertFalse(blue.stream().anyMatch(item -> item.getBall() == 13));
        assertTrue(blue.stream().anyMatch(item -> item.getBall() == 9 && item.getSource().equals("邪修杀")));
        assertTrue(blue.stream().anyMatch(item -> item.getBall() == 3 && item.getSource().equals("上期开出-红球")));
        assertEquals(blue.size(), blue.stream().map(item -> item.getBall()).distinct().count());
        history.get(5).setSpecial(7);
        assertFalse(KillBallUtils.calculate(history).getHardKillBlue().stream().anyMatch(item -> item.getBall() == 6));
    }

    @Test
    void neighborRuleDoesNotWrapAtOneOrSixteen() {
        for (int latest : List.of(1, 16)) {
            var blue = KillBallUtils.calculate(history(latest)).getHardKillBlue();
            assertEquals(List.of(latest == 1 ? 2 : 15), blue.stream()
                .filter(item -> item.getSource().equals("上期蓝球邻号±1"))
                .map(item -> item.getBall()).toList());
            assertTrue(blue.stream().allMatch(item -> item.getBall() >= 1 && item.getBall() <= 16));
        }
    }
}
