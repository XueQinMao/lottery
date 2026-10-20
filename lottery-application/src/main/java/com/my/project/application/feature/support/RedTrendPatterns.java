package com.my.project.application.feature.support;

import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;
import com.my.project.llm.bo.NumberTrendObservation.Evidence;
import com.my.project.llm.service.NumberTrendModelService.Draw;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RedTrendPatterns {
    private static final int MAX_CANDIDATES = 8;
    private static final int MAX_DIAGONAL_STEP = 3;
    private static final List<Integer> NEIGHBOR_STEPS = List.of(-1, 1);

    private final List<Set<Integer>> hits;
    private final int lastRow;
    private final Set<Segment> segments = new LinkedHashSet<>();
    private final Set<Point> anchors = new HashSet<>();
    private final Set<Segment> confirmedDiagonalEnds = new HashSet<>();
    private final Map<Integer, Map<Segment, Projection>> projections = new HashMap<>();

    private RedTrendPatterns(List<List<Integer>> redDraws) {
        hits = redDraws.stream().<Set<Integer>>map(LinkedHashSet::new).toList();
        lastRow = hits.size() - 1;
    }

    public static List<Integer> candidateNumbers(List<List<Integer>> redDraws) {
        return analyze(redDraws).rankedCandidates();
    }

    static Lines calculate(List<Draw> draws) {
        return analyze(draws.stream().map(Draw::reds).toList()).lines(draws);
    }

    private static RedTrendPatterns analyze(List<List<Integer>> redDraws) {
        var patterns = new RedTrendPatterns(redDraws);
        patterns.findRepeatsAndRuns();
        patterns.bridgeAnchoredRuns();
        patterns.findThreeRowDiagonals();
        patterns.findNeighborDiagonals();
        return patterns;
    }

    private void findRepeatsAndRuns() {
        for (int row = 0; row <= lastRow; row++) {
            for (int number : hits.get(row)) {
                var current = new Point(row, number);
                int gap = hit(row - 1, number) ? 1 : hit(row - 2, number) ? 2 : 0;
                if (gap > 0) {
                    var previous = new Point(row - gap, number);
                    connectAnchor(previous, current);
                    if (row + gap == lastRow + 1) {
                        project(current, number, Kind.REPEAT, List.of(previous, current), number);
                    }
                }
                boolean right = hit(row, number + 1);
                if (right) connectAnchor(current, new Point(row, number + 1));
                if (row == lastRow) {
                    boolean left = hit(row, number - 1);
                    if (right && !left) {
                        project(current, number - 1, Kind.RUN, List.of(current, new Point(row, number + 1)), number);
                    }
                    if (left && !right) {
                        project(current, number + 1, Kind.RUN, List.of(new Point(row, number - 1), current), number);
                    }
                }
            }
        }
    }

    private void bridgeAnchoredRuns() {
        for (int row = 0; row <= lastRow; row++) {
            for (int number : hits.get(row)) {
                var from = new Point(row, number);
                var to = new Point(row, number + 2);
                if (!hit(row, number + 1) && hit(row, to.number()) && anchored(from, to)) {
                    segments.add(new Segment(from, to));
                }
            }
        }
    }

    private void findThreeRowDiagonals() {
        for (int row = 2; row <= lastRow; row++) {
            for (int middle : hits.get(row - 1)) {
                for (int start : hits.get(row - 2)) {
                    int step = middle - start;
                    if (step == 0 || Math.abs(step) > MAX_DIAGONAL_STEP) continue;
                    confirmDiagonal(row, start, middle, middle + step);
                    confirmDiagonal(row, start, middle, start);
                }
            }
        }
    }

    private void confirmDiagonal(int row, int start, int middle, int end) {
        if (!hit(row, end)) return;
        var from = new Point(row - 2, start);
        var turn = new Point(row - 1, middle);
        var to = new Point(row, end);
        var tail = new Segment(turn, to);
        segments.add(new Segment(from, turn));
        segments.add(tail);
        confirmedDiagonalEnds.add(tail);
        if (row == lastRow) {
            project(to, end + middle - start, Kind.DIAGONAL, List.of(from, turn, to), start, middle, end);
        }
    }

    private void findNeighborDiagonals() {
        for (int row = 1; row <= lastRow; row++) {
            for (int number : hits.get(row - 1)) {
                var from = new Point(row - 1, number);
                for (int step : NEIGHBOR_STEPS) {
                    var to = new Point(row, number + step);
                    if (!hit(row, to.number()) || (row != lastRow && !anchored(from, to))) continue;
                    var segment = new Segment(from, to);
                    segments.add(segment);
                    if (row == lastRow && !confirmedDiagonalEnds.contains(segment)) {
                        project(to, to.number() + step, Kind.NEIGHBOR, List.of(from, to), number, to.number());
                    }
                }
            }
        }
    }

    private boolean hit(int row, int number) {
        return row >= 0 && hits.get(row).contains(number);
    }

    private boolean anchored(Point from, Point to) {
        return anchors.contains(from) || anchors.contains(to);
    }

    private void connectAnchor(Point from, Point to) {
        segments.add(new Segment(from, to));
        anchors.add(from);
        anchors.add(to);
    }

    private void project(Point from, int number, Kind kind, List<Point> points, Integer... evidence) {
        if (number < 1 || number > Ball.RED.count()) return;
        var segment = new Segment(from, new Point(lastRow + 1, number));
        projections.computeIfAbsent(number, ignored -> new LinkedHashMap<>())
            .put(segment, new Projection(new Reason(kind, List.of(evidence)), points));
    }

    private List<Integer> rankedCandidates() {
        return projections.keySet().stream().sorted(
            Comparator.<Integer>comparingLong(number -> projections.get(number).values().stream()
                    .map(Projection::reason).distinct().count())
                .reversed().thenComparingInt(Integer::intValue)).limit(MAX_CANDIDATES).toList();
    }

    private Lines lines(List<Draw> draws) {
        var candidates = rankedCandidates();
        var projected = candidates.stream().flatMap(number -> projections.get(number).keySet().stream()).toList();
        var observations = candidates.stream().map(number -> observation(draws, number)).toList();
        return new Lines(List.copyOf(segments), projected, candidates, observations);
    }

    private NumberTrendObservation observation(List<Draw> draws, int number) {
        var projection = projections.get(number).values().stream()
            .max(Comparator.comparingInt(item -> item.points().size())).orElseThrow();
        var points = projection.points();
        var evidence = points.stream().map(point -> new Evidence(draws.get(point.row()).period(), 0, point.number())).toList();
        var sequence = points.stream().map(point -> "%02d".formatted(point.number())).collect(Collectors.joining(" → "));
        String shape = switch (projection.reason().kind()) {
            case REPEAT -> "跨期重号";
            case RUN -> "横向连号延伸";
            case DIAGONAL -> points.getFirst().number() == points.getLast().number() ? "斜线折返" : "斜线延续";
            case NEIGHBOR -> "邻号斜线延续";
        };
        return new NumberTrendObservation(Ball.RED, number, evidence, "%s %s，指向 %02d".formatted(sequence, shape, number));
    }

    record Point(int row, int number) { }
    record Segment(Point from, Point to) { }
    record Lines(List<Segment> history, List<Segment> projections, List<Integer> candidates,
                 List<NumberTrendObservation> observations) { }
    private record Reason(Kind kind, List<Integer> evidence) { }
    private record Projection(Reason reason, List<Point> points) { }
    private enum Kind { REPEAT, RUN, DIAGONAL, NEIGHBOR }
}
