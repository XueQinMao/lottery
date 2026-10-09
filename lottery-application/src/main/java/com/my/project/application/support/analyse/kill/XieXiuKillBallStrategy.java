package com.my.project.application.support.analyse.kill;

import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.support.OmissionDueProbabilityUtils;
import com.my.project.application.support.OmissionUtils;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 邪修杀球：玄学红杀 + 邪修蓝杀。
 */
public class XieXiuKillBallStrategy implements KillBallStrategy {

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String name() {
        return "邪修杀";
    }

    @Override
    public KillBallStrategyResult calculate(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        var xx = getKillBallByXx(historyRecords, nextWinBalls);
        var killBlueByXx = killBlue(historyRecords);
        // blueKills = xxBlue（调试）；aggregationBlueKills = killBlue（硬杀蓝，与原 calculateBlueKill 一致）
        return new KillBallStrategyResult(xx.getLeft(), xx.getRight(), killBlueByXx);
    }

    /**
     * 依赖玄学杀号 1、上期开出的红球只保留一个 2、上一期的篮球在红球中杀掉 3、杀掉最近五期出现3次的超热号（红蓝都使用） 4、红球杀调连号的前后的的号码 5、杀掉遗漏35期的冷号 2、上一期的篮球直接杀
     *
     * @return left 红球 right 篮球
     */
    private Pair<List<KillNumberResultBo.KillItemBo>, List<KillNumberResultBo.KillItemBo>> getKillBallByXx(
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
                .map(KillBallSupport.boMapper("6期出现3次")).toList();
        var moreThan3BlueBallBos =
            CollectionUtils.emptyIfNull(moreThan3BlueBalls).stream().map(ball -> Pair.of(ball, 1.0))
                .map(KillBallSupport.boMapper("6期出现3次")).toList();
        // 暂时屏蔽：上期开出每期占 3 个杀号，50 期期误杀约 52%，球级误杀≈随机
        List<KillNumberResultBo.KillItemBo> lastWinRandomRedBalls = List.of();
        //杀掉3连号中间的号
        var consecutiveBeforeAfterRedBalls =
            getIfConsecutive(lastWinRedBalls).stream().filter(array -> array.length > 4).map(array -> {
                return IntStream.range(1, array.length - 1).mapToObj(i -> Pair.of(array[i], 1.0)).toList();
            }).flatMap(List::stream).filter(p -> p.getLeft() > 0 && p.getLeft() < 34)
            .map(KillBallSupport.boMapper("三连号中间号")).toList();
        // 暂时屏蔽：两连号前后号期误杀约 42%
        List<KillNumberResultBo.KillItemBo> redConsecutiveKillCandidates = List.of();

        //篮球杀掉前后号码
        var consecutiveBeforeAfterBlueBalls =
            Stream.of(first.getSpecial() + 1, first.getSpecial() - 1).filter(ball -> ball > 0 && ball < 17)
                .map(ball -> Pair.of(ball, 1.0)).map(KillBallSupport.boMapper("邪修杀")).toList();
        //计算遗漏35期的直接杀
        var mapMapPair = OmissionDueProbabilityUtils.analyzeRedBlueOmission(historyRecords.subList(0, 100));
        var moreThanOmission35Reds = mapMapPair.getLeft().entrySet().stream()
            .filter(entry -> entry.getValue().getStats().getCurrentOmission() >= 35)
            .map(entry -> Pair.of(entry.getKey(), 1.0)).map(KillBallSupport.boMapper("遗漏35期")).toList();
        var moreThanOmission35Blues = mapMapPair.getRight().entrySet().stream()
            .filter(entry -> entry.getValue().getStats().getCurrentOmission() >= 35)
            .map(entry -> Pair.of(entry.getKey(), 1.0)).map(KillBallSupport.boMapper("遗漏35期")).toList();

        //上一期的篮球，在红球中杀掉
        var blueKillReds = List.of(KillBallSupport.boMapper("上一期篮球红杀").apply(Pair.of(first.getSpecial(), 1.0)));

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

        // 暂时屏蔽：跨度直接杀与式2是同一公式，50 期误杀 24%
        List<KillNumberResultBo.KillItemBo> kdhBos = List.of();

        //和值杀
        int sum = lastWinRedBalls.stream().mapToInt(Integer::intValue).sum();
        var hzs = sum % 100;
        var hzsBos = Stream.of(hzs).filter(ball -> ball > 0 && ball < 34).map(ball -> Pair.of(ball, 1.0))
            .map(KillBallSupport.boMapper("和值杀")).toList();

        var kill18ShiRed = getKillBallBy18ShiRed(historyRecords);
        var killSixFormulasRed = getKillBallBySixFormulasRed(historyRecords);

        List<KillNumberResultBo.KillItemBo> redKillBallBos =
            Stream.of(moreThan3RedBallBos, lastWinRandomRedBalls, consecutiveBeforeAfterRedBalls, moreThanOmission35Reds,
                    blueKillReds, hzsBos, kdhBos, redConsecutiveKillCandidates, kill18ShiRed, killSixFormulasRed)
                .filter(CollectionUtils::isNotEmpty).flatMap(List::stream).toList();
        List<KillNumberResultBo.KillItemBo> blueKillBallBos =
            Stream.of(moreThan3BlueBallBos, moreThanOmission35Blues, consecutiveBeforeAfterBlueBalls)
                .flatMap(List::stream).toList();
        return Pair.of(redKillBallBos, blueKillBallBos);
    }

