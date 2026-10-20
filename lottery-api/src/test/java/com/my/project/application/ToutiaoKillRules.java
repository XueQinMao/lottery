package com.my.project.application;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.support.analyse.UnifiedKillBallRules;

import java.io.PrintStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

/** 仅供头条规则回测使用。编号、来源和边界约定见 doc/toutiao-kill-rules-backtest.md。 */
final class ToutiaoKillRules {
    private ToutiaoKillRules() { }

    static final class Context {
        private final UnifiedKillBallRules.Context shared;
        Context(String targetPeriod, List<HistoryRecord> past) { this(targetPeriod, past, null); }
        Context(String targetPeriod, List<HistoryRecord> past, java.time.LocalDate targetDate) {
            shared = new UnifiedKillBallRules.Context(past, targetDate);
        }
        List<HistoryRecord> past() { return shared.past(); }
    }

    record Rule(String id, String name, boolean blue, int historySize, boolean requiresDate,
                Function<Context, List<Integer>> formula) {
        Rule(String id, String name, boolean blue, int historySize, Function<Context, List<Integer>> formula) {
            this(id, name, blue, historySize, false, formula);
        }
        List<Integer> kills(Context c) {
            return formula.apply(c).stream().filter(Objects::nonNull)
                    .filter(n -> n >= 1 && n <= (blue ? 16 : 33)).distinct().sorted().toList();
        }
    }

    static List<Rule> catalog() { return adapt(UnifiedKillBallRules.from(UnifiedKillBallRules.TOUTIAO)); }
    static List<Rule> integratedCatalog() { return adapt(UnifiedKillBallRules.catalog()); }
    static List<Rule> adapt(List<UnifiedKillBallRules.Rule> rules) {
        return rules.stream().map(r -> new Rule(r.id(), r.name(), r.blue(), r.historySize(), r.requiresDate(),
                (Function<Context, List<Integer>>) c -> r.kills(c.shared))).toList();
    }
    static int[] reds(HistoryRecord h) { return UnifiedKillBallRules.reds(h); }
    private static List<Integer> nums(int... n) { return Arrays.stream(n).boxed().toList(); }

    static final class Stat {
        int evaluated, skipped, empty, wrongPeriods, killed, wrongBalls;
        void record(List<Integer> kills, List<Integer> hits) {
            evaluated++;
            if (kills.isEmpty()) empty++;
            if (!hits.isEmpty()) wrongPeriods++;
            killed += kills.size();
            wrongBalls += hits.size();
        }
        String summary() {
            return String.format(Locale.ROOT,
                    "有效期=%d 跳过=%d 空杀=%d 有杀号期=%d 错杀期=%d 总错杀率=%s 有杀号期错杀率=%s 累计杀号=%d 累计错杀球=%d 球级错杀率=%s 平均杀号数=%.2f",
                    evaluated, skipped, empty, evaluated - empty, wrongPeriods, rate(wrongPeriods, evaluated),
                    rate(wrongPeriods, evaluated - empty), killed, wrongBalls, rate(wrongBalls, killed),
                    evaluated == 0 ? 0.0 : (double) killed / evaluated);
        }
    }
    private static String rate(int n, int d) {
        return d == 0 ? "N/A" : String.format(Locale.ROOT, "%.2f%%(%d/%d)", 100.0 * n / d, n, d);
    }

    /** 一次加载历史，所有规则共享同一无未来数据的输入；异常直接使测试失败，不伪装成空杀。 */
    static Map<String, Stat> backtest(List<HistoryRecord> records, int sampleSize, PrintStream out) {
        return backtest(records, sampleSize, out, catalog(), "头条");
    }

