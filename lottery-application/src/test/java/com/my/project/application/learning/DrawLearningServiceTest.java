package com.my.project.application.learning;

import com.alibaba.fastjson2.JSON;
import com.my.project.application.feature.dto.FeatureTrendForecast.Summary;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.review.DrawReview;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.DrawLearningRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DrawLearningServiceTest {
    private static final String PERIOD = "2026133";
    private static final String SCOPE = "redSize";
    private final DrawLearningRepository repository = mock(DrawLearningRepository.class);
    private final HistoryRecordService history = mock(HistoryRecordService.class);
    private final DrawLearningService service = new DrawLearningService(repository, history);

    @Test
    void commitsThePairOnceAndDoesNotReplaceItOnRepeatedGeneration() {
        when(repository.lockState()).thenReturn(JSON.toJSONString(Map.of(SCOPE, LearningPolicy.Track.initial().start(LearningPolicy.SWAP))));
        when(history.getLatestRecords(1)).thenReturn(List.of(HistoryRecord.builder().period("2026132")
            .openDate(LocalDate.now(DrawDeadline.ZONE)).build()));
        var stored = new AtomicReference<String>();
        when(repository.findPrediction(PERIOD, SCOPE)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        doAnswer(invocation -> { stored.set(invocation.getArgument(2)); return null; })
            .when(repository).savePrediction(eq(PERIOD), eq(SCOPE), anyString());

        assertThat(service.publishFeature(forecast(List.of("3:3", "4:2")))).contains(List.of("3:3", "4:2"));
        assertThat(service.publishFeature(forecast(List.of("5:1", "2:4")))).contains(List.of("3:3", "4:2"));
        var pair = JSON.parseObject(stored.get(), LearningPrediction.Feature.class);
        assertThat(pair.trialValues()).containsExactly("4:2", "3:3");
        verify(repository, times(1)).savePrediction(eq(PERIOD), eq(SCOPE), anyString());
    }

    @Test
    void excludesLatePairsAndDoesNotCountTheSameDrawTwice() {
        var track = LearningPolicy.Track.initial().start(LearningPolicy.SWAP);
        when(repository.lockState()).thenReturn(JSON.toJSONString(Map.of(SCOPE, track)));
        var cutoff = DrawDeadline.at(LocalDate.of(2026, 11, 19));
        var pair = new LearningPrediction.Feature(cutoff.minusSeconds(1), track.active(), track.trial(),
            List.of("3:3", "4:2"), List.of("4:2", "3:3"));
        var late = new LearningPrediction.Feature(cutoff, track.active(), track.trial(), pair.formalValues(), pair.trialValues());
        when(repository.findPredictions(PERIOD)).thenReturn(List.of(
            new DrawLearningRepository.Prediction(SCOPE, JSON.toJSONString(pair)),
            new DrawLearningRepository.Prediction("redParity", JSON.toJSONString(late))));
        var archive = new AtomicReference<String>();
        when(repository.findReview(PERIOD)).thenAnswer(invocation -> Optional.ofNullable(archive.get()));
        doAnswer(invocation -> { archive.set(invocation.getArgument(1)); return null; })
            .when(repository).saveReview(eq(PERIOD), anyString());

        var draw = new DrawReview.Draw(PERIOD, LocalDate.of(2026, 11, 19), List.of(7, 10, 11, 14, 21, 26), 13);
        var features = List.of(feature(SCOPE), feature("redParity"));
        var review = new DrawReview(draw, new DrawReview.FeatureSummary(0, 2, 0, 0, 0), features, List.of());
        service.settle(review);
        service.settle(review);

        var outcomes = service.archivedReview(PERIOD).orElseThrow().learning().experiments();
        assertThat(outcomes).singleElement().satisfies(entry -> {
            assertThat(entry.scope()).isEqualTo(SCOPE);
            assertThat(entry.pairedPeriods()).isEqualTo(1);
            assertThat(entry.outcome().trialRank()).isEqualTo(1);
            assertThat(entry.outcome().formalRank()).isEqualTo(2);
        });
        verify(repository, times(1)).saveState(anyString());
    }

    private static Summary forecast(List<String> values) {
        return new Summary("2026132", PERIOD, SCOPE, "大小", false, "test", values, Instant.now());
    }

    private static DrawReview.Feature feature(String code) {
        return new DrawReview.Feature(code, code, false, "4:2", List.of("3:3", "4:2"), 2,
            DrawReview.FeatureStatus.ALTERNATE_HIT, "2026132", "test", Instant.parse("2026-11-19T12:00:00Z"));
    }
}
