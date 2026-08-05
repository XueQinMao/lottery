package com.my.project.service.support.analyse;

import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;
import com.my.project.service.support.OmissionDueProbabilityUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 计算杀球的工具类
 */
public class KillBallUtils {

    private static final Map<String, Function<Integer, Integer>> SOFT_STANDARD_MAP = new HashMap<>();
    private static Map<String, BiFunction<List<KillNumberResultBo.KillItemBo>, Integer, Map<Integer, KillNumberResultBo.KillItemBo>>>
        SOFT_SUPPLIER_MAP = new HashMap<>();

    static {
        SOFT_STANDARD_MAP.put("jx", size -> size <= 2 ? 0 : (size <= 7 ? 1 : 2));
        SOFT_STANDARD_MAP.put("yl", size -> size <= 2 ? 0 : (size < 6 ? 2 : size / 2));
        SOFT_STANDARD_MAP.put("zs", size -> size <= 2 ? 0 : (size <= 4 ? 1 : size / 2));

        SOFT_SUPPLIER_MAP.put("jx", (sources, size) -> sources.stream().limit(size)
            .collect(Collectors.toMap(KillNumberResultBo.KillItemBo::getBall, Function.identity())));
        SOFT_SUPPLIER_MAP.put("yl", (sources, size) -> sources.stream().limit(size)
            .collect(Collectors.toMap(KillNumberResultBo.KillItemBo::getBall, Function.identity())));
        SOFT_SUPPLIER_MAP.put("zs", (sources, size) -> sources.stream().limit(size)
            .collect(Collectors.toMap(KillNumberResultBo.KillItemBo::getBall, Function.identity())));

    }

