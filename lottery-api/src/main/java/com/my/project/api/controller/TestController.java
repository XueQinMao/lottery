package com.my.project.api.controller;

import com.my.project.persistence.repository.IPredictHitRecordRepository;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.predict.IPredictHitRecordService;
import com.my.project.service.predict.IPredictRecordService;
import com.my.project.service.predict.IPredictService;
import com.my.project.service.selection.IBuyRecordService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * TestController
 *
 * @author 刘强
 * @version 2025/11/03 15:49
 **/
@RestController
@RequestMapping("/test")
public class TestController {

    @PutMapping()
    public String test() {
        return "success";
    }

    @Resource
    private IPredictRecordService predictRecordService;

    @Resource
    private IHistoryRecordService historyRecordService;
    @Resource
    private IPredictHitRecordService predictHitRecordService;



    @GetMapping("/archiveHits")
    public String archiveHits() {
        List<HistoryRecord> latestRecords1 = historyRecordService.getLatestRecords(5);

        latestRecords1.parallelStream().sorted((h1, h2) -> h1.getOpenDate().compareTo(h2.getOpenDate()))
            .forEachOrdered(historyRecord -> predictHitRecordService.archiveHits(historyRecord.getOpenDate()));
        return "success";
    }

    @GetMapping("/delete")
    public String delete() {
        List<HistoryRecord> latestRecords1 = historyRecordService.getLatestRecords(5);

        latestRecords1.stream().sorted((h1, h2) -> h1.getOpenDate().compareTo(h2.getOpenDate()))
            .forEachOrdered(historyRecord -> {
                predictHitRecordService.archiveHits(historyRecord.getOpenDate());
                predictRecordService.deleteByOpenDate(historyRecord.getOpenDate());
            });
        return "success";
    }


}
