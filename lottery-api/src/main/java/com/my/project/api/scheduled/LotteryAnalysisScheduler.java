package com.my.project.api.scheduled;

import com.my.project.application.feature.LotteryFeatureAnalysisService;
import com.my.project.application.history.DrawSynchronizationService;
import com.my.project.application.learning.DrawDeadline;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * LotteryAnalysisScheduler
 *
 * @author 刘强
 * @version 2026/02/05 19:38
 **/
@Component
@RequiredArgsConstructor
public class LotteryAnalysisScheduler {

    private final DrawSynchronizationService synchronization;
    private final LotteryFeatureAnalysisService lotteryFeatureAnalysisService;

    @Scheduled(cron = "${lottery.learning.first-draw-sync:0 30,40,50 21 * * TUE,THU,SUN}", zone = "Asia/Shanghai")
    @Scheduled(cron = "${lottery.learning.draw-sync-retry:0 */10 22-23 * * TUE,THU,SUN}", zone = "Asia/Shanghai")
    public void synchronizeDraw() {
        synchronization.synchronizeThrough(LocalDate.now(DrawDeadline.ZONE));
    }

    @Scheduled(cron = "${lottery.learning.draw-sync-catch-up:0 0 9 * * MON,WED,FRI}", zone = "Asia/Shanghai")
    public void catchUpDraw() {
        synchronization.synchronizeThrough(LocalDate.now(DrawDeadline.ZONE).minusDays(1));
    }

    @Scheduled(cron = "0 0 1 ? * MON,WED,FRI", zone = "Asia/Shanghai")
    public void runAnalysis(){
        lotteryFeatureAnalysisService.analyzeLatest(100);
    }
}
