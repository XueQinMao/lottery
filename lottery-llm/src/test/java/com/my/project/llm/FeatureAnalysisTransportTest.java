package com.my.project.llm;

import com.my.project.llm.config.LlmClientConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class FeatureAnalysisTransportTest {
    @Test
    void sendsBailianCompatibleRequestWithCheapModelThinkingDisabledAndTokenLimit() throws Exception {
        var payload = new AtomicReference<JsonNode>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            payload.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                {"id":"local-test","object":"chat.completion","created":1,"model":"qwen-flash",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"local result"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":3,"total_tokens":13}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var client = new LlmClientConfiguration().lotteryPatternChatClient("local-test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode", http,
                Duration.ofSeconds(2), "qwen-flash", 0.2, 2048);
            assertEquals("local result", client.prompt().user("test").call().content());
            assertEquals("Bearer local-test-key", authorization.get());
            assertEquals("qwen-flash", payload.get().path("model").asText());
            assertTrue(payload.get().has("enable_thinking"));
            assertFalse(payload.get().path("enable_thinking").asBoolean());
            assertEquals(2048, payload.get().path("max_tokens").asInt());
            assertFalse(payload.get().path("stream").asBoolean());
            assertTrue(payload.get().path("messages").size() >= 2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingBailianKeyExplainsTheRequiredEnvironmentVariable() {
        try (var http = HttpClient.newHttpClient()) {
            var error = assertThrows(IllegalArgumentException.class, () ->
                new LlmClientConfiguration().lotteryPatternChatClient("", "http://127.0.0.1", http,
                    Duration.ofSeconds(1), "qwen-flash", 0.2, 2048));
            assertTrue(error.getMessage().contains("DASHSCOPE_API_KEY"));
        }
    }

    @Test
    void delegatesOneHttpAttemptToTheApplicationRatherThanNestingSdkRetries() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"error\":{\"message\":\"busy\",\"type\":\"service_unavailable_error\"}}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            var client = new LlmClientConfiguration().lotteryPatternChatClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(), http, Duration.ofSeconds(2),
                "qwen-flash", 0.2, 2048);
            assertThrows(TransientAiException.class, () -> client.prompt().user("test").call().chatResponse());
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void boundsTheEntireBodyEvenWhenProviderKeepsTheConnectionAliveWithWhitespace() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor();
             var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            server.setExecutor(workers);
            server.createContext("/v1/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                try (var output = exchange.getResponseBody()) {
                    for (int index = 0; index < 80; index++) {
                        output.write(' ');
                        output.flush();
                        Thread.sleep(Duration.ofMillis(25));
                    }
                    output.write("{}".getBytes(StandardCharsets.UTF_8));
                } catch (IOException expectedDisconnect) {
                    // The request deadline closes the response while this handler is writing.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            var client = new LlmClientConfiguration().lotteryPatternChatClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(), http, Duration.ofMillis(200),
                "qwen-flash", 0.2, 2048);
            Instant start = Instant.now();
            assertThrows(RuntimeException.class, () -> client.prompt().user("test").call().chatResponse());
            assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(2)) < 0,
                "keep-alive bytes must not restart the total response deadline");
        } finally {
            server.stop(0);
        }
    }
}
