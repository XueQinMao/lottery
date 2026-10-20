package com.my.project.application.feature.impl;

import com.my.project.application.event.AdjustCompleteEvent;
import com.my.project.application.feature.FeatureTrendForecastService;
import com.my.project.application.feature.dto.FeatureTrendForecast.Summary;
import com.my.project.application.feature.support.ForecastTargetPeriod;
import com.my.project.application.feature.support.RecommendationFeatureHits;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.learning.DrawLearningService;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.support.NextLotteryDateUtils;
import com.my.project.application.support.SsqCombinationUtils;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.application.support.analyse.KillBallUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.HybridRecommendationInput.Forecast;
import com.my.project.llm.bo.HybridRecommendationInput.OptimizationSeed;
import com.my.project.llm.bo.HybridRecommendationInput.TemperaturePool;
import com.my.project.llm.bo.KillNumberResultBo.KillItemBo;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.service.HybridRecommendationModelService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HybridRecommendationWorkflow {
    private static final int HISTORY_LIMIT = 100_000;
    private static final int MIN_KILL_HISTORY = 10;

    private final HistoryRecordService history;
    private final ForecastTargetPeriod targetPeriods;
    private final FeatureTrendForecastService forecasts;
    private final PredictCacheService cache;
    private final HybridRecommendationModelService model;
    private final LotteryReportService reports;
    private final DrawLearningService learning;
    private final RecommendationNumberTrendService numberTrends;

    public LotteryAdjustViewBo recommend(boolean useNumberTrend, Runnable onGenerating) {
        var records = history.getLatestRecords(HISTORY_LIMIT);
        Assert.notEmpty(records, "缺少历史开奖记录");
        var cutoff = records.getFirst();
        var targetPeriod = targetPeriods.after(cutoff);
        var candidates = forecasts.findPeriod(targetPeriod).stream()
            .filter(row -> cutoff.getPeriod().equals(row.sourcePeriod()) && !row.values().isEmpty())
            .collect(Collectors.toMap(Summary::featureCode, Summary::values));
        Assert.notEmpty(candidates, "请先生成 " + targetPeriod + " 期特征预测");
        var drawDate = NextLotteryDateUtils.nextDrawDateAfter(cutoff.getOpenDate());
        var observations = useNumberTrend ? numberTrends.candidates(records) : List.<NumberTrendObservation>of();
        onGenerating.run();
        var input = prepareInput(targetPeriod, drawDate, candidates, records, observations);
        var result = learning.publishNumbers(cutoff.getPeriod(), input, model.recommend(input));
        var view = new LotteryAdjustViewBo();
        view.setTargetPeriod(targetPeriod);
        view.setSourcePeriod(cutoff.getPeriod());
        if (useNumberTrend) view.setNumberTrendObservations(observations);
        view.setAdjustedTickets(result.getAdjustedTickets());
        view.setFinalRecommendation(result.getFinalRecommendation());
        view.setAdjustedTicketHits(result.getAdjustedTickets().stream().map(ticket ->
            RecommendationFeatureHits.single(ticket.getAdjustedRedBalls(), ticket.getAdjustedBlueBall(), input)).toList());
        view.setComplexHits(RecommendationFeatureHits.complex(result.getFinalRecommendation().getComplexTicket(), input));

        AdjustCompleteEvent.of(this, result, drawDate).publish();
        return reports.saveRecommendation(view);
    }

    private HybridRecommendationInput prepareInput(String targetPeriod, LocalDate drawDate,
            Map<String, List<String>> candidates, List<HistoryRecord> records, List<NumberTrendObservation> observations) {
        var features = Arrays.stream(FeatureTrendKind.values()).map(kind -> new Forecast(kind.code(),
            (kind.blue() ? "蓝球" : "红球") + kind.label(), kind.valueHint(), candidates.getOrDefault(kind.code(), List.of()))).toList();
        var temperature = ColdHotAnalysisUtils.calculate(records);
        var red = new TemperaturePool(temperature.getRedColdBalls(), temperature.getRedWarmBalls(), temperature.getRedHotBalls());
        var blue = new TemperaturePool(temperature.getBlueColdBalls(), temperature.getBlueWarmBalls(), temperature.getBlueHotBalls());

        return new HybridRecommendationInput(targetPeriod, features, red, blue, killSuggestions(records, drawDate),
            optimizationSeed(drawDate), observations.isEmpty() ? null : observations);
    }

    private static Map<String, List<Integer>> killSuggestions(List<HistoryRecord> records, LocalDate drawDate) {
        // 现有蓝球规则需要至少 10 期历史。
        if (records.size() < MIN_KILL_HISTORY) return Map.of();
        var kills = KillBallUtils.calculate(records, null, drawDate);
        Map<String, List<Integer>> suggestions = new LinkedHashMap<>();
        addKillSuggestions(suggestions, "red", kills.getHardKillRed(), 33, 6);
        addKillSuggestions(suggestions, "blue", kills.getHardKillBlue(), 16, 1);
        return suggestions;
    }

    private OptimizationSeed optimizationSeed(LocalDate drawDate) {
        var keys = cache.sampleTicketKeys(drawDate, 1);
        if (keys.isEmpty()) {
            var random = SsqCombinationUtils.generateRandomDraw();
            return new OptimizationSeed(random.getRedBalls(), random.getBlueBall(), false);
        }
        var parts = keys.getFirst().split("\\|");
        var red = Arrays.stream(parts[1].split(",")).map(Integer::valueOf).toList();
        return new OptimizationSeed(red, Integer.parseInt(parts[2]), true);
    }

    private static void addKillSuggestions(Map<String, List<Integer>> suggestions, String color,
            List<KillItemBo> items, int upper, int minimumAvailable) {
        var numbers = items.stream().map(KillItemBo::getBall).filter(Objects::nonNull)
            .filter(ball -> ball >= 1 && ball <= upper).distinct().sorted().toList();
        // 规则覆盖过宽时省略该色，给特征选号留足可用号码。
        if (!numbers.isEmpty() && upper - numbers.size() >= minimumAvailable) suggestions.put(color, numbers);
    }
}
