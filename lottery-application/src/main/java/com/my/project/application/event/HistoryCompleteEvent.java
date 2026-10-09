package com.my.project.application.event;

import com.my.project.application.support.SpringContextUtils;
import org.springframework.context.ApplicationEvent;

/**
 * HistoryCompleteEvent
 *
 * @author 刘强
 * @version 2026/01/09 14:12
 **/
public class HistoryCompleteEvent extends ApplicationEvent {
    public HistoryCompleteEvent(Object source) {
        super(source);
    }
    public static HistoryCompleteEvent of(Object source){
        return new HistoryCompleteEvent(source);
    }

    public void publish() {
        SpringContextUtils.getApplicationContext().publishEvent(this);
    }
}
