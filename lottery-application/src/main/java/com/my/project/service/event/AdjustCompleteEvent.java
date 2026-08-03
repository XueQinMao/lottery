package com.my.project.service.event;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.service.support.SpringContextUtils;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;

/**
 * PredictCompleteEvent
 * 预测结果完成时间
 *
 * @author 刘强
 * @version 2025/10/30 16:10
 **/
public class AdjustCompleteEvent extends ApplicationEvent {

    private LotteryAdjustRespBo adjustRespBo;

    public AdjustCompleteEvent(Object source) {
        super(source);
    }

    public static AdjustCompleteEvent of(Object source, LotteryAdjustRespBo adjustRespBo){
        AdjustCompleteEvent event = new AdjustCompleteEvent(source);
        event.setAdjustRespBo(adjustRespBo);
        return event;
    }

    public LotteryAdjustRespBo getAdjustRespBo() {
        return adjustRespBo;
    }

    public void setAdjustRespBo(LotteryAdjustRespBo adjustRespBo) {
        this.adjustRespBo = adjustRespBo;
    }
    public void publish() {
        SpringContextUtils.getApplicationContext().publishEvent(this);
    }
}
