package com.my.project.service.selection.pojo.bo;

import com.my.project.persistence.entity.PredictRecord;
import lombok.Data;

/**
 * ScoredCandidateBo
 *
 * @author 刘强
 * @version 2025/12/22 19:40
 **/
@Data
public class ScoredCandidateBo {

    private PredictRecord result;
    private double finalScore;
    private String explanation;
}
