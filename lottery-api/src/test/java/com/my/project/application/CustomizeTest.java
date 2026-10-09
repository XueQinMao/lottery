package com.my.project.application;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import com.my.project.application.support.OmissionDueProbabilityUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SpringBootTest
public class CustomizeTest {

    @Resource
    private HistoryRecordRepository historyRecordRepository;

    /** 统一的开奖日格式 */
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Test
    @DisplayName("统计各杀号规则错杀概率")
    public void statWrongKillRate() {
        int sampleSize = 31;
        // 注意：改成升序，保证 i+1 是时间上更晚的一期
        List<HistoryRecord> all = historyRecordRepository.lambdaQuery()
            .orderByDesc(HistoryRecord::getOpenDate)
            .last(" limit " + sampleSize)
            .list();
        // 规则名 -> 函数
        Map<String, Function<String, List<Integer>>> rules = new LinkedHashMap<>();
        rules.put("上期蓝球杀红", this::lastWinBlue);
        rules.put("6期出现3次", this::moreThan3);
        rules.put("和值尾数", this::sumTotalMantissa);
        rules.put("和值尾数+1", this::sumTotalMantissaAddOne);
        rules.put("上期蓝球+3杀红", this::lastWinBlueAdd3);
        rules.put("上期红球第一位+5", this::lastWinRedOneAdd5);
        rules.put("上期红球最后一位-5", this::lastWinRedLastDel5);
        rules.put("相邻差值尾数", this::diffRemainder);
        rules.put("奇偶个数差值绝对值", this::oddEvenDiff);
        rules.put("第1位+第6位和值尾数", this::oneSixAdd);
        rules.put("遗漏最大球-3", this::omissionMaxSub3);
        rules.put("最大+最小除2", this::maxAddMinDiv2);
        rules.put("第5位-第2位", this::fiveSubTow);
        // 用当期 openDate 推导下一期开奖日，避免未来函数
        rules.put("开奖日取余+10", this::currentOpenDateDiv10Add10);
        rules.put("上期最大-最小", this::maxMinSub);
        rules.put("第5位+第2位", this::fiveAddTow);
        rules.put("第1位+第6位", this::oneAddSix);
        rules.put("奇偶数首位相加除2", this::oddEvenDiv2);
        rules.put("连续3期出现", this::continuous3);
        rules.put("遗漏35期", this::moreThanOmission35);
        rules.put("3连号中间", this::threeConsecutiveNumbersMiddle);

        // long[]{总期数, 空次数, 错杀次数, 正确次数, 累计杀号数}
        Map<String, long[]> statMap = new LinkedHashMap<>();
        rules.keySet().forEach(k -> statMap.put(k, new long[5]));
        long[] comboStat = new long[5];

        for (int i = 0; i < all.size() - 1; i++) {
            HistoryRecord current = all.get(i);

            List<Integer> nextWins = List.of(
                current.getNum1(), current.getNum2(), current.getNum3(),
                current.getNum4(), current.getNum5(), current.getNum6());

            List<Integer> comboKilled = new ArrayList<>();

            for (Map.Entry<String, Function<String, List<Integer>>> e : rules.entrySet()) {
                List<Integer> killed;
                try {
                    killed = e.getValue().apply(current.getPeriod());
                } catch (Exception ex) {
                    killed = List.of();
                }
                if (killed == null) {
                    killed = List.of();
                }
                // 只统计 1~33 的合法红球
                killed = killed.stream().filter(n -> n != null && n >= 1 && n <= 33).distinct().toList();

                int hit = CollectionUtils.intersection(killed, nextWins).size();
                record(statMap.get(e.getKey()), killed.isEmpty(), killed.size(), hit);

                comboKilled.addAll(killed);
            }

            List<Integer> comboDistinct = comboKilled.stream()
                .filter(n -> n != null && n >= 1 && n <= 33).distinct().toList();
            int comboHit = CollectionUtils.intersection(comboDistinct, nextWins).size();
            record(comboStat, comboDistinct.isEmpty(), comboDistinct.size(), comboHit);
        }

        System.out.println("========== 单规则统计 ==========");
        statMap.forEach((name, s) -> System.out.println(format(name, s)));

        System.out.println("\n========== 组合统计 ==========");
        System.out.println(format("组合总杀球", comboStat));

        System.out.println("\n========== 按错杀率降序（越靠前越危险） ==========");
        statMap.entrySet().stream()
            .sorted((a, b) -> Double.compare(rate(b.getValue()[2], b.getValue()[0]),
                rate(a.getValue()[2], a.getValue()[0])))
            .forEach(e -> {
                long[] s = e.getValue();
                System.out.printf("%-22s 错杀率=%.2f%% 空率=%.2f%% 均杀=%.2f %s%n",
                    e.getKey(), rate(s[2], s[0]) * 100,
                    rate(s[1], s[0]) * 100, avg(s[4], s[0]), risk(s));
            });

        System.out.println("\n========== 高风险规则（错杀率>=30%） ==========");
        statMap.entrySet().stream()
            .filter(e -> rate(e.getValue()[2], e.getValue()[0]) >= 0.30)
            .forEach(e -> System.out.println(format(e.getKey(), e.getValue())));
    }

