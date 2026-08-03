package com.my.project.service.event;

import com.my.project.service.selection.pojo.bo.SsqCombinationBo;
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
public class PredictCompleteEvent extends ApplicationEvent {

    private SsqCombinationBo ssqCombinationBo;

    private String predictResult;

    private LocalDate openDate;

    private Long position;

    public PredictCompleteEvent(Object source) {
        super(source);
    }

    public static PredictCompleteEvent of(Object source, SsqCombinationBo ssqCombinationBo, String predictResult,
        LocalDate openDate, Long position){
        PredictCompleteEvent event = new PredictCompleteEvent(source);
        event.setSsqCombinationBo(ssqCombinationBo);
        event.setPredictRecord(predictResult);
        event.setOpenDate(openDate);
        event.setPosition(position);
        return event;
    }


    public SsqCombinationBo getSsqCombinationBo() {
        return ssqCombinationBo;
    }

    public void setSsqCombinationBo(SsqCombinationBo ssqCombinationBo) {
        this.ssqCombinationBo = ssqCombinationBo;
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
