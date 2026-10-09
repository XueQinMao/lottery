package com.my.project.application.startup.handler;

import com.my.project.application.predict.cache.PredictionCandidateCache;
import com.my.project.application.selection.BuyRecordService;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.selection.model.StartupContextBo;
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

    private final SmartSelectService smartSelectService;

    private final BuyRecordService buyRecordService;

    private static final Integer BUDGET = 10;

    public SmartSelectChain(SmartSelectService smartSelectService, BuyRecordService buyRecordService) {
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
        PredictionCandidateCache.getInstance().clear();
    }
}