    /** 不同来源共享时序校验和统计口径，规则目录由各自入口提供。 */
    static Map<String, Stat> backtest(List<HistoryRecord> records, int sampleSize, PrintStream out,
                                     List<Rule> rules, String sourceName) {
        if (sampleSize <= 0) throw new IllegalArgumentException("回测期数必须大于0");
        List<HistoryRecord> all = records.stream().sorted(Comparator.comparingInt(h -> Integer.parseInt(h.getPeriod()))).toList();
        Set<String> periods = new HashSet<>();
        for (HistoryRecord h : all) {
            if (!periods.add(h.getPeriod())) throw new IllegalArgumentException("重复期号: " + h.getPeriod());
            int[] red = reds(h);
            if (red[0] < 1 || red[5] > 33 || Arrays.stream(red).distinct().count() != 6
                    || h.getSpecial() < 1 || h.getSpecial() > 16) throw new IllegalArgumentException("非法开奖: " + h.getPeriod());
        }
        if (all.size() < 2) throw new IllegalArgumentException("至少需要2期开奖记录");
        Map<String, Stat> stats = new LinkedHashMap<>();
        rules.forEach(r -> stats.put(r.id(), new Stat()));
        Stat redUnion = new Stat(), blueUnion = new Stat();
        Map<Integer, Integer> redWrongDistribution = new TreeMap<>();
        int unionPeriods = 0, zeroWrongPeriods = 0, redOver20 = 0, redOver20ZeroWrong = 0;
        int redMin = Integer.MAX_VALUE, redMax = 0;
        out.println("总错杀率=至少错杀1个当期中奖号码的期数/有效回测期数；空杀计入有效期，历史不足跳过。");
        for (int i = Math.max(1, all.size() - sampleSize); i < all.size(); i++) {
            HistoryRecord target = all.get(i);
            List<HistoryRecord> past = new ArrayList<>(all.subList(0, i));
            Collections.reverse(past);
            Context context = new Context(target.getPeriod(), past, target.getOpenDate());
            out.printf("%n目标期=%s 输入截止期=%s 实际红球=%s 实际蓝球=%02d%n", target.getPeriod(),
                    past.getFirst().getPeriod(), Arrays.toString(reds(target)), target.getSpecial());
            Set<Integer> redKills = new TreeSet<>(), blueKills = new TreeSet<>();
            int evaluatedBlueRules = 0, evaluatedRedRules = 0;
            for (Rule rule : rules) {
                Stat stat = stats.get(rule.id());
                if (rule.requiresDate() && context.shared.targetDate() == null) {
                    stat.skipped++;
                    out.printf("%s %s %s 目标开奖日期缺失，跳过%n", rule.blue() ? "蓝" : "红", rule.id(), rule.name());
                    continue;
                }
                if (past.size() < rule.historySize()) {
                    stat.skipped++;
                    out.printf("%s %s %s 历史不足，跳过（需要%d期，已有%d期）%n", rule.blue() ? "蓝" : "红",
                            rule.id(), rule.name(), rule.historySize(), past.size());
                    continue;
                }
                List<Integer> kills = rule.kills(context);
                (rule.blue() ? blueKills : redKills).addAll(kills);
                if (rule.blue()) evaluatedBlueRules++;
                else evaluatedRedRules++;
                List<Integer> actual = rule.blue() ? nums(target.getSpecial()) : nums(reds(target));
                List<Integer> hits = kills.stream().filter(actual::contains).toList();
                stat.record(kills, hits);
                out.printf("%s %s %s 杀号=%s 错杀=%s 错杀数=%d %s%n", rule.blue() ? "蓝" : "红", rule.id(),
                        rule.name(), kills, hits, hits.size(), kills.isEmpty() ? "空杀/未触发" : hits.isEmpty() ? "无错杀" : "发生错杀");
            }
            List<Integer> actualReds = nums(reds(target));
            List<Integer> redHits = redKills.stream().filter(actualReds::contains).toList();
            boolean blueHit = blueKills.contains(target.getSpecial());
            if (evaluatedRedRules > 0) {
                redUnion.record(new ArrayList<>(redKills), redHits);
                redMin = Math.min(redMin, redKills.size());
                redMax = Math.max(redMax, redKills.size());
                redWrongDistribution.merge(redHits.size(), 1, Integer::sum);
                if (redKills.size() > 20) {
                    redOver20++;
                    if (redHits.isEmpty()) redOver20ZeroWrong++;
                }
            }
            if (evaluatedBlueRules > 0)
                blueUnion.record(new ArrayList<>(blueKills), blueHit ? nums(target.getSpecial()) : List.of());
            if (evaluatedRedRules + evaluatedBlueRules > 0) {
                unionPeriods++;
                if (redHits.isEmpty() && !blueHit) zeroWrongPeriods++;
            }
            String blueStatus = rules.stream().noneMatch(Rule::blue) ? "未评估（无蓝球规则）"
                    : evaluatedBlueRules == 0 ? "未评估（历史不足）" : blueHit ? "是" : "否";
            out.printf("----- %s 当期所有杀号汇总 目标期=%s（各规则并集去重，历史不足规则不计入）-----%n",
                    sourceName, target.getPeriod());
            out.printf("红球杀号汇总=%s 杀号数=%d 红球错杀=%s 红球错杀数=%d%n",
                    redKills, redKills.size(), redHits, redHits.size());
            out.printf("蓝球杀号汇总=%s 杀号数=%d 实际蓝球=%02d 蓝球是否错杀=%s 蓝球错杀数=%d%n",
                    blueKills, blueKills.size(), target.getSpecial(), blueStatus, blueHit ? 1 : 0);
            out.printf("当期总错杀数=%d（红球%d个，蓝球%d个）%n", redHits.size() + (blueHit ? 1 : 0),
                    redHits.size(), blueHit ? 1 : 0);
        }
        for (boolean blue : List.of(false, true)) {
            if (rules.stream().noneMatch(r -> r.blue() == blue)) continue;
            out.printf("%n===== %s%s球规则汇总 =====%n", sourceName, blue ? "蓝" : "红");
            rules.stream().filter(r -> r.blue() == blue).forEach(r -> out.printf("%s %s %s%n", r.id(), r.name(), stats.get(r.id()).summary()));
        }
        out.printf("%n===== %s启用规则并集总体汇总（去重） =====%n", sourceName);
        if (redUnion.evaluated > 0) {
            out.println("红球并集 " + redUnion.summary());
            out.printf("红球零错杀期=%s 每期杀号数范围=%d—%d 红球错杀个数分布=%s%n",
                    rate(redUnion.evaluated - redUnion.wrongPeriods, redUnion.evaluated), redMin, redMax, redWrongDistribution);
            out.printf("红球杀号数>20的期数=%s 同时>20且红球零错杀期=%s%n",
                    rate(redOver20, redUnion.evaluated), rate(redOver20ZeroWrong, redUnion.evaluated));
        } else out.println("红球并集未评估（无可评估规则）");
        if (blueUnion.evaluated > 0) {
            out.println("蓝球并集 " + blueUnion.summary());
            out.println("蓝球零错杀期=" + rate(blueUnion.evaluated - blueUnion.wrongPeriods, blueUnion.evaluated));
        } else out.println("蓝球并集未评估（无可评估规则）");
        out.printf("启用规则综合零错杀期=%s（仅统计可评估的红/蓝规则，空杀计入，全部历史不足的期不计入）%n",
                rate(zeroWrongPeriods, unionPeriods));
        return stats;
    }
}