    /** 记录一次统计 */
    private static void record(long[] s, boolean empty, int killedSize, int hitCount) {
        s[0]++;
        if (empty) {
            s[1]++;
        }
        if (hitCount > 0) {
            s[2]++;
        } else {
            s[3]++;
        }
        s[4] += killedSize;
    }

    private static double rate(long a, long b) {
        return b == 0 ? 0 : (double) a / b;
    }

    private static double avg(long a, long b) {
        return b == 0 ? 0 : (double) a / b;
    }

    private static String risk(long[] s) {
        double r = rate(s[2], s[0]);
        if (r >= 0.30) return "高风险";
        if (r >= 0.20) return "中风险";
        if (r >= 0.10) return "低风险";
        return "较安全";
    }

    private static String format(String name, long[] s) {
        return String.format(
            "%-22s 期数=%-4d 空=%-4d 空率=%.2f%% 错杀=%-4d 错杀率=%.2f%% 正确=%-4d 均杀=%.2f %s",
            name, s[0], s[1], rate(s[1], s[0]) * 100,
            s[2], rate(s[2], s[0]) * 100, s[3], avg(s[4], s[0]), risk(s));
    }

    @Test
    @DisplayName("测试下一期开出的号码是都包含在前20期中")
    public void test_20_zh() {
        // 这里也改成升序，保证下一期是时间上更晚的一期
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 30").list();
        for (int i = 0; i < windows.size() - 1; i++) {
            HistoryRecord w = windows.get(i);
            List<Integer> nextWins =
                List.of(w.getNum1(), w.getNum2(), w.getNum3(),
                    w.getNum4(), w.getNum5(), w.getNum6());

            List<Integer> list = continuous3(w.getPeriod());
            if (CollectionUtils.isNotEmpty(list)) {
                System.out.println(
                    "连续开出3次：" + list + " 错杀：" + CollectionUtils.intersection(list, nextWins).size());
            }
            List<Integer> integers = lastWinBlue(w.getPeriod());
            System.out.println(
                "上期篮球杀红：" + integers + " 错杀：" + CollectionUtils.intersection(integers, nextWins).size());

            List<Integer> integers1 = moreThan3(w.getPeriod());
            System.out.println(
                "6期出现3次：" + integers1 + " 错杀：" + CollectionUtils.intersection(integers1, nextWins).size());

            List<Integer> integers2 = threeConsecutiveNumbersMiddle(w.getPeriod());
            if (CollectionUtils.isNotEmpty(integers2)) {
                System.out.println(
                    "3连号中间：" + integers2 + " 错杀：" + CollectionUtils.intersection(integers2, nextWins).size());
            }

            List<Integer> integers3 = moreThanOmission35(w.getPeriod());
            if (CollectionUtils.isNotEmpty(integers3)) {
                System.out.println(
                    "遗漏35期：" + integers3 + " 错杀：" + CollectionUtils.intersection(integers3, nextWins).size());
            }

            List<Integer> integers4 = sumTotalMantissa(w.getPeriod());
            System.out.println(
                "和值尾数：" + integers4 + " 错杀：" + CollectionUtils.intersection(integers4, nextWins).size());

            List<Integer> integers5 = sumTotalMantissaAddOne(w.getPeriod());
            if (CollectionUtils.isNotEmpty(integers5)) {
                System.out.println(
                    "和值尾数+1：" + integers5 + " 错杀：" + CollectionUtils.intersection(integers5, nextWins).size());
            }
            List<Integer> integers6 = lastWinBlueAdd3(w.getPeriod());
            System.out.println(
                "上期篮球+3杀红：" + integers6 + " 错杀：" + CollectionUtils.intersection(integers6, nextWins).size());

            List<Integer> integers7 = lastWinRedOneAdd5(w.getPeriod());
            System.out.println(
                "上期红球第一位+5：" + integers7 + " 错杀：" + CollectionUtils.intersection(integers7, nextWins).size());

            List<Integer> integers8 = lastWinRedLastDel5(w.getPeriod());
            System.out.println(
                "上期红球最后一位-5：" + integers8 + " 错杀：" + CollectionUtils.intersection(integers8, nextWins)
                    .size());

            List<Integer> integers9 = diffRemainder(w.getPeriod());
            System.out.println(
                "相邻的差值的尾数：" + integers9 + " 错杀：" + CollectionUtils.intersection(integers9, nextWins).size());

            List<Integer> integers10 = oddEvenDiff(w.getPeriod());
            System.out.println(
                "奇偶球个数的差值绝对值：" + integers10 + " 错杀：" + CollectionUtils.intersection(integers10, nextWins)
                    .size());

            List<Integer> integers11 = oneSixAdd(w.getPeriod());
            System.out.println(
                "第一位+第六位的和值尾数：" + integers11 + " 错杀：" + CollectionUtils.intersection(integers11, nextWins)
                    .size());

            List<Integer> integers12 = omissionMaxSub3(w.getPeriod());
            System.out.println(
                "遗漏最大的球-3：" + integers12 + " 错杀：" + CollectionUtils.intersection(integers12, nextWins).size());

            List<Integer> integers13 = maxAddMinDiv2(w.getPeriod());
            System.out.println(
                "最大+最小除2：" + integers13 + " 错杀：" + CollectionUtils.intersection(integers13, nextWins).size());

            List<Integer> integers14 = fiveSubTow(w.getPeriod());
            System.out.println(
                "第5位-第2位：" + integers14 + " 错杀：" + CollectionUtils.intersection(integers14, nextWins).size());

            List<Integer> integers15 = currentOpenDateDiv10Add10(w.getPeriod());
            System.out.println(
                "开奖日取余数+10：" + integers15 + " 错杀：" + CollectionUtils.intersection(integers15, nextWins).size());

            List<Integer> integers16 = maxMinSub(w.getPeriod());
            System.out.println(
                "上一期最大-最小：" + integers16 + " 错杀：" + CollectionUtils.intersection(integers16, nextWins).size());

            List<Integer> integers17 = fiveAddTow(w.getPeriod());
            System.out.println(
                "第5位+第2位：" + integers17 + " 错杀：" + CollectionUtils.intersection(integers17, nextWins).size());

            List<Integer> integers18 = oneAddSix(w.getPeriod());
            System.out.println(
                "第1位+第6位：" + integers18 + " 错杀：" + CollectionUtils.intersection(integers18, nextWins).size());

            List<Integer> integers19 = oddEvenDiv2(w.getPeriod());
            System.out.println(
                "奇偶数首位相加除2：" + integers19 + " 错杀：" + CollectionUtils.intersection(integers19, nextWins)
                    .size());

            List<Integer> list1 =
                Stream.of(integers,integers1, integers2, integers3, integers4, integers5, integers6, integers7,
                        integers8,integers9, integers10, integers11, integers12, integers13, integers14, integers15,
                        integers16, integers17, integers18, integers19).filter(CollectionUtils::isNotEmpty)
                    .flatMap(List::stream).distinct().toList();

            System.out.println(w.getPeriod()+"*************************************总杀球：" + list1.size() + " 错杀：" + CollectionUtils.intersection(list1, nextWins));
        }
    }

