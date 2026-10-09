package com.my.project.service.support.analyse;

import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.support.OmissionUtils;
import com.my.project.service.support.analyse.kill.IndexKillBallStrategy;
import com.my.project.service.support.analyse.kill.KillBallStrategy;
import com.my.project.service.support.analyse.kill.KillBallStrategyResult;
import com.my.project.service.support.analyse.kill.KillBallSupport;
import com.my.project.service.support.analyse.kill.MaKillBallStrategy;
import com.my.project.service.support.analyse.kill.OmissionKillBallStrategy;
import com.my.project.service.support.analyse.kill.XieXiuKillBallStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 计算杀球的工具类：策略只负责出号，硬杀汇总口径与原来一致。
 */
public class KillBallUtils {

    private static final List<KillBallStrategy> STRATEGIES = List.of(
        new MaKillBallStrategy(),
        new IndexKillBallStrategy(),
        new OmissionKillBallStrategy(),
        new XieXiuKillBallStrategy()
    );

    /** 软杀数量标准：根据来源类型与候选总数决定软杀条数 */
    private static final Map<String, Function<Integer, Integer>> SOFT_STANDARD_MAP = new HashMap<>();
    /** 软杀选取策略：各来源统一为“按顺序取前 N 条” */
    private static final BiFunction<List<KillNumberResultBo.KillItemBo>, Integer, Map<Integer, KillNumberResultBo.KillItemBo>>
        TAKE_FIRST_N = (sources, size) -> sources.stream().limit(size)
        .collect(Collectors.toMap(KillNumberResultBo.KillItemBo::getBall, Function.identity()));

