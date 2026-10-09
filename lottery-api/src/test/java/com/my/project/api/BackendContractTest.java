package com.my.project.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.project.api.diagnostics.DiagnosticsController;
import com.my.project.api.history.HistoryController;
import com.my.project.api.llm.LlmController;
import com.my.project.api.prediction.PredictionController;
import com.my.project.api.purchase.PurchaseController;
import com.my.project.api.selection.SelectionController;
import com.my.project.application.feature.LlmAdjustTaskService;
import com.my.project.application.feature.LotteryFeatureAnalysisService;
import com.my.project.application.feature.dto.RecommendationCommand;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.predict.PredictHitRecordService;
import com.my.project.application.predict.PredictRecordService;
import com.my.project.application.report.LotteryReportService;
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
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var predictions = mock(PredictRecordService.class);
        var hits = mock(PredictHitRecordService.class);
        mvc = MockMvcBuilders.standaloneSetup(new HistoryController(history),
            new PredictionController(predictions, hits, mock(PredictCacheService.class)),
            new PurchaseController(mock(BuyRecordService.class)),
            new SelectionController(mock(SmartSelectService.class)),
            new DiagnosticsController(predictions, history, hits),
            new LlmController(analysis, tasks, reports)).build();
    }

    @Test
    void preservesAllTwentySixExistingMethodAndPathMappings() throws Exception {
        var mappings = mvc.getDispatcherServlet().getWebApplicationContext()
            .getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
        var actual = new TreeSet<String>();
        mappings.keySet().forEach(mapping -> mapping.getMethodsCondition().getMethods().forEach(method ->
            mapping.getPatternValues().forEach(path -> actual.add(method.name() + " " + path))));
        try (var fixture = getClass().getResourceAsStream("/contracts/backend-routes.json")) {
            var expected = new ObjectMapper().readValue(fixture, new TypeReference<List<String>>() { });
            assertEquals(new TreeSet<>(expected), actual);
            assertEquals(26, actual.size());
        }
    }

    @Test
    void retainsQueryDefaultsAndTheHttp200BusinessErrorEnvelope() throws Exception {
        when(history.analyzeTrend("red", 1, 100, null)).thenThrow(new IllegalArgumentException("invalid history"));
        mvc.perform(get("/api/history/trend")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.message").value("invalid history"));
        verify(history).analyzeTrend("red", 1, 100, null);
        mvc.perform(get("/api/llm/recommend-task/missing")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void retainsLegacyLowercaseBallFieldsAndUsesTheUnifiedRecommendationCommand() throws Exception {
        var view = new LotteryAdjustViewBo();
        view.setFileName("adjust_existing.json");
        when(analysis.recommend(any())).thenReturn(view);
        mvc.perform(post("/api/llm/adjust").contentType(MediaType.APPLICATION_JSON).content("""
            {"count":2,"drawRecords":[{"redballs":[1,2,3,4,5,6],"blueball":7}],"userRequirement":"keep"}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.fileName").value("adjust_existing.json"));
        var command = org.mockito.ArgumentCaptor.forClass(RecommendationCommand.class);
        verify(analysis).recommend(command.capture());
        var feature = assertInstanceOf(RecommendationCommand.Feature.class, command.getValue());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), feature.adjustment().getDrawRecords().getFirst().getRedBalls());
        assertEquals(7, feature.adjustment().getDrawRecords().getFirst().getBlueBall());
        assertEquals("keep", feature.adjustment().getUserRequirement());
    }

    @Test
    void mergesCacheAdjustmentIntoPostWithoutRemovingItsLegacyGetAlias() throws Exception {
        mvc.perform(post("/api/llm/adjust").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mode\":\"CACHE\",\"count\":3,\"isTopN\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(analysis).recommend(new RecommendationCommand.Cache(3, false, null));
        mvc.perform(get("/api/llm/adjust/3/false")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        verify(analysis).adjust(3, false, null);
    }

    @Test
    void keepsReportHistoryDefaultsAndTaskDefaultMode() throws Exception {
        mvc.perform(get("/api/llm/file-history")).andExpect(status().isOk());
        verify(reports).listFileHistory(null, 4);
        mvc.perform(post("/api/llm/recommend-task")).andExpect(status().isOk());
        var command = org.mockito.ArgumentCaptor.forClass(
            com.my.project.application.feature.dto.LlmRecommendationTaskCommand.class);
        verify(tasks).submit(command.capture());
        assertEquals(com.my.project.application.feature.enums.LlmAdjustTaskMode.FEATURE, command.getValue().getMode());
        assertNull(command.getValue().getCount());
    }
}