    /**
     * 二、双色球18式实战杀红公式（剔除式7/8/10/11；式2、式9因误杀偏高暂时屏蔽）。
     */
    private List<KillNumberResultBo.KillItemBo> getKillBallBy18ShiRed(List<HistoryRecord> historyRecords) {
        if (CollectionUtils.isEmpty(historyRecords)) {
            return List.of();
        }
        var first = historyRecords.getFirst();
        List<Integer> lastReds =
            Stream.of(first.getNum1(), first.getNum2(), first.getNum3(), first.getNum4(), first.getNum5(),
                first.getNum6()).sorted().toList();
        int sum = lastReds.stream().mapToInt(Integer::intValue).sum();
        List<KillNumberResultBo.KillItemBo> result = new ArrayList<>();

        result.add(KillBallSupport.kill("式1和值尾数+1", (sum % 10) + 1));
        int k3 = first.getSpecial() + 3;
        if (k3 > 33) {
            k3 -= 33;
        }
        result.add(KillBallSupport.kill("式3篮球+3", k3));
        result.add(KillBallSupport.kill("式4第1位+5", lastReds.getFirst() + 5));
        result.add(KillBallSupport.kill("式5第6位-5", lastReds.getLast() - 5));
        List<Integer> diffs =
            IntStream.range(0, lastReds.size() - 1).map(i -> lastReds.get(i + 1) - lastReds.get(i)).boxed().distinct()
                .toList();
        result.add(KillBallSupport.kill("式6相邻差值和尾数", diffs.stream().mapToInt(Integer::intValue).sum() % 10));
        int oddCount = (int)lastReds.stream().filter(b -> b % 2 != 0).count();
        result.add(KillBallSupport.kill("式12奇偶差绝对值", Math.abs(oddCount - (6 - oddCount))));
        result.add(KillBallSupport.kill("式13第1+第6尾数", (lastReds.getFirst() + lastReds.getLast()) % 10));
        List<HistoryRecord> past = historyRecords.subList(0, Math.min(100, historyRecords.size()));
        int maxOmissionRed = IntStream.rangeClosed(1, 33).boxed().max(Comparator.comparingInt(
            b -> OmissionUtils.omissionBallAnalyzer(past, "red", b).getStats().getCurrentOmission())).orElse(0);
        result.add(KillBallSupport.kill("式14遗漏最大红球-3", maxOmissionRed - 3));
        result.add(KillBallSupport.kill("式15大+小÷2", (lastReds.getLast() + lastReds.getFirst()) / 2));
        result.add(KillBallSupport.kill("式16第5-第2", lastReds.get(4) - lastReds.get(1)));
        result.add(KillBallSupport.kill("式17开奖日尾+10", (first.getOpenDate().getDayOfMonth() % 10) + 10));
        List<Integer> redHot = ColdHotAnalysisUtils.calculate(past).getRedHotBalls();
        if (!redHot.isEmpty()) {
            result.add(KillBallSupport.kill("式18热号区首尾和尾数", (redHot.get(0) + redHot.get(redHot.size() - 1)) % 10));
        }
        return result.stream().filter(bo -> bo.getBall() >= 1 && bo.getBall() <= 33).toList();
    }

