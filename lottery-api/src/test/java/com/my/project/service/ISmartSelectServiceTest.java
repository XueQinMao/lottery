package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.llm.impl.LotteryFeatureAnalysisServiceImpl;
import com.my.project.service.selection.impl.SmartSelectServiceImpl;
import com.my.project.service.predict.IPredictHitRecordService;
import com.my.project.service.predict.IPredictRecordService;
import com.my.project.service.support.FeatureIntervalForecastUtils;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
    public void test(){
        Stream.of("2025-11-30","2025-12-28","2026-01-01").forEach(s ->{
            LocalDate openDate = LocalDate.parse(s);
            predictHitRecordService.archiveHits(openDate);
            predictRecordService.deleteByOpenDate(openDate);
        });

    }

    @Test
    public void test_java() {
        List<HistoryRecord> records =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit " + 100).list();
        var forecastRecords = records.stream().map(this::toDrawRecord).collect(Collectors.toList());
        FeatureForecastBo featureForecastBo = lotteryFeatureAnalysisService.forecastFeaturesByJava(forecastRecords);
        System.out.println(JSON.toJSONString(featureForecastBo));
    }

    @Test
    public void test_llm(){

        List<HistoryRecord> records =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit " + 31).list();
        var forecastRecords = records.stream().filter(r ->!r.getPeriod().equals("2026094"))
            .map(this::toDrawRecord)
            .collect(Collectors.toList());

        FeatureForecastBo featureForecastBo = lotteryFeatureAnalysisService.forecastFeaturesByLlm(forecastRecords);
//        FeatureForecastBo featureForecastBo = FeatureIntervalForecastUtils.forecast(forecastRecords);
        System.out.println(JSON.toJSONString(featureForecastBo));
    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
            Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
            .blueBall(record.getSpecial()).build();
    }
}
