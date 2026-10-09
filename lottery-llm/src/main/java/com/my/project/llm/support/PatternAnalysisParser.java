package com.my.project.llm.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 保留两个中文部分，校验模型引用的逐期数据与程序事实一致。 */
public final class PatternAnalysisParser {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private PatternAnalysisParser() { }

    public static PatternFeatureAnalysisBo parse(String text, PatternFeatureAnalysisReqBo req) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("模型返回为空");
        String raw = text.strip().replace("\r\n", "\n");
        String first = "一、形态规律总结";
        String second = "二、下期" + req.getLabel() + "推演";
        int split = raw.indexOf(second);
        if (!raw.startsWith(first) || split < first.length() || raw.indexOf(second, split + 1) >= 0
            || Pattern.compile("(?m)^\\s*[三四五六七八九十]、").matcher(raw).find()
            || raw.contains("```")) {
            throw new IllegalArgumentException("必须严格输出两个指定的中文部分");
        }
        List<String> extracted = arrayLine(raw.substring(first.length(), split), "逐期特征");
        List<String> expected = req.getObservations().stream().map(o -> o.getValue()).toList();
        if (expected.size() != extracted.size()) {
            throw new IllegalArgumentException("逐期特征必须恰好" + expected.size() + "项，实际为" + extracted.size()
                + "项；按position从1到" + expected.size() + "输出");
        }
        if (!expected.equals(extracted)) {
            String mismatches = java.util.stream.IntStream.range(0, expected.size())
                .filter(index -> !expected.get(index).equals(extracted.get(index)))
                .mapToObj(index -> "position=" + (index + 1) + "，期号=" + req.getObservations().get(index).getPeriod()
                    + "，返回=" + extracted.get(index) + "，正确值=" + expected.get(index))
                .collect(java.util.stream.Collectors.joining("；"));
            throw new IllegalArgumentException("逐期特征与原始开奖不一致：" + mismatches);
        }
        String inference = raw.substring(split + second.length()).strip();
        List<String> likely = arrayLine(inference, "倾向出现的" + req.getLabel() + "池");
        List<String> unlikely = arrayLine(inference, "暂不优先的" + req.getLabel() + "池");
        for (List<String> pool : List.of(likely, unlikely)) {
            if (new HashSet<>(pool).size() != pool.size() || !req.getAllowedValues().containsAll(pool)) {
                throw new IllegalArgumentException("候选池含重复或非法取值");
            }
        }
        if (likely.stream().anyMatch(unlikely::contains)) throw new IllegalArgumentException("两池不能重叠");
        var reasons = Pattern.compile("(?m)^\\h*理由\\h*[：:]\\h*(\\S[^\\r\\n]*)$").matcher(inference);
        if (!reasons.find()) throw new IllegalArgumentException("缺少出现池理由");
        String likelyReason = reasons.group(1).strip();
        if (!reasons.find()) throw new IllegalArgumentException("缺少断档池理由");
        String unlikelyReason = reasons.group(1).strip();
        String summary = raw.substring(first.length(), split).strip();
        if (!Pattern.compile("(?m)^\\h*形态描述\\h*[：:]\\h*\\S+").matcher(summary).find()) {
            throw new IllegalArgumentException("缺少中文形态描述");
        }
        return PatternFeatureAnalysisBo.builder().code(req.getCode()).label(req.getLabel())
            .status("OK").source("llm-pattern").observations(req.getObservations())
            .sampleSize(expected.size())
            .patternSummary(summary).nextPeriodInference(inference).likelyValues(likely).unlikelyValues(unlikely)
            .likelyReason(likelyReason).unlikelyReason(unlikelyReason).build();
    }

    private static List<String> arrayLine(String text, String label) {
        var matcher = Pattern.compile("(?m)^\\h*" + Pattern.quote(label) + "\\h*[：:]").matcher(text);
        if (!matcher.find()) throw new IllegalArgumentException("缺少数组标签: " + label + "（必须输出标签及JSON字符串数组）");
        int start = matcher.end();
        if (matcher.find()) throw new IllegalArgumentException("数组标签重复: " + label);
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start == text.length() || text.charAt(start) != '[') {
            throw new IllegalArgumentException("标签后必须是JSON字符串数组: " + label);
        }
        // 按括号和字符串边界提取，兼容换行；字符串中的 ] 和转义引号不作为数组结束。
        int depth = 0;
        int end = -1;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
            } else if (ch == '"') quoted = true;
            else if (ch == '[') depth++;
            else if (ch == ']' && --depth == 0) {
                end = i + 1;
                break;
            }
        }
        if (end < 0) throw new IllegalArgumentException("JSON数组未闭合（可能被截断）: " + label);
        int lineEnd = text.indexOf('\n', end);
        if (!text.substring(end, lineEnd < 0 ? text.length() : lineEnd).isBlank()) {
            throw new IllegalArgumentException("JSON数组后存在多余内容: " + label);
        }
        JsonNode array;
        try {
            array = JSON_MAPPER.readTree(text.substring(start, end));
        } catch (JsonProcessingException invalidJson) {
            throw new IllegalArgumentException("JSON数组格式错误: " + label, invalidJson);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("候选/特征必须使用JSON字符串数组: " + label);
            }
            values.add(value.textValue());
        }
        return values;
    }
}
