package com.my.project.service.startup.handler;

import com.my.project.service.event.HistoryCompleteEvent;
import com.my.project.service.selection.pojo.bo.StartupContextBo;
import com.my.project.service.history.IHistoryRecordService;
import org.springframework.stereotype.Component;

/**
 * FetchHistoryRecordChain
 *
 * @author 刘强
 * @version 2025/10/23 15:53
 **/
@Component
public class FetchHistoryRecordChain extends AbstractStartupChain {

    private final IHistoryRecordService historyRecordService;

    public FetchHistoryRecordChain(IHistoryRecordService historyRecordService) {
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
