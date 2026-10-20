package com.my.project.application.feature.support;

import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.history.vo.FeatureStatsVo;
import com.my.project.llm.bo.FeatureTrendInput;
import com.my.project.llm.bo.FeatureTrendInput.Group;
import com.my.project.llm.bo.FeatureTrendInput.Series;
import com.my.project.llm.bo.FeatureTrendKind;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class FeatureTrendInputs {
    public static final int SAMPLE_SIZE = 30;
    public static final int ROWS_PER_IMAGE = 7;
    public static final int IMAGE_WIDTH = 1200;

    private FeatureTrendInputs() { }

    public static FeatureTrendInput select(DrawTrendVo trends, FeatureStatsVo stats, FeatureTrendKind kind) {
        var groups = kind.blue() ? trends.blueFeatures() : trends.redFeatures();
        var selected = kind.groups().stream().map(code -> groups.stream()
            .filter(group -> group.code().equals(code)).findFirst()
            .map(FeatureTrendInputs::fromDrawGroup).orElseGet(() -> fromFeatureStats(stats, code))).toList();
        return new FeatureTrendInput(trends.periods().getLast(), trends.periods(), selected);
    }

    public static boolean needsStats(FeatureTrendKind kind) {
        return switch (kind) {
            case RED_SUM, RED_PRIME, RED_ENDINGS -> true;
            default -> false;
        };
    }

    public static List<Sheet> sheets(FeatureTrendInput input) {
        return input.groups().stream().flatMap(FeatureTrendInputs::sheets).toList();
    }

    private static Stream<Sheet> sheets(Group group) {
        int rows = group.series().size();
        return IntStream.range(0, (rows + ROWS_PER_IMAGE - 1) / ROWS_PER_IMAGE).mapToObj(page -> {
            int pageRows = Math.min(ROWS_PER_IMAGE, rows - page * ROWS_PER_IMAGE);
            return new Sheet(group.code() + "-" + (page + 1), 100 + 240 * pageRows);
        });
    }

    private static Group fromDrawGroup(DrawTrendVo.Group group) {
        var series = group.series().stream().map(row -> new Series(row.code(), row.label(), row.values())).toList();
        return new Group(group.code(), group.label(), group.description(), group.occurrence(), group.min(), group.max(), series);
    }

    private static Group fromFeatureStats(FeatureStatsVo stats, String code) {
        return switch (code) {
            case "sumDelta" -> sumDelta(stats.getSumDeltaValues());
            case "primeRatio" -> primeRatio(stats.getPrimeCounts());
            case "tailCounts" -> new Group(code, "尾数分布", "红球除以10的余数。各尾数个数合计6。",
                false, 0, 6, IntStream.range(0, 10).mapToObj(digit ->
                    new Series("tail" + digit, digit + "尾个数", stats.getTailCounts().get(digit))).toList());
            default -> throw new IllegalStateException("缺少走势图数据 " + code);
        };
    }

    private static Group sumDelta(List<Integer> values) {
        IntSummaryStatistics range = values.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).summaryStatistics();
        return new Group("sumDelta", "和值增长差值", "本期和值减上一期和值，样本首期无对照。",
            false, Math.min(0, range.getMin()), Math.max(1, range.getMax()),
            List.of(new Series("sumDelta", "和值增长差值", values)));
    }

    private static Group primeRatio(List<Integer> counts) {
        return new Group("primeRatio", "质合比", "顺序为质:合。沿用历史走势页面口径，1归入合数档。",
            true, 0, 1, IntStream.rangeClosed(0, 6).mapToObj(prime -> {
                String ratio = prime + ":" + (6 - prime);
                return new Series(ratio, ratio, counts.stream().map(count -> count == prime ? 1 : 0).toList());
            }).toList());
    }

    public record Sheet(String code, int height) { }
}
