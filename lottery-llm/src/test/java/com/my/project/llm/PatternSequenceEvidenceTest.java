package com.my.project.llm;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import com.my.project.llm.support.PatternAnalysisParser;
import com.my.project.llm.support.PatternSequenceEvidence;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class PatternSequenceEvidenceTest {
    private PatternFeatureAnalysisReqBo request() {
        var values = new ArrayList<>(Collections.nCopies(18, "小"));
        values.addAll(List.of("小", "大", "小", "大", "小", "大", "大", "大", "小", "大", "小", "大"));
        return PatternFeatureAnalysisReqBo.builder().code("blueBigSmall").label("蓝球大小")
            .allowedValues(List.of("小", "大")).observations(IntStream.range(0, values.size())
                .mapToObj(i -> PatternFeatureAnalysisBo.Observation.builder().period("P" + (i + 1))
                    .position(i + 1).value(values.get(i)).build()).toList()).build();
    }

    @Test
    void distinguishesEarlierBigRunFromCurrentAlternation() {
        String evidence = PatternSequenceEvidence.describe(request());
        assertTrue(evidence.contains("[\"小\",\"大\",\"大\",\"大\",\"小\",\"大\",\"小\",\"大\"]"));
        assertTrue(evidence.contains("大连续出现1期（P30至P30）"));
        assertTrue(evidence.contains("交替长度为5期"));
        var request = request();
        request.getObservations().getLast().setValue("小");
        assertTrue(PatternSequenceEvidence.describe(request).contains("小连续出现2期"));
        assertTrue(PatternSequenceEvidence.describe(request).contains("交替长度为0期"));
    }

    @Test
    void rejectsFabricatedNarrativeEvenWhenThirtyValuesAreCorrect() {
        var req = request();
        String response = "一、形态规律总结\n逐期特征："
            + JSON.toJSONString(req.getObservations().stream().map(row -> row.getValue()).toList())
            + "\n形态描述：" + PatternSequenceEvidence.describe(req)
            + "\n二、下期蓝球大小推演\n倾向出现的蓝球大小池：[\"小\"]\n理由：下一期观察交替延续的假设。"
            + "\n暂不优先的蓝球大小池：[]\n理由：也保留交替打断的可能，不作硬排除。";
        assertEquals("OK", PatternAnalysisParser.parse(response, req).getStatus());
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response.replace(PatternSequenceEvidence.describe(req), "最近四期大连续出现两次"), req));
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response.replace("下一期观察交替延续的假设。", "最近四期连续出现大，所以继续大。"), req));
        assertThrows(IllegalArgumentException.class, () -> PatternAnalysisParser.parse(
            response.replace("下一期观察交替延续的假设。", "大号连续出现两次，所以继续大。"), req));
    }

    @Test
    void suppliesNonOverlappingWindowsAndOnlyCompletedEarlierMatches() {
        var req = request();
        var context = JSON.parseObject(PatternSequenceEvidence.context(req));
        assertEquals("P26", context.getJSONObject("recent5").getString("startPeriod"));
        assertEquals("P30", context.getJSONObject("recent5").getString("endPeriod"));
        assertEquals("P21", context.getJSONObject("preceding5").getString("startPeriod"));
        assertEquals("P25", context.getJSONObject("preceding5").getString("endPeriod"));
        assertEquals("P11", context.getJSONObject("preceding10").getString("startPeriod"));
        var similar = context.getJSONObject("earlierSimilarFragmentsAndObservedNextValue");
        for (int width : List.of(2, 3, 4)) {
            var examples = similar.getJSONArray("suffix" + width);
            assertTrue(examples.size() <= 3);
            for (int i = 0; i < examples.size(); i++) {
                var example = examples.getJSONObject(i);
                int nextPosition = Integer.parseInt(example.getString("observedNextPeriod").substring(1));
                assertTrue(nextPosition <= 30 - width);
                assertEquals(req.getObservations().get(nextPosition - 1).getValue(), example.getString("observedNextValue"));
                assertEquals(req.getObservations().subList(30 - width, 30).stream().map(row -> row.getValue()).toList(),
                    example.getJSONArray("matchedValues").toJavaList(String.class));
            }
        }
        assertFalse(context.containsKey("recent8ComponentsInDefinitionOrder"));
    }

    @Test
    void decomposesRatioComponentsWithoutChangingTheirOrder() {
        var req = request();
        req.setCode("ratio012");
        req.getObservations().forEach(row -> row.setValue(row.getPosition() % 2 == 0 ? "3:2:1" : "1:2:3"));
        var context = JSON.parseObject(PatternSequenceEvidence.context(req));
        assertEquals(List.of(1, 3, 1, 3, 1, 3, 1, 3), context.getJSONArray("recent8ComponentsInDefinitionOrder")
            .getJSONArray(0).toJavaList(Integer.class));
        assertEquals(java.util.Collections.nCopies(8, 2), context.getJSONArray("recent8ComponentsInDefinitionOrder")
            .getJSONArray(1).toJavaList(Integer.class));
    }
}
