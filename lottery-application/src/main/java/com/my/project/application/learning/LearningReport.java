package com.my.project.application.learning;

import java.util.List;

public record LearningReport(int requiredPeriods, List<Experiment> experiments) {
    public record Experiment(String scope, String change, int formalVersion, int trialVersion, int pairedPeriods,
                             int wins, int losses, double averageLift, String status, Outcome outcome) { }

    public record Outcome(List<String> formalCandidates, List<String> trialCandidates,
                          Integer formalRank, Integer trialRank, NumberScore formalNumbers, NumberScore trialNumbers,
                          NumberScore randomNumbers) { }

    public record NumberScore(long bets, double averageRedHits, long blueHitBets, long winningBets, int bestPrize) { }
}
