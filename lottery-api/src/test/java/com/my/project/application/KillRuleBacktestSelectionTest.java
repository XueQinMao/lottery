package com.my.project.application;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class KillRuleBacktestSelectionTest {
    @Test
    void catalogContainsOnlyRetainedSourcesAndIntegratedBusinessRules() {
        assertEquals(Set.of("R02", "R04", "R09", "R11", "R14", "B01", "B02", "B03", "B05", "B08"),
                ToutiaoKillRules.catalog().stream().map(ToutiaoKillRules.Rule::id).collect(Collectors.toSet()));
        assertEquals(List.of("X-R04", "X-R06"), XiaohongshuKillRules.catalog().stream().map(ToutiaoKillRules.Rule::id).toList());
        assertEquals(12, ToutiaoKillRules.integratedCatalog().size());
    }

    @Test
    void unionStatisticsDeduplicateOverlapsAndCountCombinedZeroWrongPeriods() {
        var history = List.of(draw("2026001", 1, 20, 21, 22, 23, 24, 25),
                draw("2026002", 4, 1, 2, 3, 7, 8, 9), draw("2026003", 5, 10, 11, 12, 13, 14, 15));
        var rules = List.of(new ToutiaoKillRules.Rule("A", "红一", false, 1, c -> List.of(1, 2)),
                new ToutiaoKillRules.Rule("B", "红二", false, 1, c -> List.of(2, 3)),
                new ToutiaoKillRules.Rule("C", "蓝", true, 1, c -> List.of(4)));
        var bytes = new ByteArrayOutputStream();
        ToutiaoKillRules.backtest(history, 2, new PrintStream(bytes, true, StandardCharsets.UTF_8), rules, "验证");
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("红球错杀个数分布={0=1, 3=1}"));
        assertTrue(output.contains("红球零错杀期=50.00%(1/2) 每期杀号数范围=3—3"));
        assertTrue(output.contains("累计杀号=6 累计错杀球=3"));
        assertTrue(output.contains("蓝球零错杀期=50.00%(1/2)"));
        assertTrue(output.contains("启用规则综合零错杀期=50.00%(1/2)"));
        assertTrue(output.contains("红球杀号数>20的期数=0.00%(0/2)"));
    }

    private static HistoryRecord draw(String period, int blue, int... reds) {
        return HistoryRecord.builder().period(period).special(blue).num1(reds[0]).num2(reds[1])
                .num3(reds[2]).num4(reds[3]).num5(reds[4]).num6(reds[5]).build();
    }

    @Test
    void largeKillCountAndZeroWrongAreCountedAsSeparateConditions() {
        var history = List.of(draw("2026001", 16, 1, 2, 3, 4, 5, 6),
                draw("2026002", 16, 22, 23, 24, 25, 26, 27), draw("2026003", 16, 1, 2, 3, 4, 5, 6));
        var rule = new ToutiaoKillRules.Rule("L", "大并集", false, 1,
                c -> IntStream.rangeClosed(1, 21).boxed().toList());
        var bytes = new ByteArrayOutputStream();
        ToutiaoKillRules.backtest(history, 2, new PrintStream(bytes, true, StandardCharsets.UTF_8), List.of(rule), "验证");
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("红球杀号数>20的期数=100.00%(2/2) 同时>20且红球零错杀期=50.00%(1/2)"));
        assertTrue(output.contains("红球错杀个数分布={0=1, 6=1}"));
        assertTrue(output.contains("蓝球并集未评估（无可评估规则）"));
    }

    @Test
    void unifiedEntryUsesTheProductionCatalogKeepingBusinessRulesUnchanged() {
        var history = List.of(draw("2026001", 1, 1, 3, 8, 17, 22, 33),
                draw("2026002", 4, 2, 6, 9, 18, 24, 30), draw("2026003", 5, 3, 7, 11, 19, 25, 31));
        var bytes = new ByteArrayOutputStream();
        CombinedKillRuleTest.runBacktests(history, 2, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("头条启用规则并集总体汇总"));
        assertTrue(output.contains("小红书启用规则并集总体汇总"));
        assertTrue(output.contains("头条+小红书启用规则并集总体汇总"));
        assertTrue(output.contains("红 X-R04 最近两期重号排除 杀号="));
        assertFalse(output.contains("红 R05 两两正差值 杀号="));
        assertFalse(output.contains("红 X-R05 近5期未出红球排除 杀号="));
    }
}
