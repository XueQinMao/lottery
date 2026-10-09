package com.my.project.llm;

import com.my.project.llm.bo.*;
import com.my.project.llm.service.impl.LotteryAnalysisServiceImpl;
import com.my.project.llm.support.PatternAnalysisParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternAnalysisTest {
    private PatternFeatureAnalysisReqBo request() {
        return PatternFeatureAnalysisReqBo.builder().code("oddEven").label("奇偶比")
            .definition("奇:偶，两项合计6").allowedValues(List.of("2:4", "3:3", "4:2"))
            .observations(IntStream.rangeClosed(1, 30).mapToObj(i -> PatternFeatureAnalysisBo.Observation.builder()
                .position(i).period("P" + i).redBalls(List.of(1, 2, 3, 4, 5, 6)).blueBall(1).value("3:3").build())
                .toList()).build();
    }

    private String response() {
        return """
            一、形态规律总结
            逐期特征：%s
            形态描述：第1至30期均为3:3，连续出现，但样本不足以证明未来必然延续。
            二、下期奇偶比推演
            倾向出现的奇偶比池：["3:3"]
            理由：以惯性延续作为假设。
            暂不优先的奇偶比池：["4:2"]
            理由：最近30期未出现，暂降低偏好，不认定必断档。
            """.formatted(com.alibaba.fastjson2.JSON.toJSONString(java.util.Collections.nCopies(30, "3:3")));
    }

    @Test
    void validatesFactsPoolsAndTwoSections() {
        var result = PatternAnalysisParser.parse(response(), request());
        assertEquals("OK", result.getStatus());
        assertEquals(List.of("3:3"), result.getLikelyValues());
        assertEquals(List.of("4:2"), result.getUnlikelyValues());
        assertTrue(result.getPatternSummary().contains("第1至30期"));
        assertEquals(30, result.getSampleSize());
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response().replaceFirst("3:3", "2:4"), request()));
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response().replace("池：[\"4:2\"]", "池：[\"3:3\"]"), request()));
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response().replace("池：[\"4:2\"]", "池：[\"6:6\"]"), request()));
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response() + "三、其他\n多余部分", request()));
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response().replace("池：[\"4:2\"]", "池：[4]"), request()));
    }

    @Test
    void rejectsOldTenPeriodInputAndIncompleteThirtyPeriodOutput() {
        var request = request();
        request.setObservations(request.getObservations().subList(0, 10));
        ChatModel model = mock(ChatModel.class);
        var service = new LotteryAnalysisServiceImpl(ChatClient.builder(model).build());
        assertThrows(IllegalArgumentException.class, () -> service.analyzePattern(request));
        verify(model, never()).call(any(Prompt.class));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response().replace(",\"3:3\"", ""), request())).getMessage().contains("恰好30项"));
    }

    @Test
    void acceptsIndentedLabelsAndMultilineArraysWithoutWeakeningFactValidation() {
        String wrapped = response().replace("逐期特征：", "  逐期特征 : \n  ")
            .replace(",", ",\n    ")
            .replace("形态描述：", "  形态描述 ：")
            .replace("池：", "池 : \n  ")
            .replace("理由：", "  理由 ：");
        var result = PatternAnalysisParser.parse(wrapped, request());
        assertEquals("OK", result.getStatus());
        assertEquals(List.of("3:3"), result.getLikelyValues());
        assertEquals(List.of("4:2"), result.getUnlikelyValues());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            wrapped.replaceFirst("3:3", "2:4"), request())).getMessage().contains("position=1"));
    }

    @Test
    void distinguishesMissingDuplicateMalformedAndTruncatedArrays() {
        for (var invalid : List.of(
            new String[]{"逐期特征：", "其他特征：", "缺少数组标签: 逐期特征"},
            new String[]{"逐期特征：", "逐期特征：[]\n逐期特征：", "数组标签重复: 逐期特征"},
            new String[]{"逐期特征：", "逐期特征：不是数组", "标签后必须是JSON字符串数组"},
            new String[]{"]\n形态描述", "\n形态描述", "JSON数组未闭合"},
            new String[]{"]\n形态描述", "] 额外说明\n形态描述", "JSON数组后存在多余内容"},
            new String[]{"\"3:3\",\"3:3\"", "\"3:3\" \"3:3\"", "JSON数组格式错误"},
            new String[]{"逐期特征：[\"3:3\"", "逐期特征：[3", "必须使用JSON字符串数组"})) {
            var error = assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
                response().replace(invalid[0], invalid[1]), request()));
            assertTrue(error.getMessage().contains(invalid[2]), error.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response().replace("倾向出现的奇偶比池：", "倾向出现的奇偶比池：[]\n倾向出现的奇偶比池："), request()));
    }

    @Test
    void suppliesVerifiedFeatureValuesAndRetriesMalformedResponses() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(chat("格式错误"), chat(response()));
        ChatClient client = ChatClient.builder(model).build();
        var service = new LotteryAnalysisServiceImpl(client);
        assertEquals("OK", service.analyzePattern(request()).getStatus());
        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        String input = prompts.getAllValues().getFirst().getContents();
        assertTrue(input.contains("redBalls"));
        assertTrue(input.contains("\"value\":\"3:3\""));
        assertTrue(input.contains("不得重新估算"));
        assertTrue(input.contains("最近5到8期"));
        assertTrue(input.contains("不做硬性排除"));
        assertFalse(input.contains("第11期"));
        assertFalse(input.contains("大概率"));
        assertFalse(input.contains("候选表"));
    }

    @Test
    void marksFailedItemUnavailableAfterLimitedRetries() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(chat("错误格式"));
        ChatClient client = ChatClient.builder(model).build();
        var result = new LotteryAnalysisServiceImpl(client).analyzePattern(request());
        assertEquals("UNAVAILABLE", result.getStatus());
        assertTrue(result.getLikelyValues().isEmpty());
        assertNull(result.getNextPeriodInference());
        assertTrue(result.getFailureReason().contains("必须严格输出两个指定的中文部分"));
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void retriesEmptyChoicesAndServiceBusyWithoutTreatingThemAsBadStatistics() {
        for (boolean busy : List.of(false, true)) {
            ChatModel model = mock(ChatModel.class);
            if (busy) {
                when(model.call(any(Prompt.class))).thenThrow(new org.springframework.ai.retry.TransientAiException("HTTP 503"))
                    .thenReturn(chat(response()));
            } else {
                when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()), chat(response()));
            }
            ChatClient client = ChatClient.builder(model).build();
            var service = new LotteryAnalysisServiceImpl(client);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "retryDelay", java.time.Duration.ZERO);
            assertEquals("OK", service.analyzePattern(request()).getStatus());
            var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
            verify(model, times(2)).call(prompts.capture());
            assertFalse(prompts.getAllValues().getLast().getContents().contains("上次回答未通过校验"));
        }
    }

    @Test
    void sendsSpecificFactCorrectionsOnRetryWithoutAcceptingIncorrectFacts() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(chat(response().replaceFirst("3:3", "2:4")), chat(response()));
        ChatClient client = ChatClient.builder(model).build();
        var service = new LotteryAnalysisServiceImpl(client);
        assertEquals("OK", service.analyzePattern(request()).getStatus());
        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        String correction = prompts.getAllValues().getLast().getContents();
        assertTrue(correction.contains("position=1"));
        assertTrue(correction.contains("期号=P1"));
        assertTrue(correction.contains("返回=2:4"));
        assertTrue(correction.contains("正确值=3:3"));
    }

    @Test
    void doesNotRetryPermanentProviderFailuresOrInventAResult() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new org.springframework.ai.retry.NonTransientAiException("HTTP 401"));
        ChatClient client = ChatClient.builder(model).build();
        var result = new LotteryAnalysisServiceImpl(client).analyzePattern(request());
        assertEquals("UNAVAILABLE", result.getStatus());
        assertTrue(result.getLikelyValues().isEmpty());
        assertEquals("HTTP 401", result.getFailureReason());
        assertNull(result.getNextPeriodInference());
        verify(model).call(any(Prompt.class));
    }

    @Test
    void omitsInternalObservationsFromAllSerializersAndStillReadsLegacyReports() throws Exception {
        String legacy = """
            {"code":"oddEven","status":"OK","observations":[{"position":1,"period":"P1","value":"3:3"}],
             "patternSummary":"summary","nextPeriodInference":"inference","likelyValues":["3:3"]}
            """;
        for (var feature : List.of(com.alibaba.fastjson.JSON.parseObject(legacy, PatternFeatureAnalysisBo.class),
            com.alibaba.fastjson2.JSON.parseObject(legacy, PatternFeatureAnalysisBo.class),
            new ObjectMapper().readValue(legacy, PatternFeatureAnalysisBo.class))) {
            assertEquals("3:3", feature.getObservations().getFirst().getValue());
            var report = LotteryAnalysisRespBo.builder().featureAnalysis(List.of(feature)).build();
            for (String json : List.of(com.alibaba.fastjson.JSON.toJSONString(report),
                com.alibaba.fastjson2.JSON.toJSONString(report), new ObjectMapper().writeValueAsString(report))) {
                assertFalse(json.contains("\"observations\""), json);
                assertTrue(json.contains("\"nextPeriodInference\":\"inference\""), json);
                assertTrue(json.contains("\"likelyValues\":[\"3:3\"]"), json);
            }
            assertNotNull(feature.getObservations());
        }
    }

    @Test
    void newReportExportsOnlyFourModulesAndReadsOldHistory() throws Exception {
        var forecast = FeatureForecastBo.builder().oddEven(FeatureForecastBo.FeatureForecastItem.builder()
            .value("3:3").build()).build();
        var report = LotteryAnalysisRespBo.builder().featureAnalysis(List.of())
            .recentDrawRecords(List.of()).killNumbers(KillNumberResultBo.builder().build())
            .coldHotAnalysis(ColdHotAnalysisBo.builder().build()).featureForecast(forecast)
            .risingTrendRedBalls(List.of(1)).build();
        for (String json : List.of(com.alibaba.fastjson.JSON.toJSONString(report,
            com.alibaba.fastjson.serializer.SerializerFeature.DisableCircularReferenceDetect),
            com.alibaba.fastjson2.JSON.toJSONString(report), new ObjectMapper().writeValueAsString(report))) {
            var keys = com.alibaba.fastjson2.JSON.parseObject(json).keySet();
            assertEquals(java.util.Set.of("featureAnalysis", "recentDrawRecords", "killNumbers", "coldHotAnalysis"), keys);
        }
        String old = "{\"featureForecast\":{\"oddEven\":{\"value\":\"3:3\"}}}";
        assertEquals("3:3", com.alibaba.fastjson2.JSON.parseObject(old, LotteryAnalysisRespBo.class)
            .getFeatureForecast().getOddEven().getValue());
    }

    private static ChatResponse chat(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
