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
}
