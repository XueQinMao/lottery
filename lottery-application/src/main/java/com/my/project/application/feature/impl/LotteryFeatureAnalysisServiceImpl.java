package com.my.project.application.feature.impl;

import com.my.project.application.report.LotteryReportService;

import com.my.project.llm.bo.*;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.feature.LotteryFeatureAnalysisService;
import com.my.project.application.feature.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.application.support.analyse.KillBallUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import com.my.project.application.feature.support.IndexFeatureForecaster;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LotteryFeatureAnalysisServiceImpl
 *
 * <p>报告输出红蓝球形态分析、杀号、当前冷热池和历史开奖。
 * {@code llm-pattern} 为最近30期逐项大模型描述分支；{@code java} 适配原指数预测。
 *
 * @author 刘强
 * @version 2026/08/18
 **/
@Service
@RequiredArgsConstructor
public class LotteryFeatureAnalysisServiceImpl implements LotteryFeatureAnalysisService {

    private final HistoryRecordService historyRecordService;
    private final PatternFeatureAnalysisSupport patternAnalysisSupport;

    @Value("${lottery.llm.analysis.engine:llm-pattern}")
    private String analysisEngine;
    @Value("${lottery.llm.analysis.model:qwen-flash}")
    private String analysisModel = "qwen-flash";
    @Value("${lottery.llm.analysis.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String analysisBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode";
    private final LotteryAnalysisMultiLevelCache multiLevelCache;
    private final LotteryReportService reportService;
    private final IndexFeatureForecaster indexForecaster;


    private static final String CACHE_KEY_PREFIX = "feature.analysis.cache";
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public LotteryAnalysisRespBo analyzeLatest(int sampleSize) {
        int count = "java".equals(analysisEngine) ? Math.max(sampleSize, 1) : PatternFeatureAnalysisReqBo.HISTORY_SIZE;
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        String period = latest.getFirst().getPeriod();
        String cacheKey = CACHE_KEY_PREFIX + "-pattern-v10-"+ period;
        var analysisReport = multiLevelCache.get(cacheKey, k -> {
            var result = doAnalyze(count, period);
            // 特征预测解释生成后落盘并写入文件记录表，供历史列表回看
            if (result != null) {
                reportService.saveAnalysis(result, cacheKey);
            }
            return result;
        });
        Assert.notNull(analysisReport, "特征数据获取异常，请稍后重试");
        return analysisReport;
    }


    public LotteryAnalysisRespBo doAnalyze(int sampleSize) {
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) throw new IllegalStateException("无可用的历史开奖记录用于分析");
        return doAnalyze(sampleSize, latest.getFirst().getPeriod());
    }

    private LotteryAnalysisRespBo doAnalyze(int sampleSize, String endPeriod) {
        String engine = StringUtils.defaultIfBlank(analysisEngine, "llm-pattern");
        if (!Set.of("java", "llm-pattern").contains(engine)) {
            throw new IllegalArgumentException("analysis.engine仅支持java/llm-pattern");
        }
        ReentrantLock lock = lockMap.computeIfAbsent(engine + ":" + endPeriod + ":" + sampleSize,
            key -> new ReentrantLock());
        lock.lock();
        try {
            // 固定报告截止期，避免新增开奖导致报告与缓存期号错位。
            var records = historyRecordService.getRecordsEndingAt(endPeriod, 100000);
            if (CollectionUtils.isEmpty(records)) throw new IllegalStateException("无可用的历史开奖记录用于分析");
            if (records.size() < 60) throw new IllegalStateException("最近30期形态分析及历史冷热分类至少需要60期开奖");
            int historySize = "llm-pattern".equals(engine) ? PatternFeatureAnalysisReqBo.HISTORY_SIZE : 100;
            List<LotteryAnalysisReqBo.DrawRecord> recent = new ArrayList<>(records.subList(0,
                Math.min(historySize, records.size())).stream().map(this::toDrawRecord).toList());
            List<PatternFeatureAnalysisBo> analysis;
            if ("llm-pattern".equals(engine)) {
                analysis = patternAnalysisSupport.analyze(records);
                if (analysis.stream().noneMatch(item -> "OK".equals(item.getStatus()))) {
                    throw new IllegalStateException("所有大模型形态分析均失败，请重试");
                }
            } else {
                analysis = patternAnalysisSupport.adaptJava(forecastFeaturesByIndex(recent, records), records);
            }
            Collections.reverse(recent);
            return LotteryAnalysisRespBo.builder().featureAnalysis(analysis)
                .killNumbers(KillBallUtils.calculate(records))
                .coldHotAnalysis(ColdHotAnalysisUtils.calculate(records))
                .recentDrawRecords(recent).build();
        } finally {
            lock.unlock();
        }
    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
                Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                        record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
                .blueBall(record.getSpecial()).build();
    }


    public FeatureForecastBo forecastFeaturesByIndex(List<LotteryAnalysisReqBo.DrawRecord> forecastRecords,
                                                     List<HistoryRecord> historyRecords) {
        return indexForecaster.forecast(forecastRecords, historyRecords);
    }
}
