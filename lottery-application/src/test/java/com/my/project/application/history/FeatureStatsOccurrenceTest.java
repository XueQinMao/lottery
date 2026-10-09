package com.my.project.application.history;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import com.my.project.application.history.impl.HistoryRecordServiceImpl;
import com.my.project.application.history.vo.FeatureStatsVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureStatsOccurrenceTest {
    private final HistoryRecordServiceImpl service = spy(new HistoryRecordServiceImpl(
        mock(HistoryRecordRepository.class)));

    @Test
    void preservesChronologyAndIncludesEveryCategoryWithCorrectRates() {
        HistoryRecord oldest = record("2026001", 1, 2, 3, 4, 5, 6);
        HistoryRecord newest = record("2026002", 28, 29, 30, 31, 32, 33);
        doReturn(List.of(newest, oldest)).when(service).getRecordsEndingAt("2026002", 2);

        FeatureStatsVo result = service.analyzeFeatureStats(2, "2026002");
        assertEquals(List.of("2026001", "2026002"), result.getPeriods());
        assertEquals(11, result.getOccurrenceFeatures().size());
        assertEquals(List.of(1, 0), series(result, "sumRange", "21-60").getHits());
        assertEquals(List.of(0, 1), series(result, "sumRange", "145-183").getHits());
        assertEquals(List.of(1, 0), series(result, "primeComp", "4:2").getHits());
        assertEquals(List.of(0, 1), series(result, "bigSmall", "6:0").getHits());
        assertEquals(List.of(1, 0), series(result, "threeZone", "6:0:0").getHits());
        assertEquals(List.of(1, 1), series(result, "ratio012", "2:2:2").getHits());
        assertEquals(List.of(1, 1), series(result, "span", "5").getHits());
        assertEquals(List.of(1, 0), series(result, "sumTail", "1").getHits());
        assertEquals(0.5, series(result, "sumTail", "1").getHitRate());
        assertEquals(0, series(result, "span", "32").getHitCount());
        for (var feature : result.getOccurrenceFeatures()) {
            assertEquals(2, feature.getSeries().stream().mapToInt(s -> s.getHitCount()).sum());
            for (int i = 0; i < 2; i++) {
                final int index = i;
                assertEquals(1, feature.getSeries().stream().mapToInt(s -> s.getHits().get(index)).sum());
            }
        }
        // 每期各尾个数合计仍为六个红球。
        for (int i = 0; i < 2; i++) {
            final int index = i;
            assertEquals(6, result.getTailCounts().stream().mapToInt(t -> t.get(index)).sum());
        }
        verify(service).getRecordsEndingAt("2026002", 2);
    }

    @Test
    void handlesSinglePeriodAndInclusiveRangeBoundaries() {
        doReturn(List.of(record("2026001", 1, 2, 3, 4, 18, 33)))
            .when(service).getRecordsEndingAt(null, 1);
        FeatureStatsVo single = service.analyzeFeatureStats(1, null);
        assertEquals(1.0, series(single, "sumRange", "61-66").getHitRate());
        assertEquals(1, series(single, "span", "32").getHitCount());
        assertEquals(28, single.getOccurrenceFeatures().stream().filter(f -> f.getCode().equals("span"))
            .findFirst().orElseThrow().getSeries().size());
        assertEquals(16, single.getOccurrenceFeatures().stream().filter(f -> f.getCode().equals("sumRange"))
            .findFirst().orElseThrow().getSeries().size());

        doReturn(List.of(record("2026002", 1, 2, 3, 4, 23, 33)))
            .when(service).getRecordsEndingAt(null, 1);
        FeatureStatsVo next = service.analyzeFeatureStats(1, null);
        assertEquals(1, series(next, "sumRange", "61-66").getHitCount());
        doReturn(List.of(record("2026003", 1, 2, 3, 4, 24, 33)))
            .when(service).getRecordsEndingAt(null, 1);
        assertEquals(1, series(service.analyzeFeatureStats(1, null), "sumRange", "67-72").getHitCount());
    }

    private static FeatureStatsVo.OccurrenceSeries series(FeatureStatsVo result, String code, String value) {
        return result.getOccurrenceFeatures().stream().filter(f -> f.getCode().equals(code))
            .findFirst().orElseThrow().getSeries().stream().filter(s -> s.getValue().equals(value))
            .findFirst().orElseThrow();
    }

    private static HistoryRecord record(String period, int a, int b, int c, int d, int e, int f) {
        return HistoryRecord.builder().period(period).num1(a).num2(b).num3(c).num4(d).num5(e).num6(f)
            .special(1).build();
    }
}
