package com.my.project.service.feature;

import com.my.project.service.selection.pojo.bo.SsqCombinationBo;

import java.io.IOException;
import java.util.Map;

/**
 * IFeatureCalculatorService
 *
 * @author 刘强
 * @version 2025/10/23 16:24
 **/
public interface IFeatureCalculatorService {

    void calculateAndExportFeatures() throws IOException;

    Map<String, Object> calculateFeature(SsqCombinationBo ssqCombinationBo);
}
