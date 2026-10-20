package com.my.project.application.feature;

import com.my.project.application.learning.DrawLearningService;

import com.alibaba.fastjson2.JSON;
import com.my.project.application.event.AdjustCompleteEvent;
import com.my.project.application.event.listener.AdjustCompleteEventListener;
import com.my.project.application.feature.dto.FeatureTrendForecast.Summary;
import com.my.project.application.feature.impl.HybridRecommendationWorkflow;
import com.my.project.application.feature.impl.RecommendationNumberTrendService;
import com.my.project.application.feature.support.ForecastTargetPeriod;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.selection.impl.BuyRecordServiceImpl;
import com.my.project.application.support.SpringContextUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.entity.BuyRecord;
import com.my.project.infrastructure.persistence.repository.BuyRecordRepository;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAdjustRespBo.TicketCategory;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.service.HybridRecommendationModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HybridRecommendationWorkflowTest {
    @Test
    void stopsBeforeGeneratingOrSavingWhenNumberTrendFails() {
        var history = mock(HistoryRecordService.class);
        var periods = mock(ForecastTargetPeriod.class);
        var forecasts = mock(FeatureTrendForecastService.class);
        var cache = mock(PredictCacheService.class);
        var model = mock(HybridRecommendationModelService.class);
        var reports = mock(LotteryReportService.class);
        var learning = mock(DrawLearningService.class);
        var numberTrends = mock(RecommendationNumberTrendService.class);
        var generating = mock(Runnable.class);
        var cutoff = HistoryRecord.builder().period("2026132").openDate(LocalDate.of(2026, 11, 17)).build();
        var draws = List.of(cutoff);
        when(history.getLatestRecords(anyInt())).thenReturn(draws);
        when(periods.after(cutoff)).thenReturn("2026133");
        when(forecasts.findPeriod("2026133")).thenReturn(List.of(summary("2026132", "redZones", "2:2:2")));
        var failure = new IllegalStateException("号码走势图分析失败");
        when(numberTrends.candidates(draws)).thenThrow(failure);
        var workflow = new HybridRecommendationWorkflow(history, periods, forecasts, cache, model, reports, learning,
            numberTrends);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> workflow.recommend(true, generating)));
        verifyNoInteractions(generating, cache, model, learning, reports);
    }

    @ParameterizedTest
    @CsvSource({"false, false, false", "true, false, false", "true, true, true", "true, true, false"})
    void savesAllModelTicketsAndReportsFeatureMissesWithoutRejecting(
            boolean hasCache, boolean useNumberTrend, boolean hasObservations) {
        var history = mock(HistoryRecordService.class);
        var periods = mock(ForecastTargetPeriod.class);
        var forecasts = mock(FeatureTrendForecastService.class);
        var cache = mock(PredictCacheService.class);
        var model = mock(HybridRecommendationModelService.class);
        var reports = mock(LotteryReportService.class);
        var database = mock(BuyRecordRepository.class);
        var numberTrends = mock(RecommendationNumberTrendService.class);
        var generating = mock(Runnable.class);
        List<NumberTrendObservation> observations = hasObservations
            ? List.of(new NumberTrendObservation(NumberTrendObservation.Ball.RED, 6)) : List.of();
        var drawDate = LocalDate.of(2026, 11, 19);
        var cutoff = HistoryRecord.builder().period("2026132").openDate(drawDate.minusDays(2))
            .num1(1).num2(6).num3(12).num4(18).num5(23).num6(33).special(9).build();
        when(history.getLatestRecords(anyInt())).thenReturn(List.of(cutoff));
        when(numberTrends.candidates(List.of(cutoff))).thenReturn(observations);
        when(periods.after(cutoff)).thenReturn("2026133");
        when(forecasts.findPeriod("2026133")).thenReturn(List.of(
            summary("2026132", "redZones", "2:2:2"), summary("2026132", "redPrime", "1:5"),
            summary("2026132", "blueParity", "奇", "偶"), summary("2026131", "redSum", "90")));
        when(cache.sampleTicketKeys(drawDate, 1)).thenReturn(hasCache
            ? List.of(drawDate + "|1,6,12,18,23,33|9") : List.of());
        when(model.recommend(any())).thenAnswer(invocation -> modelResult(invocation.getArgument(0)));
        when(reports.saveRecommendation(any())).thenAnswer(invocation ->
            JSON.parseObject(JSON.toJSONString(invocation.getArgument(0)), LotteryAdjustViewBo.class));

        var listener = new AdjustCompleteEventListener(new BuyRecordServiceImpl(database, history));
        var context = mock(ApplicationContext.class);
        doAnswer(invocation -> {
            listener.handleEvent(invocation.getArgument(0));
            return null;
        }).when(context).publishEvent(any(AdjustCompleteEvent.class));
        when(database.saveOrUpdateBatch(anyList())).thenAnswer(invocation -> {
            var rows = invocation.<List<BuyRecord>>getArgument(0);
            assertEquals(6, rows.size());
            assertEquals(5, rows.stream().filter(record -> record.getTotalBets() == 1).count());
            assertEquals(14, rows.getLast().getTotalBets());
            assertEquals(1, rows.stream().filter(record -> record.getOriRedBalls() != null).count());
            assertTrue(rows.stream().allMatch(record -> drawDate.equals(record.getOpenDate())));
            return true;
        });

        var learning = mock(DrawLearningService.class);
        when(learning.publishNumbers(anyString(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        var workflow = new HybridRecommendationWorkflow(history, periods, forecasts, cache, model, reports, learning,
            numberTrends);
        try (var spring = mockStatic(SpringContextUtils.class)) {
            spring.when(SpringContextUtils::getApplicationContext).thenReturn(context);
            var result = workflow.recommend(useNumberTrend, generating);
            assertEquals("2026133", result.getTargetPeriod());
            assertEquals(useNumberTrend ? observations : null, result.getNumberTrendObservations());
            assertNull(result.getNumberTrendImages());
            assertEquals(5, result.getAdjustedTickets().size());
            assertEquals(List.of(TicketCategory.OPTIMIZED, TicketCategory.RECOMMENDED, TicketCategory.RECOMMENDED,
                TicketCategory.FULL_MATCH, TicketCategory.FULL_MATCH),
                result.getAdjustedTickets().stream().map(LotteryAdjustRespBo.AdjustedTicket::getCategory).toList());
            assertEquals(1, result.getAdjustedTicketHits().getFirst().getHits().stream().filter(hit ->
                hit.getCode().equals("redPrime") && hit.getActual().equals("1:5")).count());
            assertEquals(3, result.getAdjustedTicketHits().getFirst().getHits().size());
            for (var full : result.getAdjustedTicketHits().subList(3, 5)) {
                assertTrue(full.getMissCount() > 0);
                assertEquals(3, full.getMainHitCount() + full.getAltHitCount() + full.getMissCount());
            }
            assertEquals(14, result.getComplexHits().stream().filter(hit -> hit.code().equals("blueParity"))
                .mapToLong(hit -> hit.mainHitBets() + hit.altHitBets()).sum());
        }
        var input = ArgumentCaptor.forClass(HybridRecommendationInput.class);
        var order = inOrder(numberTrends, generating, model);
        if (useNumberTrend) order.verify(numberTrends).candidates(List.of(cutoff));
        else verifyNoInteractions(numberTrends);
        order.verify(generating).run();
        order.verify(model).recommend(input.capture());
        assertEquals(hasObservations ? observations : null, input.getValue().numberTrendObservations());
        assertEquals(FeatureTrendKind.values().length, input.getValue().forecasts().size());
        assertEquals(hasCache, input.getValue().optimizationSeed().fromCache());
        if (hasCache) assertEquals(9, input.getValue().optimizationSeed().blueBall());
        assertEquals(33, poolSize(input.getValue().redTemperature()));
        assertEquals(16, poolSize(input.getValue().blueTemperature()));
        assertTrue(input.getValue().killSuggestions().isEmpty());
        assertTrue(input.getValue().forecasts().stream().anyMatch(feature ->
            "redSum".equals(feature.code()) && feature.values().isEmpty()));
        verify(database).saveOrUpdateBatch(anyList());
    }

    private static int poolSize(HybridRecommendationInput.TemperaturePool pool) {
        return pool.cold().size() + pool.warm().size() + pool.hot().size();
    }

    private static Summary summary(String source, String code, String... values) {
        return new Summary(source, "2026133", code, code, code.startsWith("blue"), "fixture", List.of(values), Instant.EPOCH);
    }

    private static LotteryAdjustRespBo modelResult(HybridRecommendationInput input) {
        var result = new LotteryAdjustRespBo();
        var singles = IntStream.range(0, 5).mapToObj(index -> {
            var ticket = new LotteryAdjustRespBo.AdjustedTicket();
            ticket.setReason("单式测试");
            if (index >= 3) {
                ticket.setCategory(TicketCategory.FULL_MATCH);
                ticket.setAdjustedRedBalls(List.of(1, 2, 3, 4, 5, 6));
                ticket.setAdjustedBlueBall(index + 1);
                return ticket;
            }
            ticket.setCategory(index == 0 ? TicketCategory.OPTIMIZED : TicketCategory.RECOMMENDED);
            ticket.setAdjustedRedBalls(List.of(1, 6, 12, 18, 23, 33));
            ticket.setAdjustedBlueBall(index + 1);
            if (index == 0) {
                var seed = input.optimizationSeed();
                ticket.setOriginalRedBalls(seed.redBalls());
                ticket.setOriginalBlueBall(seed.blueBall());
            }
            return ticket;
        }).toList();
        var complex = new LotteryAdjustRespBo.ComplexTicket();
        complex.setRedBalls(List.of(1, 6, 12, 18, 23, 28, 33));
        complex.setBlueBalls(List.of(1, 2));
        complex.setTotalBets(14);
        complex.setBasis("复式测试");
        var pack = new LotteryAdjustRespBo.FinalRecommendation();
        pack.setSingleTickets(List.of());
        pack.setComplexTicket(complex);
        result.setAdjustedTickets(singles);
        result.setFinalRecommendation(pack);
        return result;
    }
}
