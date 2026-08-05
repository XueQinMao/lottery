package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.ColdHotAnalysisBo;
import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.feature.impl.LotteryFeatureAnalysisServiceImpl;
import com.my.project.service.selection.impl.SmartSelectServiceImpl;
import com.my.project.service.predict.IPredictHitRecordService;
import com.my.project.service.predict.IPredictRecordService;
import com.my.project.service.support.FeatureForecastHitUtils;
import com.my.project.service.support.OmissionUtils;
import com.my.project.service.support.analyse.ColdHotAnalysisUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * ISmartSelectServiceTest
 *
 * @author 刘强
 * @version 2025/12/29 19:27
 **/
@SpringBootTest
public class ISmartSelectServiceTest {

    @Resource
    private SmartSelectServiceImpl smartSelectService;

    @Resource
    private IPredictRecordService predictRecordService;

    @Resource
    private IPredictHitRecordService predictHitRecordService;

    @Resource
    private LotteryFeatureAnalysisServiceImpl lotteryFeatureAnalysisService;

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Test
    public void testSmartSelect() {

        LocalDate parse = LocalDate.parse("2025-12-28");
        smartSelectService.recommendFushi(parse, 5);
    }

    @Test
    public void test() {
        Stream.of("2025-11-30", "2025-12-28", "2026-01-01").forEach(s -> {
            LocalDate openDate = LocalDate.parse(s);
            predictHitRecordService.archiveHits(openDate);
            predictRecordService.deleteByOpenDate(openDate);
        });

    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
            Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
            .blueBall(record.getSpecial()).build();
    }

    @Test
    public void test_ananlys() {

        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 51").list();

        windows.forEach(w -> {
            List<HistoryRecord> records =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).list();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            int forecastSize = Math.min(100, records.size());
            var forecastRecords =
                records.subList(0, forecastSize).stream().map(this::toDrawRecord).collect(Collectors.toList());

            FeatureForecastBo featureForecastBo =
                lotteryFeatureAnalysisService.forecastFeaturesByIndex(forecastRecords, records);
            List<Integer> reds =
                Arrays.asList(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(),
                    next.getNum6());
            Integer blue = next.getSpecial();
            int mainCnt = 0, altCnt = 0, missCnt = 0;
            StringBuilder line =
                new StringBuilder("第" + next.getPeriod() + "期开奖" + reds + "+" + blue + " 特征命中：");
            for (FeatureKindEnums value : FeatureKindEnums.values()) {
                String actual = value.extract(reds, blue);
                FeatureForecastBo.FeatureForecastItem item = featureForecastBo.itemOf(value.getCode());
                boolean mainHit = item != null && FeatureForecastHitUtils.matches(item.getValue(), actual);
                boolean altHit =
                    !mainHit && item != null && item.getAlternatives() != null && item.getAlternatives().stream()
                        .anyMatch(alt -> FeatureForecastHitUtils.matches(alt, actual));
                String type = mainHit ? "主" : (altHit ? "备" : "×");
                if (mainHit) {
                    mainCnt++;
                } else if (altHit) {
                    altCnt++;
                } else {
                    missCnt++;
                }
                line.append("\n  ").append(value.getLabel()).append(" 实际=").append(actual).append(" 主推=")
                    .append(item == null ? null : item.getValue()).append(" 备选=")
                    .append(item == null ? null : item.getAlternatives()).append(" ").append(type);
            }
            System.out.println("特征featureForecastBo = " + JSON.toJSONString(featureForecastBo));
            System.out.println(line);
            System.out.println(
                "第" + next.getPeriod() + "期命中统计：主推" + mainCnt + " 备选" + altCnt + " 未命中" + missCnt + " 含备选命中率" + pct(
                    mainCnt + altCnt, mainCnt + altCnt + missCnt));
        });
    }

    private static String pct(int hit, int total) {
        if (total <= 0) {
            return "-";
        }
        return String.format("%.1f%%", hit * 100.0 / total);
    }

    @Test
    public void test_jx() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 31").list();

        windows.forEach(w -> {
            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate()).last(" limit 100")
                    .orderByDesc(HistoryRecord::getOpenDate).list();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            List<Integer> list = IntStream.rangeClosed(1, 33).boxed()
                .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(past, "red", ball)))
                .filter(p -> p.getRight().getPhase().equals("rising") || p.getRight().getPhase().equals("rebounding"))
                .map(Pair::getLeft).toList();
            System.out.println("均线选了："+list.size()+" 命中："+ CollectionUtils.intersection(list, nextWins));

        });
    }

    /**
     * 发现均线抬头的篮球基本都不会出现那就作为杀球
     */
    @Test
    public void test_jx_blue() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 101").list();
        AtomicInteger count = new AtomicInteger(0);
        windows.forEach(w -> {
            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate()).last(" limit 100")
                    .orderByDesc(HistoryRecord::getOpenDate).list();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> list = IntStream.rangeClosed(1, 16).boxed()
                .map(ball -> Pair.of(ball, OmissionUtils.omissionBallAnalyzer(past, "blue", ball)))
                .filter(p -> p.getRight().getPhase().equals("rising") || p.getRight().getPhase().equals("rebounding"))
                .map(Pair::getLeft).toList();
            System.out.println("均线选了："+list.size()+" 命中："+ list.contains(next.getSpecial()));
            if(list.contains(next.getSpecial())){
                count.getAndAdd(1);
            }
        });
        System.out.println("共命中："+count.get());
    }

    @Test
    public void test_hot(){
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 30").list();
        AtomicInteger count = new AtomicInteger(0);
        windows.forEach(w -> {
            List<HistoryRecord> past =
                historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate()).last(" limit 30")
                    .orderByDesc(HistoryRecord::getOpenDate).list();

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            ColdHotAnalysisBo calculate = ColdHotAnalysisUtils.calculate(past);

            System.out.println("第"+next.getPeriod()+"期 冷温热预测：热："
                + CollectionUtils.intersection(calculate.getRedHotBalls(), nextWins).size()
                +" 温："+CollectionUtils.intersection(calculate.getRedWarmBalls(), nextWins).size()
                +" 冷："+CollectionUtils.intersection(calculate.getRedColdBalls(), nextWins).size()
            );

        });
    }
}
