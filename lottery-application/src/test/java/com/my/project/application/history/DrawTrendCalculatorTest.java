package com.my.project.application.history;

import com.my.project.application.history.support.DrawTrendCalculator;
import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DrawTrendCalculatorTest {
    @Test
    void coversAllRequestedFeaturesAndEveryRatioIncludingUnseenExtremes() {
        var oldest = record("2026001", 8, 1, 11, 12, 22, 23, 33);
        var newest = record("2026002", 9, 1, 2, 3, 4, 5, 6);
        var result = DrawTrendCalculator.calculate(List.of(newest, oldest), 2);
        assertEquals(List.of("2026001", "2026002"), result.periods());
        assertEquals(15, result.redFeatures().size());
        assertEquals(5, result.blueFeatures().size());
        assertEquals(List.of(2, 6), series(result.redFeatures(), "zones", "zone1").values());
        assertEquals(List.of(4, 3), series(result.redFeatures(), "oddEven", "odd").values());
        assertEquals(List.of(102, 21), series(result.redFeatures(), "sum", "sum").values());
        assertEquals(List.of(32, 5), series(result.redFeatures(), "span", "span").values());
        assertEquals(List.of(3, 6), series(result.redFeatures(), "size", "small").values());
        assertEquals(List.of(1, 0), series(result.blueFeatures(), "size", "small").values());
        assertEquals(List.of(0, 1), series(result.blueFeatures(), "intervals", "zone3").values());
        for (String code : List.of("threeZone", "roadRatio", "oddEvenRatio")) {
            var group = group(result.redFeatures(), code);
            assertEquals(code.equals("oddEvenRatio") ? 7 : 28, group.series().size());
            for (int i = 0; i < 2; i++) {
                int index = i;
                assertEquals(1, group.series().stream().mapToInt(s -> s.values().get(index)).sum());
            }
        }
        assertEquals(List.of(1, 0), series(result.redFeatures(), "threeZone", "2:2:2").values());
        assertEquals(List.of(0, 1), series(result.redFeatures(), "threeZone", "6:0:0").values());
        assertEquals(0, series(result.redFeatures(), "threeZone", "0:0:6").total());
        assertEquals(0.5, series(result.redFeatures(), "threeZone", "6:0:0").average());
    }

    @Test
    void usesOnlyPriorThirtyDrawsAndPreservesZoneAndBandTotals() {
        var records = new ArrayList<HistoryRecord>();
        for (int i = 0; i < 34; i++) {
            int start = i % 28 + 1;
            records.add(record(String.valueOf(2026100 - i), i % 16 + 1,
                start + 5, start, start + 3, start + 1, start + 4, start + 2));
        }
        var result = DrawTrendCalculator.calculate(records, 4);
        for (int index = 0; index < 4; index++) {
            int source = 3 - index;
            var bands = ColdHotAnalysisUtils.calculate(new ArrayList<>(records.subList(source + 1, source + 31)));
            var reds = result.draws().get(index).reds();
            assertEquals(reds.stream().filter(bands.getRedColdBalls()::contains).count(),
                series(result.redFeatures(), "coldHot", "cold").values().get(index).longValue());
            assertEquals(bands.getBlueWarmBalls().contains(records.get(source).getSpecial()) ? 1 : 0,
                series(result.blueFeatures(), "coldHot", "warm").values().get(index));
            for (String band : List.of("cold", "warm", "hot")) {
                int sum = 0;
                for (int zone = 1; zone <= 3; zone++) {
                    sum += series(result.redFeatures(), "zone" + zone + "ColdHot", band).values().get(index);
                }
                assertEquals(series(result.redFeatures(), "coldHot", band).values().get(index), sum);
            }
            for (String code : List.of("zones", "road", "oddEven", "size", "coldHot")) {
                int drawIndex = index;
                assertEquals(6, group(result.redFeatures(), code).series().stream()
                    .mapToInt(s -> s.values().get(drawIndex)).sum());
            }
            assertEquals(1, group(result.redFeatures(), "headRoad").series().stream()
                .mapToInt(s -> s.values().get(0)).sum());
            assertEquals(1, series(result.redFeatures(), "headRoad", "1").values().get(3));
        }
        var changed = new ArrayList<>(records);
        changed.set(0, record(records.getFirst().getPeriod(), 16, 1, 2, 3, 31, 32, 33));
        var next = DrawTrendCalculator.calculate(changed, 4);
        for (String band : List.of("cold", "warm", "hot")) {
            assertEquals(series(result.redFeatures(), "coldHot", band).values().subList(0, 3),
                series(next.redFeatures(), "coldHot", band).values().subList(0, 3));
        }
    }

    @Test
    void keepsNonTemperatureFeaturesWhenHistoryIsInsufficientWithoutInventingColdBalls() {
        var result = DrawTrendCalculator.calculate(List.of(record("2026001", 16, 1, 2, 3, 4, 5, 6)), 100);
        var cold = series(result.redFeatures(), "coldHot", "cold");
        assertNull(cold.values().getFirst());
        assertEquals(0, cold.sampleCount());
        assertEquals(List.of(21), series(result.redFeatures(), "sum", "sum").values());
        assertEquals(List.of(1), series(result.blueFeatures(), "intervals", "zone4").values());
        assertTrue(DrawTrendCalculator.calculate(List.of(), 30).periods().isEmpty());
    }

    @Test
    void classifiesExactlyAtThirtyPriorDrawsAndExcludesGapsFromAverages() {
        var records = new ArrayList<HistoryRecord>();
        for (int i = 0; i < 31; i++) records.add(record(String.valueOf(2026100 - i), 1, 1, 2, 3, 4, 5, 6));
        var result = DrawTrendCalculator.calculate(records, 31);
        for (var series : group(result.redFeatures(), "coldHot").series()) {
            assertEquals(1, series.sampleCount());
            assertTrue(series.values().subList(0, 30).stream().allMatch(java.util.Objects::isNull));
            assertEquals(series.values().getLast().doubleValue(), series.average());
        }
        assertEquals(6, group(result.redFeatures(), "coldHot").series().stream().mapToInt(s -> s.total()).sum());
    }

    @Test
    void includesEveryBlueBoundaryAndUsesNumericMinimumAndMaximumForHeadAndTail() {
        int[] blues = {1, 4, 5, 8, 9, 12, 13, 16};
        var records = new ArrayList<HistoryRecord>();
        for (int i = blues.length - 1; i >= 0; i--) {
            records.add(record(String.valueOf(2026001 + i), blues[i], 33, 23, 12, 11, 1, 22));
        }
        var result = DrawTrendCalculator.calculate(records, 8);
        assertEquals(List.of(1, 11, 12, 22, 23, 33), result.draws().getFirst().reds());
        for (int index = 0; index < blues.length; index++) {
            for (int zone = 1; zone <= 4; zone++) {
                assertEquals((blues[index] - 1) / 4 + 1 == zone ? 1 : 0,
                    series(result.blueFeatures(), "intervals", "zone" + zone).values().get(index));
            }
            int drawIndex = index;
            for (var group : result.blueFeatures()) {
                if (!group.code().equals("coldHot")) {
                    assertEquals(1, group.series().stream().mapToInt(s -> s.values().get(drawIndex)).sum());
                }
            }
            assertEquals(1, series(result.redFeatures(), "headRoad", "1").values().get(index));
            assertEquals(1, series(result.redFeatures(), "tailRoad", "0").values().get(index));
        }
    }

    private static DrawTrendVo.Group group(List<DrawTrendVo.Group> groups, String code) {
        return groups.stream().filter(g -> g.code().equals(code)).findFirst().orElseThrow();
    }

    private static DrawTrendVo.Series series(List<DrawTrendVo.Group> groups, String code, String series) {
        return group(groups, code).series().stream().filter(s -> s.code().equals(series)).findFirst().orElseThrow();
    }

    private static HistoryRecord record(String period, int blue, int... reds) {
        return HistoryRecord.builder().period(period).special(blue).num1(reds[0]).num2(reds[1]).num3(reds[2])
            .num4(reds[3]).num5(reds[4]).num6(reds[5]).build();
    }
}
