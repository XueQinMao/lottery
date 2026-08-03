package com.my.project.service;

import com.my.project.service.selection.impl.SmartSelectServiceImpl;
import com.my.project.service.predict.IPredictHitRecordService;
import com.my.project.service.predict.IPredictRecordService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
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
}
