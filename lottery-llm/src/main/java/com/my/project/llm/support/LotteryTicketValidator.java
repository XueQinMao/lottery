package com.my.project.llm.support;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.*;
import java.util.*;
import java.util.stream.Collectors;

/** 对最终号码作可执行校验；形态候选池只做偏好，不作为拒绝条件。 */
public final class LotteryTicketValidator {
    private static final Set<Integer> PRIMES = Set.of(1, 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31);
    private LotteryTicketValidator() { }

    public static void validate(LotteryAdjustRespBo result, LotteryAdjustReqBo req, int expected) {
        require(result != null && result.getAdjustedTickets() != null, "缺少adjustedTickets");
        require(result.getAdjustedTickets().size() == expected, "adjustedTickets组数不符");
        LotteryAnalysisRespBo report = JSON.parseObject(req.getAnalysisReportJson(), LotteryAnalysisRespBo.class);
        require(report != null, "分析报告无效");
        Set<Integer> killedRed = kills(report.getKillNumbers() == null ? null : report.getKillNumbers().getHardKillRed());
        Set<Integer> killedBlue = kills(report.getKillNumbers() == null ? null : report.getKillNumbers().getHardKillBlue());
        List<List<Integer>> adjustedReds = new ArrayList<>();
        List<String> identities = new ArrayList<>();
        for (int index = 0; index < expected; index++) {
            var ticket = result.getAdjustedTickets().get(index);
            require(ticket != null, "单式不能为空");
            checkSingle(ticket.getAdjustedRedBalls(), ticket.getAdjustedBlueBall(), req, killedRed, killedBlue);
            adjustedReds.add(ticket.getAdjustedRedBalls());
            identities.add(ticket.getAdjustedRedBalls() + "|" + ticket.getAdjustedBlueBall());
            if (req.getTickets() != null && !req.getTickets().isEmpty()) {
                var original = req.getTickets().get(index);
                require(Objects.equals(original.getId(), ticket.getId()), "调优id不符");
                require(Objects.equals(original.getRedBalls(), ticket.getOriginalRedBalls())
                    && Objects.equals(original.getBlueBall(), ticket.getOriginalBlueBall()), "调优原始号码不符");
                checkReplacements(original, ticket);
            } else {
                require(ticket.getOriginalRedBalls() == null && ticket.getOriginalBlueBall() == null,
                    "推荐模式不得虚构原始号码");
                require(ticket.getBlueReplacement() == null && ticket.getRedReplacements() != null
                    && ticket.getRedReplacements().isEmpty(), "推荐模式替换记录必须为空");
            }
        }
        require(new HashSet<>(identities).size() == identities.size(), "单式组重复");
        checkKillDistribution(adjustedReds, killedRed);
        var pack = result.getFinalRecommendation();
        require(pack != null && pack.getSingleTickets() != null && pack.getSingleTickets().size() == 2,
            "最终包必须包含2组单式");
        List<List<Integer>> finalReds = new ArrayList<>();
        Set<String> finalIds = new HashSet<>();
        for (var ticket : pack.getSingleTickets()) {
            require(ticket != null, "最终单式不能为空");
            checkSingle(ticket.getRedBalls(), ticket.getBlueBall(), req, killedRed, killedBlue);
            require(Objects.equals(ticket.getTotalBets(), 1), "单式注数必须为1");
            finalReds.add(ticket.getRedBalls());
            finalIds.add(ticket.getRedBalls() + "|" + ticket.getBlueBall());
        }
        require(finalIds.size() == 2, "最终两组单式重复");
        checkKillDistribution(finalReds, killedRed);
        var complex = pack.getComplexTicket();
        require(complex != null, "缺少最终复式");
        numbers(complex.getRedBalls(), 7, 10, 33);
        numbers(complex.getBlueBalls(), 2, 5, 16);
        checkReds(complex.getRedBalls(), req);
        for (Integer blue : complex.getBlueBalls()) checkBlue(blue, req, killedBlue);
        if (!killedRed.isEmpty()) {
            long count = complex.getRedBalls().stream().filter(killedRed::contains).count();
            require(count >= 1 && count <= 3, "复式硬杀红必须1-3个");
        }
        require(hasSafeSubset(complex.getRedBalls(), 0, new ArrayList<>()), "复式缺少满足安全范围的6红子集");
        int bets = combinations(complex.getRedBalls().size(), 6) * complex.getBlueBalls().size();
        require(Objects.equals(complex.getTotalBets(), bets), "复式注数错误，应为" + bets);
    }

