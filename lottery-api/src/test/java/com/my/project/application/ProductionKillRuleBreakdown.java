package com.my.project.application;

import com.my.project.application.support.NextLotteryDateUtils;
import com.my.project.application.support.analyse.KillBallUtils;
import com.my.project.application.support.analyse.RedBallKillUtils;
import com.my.project.application.support.analyse.UnifiedKillBallRules;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.KillNumberResultBo.KillItemBo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProductionKillRuleBreakdown {
    private ProductionKillRuleBreakdown() { }

    record Rule(String name, boolean added) { }

    record Draw(Set<Integer> winners, Map<Rule, Set<Integer>> rules, Set<Integer> production) { }

    record Period(Draw red, Draw blue) { }

    static Period calculate(List<HistoryRecord> past, HistoryRecord target) {
        var targetDate = NextLotteryDateUtils.nextDrawDateAfter(past.getFirst().getOpenDate());
        var production = KillBallUtils.calculate(past, null, targetDate);
        var red = new LinkedHashMap<Rule, Set<Integer>>();
        var blue = blueRules(past);
        RedBallKillUtils.calculateByRule(past, targetDate).forEach((name, numbers) -> {
            if (!RedBallKillUtils.CONTINUOUS_THREE.equals(name)) {
                red.put(new Rule(name, false), new HashSet<>(numbers));
            }
        });
        var context = new UnifiedKillBallRules.Context(past, null);
        for (var rule : UnifiedKillBallRules.catalog()) {
            var rules = rule.blue() ? blue : red;
            rules.put(new Rule(rule.id() + " " + rule.name(), true), Set.copyOf(rule.kills(context)));
        }
        return new Period(
                checkedDraw(red, production.getHardKillRed(), reds(target), 33, target.getPeriod() + " 红球"),
                checkedDraw(blue, production.getHardKillBlue(), Set.of(target.getSpecial()), 16,
                        target.getPeriod() + " 蓝球"));
    }

    private static Draw checkedDraw(Map<Rule, Set<Integer>> breakdown, List<KillItemBo> source,
                                    Set<Integer> winners, int pool, String label) {
        var raw = source.stream().map(KillItemBo::getBall).collect(Collectors.toSet());
        var union = breakdown.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        assertEquals(raw, union, label + " 规则并集必须与生产输出一致（含原始越界值）");
        Map<Rule, Set<Integer>> valid = new LinkedHashMap<>();
        breakdown.forEach((rule, numbers) -> valid.put(rule, validNumbers(numbers, pool)));
        return new Draw(winners, valid, validNumbers(raw, pool));
    }

    private static Set<Integer> validNumbers(Set<Integer> numbers, int pool) {
        return numbers.stream().filter(n -> n >= 1 && n <= pool).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<Rule, Set<Integer>> blueRules(List<HistoryRecord> past) {
        int lastBlue = past.getFirst().getSpecial();
        var rules = new LinkedHashMap<Rule, Set<Integer>>();
        rules.put(new Rule("最近4期蓝球", false), past.stream().limit(4).map(HistoryRecord::getSpecial)
                .collect(Collectors.toUnmodifiableSet()));
        rules.put(new Rule("上期不超过16的红球", false), bluesMatching(reds(past.getFirst())::contains));
        // 生产将这三项合用同一来源名，且输出按球去重，无法从最终标签反推各自命中。
        rules.put(new Rule("近10期至少出现3次", false), frequentBlues(past, 10, 3));
        rules.put(new Rule("遗漏50期", false), omittedBlues(past, 50));
        rules.put(new Rule("上期蓝球同尾", false), bluesMatching(n -> n % 10 == lastBlue % 10));
        rules.put(new Rule("上期蓝球邻号±1", false), bluesMatching(n -> Math.abs(n - lastBlue) == 1));
        rules.put(new Rule("近6期至少出现2次", false), frequentBlues(past, 6, 2));
        rules.put(new Rule("遗漏35期", false), omittedBlues(past, 35));
        return rules;
    }

    private static Set<Integer> frequentBlues(List<HistoryRecord> past, int window, int minimum) {
        int[] counts = new int[17];
        past.stream().limit(window).forEach(row -> counts[row.getSpecial()]++);
        return bluesMatching(n -> counts[n] >= minimum);
    }

    private static Set<Integer> omittedBlues(List<HistoryRecord> past, int window) {
        var recent = past.stream().limit(window).map(HistoryRecord::getSpecial).collect(Collectors.toSet());
        return bluesMatching(n -> !recent.contains(n));
    }

    private static Set<Integer> bluesMatching(IntPredicate predicate) {
        return IntStream.rangeClosed(1, 16).filter(predicate).boxed().collect(Collectors.toUnmodifiableSet());
    }

    static Set<Integer> reds(HistoryRecord row) {
        return Arrays.stream(UnifiedKillBallRules.reds(row)).boxed().collect(Collectors.toUnmodifiableSet());
    }
}
