package com.my.project.service.startup.handler;

import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.selection.pojo.bo.StartupContextBo;
import org.springframework.stereotype.Component;

/**
 * CalculateWeightsChain
 *
 * @author 刘强
 * @version 2025/11/26 20:19
 **/
@Component
public class CalculateWeightsChain extends AbstractStartupChain{

    private final ISmartSelectService smartSelectService;

    public CalculateWeightsChain(ISmartSelectService smartSelectService) {
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
