package com.my.project.python.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ModelPredictOutputBo
 *
 * @author 刘强
 * @version 2025/10/29 19:47
 **/
@Data
public class ModelPredictOutputBo {

    private BigDecimal probability;
    private String reason;
}
