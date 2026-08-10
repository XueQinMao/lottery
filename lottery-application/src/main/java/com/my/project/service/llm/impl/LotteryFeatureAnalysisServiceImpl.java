package com.my.project.service.llm.impl;

import com.alibaba.fastjson.JSONObject;
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
import com.my.project.service.llm.IColdHotAnalysisService;
import com.my.project.service.llm.IKillNumberService;
import com.my.project.service.llm.ILotteryFeatureAnalysisService;
import com.my.project.service.llm.IThreeZoneRatioPredictService;
import com.my.project.service.llm.cache.LotteryAnalysisMultiLevelCache;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * LotteryFeatureAnalysisServiceImpl
 *
 * <p>从历史开奖记录中取最近 N 期一等奖号码（双色球即 6 红球 + 1 蓝球），
 * 组装为 {@link LotteryAnalysisReqBo} 后调用 {@link ILotteryAnalysisService}。
 * * <p>可选启用杀号功能：在 LLM 分析完成后，由 {@link IKillNumberService} 基于原始样本
 *  * 计算杀号清单并挂到 {@link LotteryAnalysisRespBo#getKillNumbers()}。开关由请求级
 *  * {@link LotteryAnalysisReqBo#getEnableKillNumber()} 覆盖全局配置
 *  * {@code lottery.llm.kill-number.enabled}。
 *  *
 *  * <p>同时由 {@link IColdHotAnalysisService} 基于原始样本统计冷热温号码分类，
 *  * 挂到 {@link LotteryAnalysisRespBo#getColdHotAnalysis()}，供调优阶段 LLM 直接引用。
 *  *
 *  * <p>同时由 {@link IThreeZoneRatioPredictService} 基于「频率先验 + 马尔可夫转移」
 *  * 混合模型预测下一期三区比，挂到 {@link LotteryAnalysisRespBo#getPredictedThreeZoneRatio()}。
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
    private final LotteryAnalysisMultiLevelCache multiLevelCache;

    private final IKillNumberService killNumberService;
    private final IColdHotAnalysisService coldHotAnalysisService;
    private final IThreeZoneRatioPredictService threeZoneRatioPredictService;

    private static final String CACHE_KEY_PREFIX = "LotteryFeatureAnalysisServiceImpl.analyzeLatest";

    @Override
    public LotteryAnalysisRespBo analyzeLatest(int sampleSize) {
        int count = Math.max(sampleSize, 1);
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        String period = latest.getFirst().getPeriod();
        String cacheKey = CACHE_KEY_PREFIX + period + sampleSize;
        return multiLevelCache.get(cacheKey, k -> doAnalyze(count));
    }

    private LotteryAnalysisRespBo doAnalyze(int sampleSize) {
        log.info("拉取最近 {} 期历史开奖记录用于 LLM 分析", sampleSize);
        // getLatestRecords 按开奖日降序（最新→最旧）
        var records = historyRecordService.getLatestRecords(sampleSize);
        if (CollectionUtils.isEmpty(records)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        // 最近一期（降序首条）用于默认杀号
        var latestRecord = records.getFirst();
        // 下游杀号遗漏 / 三区比马尔可夫均要求期号升序（最旧→最新）
        var chronological = new ArrayList<>(records);
        Collections.reverse(chronological);
        var drawRecords = chronological.stream().map(this::toDrawRecord).collect(Collectors.toList());
        var reqBo = LotteryAnalysisReqBo.builder().sampleSize(drawRecords.size()).enableKillNumber(true)
            .defaultKillNumbers(toDrawRecord(latestRecord)).records(drawRecords).build();
//        var result = lotteryAnalysisService.analyze(reqBo);
        LotteryAnalysisRespBo result = new LotteryAnalysisRespBo();
        result.setKillNumbers(
            reqBo.getEnableKillNumber() ? killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers())
                : null);
        result.setColdHotAnalysis(coldHotAnalysisService.calculate(reqBo.getRecords()));
        result.setPredictedThreeZoneRatio(threeZoneRatioPredictService.predict(reqBo.getRecords()));
        return result;
    }

    @Override
    public LotteryAdjustRespBo adjust(LLmAdjustDto dto) {
        var respBo = analyzeLatest(30);
        var tickets = CollectionUtils.emptyIfNull(dto.getDrawRecords()).stream().map(
                d -> LotteryAdjustReqBo.PredictTicket.builder().redBalls(d.getRedballs()).blueBall(d.getBlueball()).build())
            .toList();
        var adjustReqBo =
            LotteryAdjustReqBo.builder()
                .analysisReportJson(JSONObject.toJSONString(respBo))
                .tickets(tickets)
                .count(dto.getCount())
                .build();
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
        var respBo = analyzeLatest(30);

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
