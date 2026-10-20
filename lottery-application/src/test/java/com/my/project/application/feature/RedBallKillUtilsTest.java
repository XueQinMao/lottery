package com.my.project.application.feature;
import com.my.project.application.support.analyse.KillBallUtils;
import com.my.project.application.support.analyse.RedBallKillUtils;
import com.my.project.application.support.analyse.UnifiedKillBallRules;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
class RedBallKillUtilsTest {
    static List<HistoryRecord> history() {
        var history = IntStream.range(0, 100).mapToObj(i -> HistoryRecord.builder().period("P" + (200 - i))
            .openDate(LocalDate.of(2026, 10, 18).minusDays(i))
            .num1(1).num2(2).num3(3).num4(4).num5(6).num6(7).special(i % 16 + 1).build()).toList();
        var latest = history.getFirst();
        latest.setNum1(5); latest.setNum2(21); latest.setNum3(22);
        latest.setNum4(23); latest.setNum5(28); latest.setNum6(31); latest.setSpecial(14);
        return history;
    }
    @Test void preservesActiveLegacyArithmeticAndIntegratesNewRules() {
        var h = history();
        var rules = RedBallKillUtils.calculateByRule(h, LocalDate.of(2026, 10, 20));
        assertEquals(21, rules.size());
        assertEquals(List.of(30), rules.get("和值余100（大于33减一次33）"));
        assertEquals(List.of(31), rules.get("和值余100加1（大于33减一次33）"));
        assertEquals(List.of(18), rules.get("奇偶各组首尾均值（奇除2、偶除3）"));
        var result = KillBallUtils.calculate(h);
        var red = result.getHardKillRed();
        assertTrue(red.stream().anyMatch(b -> b.getBall() == 28 && b.getSource().contains("R02")));
        // 同号时保留原有规则来源，不要求新增来源覆盖它。
        assertTrue(red.stream().anyMatch(b -> b.getBall() == 15));
        var original = RedBallKillUtils.calculate(h);
        assertEquals(original.toString(), red.subList(0, original.size()).toString());
        assertEquals(red.size(), red.stream().map(b -> b.getBall()).distinct().count());
    }
    @Test void preservesOriginalRawOutputsWhenAppendingRules() {
        var h = history(); var last = h.getFirst();
        last.setNum1(28); last.setNum2(29); last.setNum3(30);last.setNum4(31);last.setNum5(32);last.setNum6(33);
        var rules = RedBallKillUtils.calculateByRule(h, LocalDate.of(2026, 10, 20));
        assertEquals(List.of(50), rules.get("和值余100（大于33减一次33）"));
        assertEquals(List.of(51), rules.get("和值余100加1（大于33减一次33）"));
        assertEquals(List.of(31, 20), rules.get("奇偶各组首尾均值（奇除2、偶除3）"));
        var original = RedBallKillUtils.calculate(h);
        var combined = KillBallUtils.calculate(h).getHardKillRed();
        assertEquals(original.toString(), combined.subList(0, original.size()).toString());
        assertTrue(combined.stream().anyMatch(b -> b.getBall() == 50));
        assertTrue(combined.stream().anyMatch(b -> b.getBall() == 51));
    }
    @Test void debugWinningNumbersNeverInfluenceResultsAndHistoricalDatesAreExplicit() {
        var h = history(); var normal = KillBallUtils.calculate(h);
        var debug = KillBallUtils.calculate(h, Triple.of(List.of(12, 22, 23, 25, 27, 30), "TARGET", 4));
        assertEquals(normal.toString(), debug.toString());
        var scheduled = KillBallUtils.calculate(h, null, LocalDate.of(2026, 10, 22));
        assertTrue(scheduled.getHardKillRed().stream().anyMatch(b -> b.getBall() == 12 && b.getSource().contains("开奖日取余+10")));
        assertEquals(normal.getHardKillBlue().toString(), scheduled.getHardKillBlue().toString());
        assertThrows(IllegalArgumentException.class, () -> KillBallUtils.calculate(h, null, LocalDate.of(2026, 10, 18)));
    }
    @Test void allRulesHaveUniqueStableIdsAndDoNotContainDisabledStrategies() {
        var rules = UnifiedKillBallRules.catalog();
        assertEquals(12, rules.size());
        assertEquals(rules.size(), rules.stream().map(UnifiedKillBallRules.Rule::id).distinct().count());
        assertEquals(10, UnifiedKillBallRules.from(UnifiedKillBallRules.TOUTIAO).size());
        assertEquals(2, UnifiedKillBallRules.from(UnifiedKillBallRules.XIAOHONGSHU).size());
        assertFalse(rules.stream().anyMatch(r -> r.id().equals("R05") || r.id().equals("X-R05")));
    }
}
