package com.my.project.service.event.listener;

import com.my.project.service.event.AdjustCompleteEvent;
import com.my.project.service.selection.IBuyRecordService;
import com.my.project.service.selection.enums.BuyRecordTypeEnums;
import com.my.project.service.support.NextLotteryDateUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * AdjustCompleteEventListener
 *
 * <p>调优完成后落库购彩记录。
 *
 * @author 刘强
 * @version 2026/08/10 17:02
 **/
@Component
@Slf4j
@AllArgsConstructor
public class AdjustCompleteEventListener {

    private final IBuyRecordService buyRecordService;

    @EventListener
    public void handleEvent(AdjustCompleteEvent event) {
        buyRecordService.batchSave(event.getAdjustRespBo(), NextLotteryDateUtils.nextDrawDate(),
            BuyRecordTypeEnums.MANUAL);
    }
}
