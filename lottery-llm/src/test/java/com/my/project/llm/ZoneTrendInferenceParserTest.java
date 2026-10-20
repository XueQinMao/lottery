package com.my.project.llm;

import com.my.project.llm.support.ZoneTrendInferenceParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ZoneTrendInferenceParserTest {
    static final String VALID = """
        {"countTrends":[
          {"zone":1,"observation":"在低位间歇上探","nextInference":"观察回升能否延续","evidencePeriods":["2026001"]},
          {"zone":2,"observation":"围绕中位摆动","nextInference":"观察横向延续","evidencePeriods":["2026002"]},
          {"zone":3,"observation":"冲高后回落","nextInference":"观察回落后的停留","evidencePeriods":["2026002"]}],
         "ratioTrend":"对比个数摆动与各比例出现标记，尚无稳定周期。",
         "candidates":[{"ratio":"2:2:2","reason":"作为横向延续的候选","caution":"跳变可能打断延续","evidencePeriods":["2026002"]}],
         "counterEvidence":"样本中也有与延续假设不同的跳变，不能保证下期延续。"}
        """;
    private static final List<String> PERIODS = List.of("2026001", "2026002");
    static final String NATURAL_TREND = VALID
        .replace("观察横向延续", "下期可能回落至2或3，但跳变至1或5概率低")
        .replace("观察回落后的停留", "下期大概率反弹至2或3")
        .replace("不能保证下期延续。", "本分析不提供概率或置信度，不代表必然延续。");

    @Test void acceptsGroundedJointForecastWithoutProbabilities() {
        var result = ZoneTrendInferenceParser.parse(VALID, PERIODS);
        assertEquals(3, result.countTrends().size());
        assertEquals("2:2:2", result.candidates().getFirst().ratio());
    }

    @Test void rejectsImpossibleRatiosUnknownPeriodsAndDuplicateZones() {
        assertThrows(IllegalArgumentException.class, () -> parse(VALID.replace("2:2:2", "3:3:3")));
        assertThrows(IllegalArgumentException.class, () -> parse(VALID.replace("2026002", "2026999")));
        assertThrows(IllegalArgumentException.class, () -> parse(VALID.replace("\"zone\":3", "\"zone\":1")));
    }

    @Test void acceptsNaturalTrendWordingAndDisclaimers() {
        var result = ZoneTrendInferenceParser.parse(NATURAL_TREND, PERIODS);
        assertEquals("下期可能回落至2或3，但跳变至1或5概率低", result.countTrends().get(1).nextInference());
        assertEquals("下期大概率反弹至2或3", result.countTrends().get(2).nextInference());
        assertTrue(result.counterEvidence().contains("不提供概率或置信度"));
        assertEquals("2:2:2", result.candidates().getFirst().ratio());
    }

    @Test void rejectsUnknownFieldsEmptyAndTruncatedResponses() {
        assertThrows(IllegalArgumentException.class, () -> parse(VALID.replace("\"reason\":", "\"probability\":0.8,\"reason\":")));
        assertThrows(IllegalArgumentException.class, () -> parse(""));
        assertThrows(IllegalArgumentException.class, () -> parse(VALID.substring(0, 100)));
    }

    @Test void stillRejectsBlankAndOversizedDescriptions() {
        assertThrows(IllegalArgumentException.class, () -> parse(VALID.replace("作为横向延续的候选", " ")));
        assertThrows(IllegalArgumentException.class, () -> parse(VALID.replace("作为横向延续的候选", "长".repeat(2001))));
    }

    private void parse(String content) { ZoneTrendInferenceParser.parse(content, PERIODS); }
}
