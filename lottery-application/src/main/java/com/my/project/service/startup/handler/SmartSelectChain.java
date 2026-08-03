package com.my.project.service.startup.handler;

import com.my.project.service.config.ModelPredictCache;
import com.my.project.service.selection.IBuyRecordService;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.selection.pojo.bo.StartupContextBo;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

/**
 * SmartSelectChain
 *
 * @author 刘强
 * @version 2025/11/10 17:39
 **/
@Component
public class SmartSelectChain extends AbstractStartupChain {

    private final ISmartSelectService smartSelectService;

    private final IBuyRecordService buyRecordService;

    private static final Integer BUDGET = 10;

    public SmartSelectChain(ISmartSelectService smartSelectService, IBuyRecordService buyRecordService) {
        this.smartSelectService = smartSelectService;
        this.buyRecordService = buyRecordService;
    }

    @Override
    protected String getStepName() {
        return "购买推荐";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        var buyRecordDtos = buyRecordService.getByOpenDate(context.getOpenDate());
        if (CollectionUtils.isNotEmpty(buyRecordDtos)) {
            return;
        }
        smartSelectService.recommendFushi(context.getOpenDate(), BUDGET);
        ModelPredictCache.getInstance().clear();
    }
}