    /**
     * 奇偶数首位相加除2
     *
     * @param period
     * @return
     */
    private List<Integer> oddEvenDiv2(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        List<Integer> lasWins =
            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
        var oddList = lasWins.stream().filter(b -> b % 2 != 0).toList();
        var evenList = lasWins.stream().filter(b -> b % 2 == 0).toList();
        List<Integer> results = new ArrayList<>();
        if (oddList.size() > 2) {
            results.add((oddList.getLast() + oddList.getFirst()) / 2);
        }
        if (evenList.size() > 2) {
            results.add((evenList.getLast() + evenList.getFirst()) / 3);
        }

        return results;
    }

    /**
     * 第1位+第6位
     *
     * @param period
     * @return
     */
    private List<Integer> oneAddSix(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        var sum = last.getNum6() + last.getNum1();
        return List.of(sum > 33 ? (sum - 33) : sum);
    }

    /**
     * 第5位+第2位
     *
     * @param period
     * @return
     */
    private List<Integer> fiveAddTow(String period) {
//        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
//            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
//        var sum = last.getNum5() + last.getNum2();
//        return List.of(sum > 33 ? (sum - 33) : sum);
        return List.of();
    }

    /**
     * 上一期最大-最小
     *
     * @param period
     * @return
     */
    private List<Integer> maxMinSub(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        return List.of(last.getNum6() - last.getNum1());
    }

