package com.my.project.llm.config;

import org.springframework.ai.chat.client.ChatClient;
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
import org.slf4j.MDC;

/**
 * LlmClientConfiguration
 *
 * <p>特征分析和号码推荐使用百炼兼容接口，关闭深度思考。
 *
 * <p>提供两个 ChatClient：
 * <ul>
 *     <li>{@code lotteryPatternChatClient}：文本特征分析和号码推荐</li>
 *     <li>{@code lotteryZoneTrendChatClient}：三区走势图多模态分析</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/13
 **/
@Configuration
@Slf4j
public class LlmClientConfiguration {
    private static final ObjectMapper PROVIDER_MAPPER = new ObjectMapper();

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
        log.info("特征分析客户端：平台=百炼，模型={}，深度思考=关闭，输出上限={} tokens", model, maxTokens);
        return bailianChatClient(new BailianClientSettings(apiKey, baseUrl, readTimeout, model, temperature,
            maxTokens, com.my.project.llm.prompt.LotteryPatternAnalysisPrompt.SYSTEM), httpClient);
    }

    @Bean
    public ChatClient lotteryZoneTrendChatClient(ZoneTrendModelProperties properties,
            @Qualifier("featureAnalysisHttpClient") HttpClient httpClient) {
        Assert.isTrue(properties.getMaxTokens() > 0, "三区走势分析 max-tokens 必须大于0");
        return bailianChatClient(new BailianClientSettings(properties.getApiKey(), properties.getBaseUrl(),
            properties.getReadTimeout(), properties.getModel(), 0.2, properties.getMaxTokens(),
            com.my.project.llm.prompt.ZoneTrendPrompt.SYSTEM), httpClient);
    }

    private static ChatClient bailianChatClient(BailianClientSettings settings, HttpClient httpClient) {
        // Spring's JDK transport bounds the complete response, including a body
        // kept alive by whitespace while the provider waits for capacity.
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        // Spring AI 1.0 options do not expose vendor-specific extra_body fields.
        // This dedicated client adds the DashScope option at the JSON root.
        var restClient = RestClient.builder().requestFactory(requestFactory)
            .requestInterceptor((request, body, execution) -> {
                var payload = (ObjectNode) PROVIDER_MAPPER.readTree(body);
                payload.put("enable_thinking", false);
                byte[] json = PROVIDER_MAPPER.writeValueAsBytes(payload);
                request.getHeaders().setContentLength(json.length);
                var response = execution.execute(request, json);
                String callId = MDC.get("llmVisionCallId");
                if (callId != null && response.getStatusCode().isError()) {
                    log.warn("llm.vision.http_error callId={} httpStatus={} providerRequestId={}",
                        callId, response.getStatusCode().value(), response.getHeaders().getFirst("x-request-id"));
                }
                return response;
            });
        var api = OpenAiApi.builder().apiKey(settings.apiKey()).baseUrl(settings.baseUrl())
            .completionsPath("/v1/chat/completions").restClientBuilder(restClient).build();
        // 重试由调用方决定，避免模型客户端自动补发计费请求。
        var options = OpenAiChatOptions.builder().model(settings.model()).temperature(settings.temperature())
            .maxTokens(settings.maxTokens()).build();
        var featureModel = OpenAiChatModel.builder().openAiApi(api)
            .defaultOptions(options)
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
        return ChatClient.builder(featureModel)
            .defaultOptions(options)
            .defaultSystem(settings.systemPrompt()).build();
    }

    private record BailianClientSettings(String apiKey, String baseUrl, Duration readTimeout, String model,
                                         Double temperature, Integer maxTokens, String systemPrompt) { }

}
