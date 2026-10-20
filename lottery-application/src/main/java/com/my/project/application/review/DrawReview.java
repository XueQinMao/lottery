package com.my.project.application.review;

import com.my.project.application.learning.LearningReport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DrawReview(Draw draw, FeatureSummary featureSummary, List<Feature> features,
                         List<Recommendation> recommendations, LearningReport learning) {

    public DrawReview(Draw draw, FeatureSummary featureSummary, List<Feature> features, List<Recommendation> recommendations) {
        this(draw, featureSummary, features, recommendations, null);
    }

    public DrawReview withLearning(LearningReport report) {
        return new DrawReview(draw, featureSummary, features, recommendations, report);
    }

    public record Draw(String period, LocalDate openDate, List<Integer> reds, int blue) { }

    public enum FeatureStatus { MAIN_HIT, ALTERNATE_HIT, MISS, NOT_PREDICTED, NO_ACTUAL }

    public record Feature(String code, String label, boolean blue, String actual, List<String> candidates,
                          Integer matchedRank, FeatureStatus status, String sourcePeriod, String model,
                          Instant generatedAt) { }

    public record FeatureSummary(int mainHits, int alternateHits, int misses, int notPredicted, int noActual) { }

    public record Recommendation(String fileName, Instant generatedAt, String sourcePeriod,
                                 List<Ticket> tickets, long totalBets, long winningBets, int bestPrize,
                                 RedTrend redTrend) {
        public Recommendation withRedTrend(RedTrend trend) {
            return new Recommendation(fileName, generatedAt, sourcePeriod, tickets, totalBets, winningBets,
                bestPrize, trend);
        }
    }

    public record RedTrend(List<Integer> candidates, List<Integer> hits) {
        public RedTrend {
            candidates = List.copyOf(candidates);
            hits = List.copyOf(hits);
        }
    }

    public enum TicketKind { OPTIMIZED, RECOMMENDED, FULL_MATCH, SINGLE, COMPLEX }

    public record Ticket(TicketKind kind, String label, TicketResult result, TicketResult original) { }

    public record TicketResult(List<Integer> reds, List<Integer> blues, List<Integer> redHits,
                               boolean blueHit, long totalBets, long winningBets, int bestPrize,
                               List<PrizeCount> prizes) { }

    public record PrizeCount(int level, long bets) { }
}
