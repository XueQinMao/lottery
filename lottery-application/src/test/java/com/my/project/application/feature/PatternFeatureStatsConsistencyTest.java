package com.my.project.application.feature;

import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import com.my.project.application.history.impl.HistoryRecordServiceImpl;
import com.my.project.application.support.FeatureForecastHitUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import com.my.project.llm.bo.*;
import com.my.project.llm.service.LotteryAnalysisService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternFeatureStatsConsistencyTest {
    @Test
    void thirtyPeriodInputsMatchOccurrenceAndColdHotStatisticsPages() {
        var history = spy(new HistoryRecordServiceImpl(mock(HistoryRecordRepository.class)));
        // 覆盖所有红球、蓝球、分区边界，分类池随历史变化，不能用最后一份池倒推。
        var records = IntStream.range(0, 60).mapToObj(i -> {
            var reds = IntStream.range(0, 6).map(j -> (i * 7 + j * 5) % 33 + 1).sorted().toArray();
            return HistoryRecord.builder().period("P" + (120 - i)).num1(reds[0]).num2(reds[1])
                .num3(reds[2]).num4(reds[3]).num5(reds[4]).num6(reds[5]).special(i % 16 + 1).build();
        }).toList();
        doReturn(records.subList(0, 30)).when(history).getRecordsEndingAt("P120", 30);
        doReturn(records).when(history).getRecordsEndingAt("P120", 60);
        var stats = history.analyzeFeatureStats(30, "P120");
        var cold = history.analyzeColdHotTrend(30, "P120");
        try (var support = new PatternFeatureAnalysisSupport(mock(LotteryAnalysisService.class))) {
            var requests = support.prepare(records);
            assertEquals(18, requests.size());
            for (var request : requests) {
                assertEquals(stats.getPeriods(), request.getObservations().stream().map(o -> o.getPeriod()).toList());
                var pageFeature = stats.getOccurrenceFeatures().stream()
                    .filter(f -> f.getCode().equals(request.getCode())).findFirst();
                for (int i = 0; i < 30; i++) {
                    final int index = i;
                    String actual = request.getObservations().get(i).getValue();
                    if (pageFeature.isPresent()) {
                        String pageValue = pageFeature.get().getSeries().stream().filter(s -> s.getHits().get(index) == 1)
                            .findFirst().orElseThrow().getValue();
                        assertEquals(pageValue, actual, request.getCode() + " position=" + (i + 1));
                    } else if ("coldWarmHot".equals(request.getCode())) {
                        assertEquals(cold.getRedHotCounts().get(i) + ":" + cold.getRedWarmCounts().get(i)
                            + ":" + cold.getRedColdCounts().get(i), actual);
                    } else if ("blueColdWarmHot".equals(request.getCode())) {
                        assertEquals(cold.getBlueBandLabels().get(i), actual);
                    } else if ("blueOddEven".equals(request.getCode())) {
                        assertEquals(stats.getBlueOddEvenLabels().get(i), actual);
                    }
                }
            }
        }
    }

    @Test
    void recommendationChecksBlueColdHotAgainstCurrentPools() {
        var pools = ColdHotAnalysisBo.builder().blueHotBalls(List.of(1, 2))
            .blueWarmBalls(List.of(3)).blueColdBalls(List.of(4)).build();
        var report = LotteryAnalysisRespBo.builder().coldHotAnalysis(pools)
            .featureAnalysis(List.of(PatternFeatureAnalysisBo.builder().code("blueColdWarmHot")
                .label("蓝球冷温热").status("OK").likelyValues(List.of("热")).unlikelyValues(List.of("冷")).build()))
            .build();
        var hit = FeatureForecastHitUtils.summarizePattern(List.of(1, 2, 3, 4, 5, 6), 2, report);
        assertEquals("热", hit.getHits().getFirst().getActual());
        assertEquals(1, hit.getMainHitCount());
        assertEquals(1, FeatureForecastHitUtils.summarizePattern(List.of(1, 2, 3, 4, 5, 6), 4, report).getMissCount());
        assertThrows(IllegalArgumentException.class, () -> PatternFeatureAnalysisSupport.blueColdWarmHotValue(17, pools));
    }
}
