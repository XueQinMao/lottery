package com.my.project.service.llm.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.my.project.llm.bo.LotteryAdjustReqBo;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.llm.service.ILotteryAdjustService;
import com.my.project.llm.service.ILotteryAnalysisService;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.event.AdjustCompleteEvent;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.llm.ILotteryFeatureAnalysisService;
import com.my.project.service.llm.pojo.dto.LLmAdjustDto;
import com.my.project.service.predict.pojo.vo.PredictCacheVo;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.selection.ISmartSelectService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LotteryFeatureAnalysisServiceImpl
 *
 * <p>从历史开奖记录中取最近 N 期一等奖号码（双色球即 6 红球 + 1 蓝球），
 * 组装为 {@link LotteryAnalysisReqBo} 后调用 {@link ILotteryAnalysisService}。
 *
 * @author 刘强
 * @version 2026/07/21 20:36
 **/
@Slf4j
@Service
@AllArgsConstructor
public class LotteryFeatureAnalysisServiceImpl implements ILotteryFeatureAnalysisService {

    private final IHistoryRecordService historyRecordService;
    private final ILotteryAnalysisService lotteryAnalysisService;
    private final ILotteryAdjustService lotteryAdjustService;
    private final IPredictCacheService predictCacheService;

    private final ISmartSelectService smartSelectService;

    private static final String CACHE_KEY = "LotteryFeatureAnalysisServiceImpl.analyzeLatest";

    private static final Cache<String, LotteryAnalysisRespBo> ANALYSIS_RESP_BO_CACHE =
        Caffeine.newBuilder().maximumSize(1)
            .evictionListener((String key, LotteryAnalysisRespBo value, RemovalCause cause) -> {
                log.warn("Cache ANALYSIS_RESP_BO_CACHE evicted: key={}, cause={}", key, cause);
            }).recordStats().build();

    @Override
    public LotteryAnalysisRespBo analyzeLatest(int sampleSize) {
        var analysisRespBo = ANALYSIS_RESP_BO_CACHE.getIfPresent(CACHE_KEY);
        if (Objects.nonNull(analysisRespBo)) {
            return analysisRespBo;
        }
        int count = Math.max(sampleSize, 1);
        log.info("拉取最近 {} 期历史开奖记录用于 LLM 分析", count);
        var records = historyRecordService.getLatestRecords(count);
        if (CollectionUtils.isEmpty(records)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        var drawRecords = records.stream().map(this::toDrawRecord).collect(Collectors.toList());
        var reqBo = LotteryAnalysisReqBo.builder().sampleSize(drawRecords.size()).records(drawRecords).build();
        var analyzeResult = lotteryAnalysisService.analyze(reqBo);
        if (Objects.nonNull(analyzeResult)) {
            ANALYSIS_RESP_BO_CACHE.put(CACHE_KEY, analyzeResult);
        }
        return analyzeResult;
    }

    @Override
    public LotteryAdjustRespBo adjust(LLmAdjustDto dto) {
        var respBo = analyzeLatest(100);
        var tickets = CollectionUtils.emptyIfNull(dto.getDrawRecords()).stream().map(
                d -> LotteryAdjustReqBo.PredictTicket.builder().redBalls(d.getRedballs()).blueBall(d.getBlueball()).build())
            .toList();
        var adjustReqBo =
            LotteryAdjustReqBo.builder().analysisReportJson(JSONObject.toJSONString(respBo)).tickets(tickets).build();
        var adjust = lotteryAdjustService.adjust(adjustReqBo);
        AdjustCompleteEvent.of(this, adjust).publish();
        return adjust;
    }

    @Override
    public LotteryAdjustRespBo adjust(Integer count, boolean isTopN) {
        count = Math.min(count, 10);
        Map<String, ModelPredictOutputBo> predictCacheMaps = null;
        if (isTopN) {
            predictCacheMaps =
                Optional.ofNullable(predictCacheService.queryCache(count, null)).map(PredictCacheVo::getCacheDatas)
                    .orElse(Map.of());
        } else {
            predictCacheMaps = smartSelectService.percentileSample(count);
        }

        Assert.isTrue(MapUtils.isNotEmpty(predictCacheMaps), "预选号码组获取异常");
        var tickets = predictCacheMaps.keySet().stream().map(modelPredictOutput -> {
            var split = modelPredictOutput.split("\\|");
            var redBalls = Arrays.stream(split[1].split(",")).map(Integer::parseInt).toList();
            return LotteryAdjustReqBo.PredictTicket.builder().redBalls(redBalls).blueBall(Integer.valueOf(split[2]))
                .build();
        }).toList();
        var respBo = analyzeLatest(100);

        var adjustReqBo =
            LotteryAdjustReqBo.builder().analysisReportJson(JSONObject.toJSONString(respBo)).tickets(tickets).build();
        var adjust = lotteryAdjustService.adjust(adjustReqBo);
        AdjustCompleteEvent.of(this, adjust).publish();
        return adjust;
    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
            Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
            .blueBall(record.getSpecial()).build();
    }
}
