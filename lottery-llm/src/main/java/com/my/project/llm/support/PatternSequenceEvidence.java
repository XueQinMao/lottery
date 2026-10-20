package com.my.project.llm.support;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import com.my.project.llm.bo.PatternFeatureAnalysisBo.Observation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只计算可复核的历史事实，不计算下期概率或候选。 */
public final class PatternSequenceEvidence {
    private PatternSequenceEvidence() { }

    public static String describe(PatternFeatureAnalysisReqBo request) {
        var rows = request.getObservations();
        var values = rows.stream().map(row -> row.getValue()).toList();
        int size = values.size();
        int from = Math.max(0, size - 8);
        int run = 1;
        while (run < size && values.get(size - run - 1).equals(values.getLast())) run++;
        // 严格的两值交替后缀，不把三种类别轮换或早期片段称为当前交替。
        int alternating = size >= 2 && !values.getLast().equals(values.get(size - 2)) ? 2 : 1;
        while (alternating >= 2 && alternating < size
                && values.get(size - alternating - 1).equals(values.get(size - alternating + 1))) alternating++;
        return "最近" + (size - from) + "期（" + rows.get(from).getPeriod() + "至" + rows.getLast().getPeriod()
            + "，由旧到新）为" + JSON.toJSONString(values.subList(from, size))
            + "；截至最新期，" + values.getLast() + "连续出现" + run + "期（"
            + rows.get(size - run).getPeriod() + "至" + rows.getLast().getPeriod() + "）"
            + "；末段严格两值交替长度为" + (alternating >= 2 ? alternating : 0) + "期。";
    }

    public static boolean requiresExactSummary(PatternFeatureAnalysisReqBo request) {
        return request.getCode().startsWith("blue");
    }

    /** 给模型提供可复核的对照与相似片段，不将匹配次数转换为预测概率。 */
    public static String context(PatternFeatureAnalysisReqBo request) {
        var rows = request.getObservations();
        var values = rows.stream().map(Observation::getValue).toList();
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int width : List.of(5, 10)) {
            if (rows.size() >= width * 2) {
                facts.put("recent" + width, window(rows.subList(rows.size() - width, rows.size())));
                facts.put("preceding" + width, window(rows.subList(rows.size() - width * 2, rows.size() - width)));
            }
        }
        // 比例拆开观察各分量，防止仅看完整标签而遗漏某一项的停留和跳变。
        if (values.stream().allMatch(value -> value.matches("\\d+(?::\\d+)+"))) {
            int components = values.getFirst().split(":").length;
            if (values.stream().allMatch(value -> value.split(":").length == components)) {
                var recent = values.subList(Math.max(0, values.size() - 8), values.size());
                List<List<Integer>> sequences = new ArrayList<>();
                for (int component = 0; component < components; component++) {
                    final int index = component;
                    sequences.add(recent.stream().map(value -> Integer.parseInt(value.split(":")[index])).toList());
                }
                facts.put("recent8ComponentsInDefinitionOrder", sequences);
            }
        }
        Map<String, List<SimilarFragment>> similar = new LinkedHashMap<>();
        for (int width : List.of(2, 3, 4)) {
            int suffixStart = values.size() - width;
            if (suffixStart < 0) continue;
            var suffix = values.subList(suffixStart, values.size());
            List<SimilarFragment> matches = new ArrayList<>();
            // 后续结果必须已发生，且在当前末段开始之前；不以目标期作为已知事实。
            for (int start = 0; start + width < suffixStart; start++) {
                if (values.subList(start, start + width).equals(suffix)) {
                    matches.add(new SimilarFragment(rows.get(start).getPeriod(), rows.get(start + width - 1).getPeriod(),
                        List.copyOf(suffix), rows.get(start + width).getPeriod(), values.get(start + width)));
                }
            }
            similar.put("suffix" + width, List.copyOf(matches.subList(Math.max(0, matches.size() - 3), matches.size())));
        }
        facts.put("earlierSimilarFragmentsAndObservedNextValue", similar);
        return JSON.toJSONString(facts);
    }

    private static Window window(List<Observation> rows) {
        return new Window(rows.getFirst().getPeriod(), rows.getLast().getPeriod(), rows.stream().map(Observation::getValue).toList());
    }

    private record Window(String startPeriod, String endPeriod, List<String> values) { }
    private record SimilarFragment(String startPeriod, String endPeriod, List<String> matchedValues,
                                   String observedNextPeriod, String observedNextValue) { }
}
