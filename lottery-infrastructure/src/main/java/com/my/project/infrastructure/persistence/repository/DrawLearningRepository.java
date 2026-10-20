package com.my.project.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.infrastructure.persistence.entity.DrawLearningPrediction;
import com.my.project.infrastructure.persistence.entity.DrawLearningReview;
import com.my.project.infrastructure.persistence.entity.DrawLearningState;
import com.my.project.infrastructure.persistence.mapper.DrawLearningPredictionMapper;
import com.my.project.infrastructure.persistence.mapper.DrawLearningReviewMapper;
import com.my.project.infrastructure.persistence.mapper.DrawLearningStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@DependsOn("drawLearningSchemaMigration")
public class DrawLearningRepository {
    private final DrawLearningStateMapper states;
    private final DrawLearningPredictionMapper predictions;
    private final DrawLearningReviewMapper reviews;

    public String lockState() {
        var state = states.selectOne(Wrappers.lambdaQuery(DrawLearningState.class)
            .select(DrawLearningState::getPayload)
            .eq(DrawLearningState::getStateKey, DrawLearningState.SINGLETON_KEY)
            .last("FOR UPDATE"));
        if (state == null) throw new IllegalStateException("策略学习状态未初始化");
        return state.getPayload();
    }

    public void saveState(String payload) {
        states.update(Wrappers.lambdaUpdate(DrawLearningState.class)
            .eq(DrawLearningState::getStateKey, DrawLearningState.SINGLETON_KEY)
            .set(DrawLearningState::getPayload, payload));
    }

    public void savePrediction(String period, String scope, String payload) {
        predictions.insert(DrawLearningPrediction.builder().period(period).scope(scope).payload(payload).build());
    }

    public Optional<String> findPrediction(String period, String scope) {
        return Optional.ofNullable(predictions.selectOne(Wrappers.lambdaQuery(DrawLearningPrediction.class)
                .select(DrawLearningPrediction::getPayload)
                .eq(DrawLearningPrediction::getPeriod, period)
                .eq(DrawLearningPrediction::getScope, scope)))
            .map(DrawLearningPrediction::getPayload);
    }

    public List<Prediction> findPredictions(String period) {
        return predictions.selectList(Wrappers.lambdaQuery(DrawLearningPrediction.class)
                .select(DrawLearningPrediction::getScope, DrawLearningPrediction::getPayload)
                .eq(DrawLearningPrediction::getPeriod, period)
                .orderByAsc(DrawLearningPrediction::getScope))
            .stream().map(row -> new Prediction(row.getScope(), row.getPayload())).toList();
    }

    public Optional<String> findReview(String period) {
        return Optional.ofNullable(reviews.selectOne(Wrappers.lambdaQuery(DrawLearningReview.class)
                .select(DrawLearningReview::getPayload)
                .eq(DrawLearningReview::getPeriod, period)))
            .map(DrawLearningReview::getPayload);
    }

    public List<String> recentReviews(String before, int limit) {
        if (limit <= 0) return List.of();
        return reviews.selectPage(Page.of(1, limit, false), Wrappers.lambdaQuery(DrawLearningReview.class)
                .select(DrawLearningReview::getPayload)
                .lt(DrawLearningReview::getPeriod, before)
                .orderByDesc(DrawLearningReview::getPeriod))
            .getRecords().stream().map(DrawLearningReview::getPayload).toList();
    }

    public void saveReview(String period, String payload) {
        reviews.insert(DrawLearningReview.builder().period(period).payload(payload).build());
    }

    public List<String> pendingPeriods(int initialWindow) {
        return reviews.findPendingPeriods(initialWindow);
    }

    public record Prediction(String scope, String payload) { }
}
