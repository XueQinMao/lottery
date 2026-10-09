package com.my.project.llm.service.impl;

import com.my.project.llm.prompt.LotteryPatternAnalysisPrompt;
import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import com.my.project.llm.support.PatternAnalysisParser;
import com.my.project.llm.service.LotteryAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Service;
import java.time.Duration;

/**
 * LotteryAnalysisServiceImpl
 *
 * <p>负责单形态模型调用、有限重试和输出校验；特征请求由应用层串行调度。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
@Slf4j
@Service
public class LotteryAnalysisServiceImpl implements LotteryAnalysisService {

    private final ChatClient lotteryPatternChatClient;

    @Value("${lottery.llm.analysis.retry-delay:2s}")
    private Duration retryDelay = Duration.ofSeconds(2);

    private static final int MAX_ATTEMPTS = 2;

    public LotteryAnalysisServiceImpl(@Qualifier("lotteryPatternChatClient") ChatClient lotteryPatternChatClient) {
        this.lotteryPatternChatClient = lotteryPatternChatClient;
    }

    @Override
    public synchronized PatternFeatureAnalysisBo analyzePattern(PatternFeatureAnalysisReqBo request) {
        if (request == null || request.getObservations() == null
                || request.getObservations().size() != PatternFeatureAnalysisReqBo.HISTORY_SIZE) {
            throw new IllegalArgumentException("形态描述分析必须提供30期开奖");
        }
        // 确定性特征由 Java 计算；模型基于已校验事实分析走势，避免反复心算出错。
        var draws = request.getObservations().stream().map(o -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("position", o.getPosition());
            row.put("period", o.getPeriod());
            row.put("redBalls", o.getRedBalls());
            row.put("blueBall", o.getBlueBall());
            row.put("value", o.getValue());
            if (o.getPriorColdHot() != null) {
                var prior = o.getPriorColdHot();
                row.put("priorColdHot", "blueColdWarmHot".equals(request.getCode())
                    ? java.util.Map.of("blueHotBalls", prior.getBlueHotBalls(),
                        "blueWarmBalls", prior.getBlueWarmBalls(), "blueColdBalls", prior.getBlueColdBalls())
                    : java.util.Map.of("redHotBalls", prior.getRedHotBalls(),
                        "redWarmBalls", prior.getRedWarmBalls(), "redColdBalls", prior.getRedColdBalls()));
            }
            return row;
        }).toList();
        String correction = "";
        String failureReason = "特征模型调用被中断";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ChatResponse response = null;
            String content = "";
            try {
                if (Thread.currentThread().isInterrupted()) break;
                String feedback = correction.isEmpty() ? "按要求直接输出两个部分。" : correction;
                response = lotteryPatternChatClient.prompt()
                    .user(u -> u.text(LotteryPatternAnalysisPrompt.USER)
                        .param("label", request.getLabel())
                        .param("definition", request.getDefinition())
                        .param("valueFormat", valueFormat(request.getCode()))
                        .param("allowed", com.alibaba.fastjson2.JSON.toJSONString(request.getAllowedValues()))
                        .param("draws", com.alibaba.fastjson2.JSON.toJSONString(draws))
                        .param("featureValues", com.alibaba.fastjson2.JSON.toJSONString(
                            request.getObservations().stream().map(o -> o.getValue()).toList()))
                        .param("correction", feedback))
                    .call().chatResponse();
                content = requireContent(response);
                return PatternAnalysisParser.parse(content, request);
            } catch (Exception e) {
                failureReason = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
                boolean providerFailure = isProviderFailure(e);
                log.warn("形态描述 [{}] 第{}/{}次{}失败: {}", request.getLabel(), attempt, MAX_ATTEMPTS,
                    providerFailure ? "服务调用" : "输出校验", e.getMessage());
                if (!providerFailure && !content.isEmpty()) {
                    String preview = content.substring(0, Math.min(content.length(), 2000))
                        .replace("\r", "\\r").replace("\n", "\\n");
                    log.warn("形态输出诊断 [{}] finishReason={}，字符数={}，原文前2000字符={}",
                        request.getLabel(), response.getResult().getMetadata().getFinishReason(), content.length(), preview);
                }
                if (attempt == MAX_ATTEMPTS || isPermanentFailure(e)) break;
                // Network/service failures are not arithmetic errors. Only validation
                // failures send specific correction feedback to the next model call.
                correction = providerFailure ? "" : "上次回答未通过校验：" + e.getMessage()
                    + "。逐期特征必须按position顺序原样引用输入value，不要重新估算。"
                    + "所有数组只允许双引号字符串。请修正并重新完整输出两个部分；不要仅返回修正项。";
                if (providerFailure && !pauseBeforeRetry()) break;
            }
        }
        return PatternFeatureAnalysisBo.builder().code(request.getCode()).label(request.getLabel())
            .status("UNAVAILABLE").source("llm-pattern").observations(request.getObservations())
            .sampleSize(request.getObservations().size())
            .failureReason(StringUtils.abbreviate(failureReason, 500))
            .patternSummary("特征分析未完成，请重新分析。")
            .likelyValues(java.util.List.of()).unlikelyValues(java.util.List.of()).build();
    }

    private static String requireContent(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()
                || response.getResult().getOutput() == null
                || StringUtils.isBlank(response.getResult().getOutput().getText())) {
            throw new EmptyModelResponseException("特征模型未返回有效choices/content（空响应），不是特征计算结果");
        }
        return response.getResult().getOutput().getText();
    }

    private static String valueFormat(String featureCode) {
        return switch (featureCode) {
            case "span" -> "value已是最大红球减最小红球的精确整数字符串，例如\"21\"，原样引用，不改成区间。";
            case "sumRange" -> "value已是六个红球和值对应的候选闭区间，例如\"97-102\"，原样引用，不改成和值本身。";
            case "sumTail" -> "value已是六个红球和值%10的字符串，原样引用；不是号码尾数。";
            case "tail" -> "value已是0尾到9尾十项计数的冒号连接字符串，合计6，原样引用；候选池填尾数\"0\"到\"9\"，不是计数向量。";
            case "coldWarmHot" -> "value已按该行priorColdHot计算热:温:冷三项计数，合计6，原样引用；禁止用最新池倒推过去。";
            case "zone1Count", "zone2Count", "zone3Count" -> "value是该区当期开出的红球个数，0到6的字符串。按实际个数观察低位维持、短暂上探、回落或跳变；不是出现/未出现标记。";
            case "blueOddEven", "blueBigSmall", "blueBigSmallOddEven", "blueRatio012", "blueColdWarmHot" ->
                "value是单个蓝球的类别标签，直接引用；观察连续停留、交替与类别切换，不做红球比例或数量计算，也不把类别编号当成升降数值。";
            default -> "value已按定义中的先后顺序统计个数，以英文冒号连接，合计6；每期原样引用为一项JSON字符串。";
        };
    }

    private static boolean isProviderFailure(Exception failure) {
        return failure instanceof EmptyModelResponseException || failure instanceof TransientAiException
            || failure instanceof org.springframework.ai.retry.NonTransientAiException
            || failure instanceof org.springframework.web.client.RestClientException;
    }

    private static boolean isPermanentFailure(Exception failure) {
        return failure instanceof org.springframework.ai.retry.NonTransientAiException
            || failure instanceof RestClientResponseException response
                && response.getStatusCode().is4xxClientError()
                && response.getStatusCode().value() != 429;
    }

    private boolean pauseBeforeRetry() {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static final class EmptyModelResponseException extends RuntimeException {
        private EmptyModelResponseException(String message) { super(message); }
    }

}
