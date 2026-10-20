package com.my.project.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.project.api.diagnostics.DiagnosticsController;
import com.my.project.api.history.DrawReviewController;
import com.my.project.api.history.HistoryController;
import com.my.project.api.llm.LlmController;
import com.my.project.api.llm.LlmReportController;
import com.my.project.api.llm.LlmTaskController;
import com.my.project.api.llm.FeatureTrendForecastController;
import com.my.project.api.prediction.PredictionController;
import com.my.project.api.purchase.PurchaseController;
import com.my.project.api.selection.SelectionController;
import com.my.project.application.feature.LlmAdjustTaskService;
import com.my.project.application.feature.FeatureTrendForecastService;
import com.my.project.application.feature.dto.FeatureTrendForecast.Summary;
import com.my.project.application.feature.LotteryFeatureAnalysisService;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.DrawSynchronizationService;
import com.my.project.application.feature.ZoneTrendAnalysisService;
import com.my.project.api.llm.ZoneTrendController;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.predict.PredictHitRecordService;
import com.my.project.application.predict.PredictRecordService;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.review.DrawReview;
import com.my.project.application.review.DrawReviewService;
import com.my.project.application.selection.BuyRecordService;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises MVC binding and serialization without a database, scheduler, Python or real LLM. */
class BackendContractTest {
    private final HistoryRecordService history = mock(HistoryRecordService.class);
    private final LotteryFeatureAnalysisService analysis = mock(LotteryFeatureAnalysisService.class);
    private final LlmAdjustTaskService tasks = mock(LlmAdjustTaskService.class);
    private final LotteryReportService reports = mock(LotteryReportService.class);
    private final ZoneTrendAnalysisService zoneTrends = mock(ZoneTrendAnalysisService.class);
    private final FeatureTrendForecastService featureTrends = mock(FeatureTrendForecastService.class);
    private final DrawReviewService reviews = mock(DrawReviewService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var predictions = mock(PredictRecordService.class);
        var hits = mock(PredictHitRecordService.class);
        mvc = MockMvcBuilders.standaloneSetup(new HistoryController(history, mock(DrawSynchronizationService.class)),
            new PredictionController(predictions, hits, mock(PredictCacheService.class)),
            new PurchaseController(mock(BuyRecordService.class)),
            new SelectionController(mock(SmartSelectService.class)),
            new DiagnosticsController(predictions, history, hits),
            new LlmController(analysis), new LlmTaskController(tasks), new LlmReportController(reports), new ZoneTrendController(zoneTrends),
            new FeatureTrendForecastController(featureTrends), new DrawReviewController(reviews)).build();
    }

    @Test
    void exposesNumberAndFeatureTrendsWithoutRemovedAnalysisRoutes() throws Exception {
        var mappings = mvc.getDispatcherServlet().getWebApplicationContext()
            .getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
        var actual = new TreeSet<String>();
        mappings.keySet().forEach(mapping -> mapping.getMethodsCondition().getMethods().forEach(method ->
            mapping.getPatternValues().forEach(path -> actual.add(method.name() + " " + path))));
        try (var fixture = getClass().getResourceAsStream("/contracts/backend-routes.json")) {
            var expected = new ObjectMapper().readValue(fixture, new TypeReference<List<String>>() { });
            assertEquals(new TreeSet<>(expected), actual);
            assertEquals(30, actual.size());
        }
    }

