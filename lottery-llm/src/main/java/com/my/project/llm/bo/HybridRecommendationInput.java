package com.my.project.llm.bo;

import com.my.project.llm.bo.NumberTrendObservation.Ball;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record HybridRecommendationInput(String targetPeriod, List<Forecast> forecasts,
        TemperaturePool redTemperature, TemperaturePool blueTemperature,
        Map<String, List<Integer>> killSuggestions, OptimizationSeed optimizationSeed,
        List<NumberTrendObservation> numberTrendObservations) {

    public HybridRecommendationInput(String targetPeriod, List<Forecast> forecasts,
            TemperaturePool redTemperature, TemperaturePool blueTemperature,
            Map<String, List<Integer>> killSuggestions, OptimizationSeed optimizationSeed) {
        this(targetPeriod, forecasts, redTemperature, blueTemperature, killSuggestions, optimizationSeed, null);
    }

    public static final int SINGLE_COUNT = 5;
    public static final int RECOMMENDED_COUNT = 2;
    public static final int FULL_MATCH_COUNT = 2;

    public Set<Integer> redTrendNumbers() {
        if (numberTrendObservations == null) return Set.of();
        return numberTrendObservations.stream()
            .filter(observation -> observation.ball() == Ball.RED)
            .map(NumberTrendObservation::number)
            .collect(Collectors.toUnmodifiableSet());
    }

    public record Forecast(String code, String label, String format, List<String> values) { }

    public record TemperaturePool(List<Integer> cold, List<Integer> warm, List<Integer> hot) { }

    public record OptimizationSeed(List<Integer> redBalls, int blueBall, boolean fromCache) { }
}