    /**
     * 六套杀红公式之公式1-4。
     */
    private List<KillNumberResultBo.KillItemBo> getKillBallBySixFormulasRed(List<HistoryRecord> historyRecords) {
        if (CollectionUtils.isEmpty(historyRecords)) {
            return List.of();
        }
        var first = historyRecords.getFirst();
        List<Integer> lastReds =
            Stream.of(first.getNum1(), first.getNum2(), first.getNum3(), first.getNum4(), first.getNum5(),
                first.getNum6()).sorted().toList();
        List<KillNumberResultBo.KillItemBo> result = new ArrayList<>();

        result.add(KillBallSupport.kill("公式1首尾跨度", lastReds.getLast() - lastReds.getFirst()));
        int k2 = lastReds.getFirst() + lastReds.getLast();
        if (k2 > 33) {
            k2 -= 33;
        }
        result.add(KillBallSupport.kill("公式2首尾求和取余", k2));
        int k3 = lastReds.get(1) + lastReds.get(4);
        while (k3 > 33) {
            k3 -= 33;
        }
        result.add(KillBallSupport.kill("公式3第2+第5求和", k3));
        List<Integer> odds = lastReds.stream().filter(b -> b % 2 != 0).sorted().toList();
        if (odds.size() >= 2) {
            int avg = Math.round((odds.getFirst() + odds.getLast()) / 2.0f);
            if (avg > 33) {
                avg -= 33;
            }
            result.add(KillBallSupport.kill("公式4奇偶均值四舍五入", avg));
        }
        return result.stream().filter(bo -> bo.getBall() >= 1 && bo.getBall() <= 33).toList();
    }

    private void printXxDebug(Triple<List<Integer>, String, Integer> nextWinBalls, List<Integer> moreThan3RedBalls,
        List<KillNumberResultBo.KillItemBo> moreThan3BlueBallBos, List<Integer> lastWinRandomRedNumbers,
        List<Integer> consecutiveBeforeAfterRedNumbers, List<Integer> moreThanOmission35RedNumbers) {
        // 调试输出保持关闭，参数保留以免后续打开时丢上下文
    }

    /**
     * 最近10期出现3次直接杀掉 遗漏50期以上直接杀掉 同尾杀球
     */
    private List<KillNumberResultBo.KillItemBo> killBlue(List<HistoryRecord> historyRecords) {
        var first = historyRecords.getFirst();

        Map<Integer, Integer> blueCountMap = new HashMap<>();
        historyRecords.subList(0, 10).forEach(h -> blueCountMap.merge(h.getSpecial(), 1, Integer::sum));

        List<Integer> moreThan3 =
            blueCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 3).map(Map.Entry::getKey).toList();
        List<Integer> omission50Balls = IntStream.rangeClosed(1, 16).boxed()
            .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(historyRecords, "blue", ball)))
            .filter(p -> p.getRight().getStats().getCurrentOmission() >= 50).map(Pair::getLeft).toList();
        List<Integer> sameTailBalls = IntStream.rangeClosed(1, 16).boxed().map(ball -> Pair.of(ball, ball % 10))
            .filter(p -> p.getRight() == first.getSpecial() % 10).map(Pair::getLeft).toList();

        List<Integer> allKillBalls =
            Stream.of(moreThan3, omission50Balls, sameTailBalls).filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream).toList();
        return allKillBalls.stream().map(ball -> Pair.of(ball, 1.0)).map(KillBallSupport.boMapper("邪修杀")).toList();
    }

    private static List<Integer[]> getIfConsecutive(List<Integer> lastWinRedBalls) {
        List<Integer[]> result = new ArrayList<>();
        int n = lastWinRedBalls.size();
        int i = 0;
        while (i < n - 1) {
            if (lastWinRedBalls.get(i + 1) == lastWinRedBalls.get(i) + 1) {
                int start = i;
                while (i < n - 1 && lastWinRedBalls.get(i + 1) == lastWinRedBalls.get(i) + 1) {
                    i++;
                }
                int end = i;
                int segStart = lastWinRedBalls.get(start);
                int segEnd = lastWinRedBalls.get(end);
                int before = segStart - 1;
                int after = segEnd + 1;
                result.add(new Integer[] {before, segStart, segEnd, after});
            } else {
                i++;
            }
        }
        return result;
    }
}
