package com.my.project.application.review;

import com.my.project.application.feature.FeatureTrendForecastService;
import com.my.project.application.feature.dto.FeatureTrendForecast.Summary;
import com.my.project.application.feature.support.ForecastTargetPeriod;
import com.my.project.application.feature.support.RecommendationFeatureHits;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.support.DrawTrendCalculator;
import com.my.project.application.learning.DrawDeadline;
import com.my.project.application.learning.DrawLearningService;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.review.DrawReview.Draw;
import com.my.project.application.review.DrawReview.Feature;
import com.my.project.application.review.DrawReview.FeatureStatus;
import com.my.project.application.review.DrawReview.FeatureSummary;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.HybridRecommendationInput.TemperaturePool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DrawReviewService {
    private static final int DRAW_AND_HISTORY = DrawTrendCalculator.COLD_HOT_WINDOW + 1;

    private final HistoryRecordService history;
    private final FeatureTrendForecastService forecasts;
    private final LotteryReportService reports;
    private final DrawLearningService learning;

    public Optional<DrawReview> find(String period) {
        if (period != null) {
            ForecastTargetPeriod.validate(period);
            var archived = learning.archivedReview(period).map(this::restoreArchivedRedTrends);
            if (archived.isPresent()) return archived;
        }
        List<HistoryRecord> records;
        if (period == null) {
            records = history.getLatestRecords(DRAW_AND_HISTORY);
        } else {
            records = history.findRecordsEndingAt(period, DRAW_AND_HISTORY);
        }
        if (records.isEmpty()) return Optional.empty();

        var record = records.getFirst();
        if (period == null) {
            var archived = learning.archivedReview(record.getPeriod()).map(this::restoreArchivedRedTrends);
            if (archived.isPresent()) return archived;
        }
        var reds = IntStream.of(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(),
            record.getNum5(), record.getNum6()).sorted().boxed().toList();
        var draw = new Draw(record.getPeriod(), record.getOpenDate(), reds, record.getSpecial());
        var cutoff = DrawDeadline.at(draw.openDate());
        var features = reviewFeatures(draw, records, cutoff);
        var recommendations = reports.findRecommendations(draw.period(), cutoff).stream()
            .flatMap(report -> RecommendationReview.evaluate(draw, report).stream()).toList();
        return Optional.of(new DrawReview(draw, summarize(features), features, recommendations));
    }

    private DrawReview restoreArchivedRedTrends(DrawReview review) {
        var draw = review.draw();
        var cutoff = DrawDeadline.at(draw.openDate());
        var recommendations = review.recommendations().stream().map(item -> {
            if (item.redTrend() != null) return item;
            return reports.findRecommendation(item.fileName(), draw.period(), cutoff)
                .map(report -> RecommendationReview.redTrend(draw, report))
                .map(item::withRedTrend).orElse(item);
        }).toList();
        return new DrawReview(draw, review.featureSummary(), review.features(), recommendations, review.learning());
    }

    private List<Feature> reviewFeatures(Draw draw, List<HistoryRecord> records, Instant cutoff) {
        var byFeature = forecasts.findPeriod(draw.period()).stream()
            .filter(forecast -> forecast.generatedAt() != null && forecast.generatedAt().isBefore(cutoff))
            .filter(forecast -> forecast.sourcePeriod().compareTo(draw.period()) < 0)
            .collect(Collectors.toMap(Summary::featureCode, Function.identity()));
        var bands = records.size() == DRAW_AND_HISTORY
            ? ColdHotAnalysisUtils.calculate(records.stream().skip(1).toList()) : null;
        var redPool = bands == null ? null : new TemperaturePool(bands.getRedColdBalls(), bands.getRedWarmBalls(), bands.getRedHotBalls());
        var bluePool = bands == null ? null : new TemperaturePool(bands.getBlueColdBalls(), bands.getBlueWarmBalls(), bands.getBlueHotBalls());
        return Arrays.stream(FeatureTrendKind.values()).map(kind -> {
            var actual = RecommendationFeatureHits.actual(kind, draw.reds(), draw.blue(), redPool, bluePool);
            return evaluate(kind, actual, byFeature.get(kind.code()));
        }).toList();
    }

    private static Feature evaluate(FeatureTrendKind kind, String actual, Summary forecast) {
        var candidates = forecast == null ? List.<String>of() : forecast.values();
        int rank = actual == null || candidates.isEmpty() ? 0 : candidates.indexOf(actual) + 1;
        FeatureStatus status;
        if (candidates.isEmpty()) status = FeatureStatus.NOT_PREDICTED;
        else if (actual == null) status = FeatureStatus.NO_ACTUAL;
        else if (rank == 0) status = FeatureStatus.MISS;
        else status = rank == 1 ? FeatureStatus.MAIN_HIT : FeatureStatus.ALTERNATE_HIT;
        return new Feature(kind.code(), kind.label(), kind.blue(), actual, candidates, rank == 0 ? null : rank, status,
            forecast == null ? null : forecast.sourcePeriod(), forecast == null ? null : forecast.model(),
            forecast == null ? null : forecast.generatedAt());
    }

    private static FeatureSummary summarize(List<Feature> features) {
        var totals = features.stream().collect(Collectors.groupingBy(Feature::status, Collectors.summingInt(feature -> 1)));
        return new FeatureSummary(totals.getOrDefault(FeatureStatus.MAIN_HIT, 0),
            totals.getOrDefault(FeatureStatus.ALTERNATE_HIT, 0), totals.getOrDefault(FeatureStatus.MISS, 0),
            totals.getOrDefault(FeatureStatus.NOT_PREDICTED, 0), totals.getOrDefault(FeatureStatus.NO_ACTUAL, 0));
    }
}
