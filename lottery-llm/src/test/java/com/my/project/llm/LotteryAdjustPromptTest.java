package com.my.project.llm;

import com.my.project.llm.bo.LotteryAdjustReqBo;
import com.my.project.llm.service.impl.LotteryAdjustServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LotteryAdjustPromptTest {
    @Test
    void stripsLegacyObservationsFromBothRecommendationAndAdjustmentPrompts() {
        for (boolean adjust : List.of(false, true)) {
            ChatModel model = mock(ChatModel.class);
            when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("test provider unavailable"));
            var request = LotteryAdjustReqBo.builder().analysisReportJson("""
                {"featureAnalysis":[{"code":"oddEven","status":"UNAVAILABLE","failureReason":"HTTP 503",
                "observations":[{"period":"INTERNAL_DETAIL_MARKER"}]},
                {"code":"threeZone","status":"OK","source":"derived-zone-counts","likelyValues":["0:2:4","1:2:3"],
                "observations":[{"period":"DERIVED_INTERNAL_DETAIL_MARKER"}]}],
                "recentDrawRecords":[{"period":"P1","redBalls":[1,2,3,4,5,6],"blueBall":1}]}
                """).tickets(adjust ? List.of(LotteryAdjustReqBo.PredictTicket.builder()
                    .redBalls(List.of(1, 2, 3, 4, 5, 6)).blueBall(1).build()) : List.of()).build();
            var service = new LotteryAdjustServiceImpl(ChatClient.builder(model).build());
            assertThrows(IllegalStateException.class, () -> service.adjust(request));
            var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
            verify(model, times(2)).call(prompts.capture());
            for (Prompt prompt : prompts.getAllValues()) {
                String input = prompt.getContents();
                assertFalse(input.contains("INTERNAL_DETAIL_MARKER"));
                assertFalse(input.contains("DERIVED_INTERNAL_DETAIL_MARKER"));
                assertFalse(input.contains("\"observations\""));
                assertTrue(input.contains("\"failureReason\":\"HTTP 503\""));
                assertTrue(input.contains("\"period\":\"P1\""));
                assertTrue(input.contains("source=derived-zone-counts"));
                assertTrue(input.contains("\"source\":\"derived-zone-counts\""));
                assertTrue(input.contains("\"likelyValues\":[\"0:2:4\",\"1:2:3\"]"));
                assertTrue(input.contains("不再独立推演另一套三区候选"));
            }
            assertTrue(request.getAnalysisReportJson().contains("INTERNAL_DETAIL_MARKER"));
        }
    }
}
