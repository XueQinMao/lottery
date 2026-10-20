package com.my.project.application.feature;

import com.alibaba.fastjson2.JSON;
import com.my.project.application.support.LotteryFeatureTrendUtils;
import com.my.project.application.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import com.my.project.llm.support.PatternSequenceEvidence;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class PatternHistoricalEvidenceTest {
    private PatternFeatureAnalysisReqBo request(FeatureKind kind) throws Exception {
        try (var input = getClass().getResourceAsStream("/pattern/history-through-2026118.csv")) {
            assertNotNull(input);
            var lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines().toList().reversed();
            var observations = IntStream.range(0, lines.size()).mapToObj(index -> {
                var fields = lines.get(index).split(";");
                assertEquals(8, fields.length);
                var reds = Arrays.stream(fields, 1, 7).map(Integer::parseInt).toList();
                int blue = Integer.parseInt(fields[7]);
                return PatternFeatureAnalysisBo.Observation.builder().position(index + 1).period(fields[0])
                    .redBalls(reds).blueBall(blue).value(LotteryFeatureTrendUtils.extract(reds, blue, kind)).build();
            }).toList();
            return PatternFeatureAnalysisReqBo.builder().code(kind.getCode()).label(kind.getLabel())
                .allowedValues(LotteryFeatureTrendUtils.buckets(kind)).observations(observations).build();
        }
    }

    @Test
    void groundsAllFeatureEvidenceInThirtyDrawsWithoutReplacing116WithHeldOutAnswer() throws Exception {
        for (var kind : FeatureKind.values()) {
            var req = request(kind);
            assertEquals(30, req.getObservations().size());
            assertEquals("2026089", req.getObservations().getFirst().getPeriod());
            assertEquals("2026118", req.getObservations().getLast().getPeriod());
            var draw116 = req.getObservations().get(27);
            assertEquals("2026116", draw116.getPeriod());
            assertEquals(List.of(10, 19, 24, 27, 28, 33), draw116.getRedBalls());
            assertEquals(12, draw116.getBlueBall());
            var facts = JSON.parseObject(PatternSequenceEvidence.context(req));
            assertEquals("2026118", facts.getJSONObject("recent5").getString("endPeriod"));
            assertEquals("2026109", facts.getJSONObject("preceding5").getString("startPeriod"));
            var fragments = facts.getJSONObject("earlierSimilarFragmentsAndObservedNextValue");
            for (String key : fragments.keySet()) {
                var matches = fragments.getJSONArray(key);
                for (int i = 0; i < matches.size(); i++) {
                    assertTrue(matches.getJSONObject(i).getString("observedNextPeriod").compareTo("2026118") < 0);
                }
            }
        }
    }

    @Test
    void distinguishesActualBlueAlternationFromEarlierStreakAndKeepsOutcomeSeparate() throws Exception {
        var req = request(FeatureKind.BLUE_BIG_SMALL);
        var lastEight = req.getObservations().subList(22, 30).stream().map(row -> row.getValue()).toList();
        assertEquals(List.of("小", "大", "大", "大", "小", "大", "小", "大"), lastEight);
        assertTrue(PatternSequenceEvidence.describe(req).contains("大连续出现1期"));
        assertTrue(PatternSequenceEvidence.describe(req).contains("交替长度为5期"));
        // 用户确认的下一期只用于验算实际特征，不写入历史窗口或候选规则。
        var actualReds = List.of(12, 22, 23, 25, 27, 30);
        assertEquals("0:2:4", LotteryFeatureTrendUtils.extract(actualReds, 4, FeatureKind.THREE_ZONE));
        assertEquals("小", LotteryFeatureTrendUtils.extract(actualReds, 4, FeatureKind.BLUE_BIG_SMALL));
    }
}
