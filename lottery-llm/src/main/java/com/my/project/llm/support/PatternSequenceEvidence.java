package com.my.project.llm.support;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;

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
}
