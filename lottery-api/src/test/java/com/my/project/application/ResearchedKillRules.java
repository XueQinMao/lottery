package com.my.project.application;

import com.my.project.application.ProductionKillRuleBreakdown.Draw;
import com.my.project.application.ProductionKillRuleBreakdown.Period;
import com.my.project.application.ProductionKillRuleBreakdown.Rule;
import com.my.project.application.support.analyse.UnifiedKillBallRules;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class ResearchedKillRules {
    private ResearchedKillRules() { }

    private static final List<Formula> RED = List.of(
            new Formula("头条 相邻红球和值取个位", h -> IntStream.range(1, 6)
                    .map(i -> (h.reds()[i - 1] + h.reds()[i]) % 10)),
            new Formula("头条 上期六红分别加蓝球，超33减33", h -> Arrays.stream(h.reds())
                    .map(n -> wrap(n + h.blue(), 33))),
            new Formula("头条 上期红球两两差值", h -> IntStream.range(0, 5)
                    .flatMap(i -> IntStream.range(i + 1, 6).map(j -> h.reds()[j] - h.reds()[i]))),
            new Formula("头条 两期红球对位差", h -> IntStream.range(0, 6)
                    .map(i -> Math.abs(h.reds()[i] - h.previousReds()[i]))),
            new Formula("头条 上期六红分别减蓝球取绝对值", h -> Arrays.stream(h.reds())
                    .map(n -> Math.abs(n - h.blue()))),
            new Formula("小红书 首尾红蓝联动（含现有首尾差）", h -> IntStream.of(
                    wrap(h.reds()[0] + h.blue(), 33), h.reds()[5] - h.blue(), h.reds()[5] - h.reds()[0])),
            new Formula("小红书 上期蓝球加6杀红尾", h -> tail(h.blue() + 6, 33)),
            new Formula("小红书 上期蓝球加9杀红尾", h -> tail(h.blue() + 9, 33)),
            new Formula("小红书 最近5期未出的红球", h -> IntStream.rangeClosed(1, 33)
                    .filter(n -> !h.recentReds().contains(n))));

    private static final List<Formula> BLUE = List.of(
            new Formula("头条 17减上期蓝球", h -> IntStream.of(17 - h.blue())),
            new Formula("头条 上期蓝球乘2杀蓝尾", h -> tail(h.blue() * 2, 16)),
            new Formula("头条 上期红球最小值加蓝球，超16减一次16",
                    h -> IntStream.of(reduceOnce(h.reds()[0] + h.blue(), 16))),
            new Formula("头条 上期蓝球十位与个位之和", h -> IntStream.of(h.blue() / 10 + h.blue() % 10)),
            new Formula("头条 21减上期蓝球，取单号", h -> IntStream.of(21 - h.blue())),
            new Formula("头条 第5、6位红球和杀蓝尾", h -> tail(h.reds()[4] + h.reds()[5], 16)),
            new Formula("头条 近三期蓝球和值杀蓝尾", h -> tail(h.blue() + h.previousBlue() + h.thirdBlue(), 16)),
            new Formula("头条 上期蓝球同012路", h -> IntStream.rangeClosed(1, 16)
                    .filter(n -> n % 3 == h.blue() % 3)),
            new Formula("头条 两期蓝球和值，超16减16",
                    h -> IntStream.of(wrap(h.blue() + h.previousBlue(), 16))),
            new Formula("百度摘要 19减上期蓝球杀蓝尾", h -> tail(19 - h.blue(), 16)),
            new Formula("百度摘要 21减上期蓝球杀蓝尾", h -> tail(21 - h.blue(), 16)));

    static Period calculate(List<HistoryRecord> past, Period production) {
        var context = new Context(UnifiedKillBallRules.reds(past.getFirst()), UnifiedKillBallRules.reds(past.get(1)),
                past.getFirst().getSpecial(), past.get(1).getSpecial(), past.get(2).getSpecial(),
                past.stream().limit(5).flatMap(row -> ProductionKillRuleBreakdown.reds(row).stream())
                        .collect(Collectors.toUnmodifiableSet()));
        return new Period(toDraw(RED, context, production.red().winners(), 33),
                toDraw(BLUE, context, production.blue().winners(), 16));
    }

    static Period merge(Period production, Period research) {
        return new Period(merge(production.red(), research.red()), merge(production.blue(), research.blue()));
    }

    private static Draw merge(Draw production, Draw research) {
        var rules = new LinkedHashMap<>(production.rules());
        rules.putAll(research.rules());
        return draw(production.winners(), rules);
    }

    private static Draw toDraw(List<Formula> formulas, Context context, Set<Integer> winners, int max) {
        Map<Rule, Set<Integer>> rules = new LinkedHashMap<>();
        for (var formula : formulas) {
            var kills = formula.calculate().apply(context).filter(n -> n >= 1 && n <= max).boxed()
                    .collect(Collectors.toUnmodifiableSet());
            rules.put(new Rule(formula.name(), true), kills);
        }
        return draw(winners, rules);
    }

    private static Draw draw(Set<Integer> winners, Map<Rule, Set<Integer>> rules) {
        var union = rules.values().stream().flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
        return new Draw(winners, rules, union);
    }

    private static IntStream tail(int value, int max) {
        return IntStream.rangeClosed(1, max).filter(n -> n % 10 == Math.floorMod(value, 10));
    }

    private static int reduceOnce(int value, int max) {
        return value > max ? value - max : value;
    }

    private static int wrap(int value, int max) {
        return Math.floorMod(value - 1, max) + 1;
    }

    private record Formula(String name, Function<Context, IntStream> calculate) { }

    private record Context(int[] reds, int[] previousReds, int blue, int previousBlue, int thirdBlue,
                           Set<Integer> recentReds) { }
}
