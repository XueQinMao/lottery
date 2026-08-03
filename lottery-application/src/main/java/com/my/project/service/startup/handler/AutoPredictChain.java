package com.my.project.service.startup.handler;

import com.my.project.service.predict.IPredictService;
import com.my.project.service.selection.pojo.bo.StartupContextBo;
import org.springframework.stereotype.Component;

/**
 * TrainModelChain
 *
 * @author 刘强
 * @version 2025/10/27 16:04
 **/
@Component
public class AutoPredictChain extends AbstractStartupChain {

    private final IPredictService predictService;

    public AutoPredictChain(IPredictService predictService) {
        this.predictService = predictService;
    }

    @Override
    protected String getStepName() {
        return "自动预测";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        predictService.autoPredict(context.getOpenDate());
    }
}
