package com.my.project.application.learning;

import com.my.project.application.learning.LearningPolicy.Version;
import com.my.project.llm.bo.LotteryAdjustRespBo;

import java.time.Instant;
import java.util.List;

final class LearningPrediction {
    private LearningPrediction() { }

    record Feature(Instant publishedAt, Version active, Version trial, List<String> formalValues, List<String> trialValues) { }

    record Numbers(Instant publishedAt, Version active, Version trial, LotteryAdjustRespBo formal,
                   LotteryAdjustRespBo challenger, long candidateSeed, long randomSeed) { }
}
