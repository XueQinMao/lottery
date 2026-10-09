package com.my.project.application.event;

import com.my.project.application.selection.model.SsqCombinationBo;
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
public class PredictCompleteEvent extends ApplicationEvent {

    private SsqCombinationBo combination;

    private String predictResult;

    private LocalDate openDate;

    private Long position;

    public PredictCompleteEvent(Object source) {
        super(source);
    }

    public static PredictCompleteEvent of(Object source, SsqCombinationBo combination, String predictResult,
        LocalDate openDate, Long position){
        PredictCompleteEvent event = new PredictCompleteEvent(source);
        event.setSsqCombinationBo(combination);
        event.setPredictRecord(predictResult);
        event.setOpenDate(openDate);
        event.setPosition(position);
        return event;
    }


    public SsqCombinationBo getSsqCombinationBo() {
        return combination;
    }

    public void setSsqCombinationBo(SsqCombinationBo combination) {
        this.combination = combination;
    }

    public String getPredictRecord() {
        return predictResult;
    }

    public void setPredictRecord(String predictResult) {
        this.predictResult = predictResult;
    }

    public LocalDate getOpenDate() {
        return openDate;
    }

    public void setOpenDate(LocalDate openDate) {
        this.openDate = openDate;
    }

    public Long getPosition() {
        return position;
    }

    public void setPosition(Long position) {
        this.position = position;
    }

    public void publish() {
        SpringContextUtils.getApplicationContext().publishEvent(this);
    }
}
