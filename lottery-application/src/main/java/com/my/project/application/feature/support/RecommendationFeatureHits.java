package com.my.project.application.feature.support;

import com.my.project.application.history.support.FeatureStatsCalculator;
import com.my.project.application.support.LotteryFeatureTrendUtils;
import com.my.project.application.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.HybridRecommendationInput.Forecast;
import com.my.project.llm.bo.HybridRecommendationInput.TemperaturePool;
import com.my.project.llm.bo.LotteryAdjustRespBo.ComplexTicket;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureCoverage;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureHit;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureHitSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class RecommendationFeatureHits {
    private RecommendationFeatureHits() { }

    public static FeatureHitSummary single(List<Integer> reds, int blue, HybridRecommendationInput input) {
        var hits = predicted(input.forecasts()).stream().map(feature -> {
            var actual = actual(feature.kind(), reds, blue, input.redTemperature(), input.blueTemperature());
            var forecast = feature.forecast();
            var hit = new FeatureHit();
            hit.setCode(forecast.code());
            hit.setLabel(forecast.label());
            hit.setActual(actual);
            hit.setMainValue(forecast.values().getFirst());
            hit.setAlternatives(forecast.values().stream().skip(1).toList());
            hit.setHitType(hitType(forecast, actual));
            return hit;
        }).toList();
        var summary = new FeatureHitSummary();
        summary.setHits(hits);
        summary.setMainHitCount(countHits(hits, "MAIN"));
        summary.setAltHitCount(countHits(hits, "ALT"));
        summary.setMissCount(countHits(hits, "MISS"));
        return summary;
    }

    public static List<FeatureCoverage> complex(ComplexTicket ticket, HybridRecommendationInput input) {
        var features = predicted(input.forecasts());
        var combinations = new ArrayList<List<Integer>>();
        chooseSix(ticket.getRedBalls(), 0, new ArrayList<>(6), combinations);
        return features.stream().map(feature -> {
            long main = 0;
            long alternate = 0;
            var forecast = feature.forecast();
            for (var reds : combinations) {
                for (int blue : ticket.getBlueBalls()) {
                    switch (hitType(forecast, actual(feature.kind(), reds, blue, input.redTemperature(), input.blueTemperature()))) {
                        case "MAIN" -> main++;
                        case "ALT" -> alternate++;
                        default -> { }
                    }
                }
            }
            return new FeatureCoverage(forecast.code(), forecast.label(), forecast.values().getFirst(),
                forecast.values().stream().skip(1).toList(), main, alternate);
        }).toList();
    }

    private static void chooseSix(List<Integer> reds, int start, List<Integer> selection, List<List<Integer>> output) {
        if (selection.size() == 6) {
            output.add(List.copyOf(selection));
            return;
        }
        int lastStart = reds.size() - (6 - selection.size());
        for (int index = start; index <= lastStart; index++) {
            selection.add(reds.get(index));
            chooseSix(reds, index + 1, selection, output);
            selection.removeLast();
        }
    }

    private static List<Predicted> predicted(List<Forecast> forecasts) {
        return forecasts.stream().filter(forecast -> !forecast.values().isEmpty())
            .map(forecast -> new Predicted(FeatureTrendKind.fromCode(forecast.code()), forecast)).toList();
    }

    private static int countHits(List<FeatureHit> hits, String type) {
        return (int) hits.stream().filter(hit -> type.equals(hit.getHitType())).count();
    }

    private static String hitType(Forecast forecast, String actual) {
        if (forecast.values().getFirst().equals(actual)) return "MAIN";
        return forecast.values().contains(actual) ? "ALT" : "MISS";
    }

    public static String actual(FeatureTrendKind kind, List<Integer> reds, int blue,
                                TemperaturePool redPool, TemperaturePool bluePool) {
        return switch (kind) {
            case RED_ZONES -> LotteryFeatureTrendUtils.extract(reds, FeatureKind.THREE_ZONE);
            case RED_TEMPERATURE -> redPool == null ? null : IntStream.rangeClosed(0, 3).mapToObj(zone -> temperatureCounts(reds,
                redPool, number -> zone == 0 || (number - 1) / 11 + 1 == zone)).collect(Collectors.joining("|"));
            case RED_PARITY -> LotteryFeatureTrendUtils.extract(reds, FeatureKind.ODD_EVEN);
            case RED_ROAD -> LotteryFeatureTrendUtils.extract(reds, FeatureKind.RATIO_012);
            case RED_SUM -> Integer.toString(reds.stream().mapToInt(Integer::intValue).sum());
            case RED_SPAN -> Integer.toString(reds.getLast() - reds.getFirst());
            case RED_SIZE -> ratio(count(reds, number -> number <= 16));
            case RED_PRIME -> ratio(FeatureStatsCalculator.primeCount(reds));
            case RED_HEAD -> road(reds.getFirst());
            case RED_TAIL -> road(reds.getLast());
            case RED_ENDINGS -> IntStream.range(0, 10).mapToObj(digit -> Integer.toString(count(reds, number -> number % 10 == digit)))
                .collect(Collectors.joining(":"));
            case BLUE_TEMPERATURE -> bluePool == null ? null : band(bluePool, blue);
            case BLUE_PARITY -> blue % 2 == 0 ? "偶" : "奇";
            case BLUE_ROAD -> road(blue);
            case BLUE_SIZE -> blue <= 8 ? "小" : "大";
            case BLUE_INTERVAL -> {
                int start = (blue - 1) / 4 * 4 + 1;
                yield start + "-" + (start + 3);
            }
        };
    }

    private static String band(TemperaturePool pool, int number) {
        if (pool.cold().contains(number)) return "冷";
        if (pool.warm().contains(number)) return "温";
        return "热";
    }

    private static String temperatureCounts(List<Integer> reds, TemperaturePool pool, IntPredicate scope) {
        return List.of("冷", "温", "热").stream()
            .map(label -> Integer.toString(count(reds, number -> scope.test(number) && label.equals(band(pool, number)))))
            .collect(Collectors.joining(":"));
    }

    private static int count(List<Integer> reds, IntPredicate condition) {
        return (int) reds.stream().mapToInt(Integer::intValue).filter(condition).count();
    }

    private static String ratio(int first) { return first + ":" + (6 - first); }

    private static String road(int ball) { return ball % 3 + "路"; }

    private record Predicted(FeatureTrendKind kind, Forecast forecast) { }
}
