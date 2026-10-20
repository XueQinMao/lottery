package com.my.project.application.learning;

import com.my.project.application.learning.LearningReport.NumberScore;
import com.my.project.application.review.DrawReview.Draw;
import com.my.project.application.review.RecommendationReview;
import com.my.project.application.support.SsqCombinationUtils;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAdjustRespBo.AdjustedTicket;
import com.my.project.llm.bo.LotteryAdjustRespBo.TicketCategory;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.application.feature.support.RecommendationFeatureHits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

final class NumberLearning {
    private static final int POOL_SIZE = 256;

    private NumberLearning() { }

    static Variants variants(HybridRecommendationInput input, LotteryAdjustRespBo original,
                             String activeChange, LearningPolicy.Version trial, long seed) {
        if (activeChange == null && trial == null) return new Variants(original, null);
        var tickets = original.getAdjustedTickets();
        int slot = IntStream.range(0, tickets.size())
            .filter(index -> tickets.get(index).getCategory() == TicketCategory.RECOMMENDED).findFirst().orElse(-1);
        if (slot < 0) return new Variants(original, trial == null ? null : original);

        var originalTicket = tickets.get(slot);
        var originalCandidate = candidate(originalTicket.getAdjustedRedBalls(), originalTicket.getAdjustedBlueBall(), input);
        Set<Ticket> occupied = new LinkedHashSet<>();
        for (var ticket : tickets) occupied.add(new Ticket(ticket.getAdjustedRedBalls(), ticket.getAdjustedBlueBall()));
        var pool = new ArrayList<Candidate>();
        pool.add(originalCandidate);
        var trendReds = input.redTrendNumbers();
        for (var ticket : sample(POOL_SIZE, seed)) {
            if (occupied.contains(ticket) || trendReds.containsAll(ticket.reds())) continue;
            var candidate = candidate(ticket.reds(), ticket.blue(), input);
            // 同一批可比候选保持原始特征得分相同。新旧策略只因被降权的特征而产生差异。
            if (candidate.score() == originalCandidate.score()) pool.add(candidate);
        }
        var formal = select(original, slot, pool, activeChange);
        var challenger = trial == null ? null : select(original, slot, pool, trial.change());
        return new Variants(formal, challenger);
    }

    private static Candidate candidate(List<Integer> reds, int blue, HybridRecommendationInput input) {
        var hits = RecommendationFeatureHits.single(reds, blue, input).getHits();
        int score = hits.stream().mapToInt(hit -> points(hit.getHitType())).sum();
        return new Candidate(new Ticket(reds, blue), score, hits.stream().collect(java.util.stream.Collectors.toMap(
            LotteryAdjustViewBo.FeatureHit::getCode, hit -> points(hit.getHitType()))));
    }

    private static int points(String hitType) {
        return switch (hitType) { case "MAIN" -> 2; case "ALT" -> 1; default -> 0; };
    }

    private static LotteryAdjustRespBo select(LotteryAdjustRespBo original, int slot, List<Candidate> pool, String change) {
        if (change == null) return original;
        var selected = pool.stream().max(Comparator.comparingInt(candidate ->
            candidate.score() * 2 - candidate.featurePoints().getOrDefault(change, 0))).orElseThrow();
        if (selected == pool.getFirst()) return original;
        var tickets = new ArrayList<>(original.getAdjustedTickets());
        var previous = tickets.get(slot);
        var replacement = new AdjustedTicket();
        replacement.setId(previous.getId());
        replacement.setCategory(previous.getCategory());
        replacement.setAdjustedRedBalls(selected.ticket().reds());
        replacement.setAdjustedBlueBall(selected.ticket().blue());
        replacement.setReason("根据复盘策略下调指定特征权重后重排候选");
        tickets.set(slot, replacement);
        var result = new LotteryAdjustRespBo();
        result.setAdjustedTickets(List.copyOf(tickets));
        result.setFinalRecommendation(original.getFinalRecommendation());
        result.setConclusion(original.getConclusion());
        return result;
    }

    static NumberScore score(Draw draw, LotteryAdjustRespBo result) {
        var report = new LotteryAdjustViewBo();
        report.setAdjustedTickets(result.getAdjustedTickets());
        report.setFinalRecommendation(result.getFinalRecommendation());
        var review = RecommendationReview.evaluate(draw, report).orElseThrow();
        double totalRedHits = 0;
        long blueHitBets = 0;
        for (var ticket : review.tickets()) {
            var outcome = ticket.result();
            // 每个选中的红球在复式展开中出现的比例都是 6 / 复式红球数。
            totalRedHits += outcome.totalBets() * outcome.redHits().size() * 6.0 / outcome.reds().size();
            if (outcome.blueHit()) blueHitBets += outcome.totalBets() / outcome.blues().size();
        }
        return new NumberScore(review.totalBets(), totalRedHits / review.totalBets(), blueHitBets,
            review.winningBets(), review.bestPrize());
    }

    static NumberScore randomScore(Draw draw, int bets, long seed) {
        var response = new LotteryAdjustRespBo();
        response.setAdjustedTickets(sample(bets, seed).stream().map(ticket -> {
            var single = new AdjustedTicket();
            single.setAdjustedRedBalls(ticket.reds());
            single.setAdjustedBlueBall(ticket.blue());
            return single;
        }).toList());
        return score(draw, response);
    }

    private static List<Ticket> sample(int count, long seed) {
        // 快照在开奖前保存种子和票数，结算时可原样重建等注数的均匀随机参照。
        var random = new Random(seed);
        Set<Long> positions = new LinkedHashSet<>();
        while (positions.size() < count) positions.add(random.nextLong(SsqCombinationUtils.TOTAL_COMBINATIONS));
        return positions.stream().map(SsqCombinationUtils::fromCombinationIndex)
            .map(ticket -> new Ticket(ticket.getRedBalls(), ticket.getBlueBall())).toList();
    }

    record Variants(LotteryAdjustRespBo formal, LotteryAdjustRespBo trial) { }

    private record Ticket(List<Integer> reds, int blue) { }

    private record Candidate(Ticket ticket, int score, java.util.Map<String, Integer> featurePoints) { }
}
