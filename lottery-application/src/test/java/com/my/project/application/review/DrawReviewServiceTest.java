package com.my.project.application.review;

import com.alibaba.fastjson2.JSON;
import com.my.project.application.feature.FeatureTrendForecastService;
import com.my.project.application.feature.dto.FeatureTrendForecast.Summary;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.learning.DrawLearningService;
import com.my.project.application.report.LotteryReportService;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.LotteryAdjustRespBo.AdjustedTicket;
import com.my.project.llm.bo.LotteryAdjustRespBo.ComplexTicket;
import com.my.project.llm.bo.LotteryAdjustRespBo.FinalRecommendation;
import com.my.project.llm.bo.LotteryAdjustRespBo.TicketCategory;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DrawReviewServiceTest {
    private static final String PERIOD = "2026132";
    private static final Instant BEFORE = Instant.parse("2026-11-19T13:14:59Z");
    private static final Instant CUTOFF = Instant.parse("2026-11-19T13:15:00Z");
    private static final List<Integer> WINNING_REDS = List.of(1, 2, 3, 12, 13, 23);

    private final HistoryRecordService history = mock(HistoryRecordService.class);
    private final FeatureTrendForecastService forecasts = mock(FeatureTrendForecastService.class);
    private final LotteryReportService reports = mock(LotteryReportService.class);
    private final DrawLearningService learning = mock(DrawLearningService.class);
    private final DrawReviewService service = new DrawReviewService(history, forecasts, reports, learning);

    @Test
    void evaluatesSavedCandidatesAndExcludesPredictionsAtOrAfterDrawTime() {
        var draw = draw();
        when(history.getLatestRecords(31)).thenReturn(List.of(draw));
        when(forecasts.findPeriod(PERIOD)).thenReturn(List.of(
            forecast("redZones", List.of("2:2:2", "3:2:1"), BEFORE),
            forecast("redSum", List.of("54"), BEFORE),
            forecast("blueParity", List.of("奇"), BEFORE),
            forecast("redSpan", List.of("22"), CUTOFF),
            forecast("blueTemperature", List.of("热"), BEFORE)));

        var review = service.find(null).orElseThrow();
        assertThat(review.draw().reds()).isEqualTo(WINNING_REDS);
        assertThat(review.features()).filteredOn(feature -> feature.code().equals("redZones"))
            .singleElement().satisfies(feature -> {
                assertThat(feature.actual()).isEqualTo("3:2:1");
                assertThat(feature.status()).isEqualTo(DrawReview.FeatureStatus.ALTERNATE_HIT);
            });
        assertThat(review.featureSummary())
            .isEqualTo(new DrawReview.FeatureSummary(1, 1, 1, 12, 1));
    }

    @Test
    void countsEachComplexBetAndKeepsTheOriginalTicketOutsideRecommendationTotals() {
        when(history.getLatestRecords(31)).thenReturn(List.of(draw()));
        var report = new LotteryAdjustViewBo();
        report.setTargetPeriod(PERIOD);
        report.setFileName("adjust_test.json");
        report.setGeneratedAt(BEFORE);
        var ticket = new AdjustedTicket();
        ticket.setOriginalRedBalls(List.of(1, 2, 4, 5, 6, 7));
        ticket.setOriginalBlueBall(4);
        ticket.setAdjustedRedBalls(WINNING_REDS);
        ticket.setAdjustedBlueBall(4);
        report.setAdjustedTickets(List.of(ticket));
        var complex = new ComplexTicket();
        complex.setRedBalls(List.of(1, 2, 3, 12, 13, 23, 33));
        complex.setBlueBalls(List.of(4, 5));
        var finalRecommendation = new FinalRecommendation();
        finalRecommendation.setComplexTicket(complex);
        report.setFinalRecommendation(finalRecommendation);
        when(reports.findRecommendations(PERIOD, CUTOFF)).thenReturn(List.of(report));

        var recommendation = service.find(null).orElseThrow().recommendations().getFirst();
        assertThat(recommendation.totalBets()).isEqualTo(15);
        assertThat(recommendation.winningBets()).isEqualTo(15);
        assertThat(recommendation.redTrend()).isNull();
        assertThat(recommendation.tickets().getFirst().original().bestPrize()).isEqualTo(6);
        assertThat(recommendation.tickets().getLast().result().prizes()).containsExactly(
            new DrawReview.PrizeCount(1, 1), new DrawReview.PrizeCount(2, 1),
            new DrawReview.PrizeCount(3, 6), new DrawReview.PrizeCount(4, 6));
    }

    @Test
    void reviewsTheSavedRedTrendCandidatesAgainstTheDraw() {
        when(history.getLatestRecords(31)).thenReturn(List.of(draw()));
        var report = ordinaryRecommendation();
        report.setNumberTrendObservations(List.of(new NumberTrendObservation(Ball.RED, 13),
            new NumberTrendObservation(Ball.RED, 20), new NumberTrendObservation(Ball.RED, 2),
            new NumberTrendObservation(Ball.BLUE, 4), new NumberTrendObservation(Ball.RED, 13)));
        when(reports.findRecommendations(PERIOD, CUTOFF)).thenReturn(List.of(report));

        var review = service.find(null).orElseThrow();
        var reloaded = JSON.parseObject(JSON.toJSONString(review), DrawReview.class);
        assertThat(reloaded.recommendations().getFirst().redTrend())
            .isEqualTo(new DrawReview.RedTrend(List.of(13, 20, 2), List.of(13, 2)));
    }

    @Test
    void restoresTrendHitsForAnArchivedReviewFromItsOriginalRecommendationFile() {
        var actual = new DrawReview.Draw(PERIOD, draw().getOpenDate(), WINNING_REDS, 4);
        var report = ordinaryRecommendation();
        var recommendation = RecommendationReview.evaluate(actual, report).orElseThrow();
        var archived = new DrawReview(actual, new DrawReview.FeatureSummary(0, 0, 0, 0, 0), List.of(),
            List.of(recommendation));
        report.setNumberTrendObservations(List.of(new NumberTrendObservation(Ball.RED, 2),
            new NumberTrendObservation(Ball.RED, 20)));
        when(learning.archivedReview(PERIOD)).thenReturn(Optional.of(archived));
        when(reports.findRecommendation(report.getFileName(), PERIOD, CUTOFF)).thenReturn(Optional.of(report));

        var restored = service.find(PERIOD).orElseThrow().recommendations().getFirst();
        assertThat(restored.redTrend()).isEqualTo(new DrawReview.RedTrend(List.of(2, 20), List.of(2)));
        assertThat(restored.tickets()).isEqualTo(recommendation.tickets());
        assertThat(recommendation.redTrend()).isNull();
    }

    private static LotteryAdjustViewBo ordinaryRecommendation() {
        var report = new LotteryAdjustViewBo();
        report.setTargetPeriod(PERIOD);
        report.setSourcePeriod("2026131");
        report.setFileName("adjust_20261119211459.json");
        report.setGeneratedAt(BEFORE);
        var ticket = new AdjustedTicket();
        ticket.setCategory(TicketCategory.RECOMMENDED);
        ticket.setAdjustedRedBalls(List.of(2, 8, 12, 19, 20, 23));
        ticket.setAdjustedBlueBall(4);
        report.setAdjustedTickets(List.of(ticket));
        return report;
    }

    private static Summary forecast(String code, List<String> values, Instant generatedAt) {
        return new Summary("2026131", PERIOD, code, code, code.startsWith("blue"), "test", values, generatedAt);
    }

    private static HistoryRecord draw() {
        return HistoryRecord.builder().period(PERIOD).openDate(LocalDate.of(2026, 11, 19))
            .num1(1).num2(2).num3(3).num4(12).num5(13).num6(23).special(4).build();
    }
}
