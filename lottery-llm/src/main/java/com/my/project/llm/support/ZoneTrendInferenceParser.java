package com.my.project.llm.support;

import com.my.project.llm.bo.ZoneTrendInference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;

public final class ZoneTrendInferenceParser {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ZoneTrendInferenceParser() { }

    public static ZoneTrendInference parse(String content, List<String> periods) {
        require(StringUtils.isNotBlank(content) && content.length() <= 24000, "视觉模型未返回完整分析");
        String json = content.strip();
        if (json.startsWith("```json") && json.endsWith("```")) json = json.substring(7, json.length() - 3).strip();
        try {
            var root = MAPPER.readTree(json);
            require(root.isObject(), "视觉模型必须返回JSON对象");
            var result = MAPPER.treeToValue(root, ZoneTrendInference.class);
            require(result.countTrends() != null && result.countTrends().size() == 3, "必须包含三个区的走势");
            var zones = new HashSet<Integer>();
            for (var trend : result.countTrends()) {
                require(trend != null && trend.zone() >= 1 && trend.zone() <= 3 && zones.add(trend.zone()), "区号重复或越界");
                text(trend.observation());
                text(trend.nextInference());
                evidence(trend.evidencePeriods(), periods);
            }
            text(result.ratioTrend());
            text(result.counterEvidence());
            require(result.candidates() != null && result.candidates().size() <= 3, "下期比例候选最多三个");
            var ratios = new HashSet<String>();
            for (var candidate : result.candidates()) {
                require(candidate != null && validRatio(candidate.ratio()) && ratios.add(candidate.ratio()), "候选三区比必须唯一且合计6");
                text(candidate.reason());
                text(candidate.caution());
                evidence(candidate.evidencePeriods(), periods);
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("视觉模型返回的JSON格式或字段不符合约定", e);
        }
    }

    public static boolean validRatio(String ratio) {
        return ratio != null && ratio.matches("[0-6]:[0-6]:[0-6]")
            && ratio.charAt(0) + ratio.charAt(2) + ratio.charAt(4) - 3 * '0' == 6;
    }

    private static void text(String value) {
        require(StringUtils.isNotBlank(value) && value.length() <= 2000, "走势描述不能为空或过长");
    }

    private static void evidence(List<String> references, List<String> periods) {
        require(CollectionUtils.isNotEmpty(references) && references.size() <= 5
            && references.stream().allMatch(periods::contains), "依据期号必须来自本次样本，且为1至5个");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
