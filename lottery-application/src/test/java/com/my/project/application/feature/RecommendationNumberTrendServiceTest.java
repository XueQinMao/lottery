package com.my.project.application.feature;

import com.my.project.application.feature.impl.RecommendationNumberTrendService;
import com.my.project.application.feature.support.NumberTrendChart;
import com.my.project.application.history.support.NumberTrendCalculator;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;
import com.my.project.llm.bo.NumberTrendObservation.Evidence;
import com.my.project.llm.service.NumberTrendModelService.Draw;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationNumberTrendServiceTest {
    @Test
    void redChartKeepsTheActualPointsThatProjectCandidate14() {
        var draws = List.of(redDraw("2026132", 17), redDraw("2026133", 14), redDraw("2026134", 17));
        var candidate = NumberTrendChart.renderRed(draws).candidates().stream()
            .filter(item -> item.number() == 14).findFirst().orElseThrow();
        assertEquals(List.of(new Evidence("2026132", 0, 17), new Evidence("2026133", 0, 14),
            new Evidence("2026134", 0, 17)), candidate.evidence());
        assertTrue(candidate.observation().contains("17 → 14 → 17 斜线折返"));
    }

    private static Draw redDraw(String period, int hit) {
        return new Draw(period, List.of(hit), 1,
            IntStream.rangeClosed(1, 33).mapToObj(number -> number == hit ? 0 : 1).toList(), List.of(0));
    }

    @Test
    void usesTheSameOrderedRedCandidatesAsTheDefaultNumberTrendPage() {
        var records = IntStream.rangeClosed(1, 31).mapToObj(index -> HistoryRecord.builder()
            .period(Integer.toString(2026000 + index)).openDate(LocalDate.of(2026, 1, index))
            .num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(7).build()).toList();
        var page = NumberTrendCalculator.calculate(records, 30);

        var candidates = new RecommendationNumberTrendService().candidates(records.reversed());
        assertFalse(candidates.isEmpty());
        assertEquals(page.redCandidates(), candidates.stream().map(NumberTrendObservation::number).toList());
        assertTrue(page.redCandidates().contains(1));
        assertTrue(candidates.stream().allMatch(item ->
            item.ball() == Ball.RED && item.evidence() == null && item.observation() == null));
    }

    @Test
    void handlesAShortHistoryLikeTheNumberTrendPage() {
        var record = HistoryRecord.builder().period("2026001")
            .num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(7).build();
        var service = new RecommendationNumberTrendService();

        assertEquals(List.of(new NumberTrendObservation(Ball.RED, 7)), service.candidates(List.of(record)));
        assertTrue(service.candidates(List.of()).isEmpty());
    }
}
