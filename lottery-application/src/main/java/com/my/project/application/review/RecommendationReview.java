package com.my.project.application.review;

import com.my.project.application.enums.PrizeLevelEnum;
import com.my.project.application.review.DrawReview.Draw;
import com.my.project.application.review.DrawReview.PrizeCount;
import com.my.project.application.review.DrawReview.Recommendation;
import com.my.project.application.review.DrawReview.RedTrend;
import com.my.project.application.review.DrawReview.Ticket;
import com.my.project.application.review.DrawReview.TicketKind;
import com.my.project.application.review.DrawReview.TicketResult;
import com.my.project.application.support.SsqCombinationUtils;
import com.my.project.llm.bo.LotteryAdjustRespBo.AdjustedTicket;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class RecommendationReview {
    private static final int RED_COUNT = 6;
    private static final int MAX_RED = 33;
    private static final int MAX_BLUE = 16;

    private RecommendationReview() { }

    public static Optional<Recommendation> evaluate(Draw draw, LotteryAdjustViewBo report) {
        var tickets = new ArrayList<Ticket>();
        if (report.getAdjustedTickets() != null) {
            report.getAdjustedTickets().forEach(ticket -> adjusted(draw, ticket).ifPresent(tickets::add));
        }
        var finalRecommendation = report.getFinalRecommendation();
        if (finalRecommendation != null) {
            if (finalRecommendation.getSingleTickets() != null) {
                for (var ticket : finalRecommendation.getSingleTickets()) {
                    if (ticket == null) continue;
                    single(draw, ticket.getRedBalls(), ticket.getBlueBall())
                        .map(result -> new Ticket(TicketKind.SINGLE, ticket.getName(), result, null))
                        .ifPresent(tickets::add);
                }
            }
            var complex = finalRecommendation.getComplexTicket();
            if (complex != null && valid(complex.getRedBalls(), RED_COUNT, MAX_RED)
                && valid(complex.getBlueBalls(), 1, MAX_BLUE)) {
                tickets.add(new Ticket(TicketKind.COMPLEX, complex.getName(),
                    evaluateNumbers(draw, complex.getRedBalls(), complex.getBlueBalls()), null));
            }
        }
        if (tickets.isEmpty()) return Optional.empty();

        long totalBets = tickets.stream().mapToLong(ticket -> ticket.result().totalBets()).sum();
        long winningBets = tickets.stream().mapToLong(ticket -> ticket.result().winningBets()).sum();
        int bestPrize = tickets.stream().mapToInt(ticket -> ticket.result().bestPrize())
            .filter(level -> level > 0).min().orElse(0);
        return Optional.of(new Recommendation(report.getFileName(), report.getGeneratedAt(), report.getSourcePeriod(),
            List.copyOf(tickets), totalBets, winningBets, bestPrize, redTrend(draw, report)));
    }

    static RedTrend redTrend(Draw draw, LotteryAdjustViewBo report) {
        var observations = report.getNumberTrendObservations();
        if (observations == null) return null;
        var candidates = observations.stream().filter(Objects::nonNull)
            .filter(observation -> observation.ball() == Ball.RED)
            .map(NumberTrendObservation::number).filter(number -> number >= 1 && number <= MAX_RED)
            .distinct().toList();
        return new RedTrend(candidates, candidates.stream().filter(draw.reds()::contains).toList());
    }

    private static Optional<Ticket> adjusted(Draw draw, AdjustedTicket ticket) {
        if (ticket == null) return Optional.empty();
        var original = single(draw, ticket.getOriginalRedBalls(), ticket.getOriginalBlueBall()).orElse(null);
        var kind = ticket.getCategory() == null ? (original == null ? TicketKind.SINGLE : TicketKind.OPTIMIZED)
            : TicketKind.valueOf(ticket.getCategory().name());
        return single(draw, ticket.getAdjustedRedBalls(), ticket.getAdjustedBlueBall())
            .map(result -> new Ticket(kind, null, result, original));
    }

    private static Optional<TicketResult> single(Draw draw, List<Integer> reds, Integer blue) {
        if (reds == null || reds.size() != RED_COUNT || !valid(reds, RED_COUNT, MAX_RED)
            || blue == null || blue < 1 || blue > MAX_BLUE) return Optional.empty();
        return Optional.of(evaluateNumbers(draw, reds, List.of(blue)));
    }

    private static boolean valid(List<Integer> numbers, int minSize, int maxNumber) {
        return numbers != null && numbers.size() >= minSize && numbers.size() <= maxNumber
            && numbers.stream().allMatch(number -> number != null && number >= 1 && number <= maxNumber)
            && numbers.stream().distinct().count() == numbers.size();
    }

    private static TicketResult evaluateNumbers(Draw draw, List<Integer> reds, List<Integer> blues) {
        var sortedReds = reds.stream().sorted().toList();
        var sortedBlues = blues.stream().sorted().toList();
        var redHits = sortedReds.stream().filter(draw.reds()::contains).toList();
        boolean blueHit = blues.contains(draw.blue());
        int matchingReds = redHits.size();
        Map<Integer, Long> prizeBets = new TreeMap<>();
        for (int hits = 0; hits <= matchingReds; hits++) {
            long combinations = choose(matchingReds, hits) * choose(reds.size() - matchingReds, RED_COUNT - hits);
            if (blueHit) countPrize(prizeBets, hits, true, combinations);
            countPrize(prizeBets, hits, false, combinations * (blues.size() - (blueHit ? 1 : 0)));
        }
        var prizes = prizeBets.entrySet().stream().map(entry -> new PrizeCount(entry.getKey(), entry.getValue())).toList();
        return new TicketResult(sortedReds, sortedBlues, redHits, blueHit, choose(reds.size(), RED_COUNT) * blues.size(),
            prizes.stream().mapToLong(PrizeCount::bets).sum(), prizes.isEmpty() ? 0 : prizes.getFirst().level(), prizes);
    }

    private static long choose(int numbers, int picks) {
        return picks > numbers ? 0 : SsqCombinationUtils.combination(numbers, picks);
    }

    private static void countPrize(Map<Integer, Long> prizeBets, int redHits, boolean blueHit, long bets) {
        if (bets == 0) return;
        var prize = PrizeLevelEnum.checkPrize(redHits, blueHit);
        if (prize.isHit()) prizeBets.merge(prize.getLevel(), bets, Long::sum);
    }
}
