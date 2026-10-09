package com.my.project.application.feature;

import com.my.project.application.selection.model.SsqCombinationBo;

import java.io.IOException;
import java.util.Map;

/**
 * FeatureCalculatorService
 *
 * @author 刘强
 * @version 2025/10/23 16:24
 **/
public interface FeatureCalculatorService {

    void calculateAndExportFeatures() throws IOException;

    Map<String, Object> calculateFeature(SsqCombinationBo combination);
}
