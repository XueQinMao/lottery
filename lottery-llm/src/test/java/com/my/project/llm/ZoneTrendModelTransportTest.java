package com.my.project.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.project.llm.bo.ZoneTrendImage;
import com.my.project.llm.bo.ZoneTrendSnapshot;
import com.my.project.llm.bo.FeatureTrendInput;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.LotteryAdjustRespBo.TicketCategory;
import com.my.project.llm.config.ZoneTrendModelProperties;
import com.my.project.llm.config.LlmClientConfiguration;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import java.net.URI;
import com.my.project.llm.service.ZoneTrendModelService;
import com.my.project.llm.service.HybridRecommendationModelService;
import com.my.project.llm.service.NumberTrendModelService;
import com.my.project.llm.client.VisionModelClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ZoneTrendModelTransportTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneTrendSnapshot SNAPSHOT = new ZoneTrendSnapshot("2026002", List.of("2026001", "2026002"),
        List.of(new ZoneTrendSnapshot.Series("zone1", List.of(1, 2))), List.of());

    @ParameterizedTest
    @ValueSource(strings = {"json", "fenced", "flat-array", "short-array", "length", "incomplete", "trend-mixed",
        "trend-retry", "trend-invalid"})
    void hybridRecommendationRequestsJsonAndHandlesOutputLimits(String scenario) throws Exception {
        var payload = new AtomicReference<JsonNode>();
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            int request = requests.incrementAndGet();
            payload.set(MAPPER.readTree(exchange.getRequestBody()));
            String content = """
                {"optimizedTicket":{"redBalls":[33,1,6,12,18,23],"blueBall":9,"reason":"保留冷热分布"},
                 "recommendedTickets":[
                  {"redBalls":[3,8,13,18,24,30],"blueBall":5,"reason":"参考奇偶"},
                  {"redBalls":[4,9,13,18,24,30],"blueBall":6,"reason":"参考路数"}],
                 "fullMatchTickets":[
                  {"redBalls":[1,6,12,18,23,30],"blueBall":4,"reason":"符合三区比"},
                  {"redBalls":[2,8,13,18,24,30],"blueBall":4,"reason":"命中三区主推值"}],
                 "complexTicket":{"redBalls":[1,6,12,18,23,28,33],"blueBalls":[9,4],"reason":"覆盖两个蓝球区间"}}
                """;
            if ("trend-mixed".equals(scenario) || ("trend-retry".equals(scenario) && request == 2)) {
                content = content.replace("[3,8,13,18,24,30]", "[3,8,13,18,24,31]");
            }
            if ("fenced".equals(scenario)) content = "```json\n" + content + "```";
            if ("flat-array".equals(scenario) || "short-array".equals(scenario)) {
                var object = MAPPER.readTree(content);
                var array = MAPPER.createArrayNode().add(object.get("optimizedTicket"));
                object.get("recommendedTickets").forEach(array::add);
                object.get("fullMatchTickets").forEach(array::add);
                if ("flat-array".equals(scenario)) array.add(object.get("complexTicket"));
                content = MAPPER.writeValueAsString(array);
            }
            if ("length".equals(scenario) || "incomplete".equals(scenario)) {
                content = "```json\n" + content.substring(0, content.indexOf("\"complexTicket\""));
            }
            byte[] body = response("length".equals(scenario) ? "length" : "stop", content);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var client = new LlmClientConfiguration().lotteryPatternChatClient("fixture-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode",
                http, Duration.ofSeconds(5), "mock-qwen", 0.2, 2048);
            var red = List.of(1, 6, 12, 18, 23, 30);
            var bands = new HybridRecommendationInput.TemperaturePool(List.of(1), List.of(2), List.of(3));
            var observations = scenario.startsWith("trend-")
                ? List.of(3, 8, 13, 18, 24, 30).stream()
                    .map(number -> new NumberTrendObservation(NumberTrendObservation.Ball.RED, number)).toList()
                : ("fenced".equals(scenario) || "flat-array".equals(scenario))
                ? List.of(new NumberTrendObservation(
                NumberTrendObservation.Ball.RED, 7, List.of(new NumberTrendObservation.Evidence("2026132", 0)),
                "最近一期有开出")) : null;
            var input = new HybridRecommendationInput("2026133", List.of(
                new HybridRecommendationInput.Forecast("redZones", "红球三区比", "一区:二区:三区", List.of("2:2:2"))),
                bands, bands, Map.of("red", List.of(29)), new HybridRecommendationInput.OptimizationSeed(red, 4, true),
                observations);
            var service = new HybridRecommendationModelService(client, 4096);
            if ("trend-invalid".equals(scenario)) {
                assertTrue(assertThrows(IllegalStateException.class, () -> service.recommend(input))
                    .getMessage().contains("候选之外的红球"));
                assertEquals(2, requests.get());
                return;
            }
            if ("short-array".equals(scenario)) {
                assertTrue(assertThrows(IllegalStateException.class, () -> service.recommend(input))
                    .getMessage().contains("推荐数组必须包含五组单式和一组复式"));
                assertEquals(1, requests.get());
                return;
            }
            if ("length".equals(scenario) || "incomplete".equals(scenario)) {
                var error = assertThrows(RuntimeException.class, () -> service.recommend(input));
                assertTrue(error.getMessage().contains("length".equals(scenario)
                    ? "4096 token 输出上限，结果被截断" : "JSON 不完整"));
                assertEquals(1, requests.get());
                return;
            }
            var result = service.recommend(input);
            assertEquals("trend-retry".equals(scenario) ? 2 : 1, requests.get());
            if (scenario.startsWith("trend-")) {
                assertEquals(List.of(3, 8, 13, 18, 24, 31),
                    result.getAdjustedTickets().get(1).getAdjustedRedBalls());
                assertTrue(payload.get().path("messages").get(0).path("content").asText().contains("候选之外的红球"));
            }
            assertEquals(5, result.getAdjustedTickets().size());
            assertEquals(red, result.getAdjustedTickets().getFirst().getOriginalRedBalls());
            assertEquals(List.of(1, 6, 12, 18, 23, 33), result.getAdjustedTickets().getFirst().getAdjustedRedBalls());
            assertNull(result.getAdjustedTickets().get(2).getOriginalRedBalls());
            assertEquals(TicketCategory.OPTIMIZED, result.getAdjustedTickets().getFirst().getCategory());
            assertEquals(TicketCategory.RECOMMENDED, result.getAdjustedTickets().get(1).getCategory());
            assertEquals(TicketCategory.FULL_MATCH, result.getAdjustedTickets().getLast().getCategory());
            assertEquals("命中三区主推值", result.getAdjustedTickets().getLast().getReason());
            assertEquals(14, result.getFinalRecommendation().getComplexTicket().getTotalBets());
            var messages = payload.get().path("messages");
            assertEquals("mock-qwen", payload.get().path("model").asText());
            assertEquals(4096, payload.get().path("max_tokens").asInt());
            assertEquals("json_object", payload.get().path("response_format").path("type").asText());
            assertTrue(messages.get(0).path("content").asText().contains("软参考"));
            var userInput = MAPPER.readTree(messages.get(1).path("content").asText());
            assertFalse(userInput.has("fullMatchTickets"));
            assertEquals(observations != null, userInput.has("numberTrendObservations"));
            assertEquals(observations != null, messages.get(0).path("content").asText().contains("numberTrendObservations"));
            assertTrue(userInput.path("optimizationSeed").path("fromCache").asBoolean());
            assertEquals("2:2:2", userInput.path("forecasts").get(0).path("values").get(0).asText());
            assertEquals(29, userInput.path("killSuggestions").path("red").get(0).asInt());
        } finally { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "empty", "invalid-number", "duplicate", "too-many", "legacy-output",
        "invalid-json", "length", "chart-candidate"})
    void numberTrendUsesZoneHistoryAndReturnsOnlyCandidateNumbers(String scenario) throws Exception {
        var payload = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            payload.set(MAPPER.readTree(exchange.getRequestBody()));
            String content = switch (scenario) {
                case "valid" -> "{\"red\":[21,14],\"blue\":[14]}";
                case "empty" -> "{\"red\":[],\"blue\":[]}";
                case "chart-candidate", "length" -> "{\"red\":[],\"blue\":[14]}";
                case "invalid-number" -> "{\"red\":[],\"blue\":[17]}";
                case "duplicate" -> "{\"red\":[14,14],\"blue\":[]}";
                case "too-many" -> "{\"red\":[1,2,3],\"blue\":[]}";
                case "legacy-output" -> "{\"red\":[],\"blue\":[],\"observation\":\"多余说明\"}";
                default -> "{\"red\":[";
            };
            byte[] body = response("length".equals(scenario) ? "length" : "stop", content);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var properties = properties(server);
            var client = new VisionModelClient(new LlmClientConfiguration()
                .lotteryZoneTrendChatClient(properties, http), properties);
            var service = new NumberTrendModelService(client);
            var draws = screenshotDraws();
            var images = List.of(new ZoneTrendImage("red", "data:image/png;base64,cmVk"),
                new ZoneTrendImage("blue", "data:image/png;base64,Ymx1ZQ=="));
            switch (scenario) {
                case "valid" -> {
                    var selected = service.analyze(draws, images);
                    assertEquals(List.of(NumberTrendObservation.Ball.RED, NumberTrendObservation.Ball.BLUE),
                        selected.stream().map(NumberTrendObservation::ball).toList());
                    assertTrue(selected.stream().allMatch(item -> item.number() == 14));
                    assertTrue(selected.stream().allMatch(item -> item.evidence() == null && item.observation() == null));
                }
                case "empty" -> assertTrue(service.analyze(draws, images).isEmpty());
                case "chart-candidate" -> {
                    var candidate = new NumberTrendObservation(NumberTrendObservation.Ball.RED, 14,
                        List.of(new NumberTrendObservation.Evidence("2026132", 0, 17),
                            new NumberTrendObservation.Evidence("2026133", 0, 14),
                            new NumberTrendObservation.Evidence("2026134", 0, 17)), "17 → 14 → 17 折返指向14");
                    var selected = service.analyze(draws, images, List.of(candidate));
                    assertEquals(14, selected.getLast().number());
                    assertEquals(NumberTrendObservation.Ball.RED, selected.getLast().ball());
                    assertNull(selected.getLast().evidence());
                    assertNull(selected.getLast().observation());
                }
                case "length" -> assertTrue(assertThrows(IllegalStateException.class,
                    () -> service.analyze(draws, images)).getMessage().contains("输出被截断"));
                default -> assertTrue(assertThrows(IllegalStateException.class,
                    () -> service.analyze(draws, images)).getMessage().contains("结果未通过校验"));
            }
            assertEquals(128, payload.get().path("max_tokens").asInt());
            var messages = payload.get().path("messages");
            var instructions = messages.get(0).path("content").asText();
            for (var layer : List.of("遗漏", "红球形态线", "蓝球走势线", "区间辅助线", "红球三区", "蓝球四区")) {
                assertTrue(instructions.contains(layer), layer);
            }
            var content = messages.get(1).path("content");
            assertEquals(3, content.size());
            String userText = content.get(0).path("text").asText();
            var review = MAPPER.readTree(userText.substring(userText.indexOf('\n') + 1));
            assertEquals("[21]", review.path("redStreaksToSkip").toString());
            assertEquals("[-4,2,-5]", review.path("blueHistory").path("currentSteps").toString());
            var firstMatch = review.path("blueHistory").path("matches").get(0);
            assertEquals("2026114", firstMatch.path("startPeriod").asText());
            assertEquals("2026118", firstMatch.path("nextPeriod").asText());
            assertEquals(13, firstMatch.path("stepDistance").asInt());
            var zones = review.path("zoneHistory");
            assertEquals(30, zones.size());
            var lastZones = zones.get(29);
            assertEquals("2026134", lastZones.path("period").asText());
            assertEquals("[3,2,1]", lastZones.path("redZoneCounts").toString());
            assertEquals(2, lastZones.path("blueZone").asInt());
            assertEquals(1, zones.get(2).path("blueZone").asInt());
            assertEquals(3, zones.get(18).path("blueZone").asInt());
            assertEquals(4, zones.get(3).path("blueZone").asInt());
            if ("chart-candidate".equals(scenario)) {
                var projected = review.path("redLineCandidates").get(0);
                assertEquals(17, projected.path("evidence").get(2).path("number").asInt());
            }
            assertEquals(images.getFirst().dataUrl(), content.get(1).path("image_url").path("url").asText());
            assertEquals(images.getLast().dataUrl(), content.get(2).path("image_url").path("url").asText());
        } finally { server.stop(0); }
    }

    private static List<NumberTrendModelService.Draw> screenshotDraws() {
        var blues = List.of(8, 14, 4, 16, 6, 14, 5, 11, 11, 10, 3, 12, 4, 14, 4,
            3, 2, 9, 12, 9, 11, 12, 12, 11, 5, 2, 15, 11, 13, 8);
        return IntStream.range(0, blues.size()).mapToObj(index -> {
            var reds = switch (index) {
                case 27 -> List.of(5, 13, 17, 18, 21, 26);
                case 28 -> List.of(7, 10, 11, 14, 21, 26);
                case 29 -> List.of(4, 5, 10, 17, 21, 32);
                default -> List.of(1, 4, 9, 14, 17, 26);
            };
            int blue = blues.get(index);
            return new NumberTrendModelService.Draw(Integer.toString(2026105 + index), reds, blue,
                IntStream.rangeClosed(1, 33).mapToObj(number -> reds.contains(number) ? 0 : 1).toList(),
                IntStream.rangeClosed(1, 16).mapToObj(number -> number == blue ? 0 : 1).toList());
        }).toList();
    }

    @Test
    void hybridRecommendationKeepsDuplicateTicketsInTheirOriginalSlots() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.incrementAndGet();
            String content = """
                    {"optimizedTicket":{"redBalls":[1,6,12,18,23,33],"blueBall":9,"reason":"保留冷热分布"},
                     "recommendedTickets":[
                      {"redBalls":[30,24,18,13,8,3],"blueBall":5,"reason":"参考奇偶"},
                      {"redBalls":[8,12,15,21,26,31],"blueBall":2,"reason":"参考路数"}],
                     "fullMatchTickets":[
                      {"redBalls":[3,8,13,18,24,30],"blueBall":5,"reason":"命中主推"},
                      {"redBalls":[8,12,15,21,26,31],"blueBall":2,"reason":"命中备选"}],
                     "complexTicket":{"redBalls":[1,6,12,18,23,28,33],"blueBalls":[4,9],"reason":"覆盖两个蓝球区间"}}
                    """;
            byte[] body = response("stop", content);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var client = new LlmClientConfiguration().lotteryPatternChatClient("fixture-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode",
                http, Duration.ofSeconds(5), "mock-qwen", 0.2, 2048);
            var bands = new HybridRecommendationInput.TemperaturePool(List.of(1), List.of(2), List.of(3));
            var input = new HybridRecommendationInput("2026133", List.of(
                new HybridRecommendationInput.Forecast("redZones", "红球三区比", "一区:二区:三区", List.of("2:2:2"))),
                bands, bands, Map.of(), new HybridRecommendationInput.OptimizationSeed(List.of(1, 6, 12, 18, 23, 30), 4, true));
            var service = new HybridRecommendationModelService(client, 4096);
            var result = service.recommend(input);
            var tickets = result.getAdjustedTickets();
            assertEquals(1, requests.get());
            assertEquals(List.of(9, 5, 2, 5, 2), tickets.stream().map(ticket -> ticket.getAdjustedBlueBall()).toList());
            assertEquals(tickets.get(1).getAdjustedRedBalls(), tickets.get(3).getAdjustedRedBalls());
            assertEquals(tickets.get(2).getAdjustedRedBalls(), tickets.get(4).getAdjustedRedBalls());
            assertEquals("参考奇偶", tickets.get(1).getReason());
            assertEquals("命中主推", tickets.get(3).getReason());
            assertEquals(14, result.getFinalRecommendation().getComplexTicket().getTotalBets());
        } finally { server.stop(0); }
    }

    @Test void frameworkClientCarriesMediaAndIndependentVisionSettings() throws Exception {
        var payload = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            payload.set(MAPPER.readTree(exchange.getRequestBody()));
            byte[] response = response("stop", ZoneTrendInferenceParserTest.VALID);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var properties = properties(server);
            properties.setModel("vision-contract-test");
            properties.setMaxTokens(3072);
            var client = new LlmClientConfiguration().lotteryZoneTrendChatClient(properties, http);
            var media = new Media(MimeTypeUtils.IMAGE_PNG, URI.create("data:image/png;base64,aGVsbG8="));
            assertEquals(ZoneTrendInferenceParserTest.VALID,
                client.prompt().user(u -> u.text("逐期特征测试").media(media)).call().content());
            assertEquals("vision-contract-test", payload.get().path("model").asText());
            assertEquals(3072, payload.get().path("max_tokens").asInt());
            assertTrue(payload.get().has("enable_thinking"));
            assertFalse(payload.get().path("enable_thinking").asBoolean());
            assertEquals(media.getData(), payload.get().path("messages").get(1).path("content").get(1).path("image_url").path("url").asText());
            assertTrue(payload.get().path("messages").get(0).path("content").asText().contains("三区个数与三区比"));
            assertTrue(payload.get().path("messages").get(0).path("content").asText().contains("根据提供的走势图推演下一期特征"));
        } finally { server.stop(0); }
    }

    @Test void featureAnalysisSendsTheFeaturePromptWithFactsAndCharts() throws Exception {
        var payload = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
                payload.set(MAPPER.readTree(exchange.getRequestBody()));
                byte[] body = response("stop", """
                    {"observation":"整体出现间隔不同","candidates":[
                      {"value":"2：2：2｜1：0：1｜0：1：1｜1：1：0","reason":"对照近期走势",
                       "caution":"单区转向会改变组合","evidencePeriods":["2026002"]}],
                     "counterEvidence":"没有完全相同的走势片段"}
                    """);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            try (var http = HttpClient.newHttpClient()) {
                var group = new FeatureTrendInput.Group("coldHot", "冷温热个数", "沿用系统分类", false, 0, 6,
                    List.of(new FeatureTrendInput.Series("cold", "冷球", List.of(1, 2))));
                var input = new FeatureTrendInput("2026002", SNAPSHOT.periods(), List.of(group));
                var image = new ZoneTrendImage("coldHot-1", "data:image/png;base64,aGVsbG8=");
                var result = service(properties(server), http)
                    .analyzeFeature(FeatureTrendKind.RED_TEMPERATURE, input, List.of(image));
                assertEquals("2:2:2|1:0:1|0:1:1|1:1:0", result.candidates().getFirst().value());
                var messages = payload.get().path("messages");
                assertTrue(messages.get(0).path("content").asText().contains("形态内部和之后的历史片段"));
                var content = messages.get(1).path("content");
                assertTrue(content.get(0).path("text").asText().contains("每组按冷:温:热"));
                assertTrue(content.get(0).path("text").asText().contains("\"values\":[1,2]"));
                assertEquals(image.dataUrl(), content.get(1).path("image_url").path("url").asText());
            }
        } finally { server.stop(0); }
    }

    @Test void sendsImagesAndBackendFactsInOneAuthenticatedVisionRequest() throws Exception {
        var payload = new AtomicReference<JsonNode>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            payload.set(MAPPER.readTree(exchange.getRequestBody()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = response("stop", ZoneTrendInferenceParserTest.NATURAL_TREND);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var properties = properties(server);
            var service = service(properties, http);
            var images = List.of("counts", "ratios-1", "ratios-2", "ratios-3", "ratios-4").stream()
                .map(code -> new ZoneTrendImage(code, "data:image/png;base64," + code)).toList();
            var result = service.analyze(SNAPSHOT, images);
            assertEquals("2:2:2", result.candidates().getFirst().ratio());
            assertTrue(result.countTrends().get(2).nextInference().contains("大概率反弹"));
            assertEquals("Bearer local-vision-key", authorization.get());
            assertEquals("qwen3-vl-plus", payload.get().path("model").asText());
            assertEquals(4096, payload.get().path("max_tokens").asInt());
            assertTrue(payload.get().has("enable_thinking"));
            assertFalse(payload.get().path("enable_thinking").asBoolean());
            var content = payload.get().path("messages").path(1).path("content");
            assertEquals(6, content.size());
            var text = content.get(0).path("text").asText();
            assertTrue(text.contains("2026002"));
            assertTrue(text.contains("\"values\":[1,2]"));
            for (int i = 0; i < images.size(); i++) {
                assertEquals("image_url", content.get(i+1).path("type").asText());
                assertEquals(images.get(i).dataUrl(), content.get(i+1).path("image_url").path("url").asText());
            }
            assertFalse(payload.get().toString().contains("local-vision-key"));
        } finally { server.stop(0); }
    }

    @Test void rejectsTruncatedAndEmptyChoicesInsteadOfInventingFallbackForecast() throws Exception {
        for (String finishReason : List.of("length", "stop")) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] response = response(finishReason, "length".equals(finishReason) ? ZoneTrendInferenceParserTest.VALID : "");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            try (var http = HttpClient.newHttpClient()) {
                assertThrows(IllegalStateException.class, () -> service(properties(server), http).analyze(SNAPSHOT, List.of()));
            } finally { server.stop(0); }
        }
    }

    @Test void frameworkProviderFailuresAreSanitizedAndDoNotTriggerNestedRetries() throws Exception {
        for (int status : List.of(401, 503)) {
            var calls = new AtomicInteger();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
                calls.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                byte[] body = "{\"error\":{\"message\":\"provider-internal-detail\",\"type\":\"service_error\"}}"
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("x-request-id", "vision-error-" + status);
                exchange.sendResponseHeaders(status, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            try (var http = HttpClient.newHttpClient()) {
                var failure = assertThrows(IllegalStateException.class,
                    () -> service(properties(server), http).analyze(SNAPSHOT, List.of()));
                assertTrue(failure.getMessage().contains("视觉模型请求失败"));
                assertFalse(failure.getMessage().contains("provider-internal-detail"));
                assertEquals(1, calls.get());
            } finally { server.stop(0); }
        }
    }

    @Test void missingKeyIsReportedAsConfigurationFailure() {
        var properties = new ZoneTrendModelProperties();
        try (var http = HttpClient.newHttpClient()) {
            assertTrue(assertThrows(IllegalStateException.class,
                () -> service(properties, http).analyze(SNAPSHOT, List.of())).getMessage().contains("DASHSCOPE_API_KEY"));
        }
    }

    private static ZoneTrendModelService service(ZoneTrendModelProperties properties, HttpClient http) {
        return new ZoneTrendModelService(new VisionModelClient(
            new LlmClientConfiguration().lotteryZoneTrendChatClient(properties, http), properties));
    }

    private static ZoneTrendModelProperties properties(HttpServer server) {
        var properties = new ZoneTrendModelProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode");
        properties.setApiKey("local-vision-key");
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static byte[] response(String finishReason, String content) {
        try {
            return MAPPER.writeValueAsString(Map.of("id", "vision-mock-response",
                "usage", Map.of("prompt_tokens", 120, "completion_tokens", 30, "total_tokens", 150),
                "choices", List.of(Map.of("index", 0, "finish_reason", finishReason,
                    "message", Map.of("role", "assistant", "content", content))))).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
