package com.my.project.application.feature;

import com.my.project.application.feature.support.ZoneCandidateConsistency;
import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ZoneCandidateConsistencyTest {
    private List<PatternFeatureAnalysisBo> report(List<String> first, List<String> second, List<String> third) {
        var request = PatternFeatureAnalysisReqBo.builder().code("threeZone").label("三区比")
            .observations(IntStream.range(0, 30).mapToObj(i -> PatternFeatureAnalysisBo.Observation.builder()
                .period("P" + i).value("2:2:2").build()).toList()).build();
        return List.of(ZoneCandidateConsistency.fromHistory(request), item("zone1Count", first),
            item("zone2Count", second), item("zone3Count", third));
    }

    private PatternFeatureAnalysisBo item(String code, List<String> likely) {
        return PatternFeatureAnalysisBo.builder().code(code).label(code).status("OK")
            .likelyValues(likely).unlikelyValues(List.of("6")).likelyReason("原理由")
            .unlikelyReason("暂不优先，不代表不可能").nextPeriodInference("原始推演").build();
    }

    @Test
    void derivesOneCombinationAndProjectsCountsWithoutReclassifyingRemovedCandidates() {
        var report = report(List.of("1", "2"), List.of("2"), List.of("4", "3"));
        ZoneCandidateConsistency.reconcile(report);
        assertEquals(List.of("1:2:3"), report.getFirst().getLikelyValues());
        assertEquals("derived-zone-counts", report.getFirst().getSource());
        assertEquals(List.of("1"), report.get(1).getLikelyValues());
        assertEquals(List.of("2"), report.get(2).getLikelyValues());
        assertEquals(List.of("3"), report.get(3).getLikelyValues());
        assertEquals(30, report.getFirst().getSampleSize());
        assertTrue(report.getFirst().getPatternSummary().contains("逐期特征"));
        assertTrue(report.getFirst().getUnlikelyValues().isEmpty());
        for (var feature : report) {
            assertEquals("OK", feature.getStatus());
            assertTrue(feature.getNextPeriodInference().contains(feature.getLikelyReason()));
        }
        for (var feature : report.subList(1, 4)) {
            assertTrue(feature.getLikelyReason().contains("投影"));
            assertTrue(feature.getLikelyReason().contains("原理由"));
            assertTrue(feature.getLikelyReason().contains("缺少联合组合支持"));
            assertEquals(List.of("6"), feature.getUnlikelyValues());
        }
    }

    @Test
    void generatesSortedUniqueCombinationsWithZeroOnlyWhenOriginallySupported() {
        var report = report(List.of("1", "0", "1"), List.of("2"), List.of("4", "3", "4"));
        ZoneCandidateConsistency.reconcile(report);
        assertEquals(List.of("0:2:4", "1:2:3"), report.getFirst().getLikelyValues());
        assertEquals(List.of("0", "1"), report.get(1).getLikelyValues());
        assertEquals(List.of("3", "4"), report.get(3).getLikelyValues());
        var many = report(List.of("2", "1", "0"), List.of("3", "2", "1"), List.of("5", "4", "3", "2", "1"));
        ZoneCandidateConsistency.reconcile(many);
        assertEquals(List.of("0:1:5", "0:2:4", "0:3:3", "1:1:4", "1:2:3", "1:3:2",
            "2:1:3", "2:2:2", "2:3:1"), many.getFirst().getLikelyValues());
        for (var value : many.getFirst().getLikelyValues()) {
            var parts = value.split(":");
            assertEquals(6, java.util.Arrays.stream(parts).mapToInt(Integer::parseInt).sum());
            for (int i = 0; i < 3; i++) assertTrue(many.get(i + 1).getLikelyValues().contains(parts[i]));
        }
    }

    @Test
    void preservesIndividualResultsWhenNoCombinationExistsAndNeverAddsZero() {
        for (var first : List.of(List.of("3"), List.<String>of())) {
            var report = report(first, List.of("2"), List.of("4"));
            ZoneCandidateConsistency.reconcile(report);
            assertEquals("UNAVAILABLE", report.getFirst().getStatus());
            assertTrue(report.getFirst().getLikelyValues().isEmpty());
            assertTrue(report.getFirst().getFailureReason().contains("无法组成六个红球"));
            assertEquals(first, report.get(1).getLikelyValues());
            for (var feature : report.subList(1, 4)) {
                assertEquals("OK", feature.getStatus());
                assertEquals("原始推演", feature.getNextPeriodInference());
                assertFalse(feature.getLikelyValues().contains("0"));
            }
        }
    }

    @Test
    void propagatesFailedOrMissingZoneWithoutChangingOtherZones() {
        var report = report(List.of("1"), List.of("2"), List.of("3"));
        report.get(1).setStatus("UNAVAILABLE");
        report.get(1).setFailureReason("HTTP 503");
        ZoneCandidateConsistency.reconcile(report);
        assertEquals("UNAVAILABLE", report.getFirst().getStatus());
        assertTrue(report.getFirst().getFailureReason().contains("HTTP 503"));
        assertEquals("OK", report.get(2).getStatus());
        assertEquals("原始推演", report.get(2).getNextPeriodInference());
        ZoneCandidateConsistency.reconcile(List.of(report.getFirst(), report.get(2), report.get(3)));
        assertTrue(report.getFirst().getFailureReason().contains("分析缺失"));
    }

    @Test
    void serializesDerivedReportWithoutInternalObservations() throws Exception {
        var report = report(List.of("0", "1"), List.of("2"), List.of("3", "4"));
        ZoneCandidateConsistency.reconcile(report);
        var payload = com.my.project.llm.bo.LotteryAnalysisRespBo.builder().featureAnalysis(report).build();
        for (String json : List.of(com.alibaba.fastjson.JSON.toJSONString(payload),
            com.alibaba.fastjson2.JSON.toJSONString(payload), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload))) {
            assertFalse(json.contains("\"observations\""));
            assertTrue(json.contains("\"source\":\"derived-zone-counts\""));
            assertTrue(json.contains("\"likelyValues\":[\"0:2:4\",\"1:2:3\"]"));
        }
    }
}
