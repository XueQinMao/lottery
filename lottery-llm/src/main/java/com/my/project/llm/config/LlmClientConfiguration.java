package com.my.project.llm.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import lombok.extern.slf4j.Slf4j;

/**
 * LlmClientConfiguration
 *
 * <p>特征分析使用百炼兼容接口与 qwen-flash，关闭深度思考。
 * 调优/推荐沿用自动配置的 DeepSeek 模型。
 *
 * <p>提供两个 ChatClient：
 * <ul>
 *     <li>{@code lotteryPatternChatClient}：独立超时与重试策略的特征分析</li>
 *     <li>{@code lotteryAdjustChatClient}：调优（有候选）或推荐（无候选），输出 Schema 相同</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/13
 **/
@Configuration
@Slf4j
public class LlmClientConfiguration {

    @Bean(destroyMethod = "close")
    public HttpClient featureAnalysisHttpClient(
            @Value("${lottery.llm.analysis.connect-timeout:10s}") Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Bean
    public ChatClient lotteryPatternChatClient(
            @Value("${lottery.llm.analysis.api-key:}") String apiKey,
            @Value("${lottery.llm.analysis.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
            @Qualifier("featureAnalysisHttpClient") HttpClient httpClient,
            @Value("${lottery.llm.analysis.read-timeout:120s}") Duration readTimeout,
            @Value("${lottery.llm.analysis.model:qwen-flash}") String model,
            @Value("${lottery.llm.analysis.temperature:0.2}") Double temperature,
            @Value("${lottery.llm.analysis.max-tokens:2048}") Integer maxTokens) {
        Assert.hasText(apiKey, "特征分析使用百炼，请设置环境变量 DASHSCOPE_API_KEY");
        Assert.isTrue(maxTokens != null && maxTokens > 0, "特征分析 max-tokens 必须大于0");
        // Spring's JDK transport bounds the complete response, including a body
        // kept alive by whitespace while the provider waits for capacity.
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        // Spring AI 1.0 options do not expose vendor-specific extra_body fields.
        // This dedicated client adds the DashScope option at the JSON root.
        var mapper = new ObjectMapper();
        var restClient = RestClient.builder().requestFactory(requestFactory)
            .requestInterceptor((request, body, execution) -> {
                var payload = (ObjectNode) mapper.readTree(body);
                payload.put("enable_thinking", false);
                byte[] json = mapper.writeValueAsBytes(payload);
                request.getHeaders().setContentLength(json.length);
                return execution.execute(request, json);
            });
        var api = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl)
            .completionsPath("/v1/chat/completions").restClientBuilder(restClient).build();
        // Retry only at the feature-call boundary, including HTTP-200 empty responses.
        // Leave the recommendation model's settings and retry policy unchanged.
        var options = OpenAiChatOptions.builder().model(model).temperature(temperature).maxTokens(maxTokens).build();
        var featureModel = OpenAiChatModel.builder().openAiApi(api)
            .defaultOptions(options)
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
        log.info("特征分析客户端：平台=百炼，模型={}，深度思考=关闭，输出上限={} tokens", model, maxTokens);
        return ChatClient.builder(featureModel)
            .defaultOptions(options)
            .defaultSystem(com.my.project.llm.prompt.LotteryPatternAnalysisPrompt.SYSTEM).build();
    }

    @Bean
    public ChatClient lotteryAdjustChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        You generate or adjust SSQ tickets using featureAnalysis, killNumbers,
                        coldHotAnalysis and recentDrawRecords. Follow the Chinese user prompt.
                        First independently analyze chronological draw history across long and short windows,
                        then predict the next draw using supported number and structure trends.
                        Cross-check feature pools against raw history. Include concise, verifiable trend
                        evidence in reason/basis/conclusion, not a step-by-step reasoning transcript.
                        Pattern pools are preferences, never guaranteed outcomes. Ignore unavailable items.
                        Return only JSON matching the supplied schema, including adjustedTickets,
                        finalRecommendation with exactly two singles and one complex ticket, and conclusion.
                        """)
                .build();
    }
}
