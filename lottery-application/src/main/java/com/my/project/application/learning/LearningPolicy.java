package com.my.project.application.learning;

import com.my.project.application.review.DrawReview;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class LearningPolicy {
    static final int WINDOW = 30;
    static final String SWAP = "SWAP_TOP_TWO";
    static final String NUMBERS = "numbers";
    private static final int MIN_OBSERVATIONS = 15;
    private static final int MIN_RANK_ADVANTAGE = 3;
    private static final double FEATURE_MIN_LIFT = 0.10;
    private static final double NUMBER_MIN_LIFT = 0.01;
    private static final double MAX_P_VALUE = 0.05;

    private LearningPolicy() { }

    record Version(int number, String change) { }

    record Progress(int periods, int wins, int losses, double sumDelta) {
        static Progress empty() { return new Progress(0, 0, 0, 0); }

        Progress add(double delta) {
            return new Progress(periods + 1, wins + (delta > 1e-9 ? 1 : 0),
                losses + (delta < -1e-9 ? 1 : 0), sumDelta + delta);
        }
    }

    record Decision(int version, String period, boolean promoted) { }

    record Track(Version active, Version trial, Progress progress, Decision lastDecision) {
        static Track initial() { return new Track(new Version(1, null), null, Progress.empty(), null); }

        Track start(String change) {
            int next = lastDecision == null ? active.number() + 1 : lastDecision.version() + 1;
            return new Track(active, new Version(next, change), Progress.empty(), lastDecision);
        }
    }

    static Track record(String scope, Track track, double delta, String period) {
        var progress = track.progress().add(delta);
        if (progress.periods() < WINDOW) return new Track(track.active(), track.trial(), progress, track.lastDecision());

        // 先约定样本数和最小提升，再用同期开奖的胜负做单侧精确符号检验。
        double minimumLift = NUMBERS.equals(scope) ? NUMBER_MIN_LIFT : FEATURE_MIN_LIFT;
        boolean promoted = progress.sumDelta() / WINDOW >= minimumLift
            && signTest(progress.wins(), progress.losses()) <= MAX_P_VALUE;
        return new Track(promoted ? track.trial() : track.active(), null, progress,
            new Decision(track.trial().number(), period, promoted));
    }

    static void propose(Map<String, Track> tracks, List<DrawReview> recent) {
        Map<String, Counts> counts = new TreeMap<>();
        var numberTrack = tracks.getOrDefault(NUMBERS, Track.initial());
        Map<String, Counts> numberCounts = new TreeMap<>();
        for (var review : recent) {
            for (var feature : review.features()) {
                if (feature.actual() == null || feature.candidates().isEmpty()) continue;
                var track = tracks.getOrDefault(feature.code(), Track.initial());
                if (feature.candidates().size() >= 2 && afterDecision(track, review.draw().period())) {
                    counts.computeIfAbsent(feature.code(), ignored -> new Counts()).add(feature.matchedRank());
                }
                if (afterDecision(numberTrack, review.draw().period())) {
                    numberCounts.computeIfAbsent(feature.code(), ignored -> new Counts()).add(feature.matchedRank());
                }
            }
        }
        counts.forEach((code, score) -> {
            var track = tracks.getOrDefault(code, Track.initial());
            if (track.trial() == null && score.periods >= MIN_OBSERVATIONS
                && score.second - score.first >= MIN_RANK_ADVANTAGE) {
                tracks.put(code, track.start(track.active().change() == null ? SWAP : null));
            }
        });
        if (numberTrack.trial() != null) return;
        numberCounts.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(numberTrack.active().change()))
            .filter(entry -> entry.getValue().periods >= MIN_OBSERVATIONS && entry.getValue().firstRate() <= 0.30)
            .min(Comparator.comparingDouble(entry -> entry.getValue().firstRate()))
            .ifPresent(entry -> tracks.put(NUMBERS, numberTrack.start(entry.getKey())));
    }

    private static boolean afterDecision(Track track, String period) {
        return track.lastDecision() == null || period.compareTo(track.lastDecision().period()) > 0;
    }

    private static double signTest(int wins, int losses) {
        int trials = wins + losses;
        double probability = Math.scalb(1.0, -trials);
        double tail = 0;
        for (int count = 0; count <= trials; count++) {
            if (count >= wins) tail += probability;
            probability *= (double) (trials - count) / (count + 1);
        }
        return tail;
    }

    private static final class Counts {
        private int periods;
        private int first;
        private int second;

        void add(Integer rank) {
            periods++;
            if (Integer.valueOf(1).equals(rank)) first++;
            if (Integer.valueOf(2).equals(rank)) second++;
        }

        double firstRate() { return (double) first / periods; }
    }
}
