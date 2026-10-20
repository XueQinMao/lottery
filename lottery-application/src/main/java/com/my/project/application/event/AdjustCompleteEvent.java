package com.my.project.application.event;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.application.support.SpringContextUtils;
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

    private final LotteryAdjustRespBo adjustRespBo;
    private final LocalDate drawDate;

    private AdjustCompleteEvent(Object source, LotteryAdjustRespBo adjustRespBo, LocalDate drawDate) {
        super(source);
        this.adjustRespBo = adjustRespBo;
        this.drawDate = drawDate;
    }

    public static AdjustCompleteEvent of(Object source, LotteryAdjustRespBo adjustRespBo, LocalDate drawDate) {
        return new AdjustCompleteEvent(source, adjustRespBo, drawDate);
    }

    public LocalDate getDrawDate() {
        return drawDate;
    }

    public LotteryAdjustRespBo getAdjustRespBo() {
        return adjustRespBo;
    }

    public void publish() {
        SpringContextUtils.getApplicationContext().publishEvent(this);
    }
}