    static {
        SOFT_STANDARD_MAP.put("jx", size -> 0);
        SOFT_STANDARD_MAP.put("yl", size -> 0);
        SOFT_STANDARD_MAP.put("zs", size -> 0);
    }

    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords) {
        return calculate(historyRecords, null);
    }

    /**
     * 计算要杀的红球+篮球
     */
    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        var data = extractKillData(historyRecords, nextWinBalls);

        printXxRedSourceDebug(data.xxRed(), nextWinBalls);

        var hardKillBlue = calculateBlueKill(data);
        var redSplit = splitRedKillBySource(data);

        if (Objects.nonNull(nextWinBalls)) {
            printCalculateDebug(nextWinBalls, buildRedKillBySourceMap(redSplit, data),
                buildBlueKillBySourceMap(data));
        }

        var hardKillRed = collectHardKillRed(redSplit, data);
        var result = KillNumberResultBo.builder().hardKillRed(hardKillRed.stream().distinct().toList())
            .hardKillBlue(Stream.of(hardKillBlue).flatMap(List::stream).distinct().toList()).build();
        if (Objects.nonNull(nextWinBalls)) {
            printResultDebug(result, nextWinBalls);
        }
        return result;
    }

    /**
     * 策略出号 + 上期蓝/上期红/均线抬头等额外候选。
     */
    private static KillData extractKillData(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        Map<String, KillBallStrategyResult> byName = new LinkedHashMap<>();
        for (KillBallStrategy strategy : STRATEGIES) {
            byName.put(strategy.name(), strategy.calculate(historyRecords, nextWinBalls));
        }
        var jx = byName.getOrDefault("均线杀球", KillBallStrategyResult.empty());
        var zs = byName.getOrDefault("指数杀球", KillBallStrategyResult.empty());
        var yl = byName.getOrDefault("遗漏杀球", KillBallStrategyResult.empty());
        var xx = byName.getOrDefault("邪修杀", KillBallStrategyResult.empty());

        var last = historyRecords.getFirst();
        var lastBlue =
            historyRecords.subList(0, 4).stream().map(HistoryRecord::getSpecial).map(ball -> Pair.of(ball, 1.0))
                .map(KillBallSupport.boMapper("上期出现")).toList();
        var jxttBlue = IntStream.rangeClosed(1, 16).boxed().map(
                ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(historyRecords.subList(0, 100), "blue", ball)))
            .filter(p -> p.getRight().getPhase().equals("rising") || p.getRight().getPhase().equals("rebounding"))
            .map(p -> Pair.of(p.getLeft(), 1.0)).map(KillBallSupport.boMapper("均线抬头")).toList();
        var lastReds =
            Stream.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6())
                .map(ball -> Pair.of(ball, 1.0)).map(KillBallSupport.boMapper("上期开出-红球")).toList();

        return new KillData(jx.redKills(), jx.blueKills(), zs.redKills(), zs.blueKills(), yl.redKills(), yl.blueKills(),
            lastBlue, jxttBlue, lastReds, xx.redKills(), xx.blueKills(), xx.aggregationBlueKills());
    }

    private static void printXxRedSourceDebug(List<KillNumberResultBo.KillItemBo> xxRed,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        if (Objects.isNull(nextWinBalls)) {
            return;
        }
        xxRed.stream().collect(Collectors.groupingBy(KillNumberResultBo.KillItemBo::getSource)).forEach((key, value) -> {
            List<Integer> list = value.stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
            System.out.println(
                "邪修杀-" + key + "：" + list.size() + " 误杀：" + CollectionUtils.intersection(list, nextWinBalls.getLeft()));
        });
    }

    /**
     * 篮球硬杀：上期出现 + 上期红球(≤16) + 邪修杀蓝（enabled 时）。
     */
    private static List<KillNumberResultBo.KillItemBo> calculateBlueKill(KillData data) {
        var hardKillBlue = new ArrayList<>(data.lastBlue());
        hardKillBlue.addAll(data.lastReds().stream().filter(bo -> bo.getBall() <= 16).toList());
        if (strategyEnabled("邪修杀")) {
            hardKillBlue.addAll(data.killBlueByXx());
        }
        return hardKillBlue;
    }

    /**
     * 红球按来源条数分流软杀，其余硬杀。均线当前 enabled=false，分流结果置空，与原逻辑一致。
     */
    private static RedSplit splitRedKillBySource(KillData data) {
        var zsHardAndSoft = splitIntoHardAndSoft(data.zsRed(), "zs");
        var ylHardAndSoft = splitIntoHardAndSoft(data.ylRed(), "yl");
        var jxHardAndSoft = strategyEnabled("均线杀球")
            ? splitIntoHardAndSoft(data.jxRed(), "jx")
            : Pair.of(List.<KillNumberResultBo.KillItemBo>of(), List.<KillNumberResultBo.KillItemBo>of());
        return new RedSplit(zsHardAndSoft, ylHardAndSoft, jxHardAndSoft);
    }

    private static Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>> buildRedKillBySourceMap(
        RedSplit redSplit, KillData data) {
        Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>>
            redKillBySource = new LinkedHashMap<>();
        redKillBySource.put("均线杀球", redSplit.jxHardAndSoft());
        redKillBySource.put("指数杀球", redSplit.zsHardAndSoft());
        redKillBySource.put("邪修杀", Pair.of(data.xxRed(), List.of()));
        return redKillBySource;
    }

    private static Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>> buildBlueKillBySourceMap(
        KillData data) {
        Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>>
            blueKillBySource = new LinkedHashMap<>();
        blueKillBySource.put("上期出现", Pair.of(data.lastBlue(), List.of()));
        blueKillBySource.put("均线抬头", Pair.of(data.jxttBlue(), List.of()));
        blueKillBySource.put("上期开出-红球", Pair.of(data.lastReds(), List.of()));
        blueKillBySource.put("邪修杀(蓝)", Pair.of(data.killBlueByXx(), List.of()));
        blueKillBySource.put("邪修杀", Pair.of(data.xxBlue(), List.of()));
        return blueKillBySource;
    }

    /**
     * 汇总红球硬杀：仅 enabled 策略。指数/均线走分流硬杀；遗漏默认关闭；邪修整批硬杀。
     */
    private static List<KillNumberResultBo.KillItemBo> collectHardKillRed(RedSplit redSplit, KillData data) {
        List<KillNumberResultBo.KillItemBo> hard = new ArrayList<>();
        if (strategyEnabled("指数杀球")) {
            hard.addAll(redSplit.zsHardAndSoft().getLeft());
        }
        if (strategyEnabled("遗漏杀球")) {
            hard.addAll(redSplit.ylHardAndSoft().getLeft());
        }
        if (strategyEnabled("均线杀球")) {
            hard.addAll(redSplit.jxHardAndSoft().getLeft());
        }
        if (strategyEnabled("邪修杀")) {
            hard.addAll(data.xxRed());
        }
        return hard;
    }

    private static boolean strategyEnabled(String name) {
        return STRATEGIES.stream().anyMatch(s -> s.name().equals(name) && s.enabled());
    }

    private record KillData(
        List<KillNumberResultBo.KillItemBo> jxRed,
        List<KillNumberResultBo.KillItemBo> jxBlue,
        List<KillNumberResultBo.KillItemBo> zsRed,
        List<KillNumberResultBo.KillItemBo> zsBlue,
        List<KillNumberResultBo.KillItemBo> ylRed,
        List<KillNumberResultBo.KillItemBo> ylBlue,
        List<KillNumberResultBo.KillItemBo> lastBlue,
        List<KillNumberResultBo.KillItemBo> jxttBlue,
        List<KillNumberResultBo.KillItemBo> lastReds,
        List<KillNumberResultBo.KillItemBo> xxRed,
        List<KillNumberResultBo.KillItemBo> xxBlue,
        List<KillNumberResultBo.KillItemBo> killBlueByXx
    ) {
    }

    private record RedSplit(
        Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> zsHardAndSoft,
        Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> ylHardAndSoft,
        Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> jxHardAndSoft
    ) {
    }

    private static Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> splitIntoHardAndSoft(
        List<KillNumberResultBo.KillItemBo> source, String type) {
        var softSize = SOFT_STANDARD_MAP.get(type).apply(source.size());
        List<KillNumberResultBo.KillItemBo> hardOut = new ArrayList<>();
        List<KillNumberResultBo.KillItemBo> softOut = new ArrayList<>();
        if (softSize == 0) {
            hardOut.addAll(source);
        } else {
            var soft = TAKE_FIRST_N.apply(source, softSize);
            softOut.addAll(soft.values());
            var hard = source.stream().filter(bo -> !soft.containsKey(bo.getBall())).toList();
            hardOut.addAll(hard);
        }
        return Pair.of(hardOut, softOut);
    }

    private static void printCalculateDebug(Triple<List<Integer>, String, Integer> nextWinBalls,
        Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>> redKillBySource,
        Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>> blueKillBySource) {
        var winRedBalls = nextWinBalls.getLeft();
        var winBlueBall = nextWinBalls.getRight();
        System.out.println(
            "第" + nextWinBalls.getMiddle() + "开奖:" + StringUtils.join(winRedBalls, ",") + "    " + winBlueBall);

        System.out.println("---- 红球杀号统计 ----");
        redKillBySource.forEach((source, hardAndSoft) -> {
            List<Integer> hardBalls =
                hardAndSoft.getLeft().stream().map(KillNumberResultBo.KillItemBo::getBall).distinct().toList();
            int hardWrong = CollectionUtils.intersection(hardBalls, winRedBalls).size();
            System.out.println(source + "：必杀 " + hardBalls.size() + " 误杀 " + hardWrong + " 个");
        });
    }

    private static void printResultDebug(KillNumberResultBo result,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        System.out.println("红球必杀" + result.getHardKillRed().size() + " 必误：" + CollectionUtils.intersection(
            result.getHardKillRed().stream().map(KillNumberResultBo.KillItemBo::getBall).toList(),
            nextWinBalls.getLeft()).size() + " 篮球必杀：" + result.getHardKillBlue()
            .size() + " 必杀误杀：" + result.getHardKillBlue().stream().map(KillNumberResultBo.KillItemBo::getBall)
            .toList().contains(nextWinBalls.getRight())
            + " 篮球最终误杀：" + Stream.of(result.getHardKillBlue()).flatMap(List::stream)
            .map(KillNumberResultBo.KillItemBo::getBall).toList().contains(nextWinBalls.getRight()));
        System.out.println("******************************");
    }
}
