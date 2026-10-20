package com.my.project.llm.service;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAdjustRespBo.TicketCategory;
import com.my.project.llm.prompt.HybridRecommendationPrompt;
import com.my.project.llm.support.LotteryTicketValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
public class HybridRecommendationModelService {
    private static final Logger PAYLOAD_LOG = LoggerFactory.getLogger("llm.recommend.payload");
    private static final JsonMapper MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final Pattern JSON_FENCE = Pattern.compile("^```(?:json)?[ \\t]*\\R", Pattern.CASE_INSENSITIVE);

    private final ChatClient client;
    private final OpenAiChatOptions responseOptions;

    public HybridRecommendationModelService(@Qualifier("lotteryPatternChatClient") ChatClient client,
            @Value("${lottery.llm.recommendation.max-tokens:4096}") int maxTokens) {
        Assert.isTrue(maxTokens > 0, "推荐 max-tokens 必须大于0");
        this.client = client;
        this.responseOptions = OpenAiChatOptions.builder().maxTokens(maxTokens)
            .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, (String) null)).build();
    }

    public LotteryAdjustRespBo recommend(HybridRecommendationInput input) {
        log.info("llm.recommend.start targetPeriod={} forecastCount={} optimizationFromCache={}",
            input.targetPeriod(), input.forecasts().stream().filter(item -> !item.values().isEmpty()).count(),
            input.optimizationSeed().fromCache());
        String instructions = HybridRecommendationPrompt.SYSTEM;
        var trendReds = input.redTrendNumbers();
        if (!trendReds.isEmpty()) {
            instructions += HybridRecommendationPrompt.NUMBER_TREND;
        }
        String content = requestContent(input, instructions, "initial");
        try {
            var result = parseRecommendation(input, content);
            if (result.getAdjustedTickets().stream().anyMatch(ticket -> usesOnlyTrendReds(ticket, trendReds))) {
                String retry = requestContent(input, instructions + HybridRecommendationPrompt.NUMBER_TREND_RETRY,
                    "trend_retry");
                replaceTrendOnlyTickets(result, parseRecommendation(input, retry), trendReds);
            }
            return result;
        } catch (IllegalStateException failure) {
            log.warn("llm.recommend.failed targetPeriod={} stage=validation reason={}", input.targetPeriod(), failure.getMessage());
            throw new IllegalStateException("推荐模型结果未通过校验：" + failure.getMessage(), failure);
        }
    }

    private static boolean usesOnlyTrendReds(LotteryAdjustRespBo.AdjustedTicket ticket, Set<Integer> trendReds) {
        return ticket.getCategory() == TicketCategory.RECOMMENDED && trendReds.containsAll(ticket.getAdjustedRedBalls());
    }

    private static void replaceTrendOnlyTickets(LotteryAdjustRespBo original, LotteryAdjustRespBo retry,
                                                Set<Integer> trendReds) {
        var tickets = new ArrayList<>(original.getAdjustedTickets());
        for (int index = 0; index < tickets.size(); index++) {
            if (!usesOnlyTrendReds(tickets.get(index), trendReds)) continue;
            var replacement = retry.getAdjustedTickets().get(index);
            require(!usesOnlyTrendReds(replacement, trendReds), "每组普通推荐至少包含 1 个走势图候选之外的红球");
            tickets.set(index, replacement);
        }
        original.setAdjustedTickets(List.copyOf(tickets));
    }

    private String requestContent(HybridRecommendationInput input, String instructions, String phase) {
        String targetPeriod = input.targetPeriod();
        long started = System.nanoTime();
        ChatResponse response;
        try {
            String userInput = JSON.toJSONString(input);
            PAYLOAD_LOG.info("request targetPeriod={} phase={} maxOutputTokens={} responseFormat=json_object system={} input={}",
                targetPeriod, phase, responseOptions.getMaxTokens(), JSON.toJSONString(instructions), userInput);
            response = client.prompt().options(responseOptions).system(prompt -> prompt.text("{instructions}")
                    .param("instructions", instructions))
                .user(prompt -> prompt.text("{input}").param("input", userInput))
                .call().chatResponse();
        } catch (RuntimeException failure) {
            log.warn("llm.recommend.failed targetPeriod={} phase={} stage=provider elapsedMs={} exception={}",
                targetPeriod, phase, elapsedMs(started), failure.getClass().getSimpleName());
            throw new RecommendationRequestException("推荐模型请求失败，请检查模型配置、额度或网络后重试", failure);
        }
        var metadata = response == null ? null : response.getMetadata();
        var usage = metadata == null ? null : metadata.getUsage();
        var result = response == null || CollectionUtils.isEmpty(response.getResults())
            ? null : response.getResult();
        var finishReason = result == null || result.getMetadata() == null ? null : result.getMetadata().getFinishReason();
        String responseId = metadata == null ? null : metadata.getId();
        String content = result == null || result.getOutput() == null ? null : result.getOutput().getText();
        PAYLOAD_LOG.info("response targetPeriod={} phase={} responseId={} finishReason={} output={}",
            targetPeriod, phase, responseId, finishReason, JSON.toJSONString(content));
        log.info("llm.recommend.response targetPeriod={} phase={} model={} responseId={} finishReason={} elapsedMs={} inputTokens={} outputTokens={} maxOutputTokens={}",
            targetPeriod, phase, metadata == null ? null : metadata.getModel(), responseId, finishReason, elapsedMs(started),
            usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens(),
            responseOptions.getMaxTokens());
        if ("length".equalsIgnoreCase(finishReason)) {
            throw new RecommendationRequestException("推荐模型达到 %d token 输出上限，结果被截断"
                .formatted(responseOptions.getMaxTokens()), null);
        }
        return content;
    }

    private static Response parseResponse(String content) {
        require(StringUtils.isNotBlank(content), "模型返回内容为空");
        String json = content.strip();
        var fence = JSON_FENCE.matcher(json);
        if (fence.find()) {
            json = json.substring(fence.end()).strip();
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3).stripTrailing();
        }
        try {
            if (json.startsWith("[")) {
                var array = MAPPER.readValue(json, ArrayNode.class);
                return MAPPER.treeToValue(wrapRecommendationArray(array), Response.class);
            }
            return MAPPER.readValue(json, Response.class);
        } catch (JsonProcessingException failure) {
            boolean incomplete = failure instanceof JsonEOFException || (json.startsWith("{") && !json.endsWith("}"));
            throw new IllegalStateException(incomplete ? "推荐模型返回的 JSON 不完整" : "模型返回的 JSON 格式不正确", failure);
        }
    }

    private static ObjectNode wrapRecommendationArray(ArrayNode array) {
        require(array.size() == HybridRecommendationInput.SINGLE_COUNT + 1,
            "模型返回的推荐数组必须包含五组单式和一组复式");
        // 模型按提示词中的组别顺序输出扁平数组时，位置对应各票组。
        var object = MAPPER.createObjectNode();
        object.set("optimizedTicket", array.get(0));
        object.putArray("recommendedTickets").add(array.get(1)).add(array.get(2));
        object.putArray("fullMatchTickets").add(array.get(3)).add(array.get(4));
        object.set("complexTicket", array.get(5));
        return object;
    }

    private static LotteryAdjustRespBo parseRecommendation(HybridRecommendationInput input, String content) {
        var response = parseResponse(content);
        require(response != null && response.optimizedTicket() != null, "缺少优化号码");
        require(response.recommendedTickets() != null
            && response.recommendedTickets().size() == HybridRecommendationInput.RECOMMENDED_COUNT, "推荐号码必须为两组");
        require(response.fullMatchTickets() != null
            && response.fullMatchTickets().size() == HybridRecommendationInput.FULL_MATCH_COUNT, "全特征命中号码必须为两组");

        var singles = new ArrayList<LotteryAdjustRespBo.AdjustedTicket>(HybridRecommendationInput.SINGLE_COUNT);
        var optimizedTicket = single(response.optimizedTicket(), TicketCategory.OPTIMIZED);
        var seed = input.optimizationSeed();
        optimizedTicket.setOriginalRedBalls(seed.redBalls());
        optimizedTicket.setOriginalBlueBall(seed.blueBall());
        singles.add(optimizedTicket);
        response.recommendedTickets().forEach(ticket -> singles.add(single(ticket, TicketCategory.RECOMMENDED)));
        response.fullMatchTickets().forEach(ticket -> singles.add(single(ticket, TicketCategory.FULL_MATCH)));

        require(response.complexTicket() != null, "缺少复式");
        var complex = new LotteryAdjustRespBo.ComplexTicket();
        complex.setName("复式推荐");
        complex.setRedBalls(sorted(response.complexTicket().redBalls()));
        complex.setBlueBalls(sorted(response.complexTicket().blueBalls()));
        LotteryTicketValidator.numbers(complex.getRedBalls(), 7, 10, 33);
        LotteryTicketValidator.numbers(complex.getBlueBalls(), 2, 5, 16);
        complex.setTotalBets(LotteryTicketValidator.combinations(complex.getRedBalls().size(), 6) * complex.getBlueBalls().size());
        complex.setBasis(reason(response.complexTicket().reason()));

        var pack = new LotteryAdjustRespBo.FinalRecommendation();
        pack.setSingleTickets(List.of());
        pack.setComplexTicket(complex);
        var result = new LotteryAdjustRespBo();
        result.setAdjustedTickets(List.copyOf(singles));
        result.setFinalRecommendation(pack);
        return result;
    }

    private static LotteryAdjustRespBo.AdjustedTicket single(Single numbers, TicketCategory category) {
        require(numbers != null, "单式为空");
        var sorted = sorted(numbers.redBalls());
        LotteryTicketValidator.checkSingle(sorted, numbers.blueBall());
        var ticket = new LotteryAdjustRespBo.AdjustedTicket();
        ticket.setCategory(category);
        ticket.setAdjustedRedBalls(sorted);
        ticket.setAdjustedBlueBall(numbers.blueBall());
        ticket.setReason(reason(numbers.reason()));
        return ticket;
    }

    private static String reason(String text) {
        require(StringUtils.isNotBlank(text), "缺少推荐理由");
        return text.strip();
    }

    private static List<Integer> sorted(List<Integer> numbers) {
        require(numbers != null && numbers.stream().noneMatch(Objects::isNull), "号码列表为空或包含空号码");
        return numbers.stream().sorted().toList();
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalStateException(error);
    }

    private static long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private record Response(Single optimizedTicket, List<Single> recommendedTickets,
                            List<Single> fullMatchTickets, Complex complexTicket) { }
    private record Single(List<Integer> redBalls, Integer blueBall, String reason) { }
    private record Complex(List<Integer> redBalls, List<Integer> blueBalls, String reason) { }
    private static final class RecommendationRequestException extends RuntimeException {
        private RecommendationRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
