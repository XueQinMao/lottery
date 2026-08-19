package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.prompt.LotteryAnalysisPrompt;
import com.my.project.llm.service.ILotteryAnalysisService;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.support.LotteryFeatureTrendUtils;
import com.my.project.service.support.LotteryMorphologySnapshotUtils;
import com.my.project.service.support.MorphologySnapshotForecast;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LotteryFeatureAnalysisServiceTest
 *
 * @author 刘强
 * @version 2026/08/19 14:51
 **/
@SpringBootTest
public class LotteryFeatureAnalysisServiceTest {

    @Resource
    private IHistoryRecordService historyRecordService;

    @Resource
    private ILotteryAnalysisService lotteryAnalysisService;

    @Test
    public void test_ananylsis() {
        LotteryFeatureTrendUtils.FeatureKind kind = LotteryFeatureTrendUtils.FeatureKind.THREE_ZONE;
        var records = historyRecordService.getLatestRecords(101).stream()
            .filter(r -> !r.getPeriod().equals("2026095")).toList();
        var forecastRecords = records.stream()
            .map(this::toDrawRecord)
            .collect(Collectors.toList());

        LotteryAnalysisReqBo.DrawRecord newest = forecastRecords.getFirst();
        String lastRatio = LotteryFeatureTrendUtils.extract(
            newest.getRedBalls(), newest.getBlueBall(), kind);
        List<HistoryRecord> newestFirst = forecastRecords.stream().map(this::toHistoryRecord).toList();
        var trend = historyRecordService.analyzePatternTrend(kind.getCode(), lastRatio, newestFirst);
        String snapshot = LotteryMorphologySnapshotUtils.fromPatternTrendForLlm(trend);
        String compact = MorphologySnapshotForecast.compactForLlm(snapshot);
        System.out.println(compact);

        String replace = LotteryAnalysisPrompt.FORECAST_ONE_PROMPT.replace("{label}", kind.getLabel())
            .replace("{hint}", kind.valueHint()).replace("{snapshot}", compact)
            .replace("{format}", LotteryAnalysisPrompt.FORECAST_ONE_FORMAT);

        System.out.println(replace);

        FeatureForecastBo.FeatureForecastItem javaItem = MorphologySnapshotForecast.forecast(snapshot);
        System.out.println("java=" + JSON.toJSONString(javaItem));

        FeatureForecastBo.FeatureForecastItem llmItem =
            lotteryAnalysisService.forecastOne(kind.getLabel(), kind.valueHint(), compact);
        FeatureForecastBo.FeatureForecastItem guarded = MorphologySnapshotForecast.applyGuard(llmItem, snapshot);
        System.out.println("llm=" + JSON.toJSONString(guarded));
    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
            Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
            .blueBall(record.getSpecial()).build();
    }

    private HistoryRecord toHistoryRecord(LotteryAnalysisReqBo.DrawRecord draw) {
        List<Integer> reds = draw.getRedBalls() == null ? List.of() : draw.getRedBalls();
        return HistoryRecord.builder()
            .period(draw.getPeriod())
            .num1(reds.size() > 0 ? reds.get(0) : null)
            .num2(reds.size() > 1 ? reds.get(1) : null)
            .num3(reds.size() > 2 ? reds.get(2) : null)
            .num4(reds.size() > 3 ? reds.get(3) : null)
            .num5(reds.size() > 4 ? reds.get(4) : null)
            .num6(reds.size() > 5 ? reds.get(5) : null)
            .special(draw.getBlueBall())
            .build();
    }
}
