package com.my.project.application.startup.handler;

import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.selection.model.StartupContextBo;
import org.springframework.stereotype.Component;

/**
 * CalculateWeightsChain
 *
 * @author 刘强
 * @version 2025/11/26 20:19
 **/
@Component
public class CalculateWeightsChain extends AbstractStartupChain{

    private final SmartSelectService smartSelectService;

    public CalculateWeightsChain(SmartSelectService smartSelectService) {
        this.smartSelectService = smartSelectService;
    }

    @Override
    protected String getStepName() {
        return "开始计算权重";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        smartSelectService.refreshWeightConfig();
    }
}
