package com.my.project.application.support.analyse;

import com.my.project.application.support.NextLotteryDateUtils;
import com.my.project.application.support.OmissionUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.KillNumberResultBo.KillItemBo;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** CustomizeTest.test_20_zh 的实际红球汇总规则；历史输入为截止期开始的新到旧记录。 */
public final class RedBallKillUtils {
    public static final String CONTINUOUS_THREE = "连续3期出现";

    private RedBallKillUtils() { }

    public static List<KillItemBo> calculate(List<HistoryRecord> newestFirst) {
        if (newestFirst == null || newestFirst.isEmpty()) throw new IllegalArgumentException("红球杀号需要历史开奖");
        var lastDate = Objects.requireNonNull(newestFirst.getFirst().getOpenDate(), "截止期开奖日期不能为空");
        return calculate(newestFirst, NextLotteryDateUtils.nextDrawDateAfter(lastDate));
    }

    public static List<KillItemBo> calculate(List<HistoryRecord> newestFirst, LocalDate targetDrawDate) {
        var byRule = calculateByRule(newestFirst, targetDrawDate);
        Map<Integer, List<String>> sources = new LinkedHashMap<>();
        byRule.forEach((rule, balls) -> {
            // 原测试单独打印该规则，但没有把它加入最终 Stream.of 汇总。
            if (!CONTINUOUS_THREE.equals(rule)) balls.forEach(ball ->
                sources.computeIfAbsent(ball, ignored -> new ArrayList<>()).add(rule));
        });
        return sources.entrySet().stream().map(entry -> KillItemBo.builder().ball(entry.getKey()).score(1.0)
            .source(String.join("、", entry.getValue())).reason("test_20_zh红球规则：" + String.join("、", entry.getValue()))
            .build()).toList();
    }

    /** 原测试的逐规则结果，保留原始算式输出（包括0或大于33的结果），不额外取模或过滤。 */
    public static Map<String, List<Integer>> calculateByRule(List<HistoryRecord> newestFirst, LocalDate targetDrawDate) {
        if (newestFirst == null || newestFirst.isEmpty()) throw new IllegalArgumentException("红球杀号需要历史开奖");
        Objects.requireNonNull(targetDrawDate, "目标开奖日期不能为空");
        var past = newestFirst.stream().limit(100).toList();
        if (past.stream().anyMatch(row -> row.getOpenDate() != null && !row.getOpenDate().isBefore(targetDrawDate))) {
            throw new IllegalArgumentException("历史开奖日期必须早于目标开奖日期");
        }
        var last = past.getFirst();
        var reds = reds(last);
        var odds = reds.stream().filter(ball -> ball % 2 != 0).toList();
        var evens = reds.stream().filter(ball -> ball % 2 == 0).toList();
        var omissions = IntStream.rangeClosed(1, 33).boxed().collect(Collectors.toMap(ball -> ball,
            ball -> OmissionUtils.omissionBallAnalyzer(past, "red", ball).getStats().getCurrentOmission(),
            (first, second) -> first, LinkedHashMap::new));
        int maxOmissionBall = omissions.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        int sum = reds.stream().mapToInt(Integer::intValue).sum();
        Map<String, List<Integer>> rules = new LinkedHashMap<>();
        rules.put(CONTINUOUS_THREE, frequencies(past, 3, 3));
        rules.put("上期蓝球杀红", List.of(last.getSpecial()));
        rules.put("近6期至少出现4次", frequencies(past, 6, 4));
        // 原 getIfConsecutive 始终返回四项数组，后续 length > 4 永不满足；保留实际空规则。
        rules.put("连号中间（原实现为空）", List.of());
        rules.put("遗漏35期", omissions.entrySet().stream().filter(entry -> entry.getValue() >= 35).map(Map.Entry::getKey).toList());
        // 保留原算法 %100 且仅减33一次，不改成 %10 或循环取模。
        rules.put("和值余100（大于33减一次33）", sum % 100 == 0 ? List.of() : List.of(reduceOnce(sum % 100)));
        rules.put("和值余100加1（大于33减一次33）", List.of(reduceOnce(sum % 100 + 1)));
        rules.put("上期蓝球+3杀红", List.of(last.getSpecial() + 3));
        rules.put("上期红球第一位+5", List.of(last.getNum1() + 5));
        rules.put("上期红球最后一位-5", List.of(last.getNum6() - 5));
        rules.put("相邻差值尾数（原实现停用）", List.of());
        rules.put("奇偶个数差值绝对值", List.of(Math.abs(odds.size() - evens.size())));
        rules.put("第1位+第6位和值尾数", List.of((last.getNum1() + last.getNum6()) % 10));
        rules.put("遗漏最大球-3", List.of(maxOmissionBall - 3));
        rules.put("最大+最小除2", List.of((last.getNum1() + last.getNum6()) / 2));
        rules.put("第5位-第2位", List.of(last.getNum5() - last.getNum2()));
        rules.put("开奖日取余+10", List.of(targetDrawDate.getDayOfMonth() % 10 + 10));
        rules.put("上期最大-最小", List.of(last.getNum6() - last.getNum1()));
        rules.put("第5位+第2位（原实现停用）", List.of());
        rules.put("第1位+第6位", List.of(reduceOnce(last.getNum1() + last.getNum6())));
        List<Integer> parityMeans = new ArrayList<>();
        if (odds.size() > 2) parityMeans.add((odds.getLast() + odds.getFirst()) / 2);
        // 原测试偶数分支实际除以3，不按注释改成除以2。
        if (evens.size() > 2) parityMeans.add((evens.getLast() + evens.getFirst()) / 3);
        rules.put("奇偶各组首尾均值（奇除2、偶除3）", List.copyOf(parityMeans));
        return Collections.unmodifiableMap(rules);
    }

    private static int reduceOnce(int value) { return value > 33 ? value - 33 : value; }

    private static List<Integer> frequencies(List<HistoryRecord> past, int window, int minimum) {
        var counts = past.stream().limit(window).flatMap(row -> reds(row).stream())
            .collect(Collectors.groupingBy(ball -> ball, TreeMap::new, Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> entry.getValue() >= minimum).map(Map.Entry::getKey).toList();
    }

    private static List<Integer> reds(HistoryRecord row) {
        return List.of(row.getNum1(), row.getNum2(), row.getNum3(), row.getNum4(), row.getNum5(), row.getNum6());
    }
}
