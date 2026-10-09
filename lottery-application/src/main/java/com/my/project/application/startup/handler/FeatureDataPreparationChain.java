package com.my.project.application.startup.handler;

import com.my.project.application.selection.model.StartupContextBo;
import com.my.project.application.feature.FeatureCalculatorService;
import org.springframework.stereotype.Component;

/**
 * FetchHistoryRecordChain
 *
 * @author 刘强
 * @version 2025/10/23 15:53
 **/
@Component
public class FeatureDataPreparationChain extends AbstractStartupChain {

    private final FeatureCalculatorService featureCalculatorService;

    public FeatureDataPreparationChain(FeatureCalculatorService featureCalculatorService) {
        this.featureCalculatorService = featureCalculatorService;
    }


    @Override
    protected String getStepName() {
        return "特诊数据准备";
    }

    @Override
    protected void doHandle(StartupContextBo<Object> context) {
        try {
            featureCalculatorService.calculateAndExportFeatures();
        } catch (Exception e) {
            context.setSuccess(false);
            context.setErrorMessage(e.getMessage());
        }
    }
}
