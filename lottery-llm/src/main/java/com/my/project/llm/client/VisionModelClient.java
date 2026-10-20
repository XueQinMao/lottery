package com.my.project.llm.client;

import com.my.project.llm.bo.ZoneTrendImage;
import com.my.project.llm.config.ZoneTrendModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClientException;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class VisionModelClient {
    private final ChatClient client;
    private final ZoneTrendModelProperties properties;

    public VisionModelClient(@Qualifier("lotteryZoneTrendChatClient") ChatClient client, ZoneTrendModelProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public String modelIdentity() {
        return properties.getBaseUrl() + "|" + properties.getModel() + "|" + properties.getMaxTokens();
    }

    public String modelName() { return properties.getModel(); }

    public <T> T call(String feature, String endPeriod, List<ZoneTrendImage> images,
                          Function<ChatClientRequestSpec, ChatClientRequestSpec> configure, Function<String, T> parse) {
        return call(feature, endPeriod, images, properties.getMaxTokens(), configure, parse);
    }

    public <T> T call(String feature, String endPeriod, List<ZoneTrendImage> images, int maxOutputTokens,
            Function<ChatClientRequestSpec, ChatClientRequestSpec> configure, Function<String, T> parse) {
        Assert.state(properties.getApiKey() != null && !properties.getApiKey().isBlank(),
            "请配置 DASHSCOPE_API_KEY 或 LOTTERY_ZONE_TREND_API_KEY，并开通视觉模型");
        var media = images.stream().map(image -> new Media(MimeTypeUtils.IMAGE_PNG, URI.create(image.dataUrl())))
            .toArray(Media[]::new);
        String callId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        int tokenLimit = Math.min(maxOutputTokens, properties.getMaxTokens());
        log.info("llm.vision.start callId={} feature={} endPeriod={} model={} imageCount={} maxOutputTokens={}",
            callId, feature, endPeriod, properties.getModel(), media.length, tokenLimit);

        ChatResponse response;
        try (var ignored = MDC.putCloseable("llmVisionCallId", callId)) {
            var request = client.prompt();
            if (tokenLimit < properties.getMaxTokens()) {
                request.options(OpenAiChatOptions.builder().maxTokens(tokenLimit).build());
            }
            response = configure.apply(request).user(user -> user.media(media)).call().chatResponse();
        } catch (RestClientException | TransientAiException | NonTransientAiException e) {
            log.warn("llm.vision.failed callId={} feature={} endPeriod={} model={} elapsedMs={} stage=provider exception={}",
                callId, feature, endPeriod, properties.getModel(), elapsedMs(started), e.getClass().getSimpleName());
            throw new IllegalStateException("视觉模型请求失败，请检查模型配置、额度和网络后重试", e);
        }

        logUsage(callId, response, started);
        try {
            T result = parse.apply(requireContent(response));
            log.info("llm.vision.completed callId={} elapsedMs={}", callId, elapsedMs(started));
            return result;
        } catch (IllegalStateException e) {
            log.warn("llm.vision.failed callId={} elapsedMs={} stage=incomplete_response", callId, elapsedMs(started));
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("llm.vision.failed callId={} feature={} endPeriod={} elapsedMs={} stage=output_validation reason={}",
                callId, feature, endPeriod, elapsedMs(started), e.getMessage());
            throw new IllegalStateException("视觉模型结果未通过校验：" + e.getMessage(), e);
        }
    }

    private static void logUsage(String callId, ChatResponse response, long started) {
        var metadata = response == null ? null : response.getMetadata();
        var usage = metadata == null || metadata.getUsage() instanceof EmptyUsage ? null : metadata.getUsage();
        var result = firstGeneration(response);
        var finishReason = result == null || result.getMetadata() == null ? null : result.getMetadata().getFinishReason();
        String responseId = metadata == null ? null : metadata.getId();
        log.info("llm.vision.response callId={} responseId={} finishReason={} elapsedMs={} inputTokens={} outputTokens={}",
            callId, responseId, finishReason, elapsedMs(started),
            usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens());
    }

    private static long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String requireContent(ChatResponse response) {
        var result = firstGeneration(response);
        if (result == null || result.getMetadata() == null
                || !"stop".equalsIgnoreCase(result.getMetadata().getFinishReason())
                || result.getOutput() == null || !StringUtils.hasText(result.getOutput().getText())) {
            throw new IllegalStateException("视觉模型返回空响应或输出被截断，本次未生成预测，请重试");
        }
        return result.getOutput().getText();
    }

    private static Generation firstGeneration(ChatResponse response) {
        return response == null || CollectionUtils.isEmpty(response.getResults()) ? null : response.getResult();
    }
}
