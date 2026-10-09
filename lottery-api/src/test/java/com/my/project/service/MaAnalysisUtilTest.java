package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.support.OmissionUtils;
import com.my.project.service.support.analyse.ColdHotAnalysisUtils;
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
     * 和值杀号发：上一期的和值的尾数->尾数对应号码的尾数号杀掉（如和值107，那么下期 07 17 27）√ 跨度杀号法：最大号-最小号然后取尾数 ->下期对应的尾数杀掉（如32-7=25 那么下期05 15 25）√
     * 上期开出的号码 + 3 5 7 如果范围在33内就直接杀了  X 跨度对应的号码直接杀了 √ 上期篮球+3 -3的球杀了 √ 连号前后杀了  √ 和值%100 直接杀 √
     *
     * 二、双色球18式实战杀红公式
     * 第1-6式：基础简易公式  1.和值尾数+1  2.最大-最小  3.蓝球+3(超33减33)  4.第1位+5  5.第6位-5  6.相邻差值和尾数
     * 第7-12式：进阶精准公式 7.(第2+第4)÷2  8.和值÷6  9.重复红球+2  10.33-第3位  11.蓝球×2(超33减33)  12.奇偶差绝对值
     * 第13-18式：高准确率公式 13.(第1+第6)尾数  14.遗漏最大红球-3  15.(最大+最小)÷2  16.第5-第2  17.开奖日尾数+10  18.热号区首尾和尾数
     */
    @Test
    public void test_20290923() {
        // 18式实战杀红公式误杀次数统计（下标 1-18 对应式号）
        AtomicInteger[] hits = IntStream.range(0, 19).mapToObj(i -> new AtomicInteger(0)).toArray(AtomicInteger[]::new);
        AtomicInteger total = new AtomicInteger(0);
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        windows.forEach(w -> {
            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            HistoryRecord lastLast = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) - 1)).one();
            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).last(" limit 100").list();

            List<Integer> currenWins =
                Stream.of(w.getNum1(), w.getNum2(), w.getNum3(), w.getNum4(), w.getNum5(), w.getNum6()).sorted()
                    .toList();
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            List<Integer> lastLastWins = Objects.isNull(lastLast)
                ? List.of()
                : Stream.of(lastLast.getNum1(), lastLast.getNum2(), lastLast.getNum3(), lastLast.getNum4(),
                    lastLast.getNum5(), lastLast.getNum6()).sorted().toList();

            System.out.println("第" + next.getPeriod() + "  开奖：" + nextWins + "  上期：" + currenWins);
            total.getAndAdd(1);

            // 收集本组 18 式全部杀号（去重、仅 1-33 范围）
            List<Integer> allKills = new ArrayList<>();
            int sum = currenWins.stream().mapToInt(Integer::intValue).sum();

            // 式1：上期红球和值尾数+1
            trackKill("式1 和值尾数+1", (sum % 10) + 1, nextWins, hits[1], allKills);
            // 式2：上期红球最大号减最小号
//            trackKill("式2 跨度(大-小)", currenWins.getLast() - currenWins.getFirst(), nextWins, hits[2], allKills);
            // 式3：上期蓝球+3，超出33则减33
            int k3 = w.getSpecial() + 3;
            if (k3 > 33) { k3 -= 33; }
            trackKill("式3 篮球+3", k3, nextWins, hits[3], allKills);
            // 式4：上期第1位红球+5
            trackKill("式4 第1位+5", currenWins.getFirst() + 5, nextWins, hits[4], allKills);
            // 式5：上期第6位红球-5
            trackKill("式5 第6位-5", currenWins.getLast() - 5, nextWins, hits[5], allKills);
            // 式6：上期相邻两个红球差值相加取尾数
            List<Integer> diffs =
                IntStream.range(0, currenWins.size() - 1).map(i -> currenWins.get(i + 1) - currenWins.get(i)).boxed()
                    .distinct().toList();
            trackKill("式6 相邻差值和尾数", diffs.stream().mapToInt(Integer::intValue).sum() % 10, nextWins, hits[6],
                allKills);