    /**
     * 计算要杀的红球+篮球
     *
     * @param historyRecords 历史开奖记录
     * @return
     */
    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords) {
        var killBallByJx = getKillBallByJx(historyRecords);
        var killBallByZs = getKillBallByZs(historyRecords);
        var killBallByYl = getKillBallByYl(historyRecords);
        var lastBlue = List.of(boMapper("上期开出").apply(Pair.of(historyRecords.getFirst().getSpecial(), 1.0)));

        var killBallByXx = getKillBallByXx(historyRecords);

        // 均线蓝球始终硬杀；遗漏/指数蓝球仅在条数都 ≤2 时进硬杀，否则进软杀
        var hardKillBlue = new ArrayList<>(killBallByJx.getRight());
        hardKillBlue.addAll(lastBlue);
        List<KillNumberResultBo.KillItemBo> softKillBlue = new ArrayList<>();
        if (killBallByYl.getRight().size() <= 2) {
            hardKillBlue.addAll(killBallByYl.getRight());
        } else {
            softKillBlue.addAll(killBallByYl.getRight());
        }
        if (killBallByZs.getRight().size() <= 2) {
            hardKillBlue.addAll(killBallByZs.getRight());
        } else {
            softKillBlue.addAll(killBallByZs.getRight());
        }

        // 红球按来源条数分流软杀，其余硬杀
        var zsHardAndSoft = splitIntoHardAndSoft(killBallByZs.getLeft(), "zs");
        var ylHardAndSoft = splitIntoHardAndSoft(killBallByYl.getLeft(), "yl");
        var jxHardAndSoft = splitIntoHardAndSoft(killBallByJx.getLeft(), "jx");
        //获取所有的硬杀
        var hardKillRed = Stream.of(zsHardAndSoft.getLeft(), ylHardAndSoft.getLeft(), jxHardAndSoft.getLeft(), killBallByXx.getLeft())
            .filter(CollectionUtils::isNotEmpty).flatMap(List::stream).toList();
        var softKillRed = Stream.of(zsHardAndSoft.getRight(), ylHardAndSoft.getRight(), jxHardAndSoft.getRight(), killBallByXx.getRight())
            .filter(CollectionUtils::isNotEmpty).flatMap(List::stream).toList();

        // 软杀与必杀交集以必杀为准
        var killRedMap = hardKillRed.stream()
            .collect(Collectors.toMap(KillNumberResultBo.KillItemBo::getBall, Function.identity(), (o1, o2) -> o1));
        var killBlueMap = CollectionUtils.emptyIfNull(hardKillBlue).stream()
            .collect(Collectors.toMap(KillNumberResultBo.KillItemBo::getBall, Function.identity(), (o1, o2) -> o1));
        return KillNumberResultBo.builder().hardKillRed(hardKillRed.stream().distinct().toList())
            .softKillRed(softKillRed.stream().filter(bo -> !killRedMap.containsKey(bo.getBall())).distinct().toList())
            .hardKillBlue(Stream.of(hardKillBlue, lastBlue).flatMap(List::stream).distinct().toList()).softKillBlue(
                softKillBlue.stream().filter(bo -> !killBlueMap.containsKey(bo.getBall())).distinct().toList()).build();
    }

    private static Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> splitIntoHardAndSoft(
        List<KillNumberResultBo.KillItemBo> source, String type) {
        var softSize = SOFT_STANDARD_MAP.get(type).apply(source.size());
        List<KillNumberResultBo.KillItemBo> hardOut = new ArrayList<>();
        List<KillNumberResultBo.KillItemBo> softOut = new ArrayList<>();
        if (softSize == 0) {
            hardOut.addAll(source);
        } else {
            var soft = SOFT_SUPPLIER_MAP.get(type).apply(source, softSize);
            softOut.addAll(soft.values());
            var hard = source.stream().filter(bo -> !soft.containsKey(bo.getBall())).toList();
            hardOut.addAll(hard);
        }
        return Pair.of(hardOut, softOut);
    }

    /**
     * 通过均线获取需要杀掉的红球+篮球
     *
     * @param historyRecords
     * @return
     */
    private static Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> getKillBallByJx(
        List<HistoryRecord> historyRecords) {
        var past100 = historyRecords.subList(0, 100);
        var analyze = MaAnalysisUtils.analyze(past100);
        var redKills = analyze.getRedBalls().stream().filter(b -> Objects.equals("杀号", b.getAction()))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).sorted(Map.Entry.comparingByValue())
            .map(boMapper("均线杀球")).toList();

        var blueKills = analyze.getBlueBalls().stream().filter(b -> Objects.equals("杀号", b.getAction()))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).map(boMapper("均线杀球")).toList();
        return Pair.of(redKills, blueKills);
    }

    /**
     * 通过指数获取杀球
     *
     * @param historyRecords
     * @return
     */
    private static Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> getKillBallByZs(
        List<HistoryRecord> historyRecords) {
        var indexAnalyze = IndexAnalysisUtils.analyze(historyRecords);
        var killReds = indexAnalyze.getRedBalls().stream()
            .filter(r -> !(Objects.equals(r.getIndexTb(), "小") && r.getConfidence() >= 0.05))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).sorted(Map.Entry.comparingByValue())
            .map(boMapper("指数杀球")).toList();
        var killBlues = indexAnalyze.getBlueBalls().stream()
            .filter(r -> !(Objects.equals(r.getIndexTb(), "小") && r.getConfidence() >= 0.05))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).map(boMapper("指数杀球")).toList();
        return Pair.of(killReds, killBlues);
    }

    private static Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> getKillBallByYl(
        List<HistoryRecord> historyRecords) {
        var stringMapMap = OmissionDueProbabilityUtils.analyze(historyRecords);
        var killReds = stringMapMap.get("红球").entrySet().stream().filter(entry -> entry.getValue() <= 0)
            .map(e -> Pair.of(Integer.parseInt(e.getKey()), e.getValue())).sorted(Map.Entry.comparingByValue())
            .map(boMapper("遗漏杀球")).toList();
        var killBlues = stringMapMap.get("蓝球").entrySet().stream().filter(entry -> entry.getValue() <= 0)
            .map(e -> Pair.of(Integer.parseInt(e.getKey()), e.getValue())).sorted(Map.Entry.comparingByValue())
            .map(boMapper("遗漏杀球")).toList();
        return Pair.of(killReds, killBlues);
    }

    /**
     * 依赖玄学杀号 1、上期开出的红球只保留一个 2、上一期的篮球在红球中杀掉 3、杀掉最近五期出现3次的超热号（红蓝都使用） 4、红球杀调连号的前后的的号码 5、杀掉遗漏35期的冷号 2、上一期的篮球直接杀
     *
     * @return left 红球 right 篮球
     */
    private static Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> getKillBallByXx(
        List<HistoryRecord> historyRecords) {
        Map<Integer, Integer> readCountMap = new HashMap<>();
        Map<Integer, Integer> blueCountMap = new HashMap<>();
        historyRecords.subList(0, 5).forEach(h -> {
            readCountMap.merge(h.getNum1(), 1, Integer::sum);
            readCountMap.merge(h.getNum2(), 1, Integer::sum);
            readCountMap.merge(h.getNum3(), 1, Integer::sum);
            readCountMap.merge(h.getNum4(), 1, Integer::sum);
            readCountMap.merge(h.getNum5(), 1, Integer::sum);
            readCountMap.merge(h.getNum6(), 1, Integer::sum);
            blueCountMap.merge(h.getSpecial(), 1, Integer::sum);
        });
        var first = historyRecords.getFirst();
        var lastWinRedBalls =
            List.of(first.getNum1(), first.getNum2(), first.getNum3(), first.getNum4(), first.getNum5(),
                first.getNum6());

        var moreThan3RedBalls =
            readCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 3).map(Map.Entry::getKey).toList();
        var moreThan3BlueBalls =
            blueCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 3).map(Map.Entry::getKey).toList();
        //上期有没有出现在moreThan3RedBalls 如果在就直接杀了，如果不在那么就在上期随机杀掉5个红球
        var intersection = (List<Integer>)CollectionUtils.intersection(lastWinRedBalls, moreThan3RedBalls);
        var moreThan3RedBallBos =
            CollectionUtils.emptyIfNull(moreThan3RedBalls).stream().map(ball -> Pair.of(ball, 1.0))
                .map(boMapper("邪修杀")).toList();
        var moreThan3BlueBallBos =
            CollectionUtils.emptyIfNull(moreThan3BlueBalls).stream().map(ball -> Pair.of(ball, 1.0))
                .map(boMapper("邪修杀")).toList();
        //上期与moreThan3RedBalls有交集的直接杀掉
        var intersectionRedBallBos = CollectionUtils.emptyIfNull(intersection).stream().map(ball -> Pair.of(ball, 1.0))
            .map(boMapper("邪修杀")).toList();
        //除了intersection的个数还要随机选到5个
        var lastWinRandomRedBalls = ThreadLocalRandom.current()
            .ints(5 - intersection.size(), 0, lastWinRedBalls.size())   // 生成 5 个 [0, size) 的随机 int
            .mapToObj(lastWinRedBalls::get)       // 映射成元素
            .map(ball -> Pair.of(ball, 1.0)).map(boMapper("邪修杀")).toList();
        //红球杀调连号的前后的的号码
        var consecutiveBeforeAfterRedBalls = getIfConsecutive(lastWinRedBalls).stream()
            .map(array -> List.of(Pair.of(array[0], 1.0), Pair.of(array[array.length - 1], 1.0))).flatMap(List::stream)
            .filter(p -> p.getLeft() > 0 && p.getLeft() < 34).map(boMapper("邪修杀")).toList();
        //篮球杀掉前后号码
        var consecutiveBeforeAfterBlueBalls =
            Stream.of(first.getSpecial() + 1, first.getSpecial() - 1).filter(ball -> ball > 0 && ball < 17)
                .map(ball -> Pair.of(ball, 1.0)).map(boMapper("邪修杀")).toList();
        //计算遗漏35期的直接杀
        var mapMapPair = OmissionDueProbabilityUtils.analyzeRedBlueOmission(historyRecords.subList(0, 100));
        var moreThanOmission35Reds = mapMapPair.getLeft().entrySet().stream()
            .filter(entry -> entry.getValue().getStats().getCurrentOmission() >= 35)
            .map(entry -> Pair.of(entry.getKey(), 1.0)).map(boMapper("邪修杀")).toList();
        var moreThanOmission35Blues = mapMapPair.getRight().entrySet().stream()
            .filter(entry -> entry.getValue().getStats().getCurrentOmission() >= 35)
            .map(entry -> Pair.of(entry.getKey(), 1.0)).map(boMapper("邪修杀")).toList();

        //上一期的篮球，在红球中杀掉
        var blueKillReds =
            List.of(boMapper("邪修杀").apply(Pair.of(first.getSpecial(), 1.0)));

        List<KillNumberResultBo.KillItemBo> redKillBallBos =
            Stream.of(moreThan3RedBallBos, intersectionRedBallBos, lastWinRandomRedBalls,
                consecutiveBeforeAfterRedBalls, moreThanOmission35Reds, blueKillReds).flatMap(List::stream).toList();
        List<KillNumberResultBo.KillItemBo> blueKillBallBos =
            Stream.of(moreThan3BlueBallBos, moreThanOmission35Blues, consecutiveBeforeAfterBlueBalls)
                .flatMap(List::stream).toList();
        return Pair.of(redKillBallBos, blueKillBallBos);
    }

    private static List<Integer[]> getIfConsecutive(List<Integer> lastWinRedBalls) {
        List<Integer[]> result = new ArrayList<>();
        int n = lastWinRedBalls.size();
        int i = 0;
        while (i < n - 1) {
            if (lastWinRedBalls.get(i + 1) == lastWinRedBalls.get(i) + 1) {
                int start = i;
                // 向后扩展连续段
                while (i < n - 1 && lastWinRedBalls.get(i + 1) == lastWinRedBalls.get(i) + 1) {
                    i++;
                }
                int end = i;
                int segStart = lastWinRedBalls.get(start); // 段起点，如 6
                int segEnd = lastWinRedBalls.get(end);   // 段终点，如 7

                int before = segStart - 1;   // 前一个自然数，如 5
                int after = segEnd + 1;     // 后一个自然数，如 8
                result.add(new Integer[] {before, segStart, segEnd, after});
            } else {
                i++;
            }
        }
        return result;
    }

    private static Function<Pair<Integer, Double>, KillNumberResultBo.KillItemBo> boMapper(String source) {
        return pair -> KillNumberResultBo.KillItemBo.builder().ball(pair.getLeft()).score(pair.getValue())
            .source(source).build();
    }
}
