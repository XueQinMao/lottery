package com.my.project.application.startup.handler;

import com.my.project.application.event.HistoryCompleteEvent;
import com.my.project.application.selection.model.StartupContextBo;
import com.my.project.application.history.HistoryRecordService;
import org.springframework.stereotype.Component;

/**
 * FetchHistoryRecordChain
 *
 * @author 刘强
 * @version 2025/10/23 15:53
 **/
@Component
public class FetchHistoryRecordChain extends AbstractStartupChain {

    private final HistoryRecordService historyRecordService;

    public FetchHistoryRecordChain(HistoryRecordService historyRecordService) {
        this.historyRecordService = historyRecordService;
    }

    @Override
    protected String getStepName() {
        return "拉取历史开奖数据";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        try {
            historyRecordService.syncHistoryRecords();
            HistoryCompleteEvent.of(this).publish();
        } catch (Exception e) {
            context.setSuccess(false);
            context.setErrorMessage(e.getMessage());
        }
    }
}
