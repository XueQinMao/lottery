package com.my.project.application.startup.handler;

import com.my.project.application.predict.PredictService;
import com.my.project.application.selection.model.StartupContextBo;
import org.springframework.stereotype.Component;

/**
 * TrainModelChain
 *
 * @author 刘强
 * @version 2025/10/27 16:04
 **/
@Component
public class AutoPredictChain extends AbstractStartupChain {

    private final PredictService predictService;

    public AutoPredictChain(PredictService predictService) {
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
