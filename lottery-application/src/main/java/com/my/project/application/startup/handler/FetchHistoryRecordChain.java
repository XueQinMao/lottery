package com.my.project.application.startup.handler;

import com.my.project.application.event.HistoryCompleteEvent;
import com.my.project.application.selection.model.StartupContextBo;
import com.my.project.application.history.DrawSynchronizationService;
import org.springframework.stereotype.Component;

/**
 * FetchHistoryRecordChain
 *
 * @author 刘强
 * @version 2025/10/23 15:53
 **/
@Component
public class FetchHistoryRecordChain extends AbstractStartupChain {

    private final DrawSynchronizationService synchronization;

    public FetchHistoryRecordChain(DrawSynchronizationService synchronization) {
        this.synchronization = synchronization;
    }

    @Override
    protected String getStepName() {
        return "拉取历史开奖数据";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        try {
            synchronization.synchronize();
            HistoryCompleteEvent.of(this).publish();
        } catch (Exception e) {
            context.setSuccess(false);
            context.setErrorMessage(e.getMessage());
        }
    }
}