    /**
     * 开奖日取余数+10
     * 回测版：基于传入 period 对应的开奖日推导下一期开奖日，避免未来函数
     *
     * @param period
     * @return
     */
    private List<Integer> currentOpenDateDiv10Add10(String period) {
        var current = historyRecordRepository.lambdaQuery().eq(HistoryRecord::getPeriod, period).one();
        if(Objects.isNull(current)){
            return List.of();
        }
        var yyyyMMdd = Integer.valueOf(current.getOpenDate().format(YYYYMMDD));
        return List.of((yyyyMMdd % 10) + 10);
    }


    /**
     * 第5位-第2位
     *
     * @param period
     * @return
     */
    private List<Integer> fiveSubTow(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        return List.of(last.getNum5() - last.getNum2());
    }

    /**
     * 最大+最小除2
     *
     * @param period
     * @return
     */
    private List<Integer> maxAddMinDiv2(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        List<Integer> lasWins =
            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
        return List.of((lasWins.getFirst() + lasWins.getLast()) / 2);
    }

    /**
     * 计算100期遗漏最大的球-3
     *
     * @param period
     * @return
     */
    private List<Integer> omissionMaxSub3(String period) {
        var last100 = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 100").list();
        var mapMapPair = OmissionDueProbabilityUtils.analyzeRedBlueOmission(last100);

        Optional<Integer> maxKey = mapMapPair.getLeft().entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().getStats().getCurrentOmission()))
            .map(Map.Entry::getKey);

        return maxKey.map(k -> List.of(k - 3)).orElseGet(List::of);
    }

    /**
     * 第一位+第六位的和值尾数
     *
     * @param period
     * @return
     */
    private List<Integer> oneSixAdd(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        List<Integer> lasWins =
            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
        return List.of((lasWins.getFirst() + lasWins.getLast()) % 10);
    }

    /**
     * 奇偶球个数的差值的绝对值
     *
     * @param period
     * @return
     */
    private List<Integer> oddEvenDiff(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        List<Integer> lasWins =
            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
        int oddCount = (int) lasWins.stream().filter(b -> b % 2 != 0).count();
        return List.of(Math.abs(oddCount - (6 - oddCount)));
    }

    /**
     * 相邻的差
     *
     * @param period
     * @return
     */
    private List<Integer> diffRemainder(String period) {
//        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
//            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
//        List<Integer> lasWins =
//            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
//        List<Integer> diffs =
//            IntStream.range(0, lasWins.size() - 1).map(i -> lasWins.get(i + 1) - lasWins.get(i)).boxed().distinct()
//                .toList();
//        return diffs.stream().map(d -> Math.floorMod(d, 10)).toList();
        return List.of();
    }

    /**
     * 上一期最后一位-5
     *
     * @param period
     * @return
     */
    private List<Integer> lastWinRedLastDel5(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        return List.of(last.getNum6() - 5);
    }

    /**
     * 上一期第一位红球+5
     *
     * @param period
     * @return
     */
    private List<Integer> lastWinRedOneAdd5(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        return List.of(last.getNum1() + 5);
    }

    /**
     * 上一期篮球+3
     *
     * @param period
     * @return
     */
    private List<Integer> lastWinBlueAdd3(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        return List.of(last.getSpecial() + 3);
    }

    /**
     * 和值尾数+1
     *
     * @param period
     * @return
     */
    private List<Integer> sumTotalMantissaAddOne(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        List<Integer> lasWins =
            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
        int sum = lasWins.stream().mapToInt(Integer::intValue).sum();
        var hzsAddOne = (sum % 100) + 1;
        if (hzsAddOne == 0) {
            return List.of();
        }
        if (hzsAddOne > 33) {
            return List.of(hzsAddOne - 33);
        }
        return List.of(hzsAddOne);
    }

    /**
     * 和值尾数
     *
     * @param period
     * @return
     */
    private List<Integer> sumTotalMantissa(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        List<Integer> lasWins =
            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
        int sum = lasWins.stream().mapToInt(Integer::intValue).sum();
        var hzs = sum % 100;
        if (hzs == 0) {
            return List.of();
        }
        if (hzs > 33) {
            return List.of(hzs - 33);
        }
        return List.of(hzs);
    }

    /**
     * 遗漏35期
     *
     * @param period
     * @return
     */
    private List<Integer> moreThanOmission35(String period) {
        var last100 = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 100").list();
        var mapMapPair = OmissionDueProbabilityUtils.analyzeRedBlueOmission(last100);
        return mapMapPair.getLeft().entrySet().stream()
            .filter(entry -> entry.getValue().getStats().getCurrentOmission() >= 35).map(Map.Entry::getKey).toList();
    }

    /**
     * 3连号中间号
     *
     * @param period
     * @return
     */
    private List<Integer> threeConsecutiveNumbersMiddle(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        List<Integer> lasWins =
            List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6());
        return getIfConsecutive(lasWins).stream().filter(array -> array.length > 4)
            .map(array -> Arrays.stream(array, 1, array.length - 1).toList()).flatMap(List::stream)
            .filter(p -> p > 0 && p < 34).toList();
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

    /**
     * 6期出现3次
     *
     * @param period
     * @return
     */
    private List<Integer> moreThan3(String period) {
        Map<Integer, Integer> readCountMap = HashMap.newHashMap(16);
        var last6 = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 6").list();
        last6.forEach(h -> {
            readCountMap.merge(h.getNum1(), 1, Integer::sum);
            readCountMap.merge(h.getNum2(), 1, Integer::sum);
            readCountMap.merge(h.getNum3(), 1, Integer::sum);
            readCountMap.merge(h.getNum4(), 1, Integer::sum);
            readCountMap.merge(h.getNum5(), 1, Integer::sum);
            readCountMap.merge(h.getNum6(), 1, Integer::sum);
        });
        return readCountMap.entrySet().stream().filter(entry -> entry.getValue() > 3).map(Map.Entry::getKey).toList();
    }

    /**
     * 上一期开出的篮球
     *
     * @param period
     * @return
     */
    private List<Integer> lastWinBlue(String period) {
        var last = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 1").one();
        return List.of(last.getSpecial());
    }

    /**
     * 连续3期都出现
     *
     * @return
     */
    private List<Integer> continuous3(String period) {
        Map<Integer, Integer> readCountMap = HashMap.newHashMap(16);
        var last3 = historyRecordRepository.lambdaQuery().lt(HistoryRecord::getPeriod, Integer.parseInt(period))
            .orderByDesc(HistoryRecord::getPeriod).last(" limit 3").list();
        last3.forEach(h -> {
            readCountMap.merge(h.getNum1(), 1, Integer::sum);
            readCountMap.merge(h.getNum2(), 1, Integer::sum);
            readCountMap.merge(h.getNum3(), 1, Integer::sum);
            readCountMap.merge(h.getNum4(), 1, Integer::sum);
            readCountMap.merge(h.getNum5(), 1, Integer::sum);
            readCountMap.merge(h.getNum6(), 1, Integer::sum);
        });
        return readCountMap.entrySet().stream().filter(entry -> entry.getValue() >= 3).map(Map.Entry::getKey).toList();
    }
}
