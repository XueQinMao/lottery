package com.my.project.llm;

import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.support.FeatureTrendInferenceParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTrendInferenceParserTest {
    private static final List<String> PERIODS = List.of("2026132");
    private static final String OUTPUT = """
        {"observation":"末段走势","candidates":[{"value":"%s","reason":"观察末段",
        "caution":"也有转路可能","evidencePeriods":["2026132"]}],"counterEvidence":"样本内有转路"}
        """;

    @Test
    void tailRoadCanonicalizesSingleNumbersButRejectsCompoundValues() {
        for (String alias : List.of("2", "２路", "2　路")) {
            var inference = FeatureTrendInferenceParser.parse(OUTPUT.formatted(alias), FeatureTrendKind.RED_TAIL, PERIODS);
            assertEquals("2路", inference.candidates().getFirst().value());
        }
        for (String invalid : List.of("3路", "0路或1路", "0:1:0", "可能2路")) {
            var failure = assertThrows(IllegalArgumentException.class,
                () -> FeatureTrendInferenceParser.parse(OUTPUT.formatted(invalid), FeatureTrendKind.RED_TAIL, PERIODS));
            assertTrue(failure.getMessage().contains("第1个候选"));
            assertTrue(failure.getMessage().contains("0路、1路或2路"));
        }
    }
}
