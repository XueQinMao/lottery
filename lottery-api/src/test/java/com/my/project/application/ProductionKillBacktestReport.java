package com.my.project.application;

import com.my.project.application.ProductionKillRuleBreakdown.Draw;
import com.my.project.application.ProductionKillRuleBreakdown.Period;
import com.my.project.application.ProductionKillRuleBreakdown.Rule;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class ProductionKillBacktestReport {
    private static final int PRIOR_KILLS = 100;
    private static final String COMBINATION_HEADER =
            "| 方案 | 红均杀 | 红错杀期 | 红随机 | 蓝均杀 | 蓝错杀期 | 蓝随机 | 任一颜色错杀期 | 联合随机 |\n"
            + "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |";

    private ProductionKillBacktestReport() { }

    static void write(PrintWriter out, List<Period> calibration, List<Period> validation) {
        var redRates = ruleRates(calibration.stream().map(Period::red).toList(), 6.0 / 33);
        var blueRates = ruleRates(calibration.stream().map(Period::blue).toList(), 1.0 / 16);
        out.println("## 留出验证的组合比较\n");
        out.println("训练期按单球错杀率给规则估分，加入相当于 100 次杀号的随机基准作为平滑。"
                + "筛选方案保留训练期累计排除至少 100 球且估分低于随机基准的规则。"
                + "限量和定额方案对候选取所有提名规则中的最低估分，平分按号码升序，排名只使用训练期结果。"
                + "定额方案候选不足时，从未提名的号码中按升序补足。\n");
        out.println(COMBINATION_HEADER);
        combination(out, validation, "当前全部规则", Draw::production, Draw::production);
        combination(out, validation, "混合推荐传入的软建议",
                draw -> omitIfTooBroad(draw, 27), draw -> omitIfTooBroad(draw, 15));
        combination(out, validation, "仅原有规则", draw -> union(draw, rule -> !rule.added()),
                draw -> union(draw, rule -> !rule.added()));
        combination(out, validation, "仅追加规则", draw -> union(draw, Rule::added), draw -> union(draw, Rule::added));
        combination(out, validation, "训练期低于随机的规则并集",
                draw -> union(draw, rule -> redRates.get(rule).passes(6.0 / 33)),
                draw -> union(draw, rule -> blueRates.get(rule).passes(1.0 / 16)));
        for (int cap : List.of(1, 2, 3, 5)) {
            int blueCap = Math.min(cap, 3);
            combination(out, validation, "限红%d蓝%d".formatted(cap, blueCap),
                    draw -> ranked(draw, redRates, cap), draw -> ranked(draw, blueRates, blueCap));
        }
        for (int redCount : List.of(9, 10, 11)) {
            int blueCount = redCount - 2;
            combination(out, validation, "固定杀红%d蓝%d".formatted(redCount, blueCount),
                    draw -> fixedCount(draw, redRates, redCount, 33),
                    draw -> fixedCount(draw, blueRates, blueCount, 16));
        }
        combination(out, calibration, "当前全部规则（训练期）", Draw::production, Draw::production);
        long redShortage = validation.stream().filter(period -> period.red().production().size() < 10).count();
        long blueShortage = validation.stream().filter(period -> period.blue().production().size() < 8).count();
        out.printf("%n固定杀红 10、蓝 8 时，候选不足而补号的期数为红 %d 期、蓝 %d 期。%n", redShortage, blueShortage);

        ruleTable(out, "红球", calibration, validation, Period::red, 33);
        ruleTable(out, "蓝球", calibration, validation, Period::blue, 16);
    }

    static void writeResearch(PrintWriter out, List<Period> production, List<Period> researched, int calibrationSize) {
        out.println("\n## 搜索公式的定额回测\n");
        out.println("每期固定排除红 10、蓝 8，沿用训练期估分和候选不足时按升序补足的口径。"
                + "搜索公式在已有开奖上做回溯验证，公式的发表日期和开奖历史可能重叠。\n");
        out.println("公式来源包括今日头条原文、百度搜索摘要和项目已有的小红书笔记记录。"
                + "百度摘要中的杀整尾与头条原文中的取单号分别回测。\n");
        out.println("- [头条红球相邻和与红蓝联动](https://www.toutiao.com/article/7708300459227857435/)"
                + " · [头条红蓝加减公式](https://www.toutiao.com/article/7708257406790205995/)");
        out.println("- [头条蓝球取单号与整尾](https://www.toutiao.com/article/7705303387209810459/)"
                + " · [头条蓝球同余数与和值尾](https://www.toutiao.com/article/7708679743483068968/)");
        out.println("- [百度蓝球杀号搜索](https://www.baidu.com/s?wd=双色球%20蓝球%20杀号%20技巧)"
                + " · [小红书既有笔记记录](../../../doc/xiaohongshu-kill-rules-survey.md)\n");
        out.println(COMBINATION_HEADER);
        fixedResearchComparison(out, "现用规则", production, calibrationSize);
        fixedResearchComparison(out, "搜索公式", researched, calibrationSize);
        var merged = IntStream.range(0, production.size())
                .mapToObj(i -> ResearchedKillRules.merge(production.get(i), researched.get(i))).toList();
        fixedResearchComparison(out, "现用规则加搜索公式", merged, calibrationSize);
        out.println("\n各方案仅按训练期估分排序。补号期数按红球、蓝球分别统计。\n");
        for (var entry : List.of(Map.entry("现用规则", production), Map.entry("搜索公式", researched),
                Map.entry("现用规则加搜索公式", merged))) {
            var validation = entry.getValue().subList(calibrationSize, production.size());
            long redShortage = validation.stream().filter(period -> period.red().production().size() < 10).count();
            long blueShortage = validation.stream().filter(period -> period.blue().production().size() < 8).count();
            out.printf("- %s补号红 %d 期、蓝 %d 期。%n", entry.getKey(), redShortage, blueShortage);
        }
        var calibration = researched.subList(0, calibrationSize);
        var validation = researched.subList(calibrationSize, researched.size());
        ruleTable(out, "搜索公式红球", calibration, validation, Period::red, 33);
        ruleTable(out, "搜索公式蓝球", calibration, validation, Period::blue, 16);
    }

    private static void fixedResearchComparison(PrintWriter out, String name, List<Period> history, int calibrationSize) {
        var calibration = history.subList(0, calibrationSize);
        var redRates = ruleRates(calibration.stream().map(Period::red).toList(), 6.0 / 33);
        var blueRates = ruleRates(calibration.stream().map(Period::blue).toList(), 1.0 / 16);
        combination(out, history.subList(calibrationSize, history.size()), name,
                draw -> fixedCount(draw, redRates, 10, 33), draw -> fixedCount(draw, blueRates, 8, 16));
    }

    private static Set<Integer> omitIfTooBroad(Draw draw, int maximum) {
        return draw.production().size() <= maximum ? draw.production() : Set.of();
    }

    private static void combination(PrintWriter out, List<Period> periods, String name,
                                    Function<Draw, Set<Integer>> red, Function<Draw, Set<Integer>> blue) {
        var redSamples = samples(periods.stream().map(Period::red).toList(), red);
        var blueSamples = samples(periods.stream().map(Period::blue).toList(), blue);
        var redStats = summarize(redSamples, 33, 6);
        var blueStats = summarize(blueSamples, 16, 1);
        int wrong = 0;
        double expected = 0.0;
        for (int i = 0; i < periods.size(); i++) {
            var redSample = redSamples.get(i);
            var blueSample = blueSamples.get(i);
            if (redSample.wrong() > 0 || blueSample.wrong() > 0) wrong++;
            expected += 1 - (1 - randomRisk(33, 6, redSample.killed())) * (1 - randomRisk(16, 1, blueSample.killed()));
        }
        out.printf("| %s | %.2f | %s | %s | %.2f | %s | %s | %s | %s |%n", name,
                redStats.averageKills(), redStats.periodRate(), percentage(redStats.randomRisk()),
                blueStats.averageKills(), blueStats.periodRate(), percentage(blueStats.randomRisk()),
                rate(wrong, periods.size()), percentage(expected / periods.size()));
    }

    private static void ruleTable(PrintWriter out, String color, List<Period> calibration, List<Period> validation,
                                  Function<Period, Draw> extract, int pool) {
        out.printf("%n## %s逐规则错杀率%n%n", color);
        out.println("移除列为仅关掉该规则后，无其他规则错杀的净增期数，以及每期平均少排除的号码数。\n");
        out.println("| 规则 | 训练期按期错杀 | 验证期按期错杀 | 有排除期错杀 | 按排除号码错杀 | 验证均杀 | 验证同规模随机 | 移除后净增零错杀期 | 移除后平均少杀 |");
        out.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        var train = calibration.stream().map(extract).toList();
        var test = validation.stream().map(extract).toList();
        int winners = test.getFirst().winners().size();
        for (Rule rule : test.getFirst().rules().keySet()) {
            var early = summarize(samples(train, draw -> draw.rules().get(rule)), pool, winners);
            var late = summarize(samples(test, draw -> draw.rules().get(rule)), pool, winners);
            var removal = removalImpact(test, rule);
            out.printf("| %s | %s | %s | %s | %s | %.2f | %s | %d | %.2f |%n", rule.name(), early.periodRate(),
                    late.periodRate(), rate(late.wrongPeriods(), late.triggered()), rate(late.wrongBalls(), late.killed()),
                    late.averageKills(), percentage(late.randomRisk()), removal.savedPeriods(), removal.fewerPerDraw());
        }
    }

    private static Removal removalImpact(List<Draw> draws, Rule rule) {
        int saved = 0;
        int fewer = 0;
        for (Draw draw : draws) {
            var claimed = draw.rules().get(rule);
            if (claimed.isEmpty()) continue;
            var remaining = union(draw, candidate -> !candidate.equals(rule));
            fewer += draw.production().size() - remaining.size();
            if (claimed.stream().anyMatch(draw.winners()::contains)
                    && remaining.stream().noneMatch(draw.winners()::contains)) saved++;
        }
        return new Removal(saved, (double) fewer / draws.size());
    }

    private static Map<Rule, RuleRate> ruleRates(List<Draw> draws, double prior) {
        return draws.getFirst().rules().keySet().stream().collect(Collectors.toUnmodifiableMap(Function.identity(), rule -> {
            var series = samples(draws, draw -> draw.rules().get(rule));
            int killed = series.stream().mapToInt(Sample::killed).sum();
            int wrong = series.stream().mapToInt(Sample::wrong).sum();
            return new RuleRate(killed, (wrong + PRIOR_KILLS * prior) / (killed + PRIOR_KILLS));
        }));
    }

    private static Set<Integer> ranked(Draw draw, Map<Rule, RuleRate> rates, int cap) {
        return rankedNumbers(draw, rates).limit(cap).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<Integer> fixedCount(Draw draw, Map<Rule, RuleRate> rates, int count, int pool) {
        return Stream.concat(rankedNumbers(draw, rates), IntStream.rangeClosed(1, pool).boxed())
                .distinct().limit(count).collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<Integer> rankedNumbers(Draw draw, Map<Rule, RuleRate> rates) {
        var risks = draw.rules().entrySet().stream().flatMap(entry -> entry.getValue().stream()
                .map(ball -> Map.entry(ball, rates.get(entry.getKey()).probability())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Math::min));
        return risks.keySet().stream().sorted(Comparator.comparingDouble((Integer ball) -> risks.get(ball))
                .thenComparingInt(Integer::intValue));
    }

    private static Set<Integer> union(Draw draw, Predicate<Rule> keep) {
        return draw.rules().entrySet().stream().filter(entry -> keep.test(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()).collect(Collectors.toUnmodifiableSet());
    }

    private static List<Sample> samples(List<Draw> draws, Function<Draw, Set<Integer>> predict) {
        return draws.stream().map(draw -> {
            var kills = predict.apply(draw);
            return new Sample(kills.size(), (int) draw.winners().stream().filter(kills::contains).count());
        }).toList();
    }

    private static Summary summarize(List<Sample> samples, int pool, int winners) {
        int triggered = 0;
        int wrongPeriods = 0;
        int killed = 0;
        int wrongBalls = 0;
        double expected = 0.0;
        for (Sample sample : samples) {
            if (sample.killed() > 0) triggered++;
            if (sample.wrong() > 0) wrongPeriods++;
            killed += sample.killed();
            wrongBalls += sample.wrong();
            expected += randomRisk(pool, winners, sample.killed());
        }
        return new Summary(samples.size(), triggered, wrongPeriods, killed, wrongBalls, expected / samples.size());
    }

    private static double randomRisk(int pool, int winners, int killed) {
        if (killed > pool - winners) return 1.0;
        double miss = 1.0;
        for (int i = 0; i < winners; i++) {
            miss *= (double) (pool - killed - i) / (pool - i);
        }
        return 1.0 - miss;
    }

    private static String rate(int count, int total) {
        return total == 0 ? "—" : "%s (%d/%d)".formatted(percentage((double) count / total), count, total);
    }

    private static String percentage(double probability) {
        return "%.2f%%".formatted(100 * probability);
    }

    private record Removal(int savedPeriods, double fewerPerDraw) { }

    private record RuleRate(int killed, double probability) {
        boolean passes(double baseline) { return killed >= PRIOR_KILLS && probability < baseline; }
    }

    private record Sample(int killed, int wrong) { }

    private record Summary(int periods, int triggered, int wrongPeriods, int killed, int wrongBalls, double randomRisk) {
        String periodRate() { return rate(wrongPeriods, periods); }
        double averageKills() { return (double) killed / periods; }
    }
}
