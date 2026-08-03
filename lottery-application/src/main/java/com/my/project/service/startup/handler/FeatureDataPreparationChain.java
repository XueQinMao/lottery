package com.my.project.service.startup.handler;

import com.my.project.service.selection.pojo.bo.StartupContextBo;
import com.my.project.service.feature.IFeatureCalculatorService;
import org.springframework.stereotype.Component;

/**
 * FetchHistoryRecordChain
 *
 * @author 刘强
 * @version 2025/10/23 15:53
 **/
@Component
public class FeatureDataPreparationChain extends AbstractStartupChain {

    private final IFeatureCalculatorService featureCalculatorService;

    public FeatureDataPreparationChain(IFeatureCalculatorService featureCalculatorService) {
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
