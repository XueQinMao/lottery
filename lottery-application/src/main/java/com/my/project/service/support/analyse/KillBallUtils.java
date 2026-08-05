package com.my.project.service.support.analyse;

import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;
import com.my.project.service.support.OmissionDueProbabilityUtils;
import com.my.project.service.support.OmissionUtils;
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
 * 计算杀球的工具类
 */
public class KillBallUtils {

    /** 软杀数量标准：根据来源类型与候选总数决定软杀条数 */
    private static final Map<String, Function<Integer, Integer>> SOFT_STANDARD_MAP = new HashMap<>();
    /** 软杀选取策略：各来源统一为“按顺序取前 N 条” */
    private static final BiFunction<List<KillNumberResultBo.KillItemBo>, Integer, Map<Integer, KillNumberResultBo.KillItemBo>>
        TAKE_FIRST_N = (sources, size) -> sources.stream().limit(size)
        .collect(Collectors.toMap(KillNumberResultBo.KillItemBo::getBall, Function.identity()));

    static {
        //        SOFT_STANDARD_MAP.put("jx", size -> size <= 2 ? 0 : (size <= 7 ? 2 : 4));
        //        SOFT_STANDARD_MAP.put("yl", size -> size <= 2 ? 0 : (size <= 6 ? 2 : 4));
        //        SOFT_STANDARD_MAP.put("zs", size -> size <= 2 ? 0 : (size <= 4 ? 1 : 4));

        SOFT_STANDARD_MAP.put("jx", size -> 0);
        SOFT_STANDARD_MAP.put("yl", size -> 0);
        SOFT_STANDARD_MAP.put("zs", size -> 0);
    }

    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords) {
        return calculate(historyRecords, null);
    }

    /**
     * 计算要杀的红球+篮球
     *
     * @param historyRecords 历史开奖记录
     * @return
     */
    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        // 1. 公共数据提取：统一抽取各来源红球/篮球杀号候选
        var data = extractKillData(historyRecords, nextWinBalls);

        // 2. 邪修红球按来源打印调试（保持原内联逻辑，无条件执行）
        printXxRedSourceDebug(data.xxRed(), nextWinBalls);

        // 3. 篮球杀号计算
        var hardKillBlue = calculateBlueKill(data);

        // 4. 红球杀号计算（按来源条数分流软杀，其余硬杀）
        var redSplit = splitRedKillBySource(data);

        // 5. 调试输出：各来源红球/篮球杀号统计
        if (Objects.nonNull(nextWinBalls)) {
            printCalculateDebug(nextWinBalls, buildRedKillBySourceMap(redSplit, data),
                buildBlueKillBySourceMap(data));
        }

        // 6. 汇总红球硬杀并构建最终结果
        var hardKillRed = collectHardKillRed(redSplit, data);
        var result = KillNumberResultBo.builder().hardKillRed(hardKillRed.stream().distinct().toList())
            .hardKillBlue(Stream.of(hardKillBlue).flatMap(List::stream).distinct().toList()).build();
        if (Objects.nonNull(nextWinBalls)) {
            printResultDebug(result, nextWinBalls);
        }
        return result;
    }

    /**
     * 公共数据提取：统一抽取均线/指数/遗漏/邪修/上期等各来源的红球与篮球杀号候选
     */
    private static KillData extractKillData(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        var killBallByJx = getKillBallByJx(historyRecords);
        var killBallByZs = getKillBallByZs(historyRecords);
        var killBallByYl = getKillBallByYl(historyRecords);
        var last = historyRecords.getFirst();
        var lastBlue =
            historyRecords.subList(0, 4).stream().map(HistoryRecord::getSpecial).map(ball -> Pair.of(ball, 1.0))
                .map(boMapper("上期出现")).toList();
        //发现均线抬头的篮球基本都是错误的所以我直接杀掉
        var jxttBlue = IntStream.rangeClosed(1, 16).boxed().map(
                ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(historyRecords.subList(0, 100), "blue", ball)))
            .filter(p -> p.getRight().getPhase().equals("rising") || p.getRight().getPhase().equals("rebounding"))
            .map(p -> Pair.of(p.getLeft(), 1.0)).map(boMapper("均线抬头")).toList();
        var lastReds =
            Stream.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6())
                .map(ball -> Pair.of(ball, 1.0)).map(boMapper("上期开出-红球")).toList();
        var killBallByXx = getKillBallByXx(historyRecords, nextWinBalls);
        var killBlueByXx = killBlue(historyRecords);
        return new KillData(
            killBallByJx.getLeft(), killBallByJx.getRight(),
            killBallByZs.getLeft(), killBallByZs.getRight(),
            killBallByYl.getLeft(), killBallByYl.getRight(),
            lastBlue, jxttBlue, lastReds,
            killBallByXx.getLeft(), killBallByXx.getRight(),
            killBlueByXx
        );
    }

    /**
     * 邪修红球按来源打印调试（保持原 calculate 内联逻辑，无条件执行）
     */
    private static void printXxRedSourceDebug(List<KillNumberResultBo.KillItemBo> xxRed,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        xxRed.stream().collect(Collectors.groupingBy(KillNumberResultBo.KillItemBo::getSource))
            .forEach((key, value) -> {
                List<Integer> list = value.stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
                System.out.println("邪修杀-"+key+"："+list.size()+" 误杀："+CollectionUtils.intersection(list, nextWinBalls.getLeft()));
            });
    }

    /**
     * 篮球杀号计算：均线蓝球始终硬杀；上期出现/上期开出-红球/邪修杀(蓝) 均进硬杀
     */
    private static List<KillNumberResultBo.KillItemBo> calculateBlueKill(KillData data) {
        // 均线蓝球始终硬杀；遗漏/指数蓝球仅在条数都 ≤2 时进硬杀，否则进软杀
        var hardKillBlue = new ArrayList<>(data.lastBlue());
        hardKillBlue.addAll(data.lastReds());
        hardKillBlue.addAll(data.killBlueByXx());
        return hardKillBlue;
    }

    /**
     * 红球按来源条数分流软杀，其余硬杀
     */
    private static RedSplit splitRedKillBySource(KillData data) {
        var zsHardAndSoft = splitIntoHardAndSoft(data.zsRed(), "zs");
        var ylHardAndSoft = splitIntoHardAndSoft(data.ylRed(), "yl");
        var jxHardAndSoft = splitIntoHardAndSoft(data.jxRed(), "jx");
        return new RedSplit(zsHardAndSoft, ylHardAndSoft, jxHardAndSoft);
    }

    /**
     * 构建红球来源 -> (硬杀, 软杀) 调试映射
     */
    private static Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>> buildRedKillBySourceMap(
        RedSplit redSplit, KillData data) {
        Map<String, Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>>>
            redKillBySource = new LinkedHashMap<>();
        redKillBySource.put("均线杀球", redSplit.jxHardAndSoft());
        redKillBySource.put("指数杀球", redSplit.zsHardAndSoft());
        //            redKillBySource.put("遗漏杀球", ylHardAndSoft);
        redKillBySource.put("邪修杀", Pair.of(data.xxRed(), List.of()));
        return redKillBySource;
    }

    /**
     * 构建篮球来源 -> (硬杀, 软杀) 调试映射
     */
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
     * 汇总红球硬杀：指数硬杀 + 均线硬杀 + 邪修红杀
     */
    private static List<KillNumberResultBo.KillItemBo> collectHardKillRed(RedSplit redSplit, KillData data) {
        //获取所有的硬杀
        return Stream.of(redSplit.zsHardAndSoft().getLeft(),
            //                    ylHardAndSoft.getLeft(),// 1
            redSplit.jxHardAndSoft().getLeft(),//2
            data.xxRed()).filter(CollectionUtils::isNotEmpty).flatMap(List::stream).toList();
    }

    /**
     * 杀球数据上下文：承载各来源红球/篮球杀号候选
     */
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

    /**
     * 红球按来源分流结果：各来源的 (硬杀, 软杀) 对
     */
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

    /** 篮球遗漏/指数：条数 ≤2 进必杀，否则进软杀 */
    private static Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> splitBlueByCount(
        List<KillNumberResultBo.KillItemBo> source) {
        if (source.size() <= 2) {
            return Pair.of(source, List.of());
        }
        return Pair.of(List.of(), source);
    }

    /**
     * 打印各杀球方式的红球/篮球杀号统计（调试用），区分必杀和软杀。
     */
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

        //        System.out.println("---- 篮球杀号统计 ----");
        //        blueKillBySource.forEach((source, hardAndSoft) -> {
        //            List<Integer> hardBalls =
        //                hardAndSoft.getLeft().stream().map(KillNumberResultBo.KillItemBo::getBall).distinct().toList();
        //            List<Integer> softBalls =
        //                hardAndSoft.getRight().stream().map(KillNumberResultBo.KillItemBo::getBall).distinct().toList();
        //            boolean hardWrong = hardBalls.contains(winBlueBall);
        //            boolean softWrong = softBalls.contains(winBlueBall);
        //            System.out.println(
        //                source + "：必杀 " + hardBalls.size() + " 个，是否误杀：" + hardWrong + "；软杀 " + softBalls.size() + " 个，是否误杀：" + softWrong);
        //        });
    }

    /**
     * 打印最终杀号结果统计（调试用）。
     */
    private static void printResultDebug(KillNumberResultBo result,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        System.out.println("红球必杀" + result.getHardKillRed().size() + " 必误：" + CollectionUtils.intersection(
            result.getHardKillRed().stream().map(KillNumberResultBo.KillItemBo::getBall).toList(),
            nextWinBalls.getLeft()).size() + " 篮球必杀：" + result.getHardKillBlue()
            .size() + " 必杀误杀：" + result.getHardKillBlue().stream().map(KillNumberResultBo.KillItemBo::getBall)
            .toList().contains(nextWinBalls.getRight())
            //            + " 篮球软杀：" + result.getSoftKillBlue().size()
            //            + " 软杀误杀：" + result.getSoftKillBlue().stream().map(KillNumberResultBo.KillItemBo::getBall).toList().contains(nextWinBalls.getRight())
            + " 篮球最终误杀：" + Stream.of(result.getHardKillBlue()).flatMap(List::stream)
            .map(KillNumberResultBo.KillItemBo::getBall).toList().contains(nextWinBalls.getRight()));
        System.out.println("******************************");
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
        List<HistoryRecord> historyRecords, Triple<List<Integer>, String, Integer> nextWinBalls) {
        Map<Integer, Integer> readCountMap = new HashMap<>();
        Map<Integer, Integer> blueCountMap = new HashMap<>();
        historyRecords.subList(0, 6).forEach(h -> {
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
            readCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 4).map(Map.Entry::getKey).toList();
        var moreThan3BlueBalls =
            blueCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 2).map(Map.Entry::getKey).toList();
        var moreThan3RedBallBos =
            CollectionUtils.emptyIfNull(moreThan3RedBalls).stream().map(ball -> Pair.of(ball, 1.0))
                .map(boMapper("6期出现3次")).toList();
        var moreThan3BlueBallBos =
            CollectionUtils.emptyIfNull(moreThan3BlueBalls).stream().map(ball -> Pair.of(ball, 1.0))
                .map(boMapper("6期出现3次")).toList();
        // 上期红球按近 5 期出现次数降序，取 5 个
        var lastWinRandomRedBalls = lastWinRedBalls.stream()
            .sorted(Comparator.comparingInt((Integer ball) -> readCountMap.getOrDefault(ball, 0))).limit(3)
            .map(ball -> Pair.of(ball, 1.0)).map(boMapper("上期开出")).toList();
        //杀掉3连号中间的号
        var consecutiveBeforeAfterRedBalls =
            getIfConsecutive(lastWinRedBalls).stream().filter(array -> array.length > 4).map(array -> {
                return IntStream.range(1, array.length - 1).mapToObj(i -> Pair.of(array[i], 1.0)).toList();
            }).flatMap(List::stream).filter(p -> p.getLeft() > 0 && p.getLeft() < 34).map(boMapper("三连号中间号")).toList();
        //两连号前后
        var redConsecutiveKillCandidates = getIfConsecutive(lastWinRedBalls).stream().filter(array -> array.length == 4)
            .map(array -> List.of(array[0], array[array.length - 1])).flatMap(List::stream)
            .filter(ball -> ball > 0 && ball < 34).map(ball -> Pair.of(ball, 1.0)).map(boMapper("两连号前后号")).toList();

        //篮球杀掉前后号码
        var consecutiveBeforeAfterBlueBalls =
            Stream.of(first.getSpecial() + 1, first.getSpecial() - 1).filter(ball -> ball > 0 && ball < 17)
                .map(ball -> Pair.of(ball, 1.0)).map(boMapper("邪修杀")).toList();
        //计算遗漏35期的直接杀
        var mapMapPair = OmissionDueProbabilityUtils.analyzeRedBlueOmission(historyRecords.subList(0, 100));
        var moreThanOmission35Reds = mapMapPair.getLeft().entrySet().stream()
            .filter(entry -> entry.getValue().getStats().getCurrentOmission() >= 35)
            .map(entry -> Pair.of(entry.getKey(), 1.0)).map(boMapper("遗漏35期")).toList();
        var moreThanOmission35Blues = mapMapPair.getRight().entrySet().stream()
            .filter(entry -> entry.getValue().getStats().getCurrentOmission() >= 35)
            .map(entry -> Pair.of(entry.getKey(), 1.0)).map(boMapper("遗漏35期")).toList();

        //上一期的篮球，在红球中杀掉
        var blueKillReds = List.of(boMapper("上一期篮球红杀").apply(Pair.of(first.getSpecial(), 1.0)));

        List<Integer> lastWinRandomRedNumbers =
            lastWinRandomRedBalls.stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
        List<Integer> moreThanOmission35RedNumbers =
            moreThanOmission35Reds.stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
        List<Integer> consecutiveBeforeAfterRedNumbers =
            consecutiveBeforeAfterRedBalls.stream().map(KillNumberResultBo.KillItemBo::getBall).toList();

        if (Objects.nonNull(nextWinBalls)) {
            printXxDebug(nextWinBalls, moreThan3RedBalls, moreThan3BlueBallBos, lastWinRandomRedNumbers,
                consecutiveBeforeAfterRedNumbers, moreThanOmission35RedNumbers);
        }

        var kdhBos = Stream.of(lastWinRedBalls.getLast() - lastWinRedBalls.getFirst()).map(ball -> Pair.of(ball, 1.0))
            .map(boMapper("跨度直接杀")).toList();

        //和值杀
        int sum = lastWinRedBalls.stream().mapToInt(Integer::intValue).sum();
        var hzs = sum % 100;
        var hzsBos =
            Stream.of(hzs).filter(ball -> ball > 0 && ball < 34).map(ball -> Pair.of(ball, 1.0)).map(boMapper("和值杀"))
                .toList();

        List<KillNumberResultBo.KillItemBo> redKillBallBos =
            Stream.of(moreThan3RedBallBos, lastWinRandomRedBalls, consecutiveBeforeAfterRedBalls,
                    moreThanOmission35Reds, blueKillReds, hzsBos, kdhBos, redConsecutiveKillCandidates)
                .filter(CollectionUtils::isNotEmpty).flatMap(List::stream).toList();
        List<KillNumberResultBo.KillItemBo> blueKillBallBos =
            Stream.of(moreThan3BlueBallBos, moreThanOmission35Blues, consecutiveBeforeAfterBlueBalls)
                .flatMap(List::stream).toList();
        return Pair.of(redKillBallBos, blueKillBallBos);
    }

    /**
     * 打印玄学杀号统计（调试用），输出格式与原内联实现保持一致。
     */
    private static void printXxDebug(Triple<List<Integer>, String, Integer> nextWinBalls,
        List<Integer> moreThan3RedBalls, List<KillNumberResultBo.KillItemBo> moreThan3BlueBallBos,
        List<Integer> lastWinRandomRedNumbers, List<Integer> consecutiveBeforeAfterRedNumbers,
        List<Integer> moreThanOmission35RedNumbers) {
        //        System.out.println(
        //            "邪修：最近5期出现3次" + moreThan3RedBalls + "误杀：" + CollectionUtils.intersection(moreThan3BlueBallBos,
        //                nextWinBalls.getLeft()));
        //        System.out.println(
        //            "邪修：上期开出" + lastWinRandomRedNumbers + "误杀：" + CollectionUtils.intersection(lastWinRandomRedNumbers,
        //                nextWinBalls.getLeft()));
        //        System.out.println("邪修：连号前后" + consecutiveBeforeAfterRedNumbers + "误杀：" + CollectionUtils.intersection(
        //            consecutiveBeforeAfterRedNumbers, nextWinBalls.getLeft()));
        //        System.out.println("邪修：遗漏35期" + moreThanOmission35RedNumbers + "误杀：" + CollectionUtils.intersection(
        //            moreThanOmission35RedNumbers, nextWinBalls.getLeft()));
        //        System.out.println("邪修：上期篮球" + nextWinBalls.getRight() + "误杀：" + CollectionUtils.intersection(
        //            List.of(nextWinBalls.getRight()), nextWinBalls.getLeft()));
        //        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    /**
     * 最近10期出现3次直接杀掉 遗漏50期以上直接杀掉 篮球1-8为小 9-16为大，比如上次开的大基，那么这次就杀掉大基选择小基或者小偶 同尾杀球，比如上次尾数5那么下期杀掉5 15
     * 篮球分区间1-4,5-8,9-12,13-16如果篮球出现在1期间，直接把整个区间都杀掉
     */
    private static List<KillNumberResultBo.KillItemBo> killBlue(List<HistoryRecord> historyRecords) {
        var first = historyRecords.getFirst();

        Map<Integer, Integer> blueCountMap = new HashMap<>();
        historyRecords.subList(0, 10).forEach(h -> {
            blueCountMap.merge(h.getSpecial(), 1, Integer::sum);
        });

        //10期出现3次
        List<Integer> moreThan3 =
            blueCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 3).map(Map.Entry::getKey).toList();
        //遗漏50期
        List<Integer> omission50Balls = IntStream.rangeClosed(1, 16).boxed()
            .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(historyRecords, "blue", ball)))
            .filter(p -> p.getRight().getStats().getCurrentOmission() >= 50).map(Pair::getLeft).toList();
        //同尾数杀
        List<Integer> sameTailBalls = IntStream.rangeClosed(1, 16).boxed().map(ball -> Pair.of(ball, ball % 10))
            .filter(p -> p.getRight() == first.getSpecial() % 10).map(Pair::getLeft).toList();
        //相反杀
        var big = first.getSpecial() > 8;
        var isJs = first.getSpecial() % 2 != 0;
        List<Integer> oppositeBalls = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            boolean matchBig = big ? (i > 8) : (i <= 8);
            boolean matchJs = isJs ? (i % 2 != 0) : (i % 2 == 0);

            if (matchBig && matchJs) {
                oppositeBalls.add(i);
            }
        }

        List<Integer> allKillBalls =
            Stream.of(moreThan3, omission50Balls, sameTailBalls).filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream).toList();
        return allKillBalls.stream().map(ball -> Pair.of(ball, 1.0)).map(boMapper("邪修杀")).toList();
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
