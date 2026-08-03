package com.my.project.service.startup.handler;

import com.my.project.service.selection.pojo.bo.StartupContextBo;
import com.my.project.service.predict.ITrainModelService;
import org.springframework.stereotype.Component;

/**
 * TrainModelChain
 *
 * @author 刘强
 * @version 2025/10/27 16:04
 **/
@Component
public class TrainModelChain extends AbstractStartupChain{

    private final ITrainModelService trainModelService;

    public TrainModelChain(ITrainModelService trainModelService) {
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
