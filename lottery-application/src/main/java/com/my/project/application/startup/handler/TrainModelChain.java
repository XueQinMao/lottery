package com.my.project.application.startup.handler;

import com.my.project.application.selection.model.StartupContextBo;
import com.my.project.application.predict.TrainModelService;
import org.springframework.stereotype.Component;

/**
 * TrainModelChain
 *
 * @author 刘强
 * @version 2025/10/27 16:04
 **/
@Component
public class TrainModelChain extends AbstractStartupChain{

    private final TrainModelService trainModelService;

    public TrainModelChain(TrainModelService trainModelService) {
        this.trainModelService = trainModelService;
    }

    @Override
    protected String getStepName() {
        return "模型训练";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        try {
            trainModelService.train();
        }catch (Exception e){
            context.setSuccess(false);
            context.setErrorMessage(e.getMessage());
        }

    }
}
