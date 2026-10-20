package com.my.project.application.learning;

import com.my.project.application.review.DrawReview.Draw;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.HybridRecommendationInput.Forecast;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAdjustRespBo.AdjustedTicket;
import com.my.project.llm.bo.LotteryAdjustRespBo.ComplexTicket;
import com.my.project.llm.bo.LotteryAdjustRespBo.FinalRecommendation;
import com.my.project.llm.bo.LotteryAdjustRespBo.TicketCategory;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NumberLearningTest {
    @Test
    void doesNotReplaceTheOrdinaryTicketWithSixTrendRedCandidates() {
        var forecasts = List.of(new Forecast("blueParity", "", "", List.of("奇")),
            new Forecast("blueSize", "", "", List.of("小")));
        var originalTicket = new AdjustedTicket();
        originalTicket.setCategory(TicketCategory.RECOMMENDED);
        originalTicket.setAdjustedRedBalls(List.of(1, 2, 3, 4, 5, 6));
        originalTicket.setAdjustedBlueBall(9);
        var original = new LotteryAdjustRespBo();
        original.setAdjustedTickets(List.of(originalTicket));

        var input = new HybridRecommendationInput("2026133", forecasts, null, null, Map.of(), null);
        var unrestricted = NumberLearning.variants(input, original, "blueParity", null, 7).formal();
        var trendReds = unrestricted.getAdjustedTickets().getFirst().getAdjustedRedBalls();
        assertThat(trendReds).isNotEqualTo(originalTicket.getAdjustedRedBalls());
        var observations = trendReds.stream().map(number -> new NumberTrendObservation(Ball.RED, number)).toList();
        var restrictedInput = new HybridRecommendationInput("2026133", forecasts, null, null, Map.of(), null, observations);
        var restricted = NumberLearning.variants(restrictedInput, original, "blueParity", null, 7).formal();
        assertThat(trendReds.containsAll(restricted.getAdjustedTickets().getFirst().getAdjustedRedBalls())).isFalse();
    }

    @Test
    void reordersOneSharedCandidateSlotWithoutChangingEitherVersionsBetCount() {
        var input = new HybridRecommendationInput("2026133", List.of(
            new Forecast("blueParity", "", "", List.of("奇")),
            new Forecast("blueSize", "", "", List.of("小"))), null, null, Map.of(), null);
        var draw = new Draw("2026133", LocalDate.of(2026, 11, 19), List.of(1, 2, 3, 4, 5, 6), 2);
        var single = new AdjustedTicket();
        single.setCategory(TicketCategory.RECOMMENDED);
        single.setAdjustedRedBalls(draw.reds());
        single.setAdjustedBlueBall(9);
        var complex = new ComplexTicket();
        complex.setRedBalls(List.of(1, 2, 3, 4, 5, 6, 7));
        complex.setBlueBalls(List.of(2, 3));
        var pack = new FinalRecommendation();
        pack.setComplexTicket(complex);
        var original = new LotteryAdjustRespBo();
        original.setAdjustedTickets(List.of(single));
        original.setFinalRecommendation(pack);

        var variants = NumberLearning.variants(input, original, null, new LearningPolicy.Version(2, "blueParity"), 7);
        assertThat(variants.formal()).isSameAs(original);
        assertThat(variants.trial().getAdjustedTickets().getFirst().getAdjustedBlueBall()).isIn(2, 4, 6, 8);
        assertThat(variants.trial().getFinalRecommendation()).isSameAs(pack);
        var formal = NumberLearning.score(draw, variants.formal());
        var trial = NumberLearning.score(draw, variants.trial());
        assertThat(formal.bets()).isEqualTo(15);
        assertThat(trial.bets()).isEqualTo(formal.bets());
        assertThat(formal.averageRedHits()).isCloseTo(78.0 / 15, within(1e-9));
        var random = NumberLearning.randomScore(draw, 15, 17);
        assertThat(random.bets()).isEqualTo(15);
        assertThat(NumberLearning.randomScore(draw, 15, 17)).isEqualTo(random);
    }
}