//            // 式7：（上期红球第2位+第4位）÷2 取整
//            trackKill("式7 (第2+第4)÷2", (currenWins.get(1) + currenWins.get(3)) / 2, nextWins, hits[7], allKills);
//            // 式8：上期红球和值÷6 取整
//            trackKill("式8 和值÷6", sum / 6, nextWins, hits[8], allKills);
            // 式9：上期重复红球号码+2
            List<Integer> k9 = CollectionUtils.intersection(currenWins, lastLastWins).stream()
                .map(b -> b + 2).filter(b -> b <= 33).toList();
            trackKillList("式9 重复红球+2", k9, nextWins, hits[9], allKills);
//            // 式10：33 减上期第3位红球
//            trackKill("式10 33-第3位", 33 - currenWins.get(2), nextWins, hits[10], allKills);
            // 式11：上期蓝球×2，超出33减33
            int k11 = w.getSpecial() * 2;
            if (k11 > 33) { k11 -= 33; }
//            trackKill("式11 篮球×2", k11, nextWins, hits[11], allKills);
            // 式12：上期红球奇偶号码差值取绝对值
            int oddCount = (int)currenWins.stream().filter(b -> b % 2 != 0).count();
            trackKill("式12 奇偶差绝对值", Math.abs(oddCount - (6 - oddCount)), nextWins, hits[12], allKills);
            // 式13：上期红球第1位+第6位 和值尾数
            trackKill("式13 (第1+第6)尾数", (currenWins.getFirst() + currenWins.getLast()) % 10, nextWins, hits[13],
                allKills);
            // 式14：上期遗漏值最大的红球号码-3
            int maxOmissionRed = IntStream.rangeClosed(1, 33).boxed().max(Comparator.comparingInt(
                b -> OmissionUtils.omissionBallAnalyzer(past, "red", b).getStats().getCurrentOmission())).orElse(0);
            trackKill("式14 遗漏最大红球-3", maxOmissionRed - 3, nextWins, hits[14], allKills);
            // 式15：（上期最大红球+上期最小红球）÷2 取整
            trackKill("式15 (大+小)÷2", (currenWins.getLast() + currenWins.getFirst()) / 2, nextWins, hits[15],
                allKills);
            // 式16：上期第5位红球-第2位红球
            trackKill("式16 第5-第2", currenWins.get(4) - currenWins.get(1), nextWins, hits[16], allKills);
            // 式17：上期开奖日日期尾数+10
            int k17 = (w.getOpenDate().getDayOfMonth() % 10) + 10;
            trackKill("式17 开奖日尾+10", k17, nextWins, hits[17], allKills);
            // 式18：上期热号区首尾号码相加取尾数
            List<Integer> redHot = ColdHotAnalysisUtils.calculate(past).getRedHotBalls();
            if (redHot.isEmpty()) {
                System.out.println("式18 热号区首尾和尾数：无热号 跳过");
            } else {
                trackKill("式18 热号区首尾和尾数", (redHot.get(0) + redHot.get(redHot.size() - 1)) % 10, nextWins,
                    hits[18], allKills);
            }

            // 本期总杀球数（去重）与总误杀数
            List<Integer> distinctKills = allKills.stream().distinct().sorted().toList();
            var wrongKills = CollectionUtils.intersection(distinctKills, nextWins);
            System.out.println("**************************总杀球：" + distinctKills.size() + " " + distinctKills
                + " 总误杀：" + wrongKills.size() + " " + wrongKills);
        });

        System.out.println("==== 18式误杀统计（共 " + total.get() + " 期）====");
        for (int i = 1; i <= 18; i++) {
            System.out.println("式" + i + " 误杀次数：" + hits[i].get() + " / " + total.get());
        }
    }

    /**
     * 六套杀红公式（综合汇总杀号池）：
     * 1.首尾跨度杀号法：凤尾-龙头（单号）
     * 2.红球首尾求和取余杀号法：龙头+凤尾，>33 减 33（单号）
     * 3.第二位+第五位求和杀号：第2位+第5位，>33 反复减 33（单号）
     * 4.奇偶均值四舍五入杀号：奇数中最小+最大 ÷2 四舍五入，>33 减 33（单号）
     * 5.三区分布过滤筛选杀号：回看近3期，从最热区按近10期频次降序取7个号
     * 6.尾数频次统计筛选杀号：统计近8期尾数频次，取最高2个尾数，每尾数按近10期频次降序取4个号
     */
    @Test
    public void test_20260923_six_formulas() {
        AtomicInteger[] hits = IntStream.range(0, 7).mapToObj(i -> new AtomicInteger(0)).toArray(AtomicInteger[]::new);
        AtomicInteger total = new AtomicInteger(0);
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();
        windows.forEach(w -> {
            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<HistoryRecord> past10 =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).last(" limit 10").list();
            List<HistoryRecord> past8 =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).last(" limit 8").list();

            List<Integer> currenWins =
                Stream.of(w.getNum1(), w.getNum2(), w.getNum3(), w.getNum4(), w.getNum5(), w.getNum6()).sorted()
                    .toList();
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            System.out.println("第" + next.getPeriod() + "  开奖：" + nextWins + "  上期：" + currenWins);
            total.getAndAdd(1);

            List<Integer> allKills = new ArrayList<>();

            // 公式1：首尾跨度杀号法（凤尾 - 龙头）
            trackKill("公式1 首尾跨度", currenWins.getLast() - currenWins.getFirst(), nextWins, hits[1], allKills);

            // 公式2：红球首尾求和取余杀号法（龙头 + 凤尾，>33 减 33）
            int k2 = currenWins.getFirst() + currenWins.getLast();
            if (k2 > 33) { k2 -= 33; }
            trackKill("公式2 首尾求和取余", k2, nextWins, hits[2], allKills);

            // 公式3：第二位 + 第五位求和杀号（>33 反复减 33）
            int k3 = currenWins.get(1) + currenWins.get(4);
            while (k3 > 33) { k3 -= 33; }
            trackKill("公式3 第2+第5求和", k3, nextWins, hits[3], allKills);

            // 公式4：奇偶均值四舍五入杀号（奇数中最小+最大 ÷2 四舍五入，>33 减 33）
            List<Integer> odds = currenWins.stream().filter(b -> b % 2 != 0).sorted().toList();
            if (odds.size() >= 2) {
                int avg = Math.round((odds.getFirst() + odds.getLast()) / 2.0f);
                if (avg > 33) { avg -= 33; }
                trackKill("公式4 奇偶均值四舍五入", avg, nextWins, hits[4], allKills);
            } else {
                System.out.println("公式4 奇偶均值四舍五入：奇数不足2个 跳过");
            }

            // 公式5：三区分布过滤筛选杀号（近3期最热区，按近10期频次降序取7个号）
            List<Integer> k5 = pickHotZoneKills(past10, 3, 7);
//            trackKillList("公式5 三区分布筛选", k5, nextWins, hits[5], allKills);

            // 公式6：尾数频次统计筛选杀号（近8期最高频2个尾数，每尾数按近10期频次降序取4个号）
            List<Integer> k6 = pickHotTailKills(past8, past10, 2, 4);
//            trackKillList("公式6 尾数频次筛选", k6, nextWins, hits[6], allKills);

            List<Integer> distinctKills = allKills.stream().distinct().sorted().toList();
            var wrongKills = CollectionUtils.intersection(distinctKills, nextWins);
            System.out.println("**************************总杀球：" + distinctKills.size() + " " + distinctKills
                + " 总误杀：" + wrongKills.size() + " " + wrongKills);
        });

        System.out.println("==== 六套公式误杀统计（共 " + total.get() + " 期）====");
        for (int i = 1; i <= 6; i++) {
            System.out.println("公式" + i + " 误杀次数：" + hits[i].get() + " / " + total.get());
        }
    }

    /** 单号杀：打印并统计误杀（仅 1-33 范围内才计入误杀与总杀球） */
    private void trackKill(String formula, int kill, List<Integer> nextWins, AtomicInteger counter,
        List<Integer> allKills) {
        boolean hit = kill >= 1 && kill <= 33 && nextWins.contains(kill);
        if (kill >= 1 && kill <= 33) {
            allKills.add(kill);
        }
        if (hit) {
            counter.addAndGet(1);
        }
        System.out.println(formula + "：杀 " + kill + " 误杀：" + hit);
    }

    /** 多号杀：打印并统计误杀 */
    private void trackKillList(String formula, List<Integer> kills, List<Integer> nextWins, AtomicInteger counter,
        List<Integer> allKills) {
        allKills.addAll(kills);
        var hit = CollectionUtils.intersection(kills, nextWins);
        if (!hit.isEmpty()) {
            counter.addAndGet(1);
        }
        System.out.println(formula + "：杀 " + kills + " 误杀：" + hit);
    }

    /**
     * 公式5：回看近 lookBack 期，统计一区(1-11)/二区(12-22)/三区(23-33) 开出号码总数，
     * 取最热区，从该区 11 个号码中按近10期出现频次降序、同频次按号码升序，取前 pickCount 个号。
     */
    private List<Integer> pickHotZoneKills(List<HistoryRecord> past, int lookBack, int pickCount) {
        int actualLook = Math.min(lookBack, past.size());
        Map<Integer, Integer> zoneCount = new HashMap<>(Map.of(1, 0, 2, 0, 3, 0));
        Map<Integer, Integer> freq10 = new HashMap<>();
        for (int i = 0; i < past.size(); i++) {
            List<Integer> reds = Stream.of(past.get(i).getNum1(), past.get(i).getNum2(), past.get(i).getNum3(),
                past.get(i).getNum4(), past.get(i).getNum5(), past.get(i).getNum6()).toList();
            for (int r : reds) {
                freq10.merge(r, 1, Integer::sum);
                if (i < actualLook) {
                    int zone = r <= 11 ? 1 : (r <= 22 ? 2 : 3);
                    zoneCount.merge(zone, 1, Integer::sum);
                }
            }
        }
        int hotZone = zoneCount.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(1);
        int zoneStart = hotZone == 1 ? 1 : (hotZone == 2 ? 12 : 23);
        int zoneEnd = hotZone == 1 ? 11 : (hotZone == 2 ? 22 : 33);
        System.out.println("  公式5 最热区=" + hotZone + "（" + zoneStart + "-" + zoneEnd + "）近" + actualLook
            + "期开出 " + zoneCount.get(hotZone) + " 个");
        return IntStream.rangeClosed(zoneStart, zoneEnd).boxed()
            .sorted(Comparator.comparingInt((Integer b) -> freq10.getOrDefault(b, 0)).reversed()
                .thenComparing(Comparator.naturalOrder()))
            .limit(pickCount).toList();
    }

    /**
     * 公式6：统计近 tailLookBack 期 0-9 尾数频次，取最高的 topTailCount 个尾数；
     * 每个尾数下的号码按近10期出现频次降序、同频次按号码升序，取前 pickPerTail 个号。
     */
    private List<Integer> pickHotTailKills(List<HistoryRecord> pastTail, List<HistoryRecord> past10,
        int topTailCount, int pickPerTail) {
        Map<Integer, Integer> tailFreq = new HashMap<>();
        for (HistoryRecord h : pastTail) {
            Stream.of(h.getNum1(), h.getNum2(), h.getNum3(), h.getNum4(), h.getNum5(), h.getNum6())
                .forEach(b -> tailFreq.merge(b % 10, 1, Integer::sum));
        }
        Map<Integer, Integer> freq10 = new HashMap<>();
        for (HistoryRecord h : past10) {
            Stream.of(h.getNum1(), h.getNum2(), h.getNum3(), h.getNum4(), h.getNum5(), h.getNum6())
                .forEach(b -> freq10.merge(b, 1, Integer::sum));
        }
        List<Integer> topTails = tailFreq.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(topTailCount).map(Map.Entry::getKey).toList();
        System.out.println("  公式6 高频尾数=" + topTails + " 近" + pastTail.size() + "期尾数频次=" + tailFreq);
        List<Integer> kills = new ArrayList<>();
        for (int tail : topTails) {
            List<Integer> tailBalls = IntStream.rangeClosed(1, 33).boxed().filter(b -> b % 10 == tail)
                .sorted(Comparator.comparingInt((Integer b) -> freq10.getOrDefault(b, 0)).reversed()
                    .thenComparing(Comparator.naturalOrder()))
                .limit(pickPerTail).toList();
            kills.addAll(tailBalls);
        }
        return kills;
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
