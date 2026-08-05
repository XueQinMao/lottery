package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.support.OmissionUtils;
import com.my.project.service.support.analyse.KillBallUtils;
import com.my.project.service.support.analyse.MaAnalysisUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 用全量历史开奖跑信息指数工具，只打印结果，不接入推荐/调优主流程。
 */
@SpringBootTest
class MaAnalysisUtilTest {

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Test
    void printInformationIndexFromHistory() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 10").list();

        windows.forEach(w -> {
            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate()).last(" limit 100")
                    .orderByDesc(HistoryRecord::getOpenDate).list();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            MaAnalysisUtils.MaAnalysisResult analyze = MaAnalysisUtils.analyze(past);
            System.out.println(JSON.toJSONString(analyze));
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            List<Integer> redKills = analyze.getRedBalls().stream().filter(b -> Objects.equals("杀号", b.getAction()))
                .map(MaAnalysisUtils.MaResult::getBall).toList();

            List<Integer> blueKills = analyze.getBlueBalls().stream().filter(b -> Objects.equals("杀号", b.getAction()))
                .map(MaAnalysisUtils.MaResult::getBall).toList();
            List<Integer> hitPicks = (List<Integer>)CollectionUtils.intersection(redKills, nextWins);
            System.out.println(
                "根据第" + w.getPeriod() + "期数据计算杀球，红球/误杀" + redKills.size() + "/" + hitPicks.size() + " 篮球/误杀：" + blueKills.size() + "/" + blueKills.contains(
                    next.getSpecial()));
            System.out.println("**************************************");
        });
    }

    @Test
    public void test_kill() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 51").list();
        windows.forEach(w -> {
            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).list();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            KillBallUtils.calculate(past, Triple.of(nextWins, next.getPeriod(), next.getSpecial()));
        });
    }

    @Test
    public void test_red_kill_blue() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        AtomicInteger count = new AtomicInteger(0);
        windows.forEach(w -> {

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(w.getNum1(), w.getNum2(), w.getNum3(), w.getNum4(), w.getNum5(), w.getNum6());

            System.out.println(
                "上期开出的红球：" + StringUtils.join(nextWins, ",") + " 是否命中篮球：" + nextWins.contains(
                    next.getSpecial()));
            if (nextWins.contains(next.getSpecial())) {
                count.addAndGet(1);
            }
        });
        System.out.println("共命中：" + count.get() + "/" + 30);
    }

    /// ***************************
    @Test
    public void test_omsiss() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        AtomicInteger count = new AtomicInteger(0);
        windows.forEach(w -> {

            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).last(" limit 50").list();
            var reds = IntStream.rangeClosed(1, 33).boxed()
                .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(past, "red", ball)))
                .filter(p -> p.getRight().getStats().getCurrentOmission() > 11).map(Pair::getLeft).toList();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());

            System.out.println("第" + next.getPeriod() + "期使用遗漏15期杀球" + StringUtils.join(reds,
                ",") + " 误杀：" + CollectionUtils.intersection(reds, nextWins));
        });
    }

    /**
     * 同尾数
     */
    @Test
    public void test_tw() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        windows.forEach(w -> {
            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }

            //计算同位数
            List<Integer> wwinReds =
                List.of(w.getNum1(), w.getNum2(), w.getNum3(), w.getNum4(), w.getNum5(), w.getNum6());
            var groups = wwinReds.stream().collect(Collectors.groupingBy(n -> n % 10));
            List<Integer> twUnmber =
                groups.entrySet().stream().filter(entry -> entry.getValue().size() > 1).map(Map.Entry::getKey).toList();
            var killReds = IntStream.rangeClosed(1, 33).boxed().map(ball -> Pair.of(ball, ball % 10))
                .filter(p -> twUnmber.contains(p.getRight())).map(Pair::getLeft).toList();
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());

            System.out.println("第" + next.getPeriod() + "期使同尾杀球" + StringUtils.join(killReds,
                ",") + " 误杀：" + CollectionUtils.intersection(killReds, nextWins));
        });
    }

    /**
     * 最近10期出现3次直接杀掉 遗漏50期以上直接杀掉 篮球1-8为小 9-16为大，比如上次开的大基，那么这次就杀掉大基选择小基或者小偶 同尾杀球，比如上次尾数5那么下期杀掉5 15
     * 篮球分区间1-4,5-8,9-12,13-16如果篮球出现在1期间，直接把整个区间都杀掉
     */
    @Test
    public void test_blue() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        windows.forEach(w -> {
            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).last(" limit 50").list();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }

            Map<Integer, Integer> blueCountMap = new HashMap<>();
            past.subList(0, 10).forEach(h -> {
                blueCountMap.merge(h.getSpecial(), 1, Integer::sum);
            });
            //10期出现3次
            List<Integer> moreThan3 =
                blueCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 3).map(Map.Entry::getKey).toList();
            System.out.println(
                "10期出现3次杀：" + StringUtils.join(moreThan3, ",") + " 误杀：" + moreThan3.contains(next.getSpecial()));
            //遗漏50期
            List<Integer> list = IntStream.rangeClosed(1, 16).boxed()
                .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(past, "blue", ball)))
                .filter(p -> p.getRight().getStats().getCurrentOmission() >= 50).map(Pair::getLeft).toList();
            System.out.println(
                "遗漏50期杀：" + StringUtils.join(list, ",") + " 误杀：" + list.contains(next.getSpecial()));
            //区域划分
            // 预先划分区域
            List<List<Integer>> zones = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                List<Integer> zone = new ArrayList<>();
                for (int j = 1; j <= 4; j++) {
                    zone.add(i * 4 + j);
                }
                zones.add(zone);
            }
            //            List<Integer> list1 =
            //                zones.stream().filter(array -> array.contains(w.getSpecial())).flatMap(List::stream).toList();
            //            System.out.println("区域所在全杀："+StringUtils.join(list1,",")+" 误杀："+list1.contains(next.getSpecial()));
            //同尾数杀
            List<Integer> list2 = IntStream.rangeClosed(1, 16).boxed().map(ball -> Pair.of(ball, ball % 10))
                .filter(p -> p.getRight() == w.getSpecial() % 10).map(Pair::getLeft).toList();
            System.out.println(
                "同尾数杀：" + StringUtils.join(list2, ",") + " 误杀：" + list2.contains(next.getSpecial()));
            //相反杀
            var big = w.getSpecial() > 8;
            var isJs = w.getSpecial() % 2 != 0;
            List<Integer> result = new ArrayList<>();
            for (int i = 1; i <= 16; i++) {
                boolean matchBig = big ? (i > 8) : (i <= 8);
                boolean matchJs = isJs ? (i % 2 != 0) : (i % 2 == 0);

                if (matchBig && matchJs) {
                    result.add(i);
                }
            }
            //            System.out.println("相反杀："+StringUtils.join(result,",")+" 误杀："+result.contains(next.getSpecial()));

            List<Integer> list3 =
                Stream.of(moreThan3, list, list2).filter(CollectionUtils::isNotEmpty).flatMap(List::stream).toList();

            System.out.println(
                "第" + next.getPeriod() + "杀球" + StringUtils.join(list3, ",") + " 误杀：" + list3.contains(
                    next.getSpecial()));
        });
    }

    /**
     * 1、跨度对应的号直接杀，跨度对应的尾数直接杀。 2、和值的尾数->对应的尾数杀。 3、查看开出号码的尾数。
     */
    @Test
    public void test_kill_red() {

        AtomicInteger a = new AtomicInteger(0);
        AtomicInteger b = new AtomicInteger(0);
        AtomicInteger c = new AtomicInteger(0);
        AtomicInteger d = new AtomicInteger(0);
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        windows.forEach(w -> {
            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            System.out.println("第" + next.getPeriod() + "  开奖：" + nextWins);
            List<Integer> currenWins =
                Stream.of(w.getNum1(), w.getNum2(), w.getNum3(), w.getNum4(), w.getNum5(), w.getNum6()).sorted()
                    .toList();
            System.out.println("推测号码：" + currenWins);

            var hzkd = currenWins.getLast() - currenWins.getFirst();

            System.out.println("跨度直接杀：" + hzkd + "  误杀：" + nextWins.contains(hzkd));
            if (nextWins.contains(hzkd)) {
                a.getAndAdd(1);
            }

            var hzkdw = hzkd % 10;
            List<Integer> hzkdws = IntStream.rangeClosed(1, 33).boxed().filter(ball -> (ball % 10) == hzkdw).toList();
            System.out.println(
                "跨度位数杀：" + hzkdws + "  误杀：" + CollectionUtils.intersection(hzkdws, nextWins).size());
            if (CollectionUtils.isNotEmpty(CollectionUtils.intersection(hzkdws, nextWins))) {
                b.getAndAdd(1);
            }

            int sum = currenWins.stream().mapToInt(Integer::intValue).sum();
            var hzw = sum % 10;
            List<Integer> hzws = IntStream.rangeClosed(1, 33).boxed().filter(ball -> (ball % 10) == hzw).toList();
            System.out.println("和值尾数杀：" + hzws + "  误杀：" + CollectionUtils.intersection(hzws, nextWins).size());
            if (CollectionUtils.isNotEmpty(CollectionUtils.intersection(hzws, nextWins))) {
                c.getAndAdd(1);
            }
            System.out.println("**************************");
        });
        System.out.println("跨度误杀：" + a.get() + " 跨度尾数：" + b.get() + " 和值尾数：" + c.get());
    }

    /**
     * 和值杀号发：上一期的和值的尾数->尾数对应号码的尾数号杀掉（如和值107，那么下期 07 17 27）√
     * 跨度杀号法：最大号-最小号然后取尾数 ->下期对应的尾数杀掉（如32-7=25 那么下期05 15 25）√
     * 上期开出的号码 + 3 5 7 如果范围在33内就直接杀了  X
     * 跨度对应的号码直接杀了 √
     * 上期篮球+3 -3的球杀了 √
     * 连号前后杀了  √
     * 和值%100 直接杀 √
     *
     */
    @Test
    public void test_20290922() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        windows.forEach(w -> {
            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            List<Integer> currenWins =
                Stream.of(w.getNum1(), w.getNum2(), w.getNum3(), w.getNum4(), w.getNum5(), w.getNum6()).sorted()
                    .toList();
            //和值尾数
            int sum = currenWins.stream().mapToInt(Integer::intValue).sum();
            //            var hzws = sum % 10;
            //            List<Integer> hzwsKillBalls = IntStream.rangeClosed(1, 33).boxed().filter(ball -> (ball % 10) == hzws).toList();
            //            System.out.println("和值尾数杀："+hzwsKillBalls.size()+" 误杀："+CollectionUtils.intersection(hzwsKillBalls, nextWins));
            //
            //            //跨度尾数杀
            //            var kdws = (currenWins.getLast()-currenWins.getFirst()) % 10;
            //            List<Integer> kdwsKillBalls = IntStream.rangeClosed(1, 33).boxed().filter(ball -> (ball % 10) == kdws).toList();
            //            System.out.println("跨度尾数杀："+kdwsKillBalls.size()+" 误杀："+CollectionUtils.intersection(kdwsKillBalls, nextWins));

            var kdh = currenWins.getLast() - currenWins.getFirst();
            System.out.println("跨度号杀：1 误杀：" + nextWins.contains(kdh));

            //和值杀
            var hzs = sum % 100;
            List<Integer> list = Stream.of(hzs).filter(ball -> ball > 0 && ball < 34).toList();
            if (CollectionUtils.isNotEmpty(list)) {
                System.out.println("和值杀：" + list + " 误杀：" + nextWins.contains(hzs));
            }

            //            List<Integer> list1 =
            //                Stream.of(next.getSpecial() - 3, next.getSpecial() + 3).filter(ball -> ball >= 0).toList();
            //            System.out.println("篮球+-3杀："+list1.size()+" 误杀："+CollectionUtils.intersection(list1, nextWins));
            var consecutiveBeforeAfterRedBalls =
                getIfConsecutive(currenWins).stream().filter(array -> array.length == 4)
                    .map(array -> List.of(array[0], array[array.length - 1])).flatMap(List::stream).toList();
            System.out.println(
                "2连号前后杀：" + consecutiveBeforeAfterRedBalls.size() + "|" + consecutiveBeforeAfterRedBalls + " 误杀：" + CollectionUtils.intersection(
                    consecutiveBeforeAfterRedBalls, nextWins));

            var allList =
                Stream.of(List.of(kdh), list, consecutiveBeforeAfterRedBalls).flatMap(List::stream).distinct().toList();
            System.out.println(
                next.getPeriod() + "***************************** " + allList.size() + "  误杀：" + CollectionUtils.intersection(
                    allList, nextWins));
        });
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
}
