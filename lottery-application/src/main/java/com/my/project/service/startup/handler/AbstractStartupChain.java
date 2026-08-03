package com.my.project.service.startup.handler;

import com.alibaba.fastjson.JSON;
import com.my.project.service.selection.pojo.bo.StartupContextBo;
import com.my.project.service.startup.StartupHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * AbstractStartupChain
 *
 * @author 刘强
 * @version 2025/09/01 15:24
 **/
public abstract class AbstractStartupChain implements StartupHandler {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    protected StartupHandler next;

    @Override
    public void setNext(StartupHandler next) {
        this.next = next;
    }

    @Override
    public void handle(StartupContextBo<Object> context) {
        Instant instant = Instant.now();
        doHandle(context);
        logger.info("执行责任链 {} 耗时 {} 秒", getStepName(), ChronoUnit.SECONDS.between(instant, Instant.now()));
        if (context.isSuccess() && Objects.nonNull(next)) {
            next.handle(context);
        } else {
            logger.error("开始责任链任务 {} 失败 {}", getStepName(), JSON.toJSONString(context));

        }
    }

    protected abstract String getStepName();

    /**
     * 执行具体的任务
     *
     * @param context
     */
    protected abstract void doHandle(StartupContextBo<Object> context);
}