    @Test
    void exposesLatestReviewAndReturnsNotFoundWhenADrawHasNotBeenStored() throws Exception {
        var draw = new DrawReview.Draw("2026132", LocalDate.of(2026, 11, 19), List.of(1, 2, 3, 12, 13, 23), 4);
        var summary = new DrawReview.FeatureSummary(0, 0, 0, 16, 0);
        when(reviews.find(null)).thenReturn(Optional.of(new DrawReview(draw, summary, List.of(), List.of())));

        mvc.perform(get("/api/history/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.draw.period").value("2026132"))
            .andExpect(jsonPath("$.data.featureSummary.notPredicted").value(16));
        mvc.perform(get("/api/history/reviews").param("period", "2026133"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void featureForecastQueriesSeparatePeriodSummariesFromDetails() throws Exception {
        String target = "2026133";
        var summary = new Summary("2026132", target, "blueParity", "奇偶", true,
            "vision-test", List.of("偶"), Instant.parse("2026-11-18T00:00:00Z"));
        when(featureTrends.recentPeriods(null, 12)).thenReturn(List.of(target));
        when(featureTrends.findPeriod(target)).thenReturn(List.of(summary));
        var temperature = com.my.project.llm.bo.ColdHotAnalysisBo.builder()
            .redColdBalls(List.of(6)).blueHotBalls(List.of(8)).build();
        when(featureTrends.prepare("2026132")).thenReturn(
            new com.my.project.application.feature.dto.FeatureTrendForecast.Plan("2026132", target, List.of(), temperature));
        mvc.perform(get("/api/llm/feature-trends/input").param("endPeriod", "2026132"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourcePeriod").value("2026132"))
            .andExpect(jsonPath("$.data.targetPeriod").value(target))
            .andExpect(jsonPath("$.data.coldHotAnalysis.redColdBalls[0]").value(6))
            .andExpect(jsonPath("$.data.coldHotAnalysis.blueHotBalls[0]").value(8));
        mvc.perform(get("/api/llm/feature-trends"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value(target));
        mvc.perform(get("/api/llm/feature-trends/" + target))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].values[0]").value("偶"))
            .andExpect(jsonPath("$.data[0].evidence").doesNotExist());
        mvc.perform(get("/api/llm/feature-trends/" + target + "/blueParity"))
            .andExpect(status().isNotFound());
        verify(featureTrends).findDetail(target, "blueParity");
    }

    @Test
    void zoneTrendPrototypeUsesPostAndDistinguishesBadInputFromProviderFailure() throws Exception {
        when(zoneTrends.analyze(any())).thenThrow(new IllegalArgumentException("图片不完整"));
        mvc.perform(post("/api/llm/zone-trends").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("图片不完整"));
        doThrow(new IllegalStateException("视觉模型不可用")).when(zoneTrends).analyze(any());
        mvc.perform(post("/api/llm/zone-trends").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(502));
        verifyNoInteractions(analysis);
    }

    @Test
    void bindsDrawTrendQueriesAndSerializesAllFeatureGroups() throws Exception {
        var record = com.my.project.infrastructure.persistence.entity.HistoryRecord.builder()
            .period("2026001").num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(16).build();
        var result = com.my.project.application.history.support.DrawTrendCalculator.calculate(List.of(record), 30);
        when(history.analyzeDrawTrends(30, "2026001")).thenReturn(result);
        mvc.perform(get("/api/history/draw-trends").param("sampleSize", "30").param("endPeriod", "2026001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.periods[0]").value("2026001"))
            .andExpect(jsonPath("$.data.redFeatures.length()").value(15))
            .andExpect(jsonPath("$.data.blueFeatures.length()").value(5))
            .andExpect(jsonPath("$.data.redFeatures[0].series[0].values[0]").isEmpty());
        mvc.perform(get("/api/history/draw-trends")).andExpect(status().isOk());
        verify(history).analyzeDrawTrends(100, null);
        mvc.perform(get("/api/history/draw-trends").param("sampleSize", "invalid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void retainsQueryDefaultsAndTheHttp200BusinessErrorEnvelope() throws Exception {
        when(history.analyzeNumberTrends(100, null)).thenThrow(new IllegalArgumentException("invalid history"));
        mvc.perform(get("/api/history/number-trends")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.message").value("invalid history"));
        verify(history).analyzeNumberTrends(100, null);
        mvc.perform(get("/api/llm/recommend-task/missing")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void removesBothAnalysisEndpointsAndSerializesNumberOmissions() throws Exception {
        mvc.perform(get("/api/history/trend")).andExpect(status().isNotFound());
        mvc.perform(get("/api/history/pattern-trend")).andExpect(status().isNotFound());
        var record = com.my.project.infrastructure.persistence.entity.HistoryRecord.builder()
            .period("2026001").num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(16).build();
        var result = com.my.project.application.history.support.NumberTrendCalculator.calculate(List.of(record), 30);
        when(history.analyzeNumberTrends(30, "2026001")).thenReturn(result);
        mvc.perform(get("/api/history/number-trends").param("sampleSize", "30").param("endPeriod", "2026001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].period").value("2026001"))
            .andExpect(jsonPath("$.data.rows[0].redOmissions.length()").value(33))
            .andExpect(jsonPath("$.data.rows[0].blueOmissions.length()").value(16))
            .andExpect(jsonPath("$.data.redCandidates[0]").value(7))
            .andExpect(jsonPath("$.data.rows[0].blueOmissions[15]").value(0));
    }

    @Test
    void removesLegacyRecommendationEndpointsAndKeepsHistoryReads() throws Exception {
        mvc.perform(post("/api/llm/adjust")).andExpect(status().isNotFound());
        mvc.perform(get("/api/llm/adjust/3/false")).andExpect(status().isNotFound());
        mvc.perform(get("/api/llm/adjust/history")).andExpect(status().isNotFound());

        var view = new LotteryAdjustViewBo();
        view.setFileName("adjust_existing.json");
        when(reports.loadAdjustHistory(view.getFileName())).thenReturn(view);
        mvc.perform(get("/api/llm/adjust/history/{fileName}", view.getFileName()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fileName").value(view.getFileName()));
        mvc.perform(get("/api/llm/file-history").param("type", "RECOMMEND")).andExpect(status().isOk());
        verify(reports).listFileHistory("RECOMMEND", 4);
    }

    @Test
    void submitsCurrentRecommendationAndRejectsLegacyModes() throws Exception {
        mvc.perform(post("/api/llm/recommend-task")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/llm/recommend-task").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mode\":\"HYBRID\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        for (String mode : List.of("FEATURE", "CACHE")) {
            mvc.perform(post("/api/llm/recommend-task").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"%s\"}".formatted(mode))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        }
        verify(tasks, times(2)).submit(false);
    }

    @Test
    void optsInToNumberTrendAnalysis() throws Exception {
        mvc.perform(post("/api/llm/recommend-task").contentType(MediaType.APPLICATION_JSON)
            .content("{\"useNumberTrend\":true}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        verify(tasks).submit(true);
    }
}
