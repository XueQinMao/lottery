package com.my.project.api.diagnostics;

import lombok.RequiredArgsConstructor;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.predict.PredictHitRecordService;
import com.my.project.application.predict.PredictRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DiagnosticsController
 *
 * @author 刘强
 * @version 2025/11/03 15:49
 **/
@RequiredArgsConstructor
@RestController
@RequestMapping("/test")
public class DiagnosticsController {

    @PutMapping()
    public String test() {
        return "success";
    }

    private final PredictRecordService predictRecordService;

    private final HistoryRecordService historyRecordService;
    private final PredictHitRecordService predictHitRecordService;



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
