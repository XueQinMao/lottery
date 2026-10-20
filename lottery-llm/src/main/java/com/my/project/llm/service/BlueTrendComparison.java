package com.my.project.llm.service;

import com.my.project.llm.service.NumberTrendModelService.Draw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

record BlueTrendComparison(List<Integer> currentNumbers, List<Integer> currentSteps, List<Match> matches) {
    private static final int WINDOW_SIZE = 4;
    private static final int MAX_MATCHES = 3;

    static BlueTrendComparison compare(List<Draw> draws) {
        int currentStart = draws.size() - WINDOW_SIZE;
        if (currentStart < 0) {
            return new BlueTrendComparison(draws.stream().map(Draw::blue).toList(), List.of(), List.of());
        }
        var current = numbers(draws, currentStart);
        var currentSteps = steps(current);
        var matches = new ArrayList<Match>();
        for (int start = 0; start + WINDOW_SIZE < currentStart; start++) {
            var historical = numbers(draws, start);
            var historicalSteps = steps(historical);
            if (!sameDirections(currentSteps, historicalSteps)) continue;
            var end = draws.get(start + WINDOW_SIZE - 1);
            var next = draws.get(start + WINDOW_SIZE);
            int distance = IntStream.range(0, currentSteps.size()).map(index ->
                Math.abs(currentSteps.get(index) - historicalSteps.get(index))).sum();
            matches.add(new Match(draws.get(start).period(), end.period(), historical, historicalSteps,
                distance, next.period(), next.blue(), next.blue() - end.blue()));
        }
        var closest = matches.stream()
            .sorted(Comparator.comparingInt(Match::stepDistance).thenComparing(Match::startPeriod, Comparator.reverseOrder()))
            .limit(MAX_MATCHES).toList();
        return new BlueTrendComparison(current, currentSteps, closest);
    }

    private static List<Integer> numbers(List<Draw> draws, int start) {
        return IntStream.range(start, start + WINDOW_SIZE).mapToObj(index -> draws.get(index).blue()).toList();
    }

    private static List<Integer> steps(List<Integer> numbers) {
        return IntStream.range(1, numbers.size()).mapToObj(index -> numbers.get(index) - numbers.get(index - 1)).toList();
    }

    private static boolean sameDirections(List<Integer> current, List<Integer> historical) {
        return IntStream.range(0, current.size()).allMatch(index ->
            Integer.signum(current.get(index)) == Integer.signum(historical.get(index)));
    }

    record Match(String startPeriod, String endPeriod, List<Integer> numbers, List<Integer> steps,
                 int stepDistance, String nextPeriod, int nextBlue, int nextStep) { }
}
