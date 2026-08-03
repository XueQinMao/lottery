package com.my.project.service.selection.pojo.bo;

import lombok.Data;

/**
 * PeriodFeaturesBo
 *
 * @author 刘强
 * @version 2025/12/22 19:38
 **/
@Data
public class PeriodFeaturesBo {
    private NumericFeaturesBo numeric;
    private CategoricalFeaturesBo categorical;
    private BlueFeaturesBo blue;
}
