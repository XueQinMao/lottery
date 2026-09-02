package com.my.project.service;

import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import com.my.project.service.support.InformationIndexUtils;
import com.my.project.service.support.InformationIndexUtils.Forecast;
import com.my.project.service.support.InformationIndexUtils.Report;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用全量历史开奖跑信息指数工具，只打印结果，不接入推荐/调优主流程。
 */
@SpringBootTest
class InformationIndexTest {

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Resource
    private IHistoryRecordService historyRecordService;

    @Test
    void printInformationIndexFromHistory() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 30").list();

        windows.forEach(w -> {
            List<HistoryRecord> past100 = historyRecordRepository.lambdaQuery()
                .le(HistoryRecord::getOpenDate, w.getOpenDate())
                .orderByDesc(HistoryRecord::getOpenDate).last("limit 100").list();
            if (CollectionUtils.size(past100) < 10) {
                throw new IllegalStateException("历史开奖不足，无法计算信息指数");
            }
            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());

            Report report = InformationIndexUtils.analyze(past100);

            PatternTrendVo sampleTrend =
                historyRecordService.analyzePatternTrend("oddEven", "3:3", past100);
            Forecast sampleForecast = InformationIndexUtils.fromPatternTrend(sampleTrend);
            assertEquals("3:3", sampleForecast.getKey());
            assertTrue(sampleForecast.getInformationIndex() >= 0 && sampleForecast.getInformationIndex() <= 1);

            List<Integer> pickReds = report.getRedBalls().stream()
                .filter(Forecast::isBullish)
                .map(f -> Integer.parseInt(f.getKey()))
                .toList();
            List<Integer> killReds = report.getRedBalls().stream()
                .filter(f -> !f.isBullish())
                .map(f -> Integer.parseInt(f.getKey()))
                .toList();
            List<Integer> pickBlues = report.getBlueBalls().stream()
                .filter(Forecast::isBullish)
                .map(f -> Integer.parseInt(f.getKey()))
                .toList();
            List<Integer> hitPicks = (List<Integer>) CollectionUtils.intersection(pickReds, nextWins);
            List<Integer> missKills = (List<Integer>) CollectionUtils.intersection(killReds, nextWins);

            System.out.println("第" + w.getPeriod() + "期信息指数（样本 " + report.getFromPeriod()
                + " → " + report.getToPeriod() + "）");
            System.out.println("红球同比大：" + StringUtils.join(pickReds, ",")
                + " 命中：" + hitPicks.size() + "/" + pickReds.size()
                + " 实际：" + StringUtils.join(nextWins, ","));
            System.out.println("红球同比小误杀：" + missKills.size()
                + " 蓝球同比大：" + StringUtils.join(pickBlues, ",")
                + " 蓝球命中：" + pickBlues.contains(next.getSpecial()));

            List<String> missedFeatures = new ArrayList<>();
            for (FeatureKindEnums kind : FeatureKindEnums.values()) {
                String actual = kind.getFunction().apply(nextWins, next.getSpecial());
                var group = report.getFeatures().stream()
                    .filter(g -> kind.getCode().equals(g.getCode()))
                    .findFirst()
                    .orElseThrow();
                List<Forecast> bullish = group.getBuckets().stream()
                    .filter(Forecast::isBullish)
                    .toList();
                String picks = bullish.stream()
                    .map(f -> f.getKey() + "(" + pct(f.getInformationIndex()) + ")")
                    .collect(Collectors.joining(","));
                boolean hit = bullish.stream().anyMatch(f -> f.getKey().equals(actual));
                System.out.println("第" + w.getPeriod() + "预测" + kind.getLabel()
                    + " 同比大：" + picks
                    + " 实际：" + actual
                    + " 是否命中：" + hit);
                if (!hit) {
                    missedFeatures.add(kind.getLabel());
                }
            }
            assertFalse(report.getFeatures().isEmpty());
            System.out.println("奇偶比3:3 接口趋势信息指数：" + pct(sampleForecast.getInformationIndex())
                + " 同比：" + sampleForecast.getComparison()
                + " 指数：" + sampleForecast.getIndex()
                + " → 预测：" + sampleForecast.getPredictedIndex());
            System.out.println("特征未命中的：" + StringUtils.join(missedFeatures, ","));
            System.out.println("***********************************");
        });
    }

    private static String pct(double p) {
        return String.format("%.2f%%", p * 100);
    }
}
