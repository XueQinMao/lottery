package com.my.project.application.support.analyse;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

/** 头条/小红书追加规则的共享目录；现有KillBallUtils规则原样保留，历史输入从新到旧。 */
public final class UnifiedKillBallRules {
    public static final String TOUTIAO = "今日头条", XIAOHONGSHU = "小红书";
    private UnifiedKillBallRules() { }

    public static final class Context {
        private final List<HistoryRecord> past;
        private final LocalDate targetDate;
        public Context(List<HistoryRecord> newestFirst, LocalDate targetDate) {
            if (newestFirst == null || newestFirst.isEmpty()) throw new IllegalArgumentException("杀号需要历史开奖");
            past = List.copyOf(newestFirst);
            this.targetDate = targetDate;
        }
        public List<HistoryRecord> past() { return past; }
        public LocalDate targetDate() { return targetDate; }
        public int[] r() { return reds(past.getFirst()); }
        public int b() { return past.getFirst().getSpecial(); }

    }

    public record Rule(String id, String name, String source, boolean blue, int historySize,
                       boolean requiresDate, Function<Context, List<Integer>> formula) {
        public boolean applicable(Context c) {
            return c.past().size() >= historySize && (!requiresDate || c.targetDate() != null);
        }
        public List<Integer> kills(Context c) {
            if (!applicable(c)) return List.of();
            return formula.apply(c).stream().filter(Objects::nonNull)
                    .filter(n -> n >= 1 && n <= (blue ? 16 : 33)).distinct().sorted().toList();
        }
    }

    private static final List<Rule> CATALOG = buildCatalog();
    public static List<Rule> catalog() { return CATALOG; }
    public static List<Rule> from(String source) { return CATALOG.stream().filter(r -> r.source().equals(source)).toList(); }

    private static List<Rule> buildCatalog() {
        List<Rule> rules = new ArrayList<>();
        add(rules, "R02", "蓝球2倍及3倍", TOUTIAO, false, 1, c -> nums(c.b() * 2, c.b() * 3));
        add(rules, "R04", "AC值对应号码", TOUTIAO, false, 1, c -> {
            Set<Integer> differences = new HashSet<>(); int[] r = c.r();
            for (int i = 0; i < 6; i++) for (int j = i + 1; j < 6; j++) differences.add(r[j] - r[i]);
            return nums(differences.size() - 5);
        });
        add(rules, "R09", "和值除12余0取12", TOUTIAO, false, 1,
                c -> nums(Arrays.stream(c.r()).sum() % 12 == 0 ? 12 : Arrays.stream(c.r()).sum() % 12));
        add(rules, "R11", "蓝球加5", TOUTIAO, false, 1, c -> nums(c.b() + 5));
        add(rules, "R14", "第二第四红之和超33减33", TOUTIAO, false, 1,
                c -> nums(Math.floorMod(c.r()[1] + c.r()[3] - 1, 33) + 1));
        add(rules, "B01", "最大红除蓝球取余", TOUTIAO, true, 1, c -> nums(c.r()[5] % c.b()));
        add(rules, "B02", "蓝球加1加2循环", TOUTIAO, true, 1,
                c -> nums(c.b() % 16 + 1, (c.b() + 1) % 16 + 1));
        add(rules, "B03", "两期蓝绝对差", TOUTIAO, true, 2,
                c -> nums(Math.abs(c.b() - c.past().get(1).getSpecial())));
        add(rules, "B05", "15减蓝整尾（负值模10）", TOUTIAO, true, 1,
                c -> tails(Math.floorMod(15 - c.b(), 10), 16));
        add(rules, "B08", "红球首尾和杀蓝尾", TOUTIAO, true, 1, c -> tails((c.r()[0] + c.r()[5]) % 10, 16));
        add(rules, "X-R04", "最近两期重号排除", XIAOHONGSHU, false, 2, c -> {
            int[] previous = reds(c.past().get(1));
            return Arrays.stream(c.r()).filter(n -> Arrays.stream(previous).anyMatch(p -> p == n)).boxed().toList();
        });
        add(rules, "X-R06", "最大相邻间距减1", XIAOHONGSHU, false, 1, c -> {
            int[] r = c.r(); return nums(IntStream.range(1, 6).map(i -> r[i] - r[i - 1]).max().orElseThrow() - 1);
        });
        return List.copyOf(rules);
    }

    private static void add(List<Rule> rules, String id, String name, String source, boolean blue, int history,
                            Function<Context, List<Integer>> fn) {
        rules.add(new Rule(id, name, source, blue, history, false, fn));
    }
    public static int[] reds(HistoryRecord h) {
        return IntStream.of(h.getNum1(), h.getNum2(), h.getNum3(), h.getNum4(), h.getNum5(), h.getNum6()).sorted().toArray();
    }
    private static List<Integer> nums(int... n) { return Arrays.stream(n).boxed().toList(); }
    private static List<Integer> tails(int tail, int max) {
        return IntStream.rangeClosed(1, max).filter(n -> n % 10 == tail).boxed().toList();
    }
}
