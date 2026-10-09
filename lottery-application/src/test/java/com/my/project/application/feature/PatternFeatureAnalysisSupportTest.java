package com.my.project.application.feature;

import com.my.project.llm.bo.*;
import com.my.project.llm.service.LotteryAnalysisService;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternFeatureAnalysisSupportTest {
    @Test
    void usesThirtyChronologicalDrawsAndOnlyTheirPriorThirtyForRedAndBlueColdWarmHot() {
        var support = new PatternFeatureAnalysisSupport(mock(LotteryAnalysisService.class));
        List<HistoryRecord> records = records();
        var pools = ColdHotAnalysisBo.builder().redHotBalls(List.of(1, 2))
            .redWarmBalls(List.of(3, 4, 5)).redColdBalls(List.of(6))
            .blueHotBalls(List.of(1)).blueWarmBalls(List.of(2, 3)).blueColdBalls(List.of(4)).build();
        try (var coldHot = mockStatic(ColdHotAnalysisUtils.class)) {
            coldHot.when(() -> ColdHotAnalysisUtils.calculate(anyList())).thenReturn(pools);
            var requests = support.prepare(records);
            assertEquals(List.of("oddEven", "bigSmall", "primeComp", "ratio012", "span", "sumRange",
                "sumTail", "threeZone", "zone1Count", "zone2Count", "zone3Count", "blueOddEven",
                "blueBigSmall", "blueBigSmallOddEven", "blueRatio012", "tail", "coldWarmHot", "blueColdWarmHot"),
                requests.stream().map(r -> r.getCode()).toList());
            for (var req : requests) {
                assertEquals(30, req.getObservations().size());
                assertEquals("P29", req.getObservations().getFirst().getPeriod());
                assertEquals("P0", req.getObservations().getLast().getPeriod());
            }
            var tail = requests.get(15);
            assertEquals("0:1:1:1:1:1:1:0:0:0", tail.getObservations().getFirst().getValue());
            assertEquals("4:2", requests.get(2).getObservations().getFirst().getValue());
            assertEquals("2:3:1", requests.get(16).getObservations().getFirst().getValue());
            assertEquals("热", requests.get(17).getObservations().getFirst().getValue());
            assertEquals("6", requests.get(8).getObservations().getFirst().getValue());
            assertEquals("0", requests.get(9).getObservations().getFirst().getValue());
            assertEquals("奇", requests.get(11).getObservations().getFirst().getValue());
            assertEquals("小", requests.get(12).getObservations().getFirst().getValue());
            assertEquals("小奇", requests.get(13).getObservations().getFirst().getValue());
            assertEquals("1路", requests.get(14).getObservations().getFirst().getValue());
            for (int index = 0; index < 30; index++) {
                int from = index + 1;
                coldHot.verify(() -> ColdHotAnalysisUtils.calculate(records.subList(from, from + 30)));
            }
            coldHot.verifyNoMoreInteractions();
        }
        assertThrows(IllegalStateException.class, () -> support.prepare(records.subList(0, 59)));
    }

    @Test
    void keepsEighteenIndependentFeatureCallsAndPreservesOrder() {
        var llm = mock(LotteryAnalysisService.class);
        when(llm.analyzePattern(any())).thenAnswer(call -> {
            PatternFeatureAnalysisReqBo req = call.getArgument(0);
            return PatternFeatureAnalysisBo.builder().code(req.getCode()).status("OK").build();
        });
        var support = new PatternFeatureAnalysisSupport(llm);
        var result = support.analyze(records());
        assertEquals(18, result.size());
        assertEquals("tail", result.get(15).getCode());
        assertEquals("blueColdWarmHot", result.getLast().getCode());
        verify(llm, times(18)).analyzePattern(any());
    }

    private List<HistoryRecord> records() {
        return IntStream.range(0, 60).mapToObj(i -> HistoryRecord.builder().period("P" + i)
            .num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(1).build()).toList();
    }
}
