package com.my.project.llm.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.project.llm.bo.FeatureTrendInference;
import com.my.project.llm.bo.FeatureTrendKind;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public final class FeatureTrendInferenceParser {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private FeatureTrendInferenceParser() { }

    public static FeatureTrendInference parse(String content, FeatureTrendKind kind, List<String> periods) {
        String json = content.strip();
        if (json.startsWith("```json") && json.endsWith("```")) {
            json = json.substring(7, json.length() - 3).strip();
        }
        if (json.length() > 24000) throw new IllegalArgumentException("推演内容过长");
        try {
            var result = MAPPER.readValue(json, FeatureTrendInference.class);
            if (result == null || result.candidates() == null || result.candidates().size() > 3) {
                throw new IllegalArgumentException("候选必须为0至3项");
            }
            var values = new HashSet<String>();
            var sample = Set.copyOf(periods);
            return new FeatureTrendInference(text(result.observation()), IntStream.range(0, result.candidates().size())
                .mapToObj(index -> validate(result.candidates().get(index), index + 1, kind, sample, values))
                .toList(), text(result.counterEvidence()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("推演JSON格式或字段不正确", e);
        }
    }

    private static FeatureTrendInference.Candidate validate(FeatureTrendInference.Candidate candidate, int position,
            FeatureTrendKind kind, Set<String> sample, Set<String> values) {
        String value;
        try {
            if (candidate == null) throw new IllegalArgumentException("缺少候选内容");
            value = kind.normalizeValue(candidate.value());
            if (!values.add(value)) throw new IllegalArgumentException("候选值重复");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("第%d个候选：%s".formatted(position, e.getMessage()), e);
        }
        var evidence = candidate.evidencePeriods();
        if (CollectionUtils.isEmpty(evidence) || evidence.size() > 5
                || new HashSet<>(evidence).size() != evidence.size()
                || evidence.stream().anyMatch(period -> period == null || !sample.contains(period))) {
            throw new IllegalArgumentException("候选需要引用1至5个不同的样本期号");
        }
        return new FeatureTrendInference.Candidate(value, text(candidate.reason()), text(candidate.caution()),
            List.copyOf(evidence));
    }

    private static String text(String value) {
        if (StringUtils.isBlank(value) || value.length() > 2000) {
            throw new IllegalArgumentException("走势描述不能为空或过长");
        }
        return value.strip();
    }
}