    private static void checkSingle(List<Integer> reds, Integer blue, LotteryAdjustReqBo req,
                                    Set<Integer> killedRed, Set<Integer> killedBlue) {
        numbers(reds, 6, 6, 33);
        checkBlue(blue, req, killedBlue);
        checkReds(reds, req);
        require(safeSix(reds), "单式和值/跨度/奇偶/质合超出安全范围");
        require(reds.stream().filter(killedRed::contains).count() <= 2, "单式硬杀红超过2个");
    }

    private static void checkBlue(Integer blue, LotteryAdjustReqBo req, Set<Integer> killedBlue) {
        require(blue != null && blue >= 1 && blue <= 16, "蓝球范围错误");
        require(!killedBlue.contains(blue), "蓝球包含硬杀号");
        require(!Objects.equals(blue, req.getLastDrawBlueBall()), "蓝球与上期重复");
    }

    private static void checkReds(List<Integer> reds, LotteryAdjustReqBo req) {
        Map<Integer, Long> tails = reds.stream().collect(Collectors.groupingBy(n -> n % 10, Collectors.counting()));
        require(tails.values().stream().allMatch(count -> count <= 3), "红球包含4同尾");
        require(req.getLastDrawBlueBall() == null || !reds.contains(req.getLastDrawBlueBall()), "红球包含上期蓝球");
        if (req.getLastDrawRedBalls() != null && !req.getLastDrawRedBalls().isEmpty()) {
            require(reds.stream().filter(req.getLastDrawRedBalls()::contains).count() <= 2, "红球重号超过2个");
        }
    }

    private static boolean safeSix(List<Integer> reds) {
        int sum = reds.stream().mapToInt(Integer::intValue).sum();
        int span = reds.getLast() - reds.getFirst();
        return sum >= 90 && sum <= 130 && span >= 16 && span <= 28
            && reds.stream().filter(n -> n % 2 == 1).count() <= 5
            && reds.stream().filter(PRIMES::contains).count() <= 4;
    }

    private static boolean hasSafeSubset(List<Integer> reds, int start, List<Integer> chosen) {
        if (chosen.size() == 6) return safeSix(chosen);
        for (int index = start; index <= reds.size() - (6 - chosen.size()); index++) {
            chosen.add(reds.get(index));
            if (hasSafeSubset(reds, index + 1, chosen)) return true;
            chosen.removeLast();
        }
        return false;
    }

    private static void checkReplacements(LotteryAdjustReqBo.PredictTicket original,
                                          LotteryAdjustRespBo.AdjustedTicket ticket) {
        Set<Integer> removed = new HashSet<>(original.getRedBalls());
        removed.removeAll(ticket.getAdjustedRedBalls());
        Set<Integer> added = new HashSet<>(ticket.getAdjustedRedBalls());
        added.removeAll(original.getRedBalls());
        var replacements = Optional.ofNullable(ticket.getRedReplacements()).orElse(List.of());
        require(replacements.size() == removed.size(), "红球替换记录数量不符");
        Set<Integer> from = new HashSet<>();
        Set<Integer> to = new HashSet<>();
        replacements.forEach(r -> { require(r != null, "替换记录为空"); from.add(r.getFrom()); to.add(r.getTo()); });
        require(from.equals(removed) && to.equals(added), "红球替换记录与实际差异不符");
        var blue = ticket.getBlueReplacement();
        if (Objects.equals(original.getBlueBall(), ticket.getAdjustedBlueBall())) {
            require(blue == null, "蓝球未变化却有替换记录");
        } else {
            require(blue != null && Objects.equals(blue.getFrom(), original.getBlueBall())
                && Objects.equals(blue.getTo(), ticket.getAdjustedBlueBall()), "蓝球替换记录不符");
        }
    }

    private static void checkKillDistribution(List<List<Integer>> groups, Set<Integer> killed) {
        require(groups.stream().filter(reds -> reds.stream().anyMatch(killed::contains)).count() <= 1,
            "至多一组单式可含硬杀红");
    }

    private static Set<Integer> kills(List<KillNumberResultBo.KillItemBo> items) {
        return Optional.ofNullable(items).orElse(List.of()).stream().map(KillNumberResultBo.KillItemBo::getBall)
            .collect(Collectors.toSet());
    }

    private static void numbers(List<Integer> values, int min, int max, int upper) {
        require(values != null && values.size() >= min && values.size() <= max, "号码个数错误");
        int previous = 0;
        for (Integer value : values) {
            require(value != null && value > previous && value <= upper, "号码必须在合法范围内互异升序");
            previous = value;
        }
    }

    private static int combinations(int n, int k) {
        int result = 1;
        for (int index = 1; index <= k; index++) result = result * (n - index + 1) / index;
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
