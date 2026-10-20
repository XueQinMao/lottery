package com.my.project.application.feature.impl;

import com.my.project.application.history.support.NumberTrendCalculator;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecommendationNumberTrendService {
    private static final int SAMPLE_SIZE = 30;

    public List<NumberTrendObservation> candidates(List<HistoryRecord> newestFirst) {
        var recentDraws = newestFirst.stream().limit(SAMPLE_SIZE).toList().reversed();
        return NumberTrendCalculator.calculate(recentDraws, SAMPLE_SIZE).redCandidates().stream()
            .map(number -> new NumberTrendObservation(Ball.RED, number)).toList();
    }
}
