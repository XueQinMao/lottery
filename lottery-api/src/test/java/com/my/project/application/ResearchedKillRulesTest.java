package com.my.project.application;

import com.my.project.application.ProductionKillRuleBreakdown.Draw;
import com.my.project.application.ProductionKillRuleBreakdown.Period;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchedKillRulesTest {
    @Test
    void reproducesArticleExamplesAndDistinguishesSingleNumberFromWholeTail() {
        var latest = HistoryRecord.builder().num1(3).num2(9).num3(14).num4(19).num5(25).num6(31).special(7).build();
        var previous = HistoryRecord.builder().num1(2).num2(7).num3(13).num4(18).num5(24).num6(30).special(11).build();
        var earlier = HistoryRecord.builder().num1(1).num2(4).num3(5).num4(6).num5(15).num6(16).special(9).build();
        var empty = new Draw(Set.of(), Map.of(), Set.of());
        var result = ResearchedKillRules.calculate(List.of(latest, previous, earlier), new Period(empty, empty));

        assertEquals(Set.of(2, 3, 4, 6), numbers(result.red(), "头条 相邻红球和值取个位"));
        assertEquals(Set.of(5, 10, 16, 21, 26, 32), numbers(result.red(), "头条 上期六红分别加蓝球，超33减33"));
        assertEquals(Set.of(10, 24, 28), numbers(result.red(), "小红书 首尾红蓝联动（含现有首尾差）"));
        assertEquals(Set.of(14), numbers(result.blue(), "头条 21减上期蓝球，取单号"));
        assertEquals(Set.of(4, 14), numbers(result.blue(), "百度摘要 21减上期蓝球杀蓝尾"));
        assertEquals(Set.of(7), numbers(result.blue(), "头条 近三期蓝球和值杀蓝尾"));
    }

    private static Set<Integer> numbers(Draw draw, String name) {
        return draw.rules().entrySet().stream().filter(entry -> name.equals(entry.getKey().name()))
                .findFirst().orElseThrow().getValue();
    }
}
