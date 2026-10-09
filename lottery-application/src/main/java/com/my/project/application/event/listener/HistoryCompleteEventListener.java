package com.my.project.application.event.listener;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.event.HistoryCompleteEvent;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.predict.PredictHitRecordService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HistoryCompleteEventListener
 *
 * @author 刘强
 * @version 2026/01/09 14:13
 **/
@Component
@Slf4j
@AllArgsConstructor
public class HistoryCompleteEventListener {

    private final HistoryRecordService historyRecordService;

    private final PredictHitRecordService predictHitRecordService;
    


    @EventListener
    public void handleEvent(HistoryCompleteEvent event) {
        List<HistoryRecord> latestRecords = historyRecordService.getLatestRecords(2);
        // 归档最新一期的开奖日志
        predictHitRecordService.archiveHits(latestRecords.getFirst().getOpenDate());
        //删除最新一期上期的推荐结果
        //        predictRecordService.deleteByOpenDate(latestRecords.getLast().getOpenDate());
    }
}
