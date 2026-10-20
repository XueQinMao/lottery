package com.my.project.application.history;

import com.my.project.application.learning.DrawLearningService;
import com.my.project.application.review.DrawReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DrawSynchronizationService {
    private final HistoryRecordService history;
    private final DrawReviewService reviews;
    private final DrawLearningService learning;

    public synchronized void synchronize() {
        history.syncHistoryRecords();
        settlePendingDraws();
    }

    public synchronized void synchronizeThrough(LocalDate expectedDrawDate) {
        var latest = history.getLatestRecords(1);
        if (latest.isEmpty() || latest.getFirst().getOpenDate().isBefore(expectedDrawDate)) {
            history.syncHistoryRecords();
        }
        settlePendingDraws();
    }

    private void settlePendingDraws() {
        // 同步和结算分开提交。若进程在开奖入库后退出，下次同步会补结算。
        for (String period : learning.pendingPeriods()) {
            reviews.find(period).ifPresent(learning::settle);
        }
    }
}
