package com.my.project.application.feature.support;

import com.my.project.application.enums.FeatureDimension;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.support.FeatureIntervalForecastUtils;
import com.my.project.application.support.LotteryFeatureTrendUtils;
import com.my.project.application.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.application.support.LotteryMorphologySnapshotUtils;
import com.my.project.application.support.MorphologySnapshotForecast;
import com.my.project.application.support.OmissionDueProbabilityUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class IndexFeatureForecaster {
    private final HistoryRecordService historyRecordService;

    /** 本地 Java 指数计算使用的执行器，不用于大模型调用。 */
    private static final Executor FEATURE_FORECAST_EXECUTOR =
            Executors.newFixedThreadPool(FeatureKind.values().length,
                Thread.ofPlatform().daemon().name("feature-forecast").factory());


    /**
     * 红球 11 + 蓝球 4：用 forecastRecords 算出各形态 snapshot，按 indexValues 前后期差值趋势本地选主推。
     * <p>生成阶段即按遗漏到期概率把概率 &gt; 0 的形态取值排除（一/二/三区个数除外）：
     * 主推与备选都不会落入被排除值，避免事后剔除导致整维被排空。
     */
    public FeatureForecastBo forecast(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords,
                                                     List<HistoryRecord> historyRecords) {
        Map<String, Set<String>> dueByCode = buildOmissionDueByCode(historyRecords);
        return assembleFeatureForecast(forecastRecords, dueByCode);
    }



    private FeatureForecastBo assembleFeatureForecast(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords,
                                                      Map<String, Set<String>> dueByCode) {
        EnumMap<FeatureKind, CompletableFuture<FeatureForecastItem>> futures = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            Set<String> dueValues = dueByCode == null ? null : dueByCode.get(kind.getCode());
            futures.put(kind, CompletableFuture.supplyAsync(
                    () -> forecastOneFeature(kind, forecastRecords, dueValues),
                    FEATURE_FORECAST_EXECUTOR));
        }
        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

        EnumMap<FeatureKind, FeatureForecastItem> items = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            items.put(kind, futures.get(kind).join());
        }
        FeatureIntervalForecastUtils.reconcileBlueItems(items);
        FeatureIntervalForecastUtils.reconcileRedItems(items, forecastRecords);

        FeatureForecastBo forecast = FeatureIntervalForecastUtils.toBo(items);
        String engineHint = "本地按 indexValues 差值趋势评分；";
        String dueHint = dueByCode == null || dueByCode.values().stream().allMatch(Set::isEmpty)
                ? "" : "已按遗漏到期概率>0在生成阶段排除形态取值（一/二/三区个数除外）；";
        forecast.setBasis(String.format(Locale.ROOT,
                "红球11维+蓝球4维：主推贴理论众数，指数差值只做轻量加减分；%s%s"
                        + "众数刚出仍可主推；低频刚出与热度断档不主推；"
                        + "跨度/和值/区个数相邻高分合并为区间。样本%d期。",
                engineHint, dueHint, forecastRecords.size()));
        return forecast;
    }

    /**
     * 计算各形态维需排除的取值集合（遗漏到期概率 &gt; 0）。
     * <p>一区/二区/三区个数不参与排除；其余维度按 {@link FeatureDimension} 的 label 取对应概率 Map，
     * 概率 &gt; 0 的取值进入排除集。返回 code -&gt; 排除取值集合。
     */
    private Map<String, Set<String>> buildOmissionDueByCode(List<HistoryRecord> historyRecords) {
        if (CollectionUtils.isEmpty(historyRecords)) {
            return Map.of();
        }
        Map<String, Map<String, Double>> dueMap = OmissionDueProbabilityUtils.analyze(historyRecords);
        if (MapUtils.isEmpty(dueMap)) {
            return Map.of();
        }
        Map<String, Set<String>> dueByCode = new HashMap<>();
        List<String> excludedKinds = new ArrayList<>();
        for (FeatureDimension kind : FeatureDimension.values()) {
            if (isZoneCountKind(kind)) {
                continue;
            }
            Map<String, Double> valueProbs = dueMap.get(kind.getLabel());
            if (MapUtils.isEmpty(valueProbs)) {
                continue;
            }
            Set<String> dueValues = valueProbs.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if (dueValues.isEmpty()) {
                continue;
            }
            dueByCode.put(kind.getCode(), dueValues);
            excludedKinds.add(kind.getLabel() + "=" + StringUtils.join(dueValues, ","));
        }
        if (!excludedKinds.isEmpty()) {
            log.info("featureForecast 生成阶段排除遗漏到期概率>0的形态取值: {}", excludedKinds);
        }
        return dueByCode;
    }

    private static boolean isZoneCountKind(FeatureDimension kind) {
        return kind == FeatureDimension.ZONE1_COUNT
                || kind == FeatureDimension.ZONE2_COUNT
                || kind == FeatureDimension.ZONE3_COUNT;
    }

    private FeatureForecastItem forecastOneFeature(FeatureKind kind,
                                                   List<LotteryAnalysisReqBo.DrawRecord> forecastRecords, Set<String> dueValues) {
        try {
            String snapshot = buildFeatureSnapshot(kind, forecastRecords);
            return MorphologySnapshotForecast.forecast(snapshot, dueValues);
        } catch (Exception e) {
            log.warn("形态 [{}] 推算失败，回退 indexValues 差值趋势", kind.getLabel(), e);
            try {
                return MorphologySnapshotForecast.forecast(buildFeatureSnapshot(kind, forecastRecords), dueValues);
            } catch (Exception ex) {
                log.error("形态 [{}] indexValues 回退也失败", kind.getLabel(), ex);
                return null;
            }
        }
    }

    private String buildFeatureSnapshot(FeatureKind kind, List<LotteryAnalysisReqBo.DrawRecord> forecastRecords) {
        LotteryAnalysisReqBo.DrawRecord newest = forecastRecords.getFirst();
        String lastRatio = LotteryFeatureTrendUtils.extract(newest.getRedBalls(), newest.getBlueBall(), kind);
        List<HistoryRecord> newestFirst = forecastRecords.stream().map(this::toHistoryRecord).toList();
        var trend = historyRecordService.analyzePatternTrend(kind.getCode(), lastRatio, newestFirst);
        return LotteryMorphologySnapshotUtils.fromPatternTrendForLlm(trend);
    }

    private HistoryRecord toHistoryRecord(LotteryAnalysisReqBo.DrawRecord draw) {
        List<Integer> reds = draw.getRedBalls() == null ? List.of() : draw.getRedBalls();
        return HistoryRecord.builder().period(draw.getPeriod()).num1(!reds.isEmpty() ? reds.get(0) : null)
                .num2(reds.size() > 1 ? reds.get(1) : null).num3(reds.size() > 2 ? reds.get(2) : null)
                .num4(reds.size() > 3 ? reds.get(3) : null).num5(reds.size() > 4 ? reds.get(4) : null)
                .num6(reds.size() > 5 ? reds.get(5) : null).special(draw.getBlueBall()).build();
    }
}
