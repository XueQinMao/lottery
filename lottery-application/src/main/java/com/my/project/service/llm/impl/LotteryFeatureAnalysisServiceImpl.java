package com.my.project.service.llm.impl;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.*;
import com.my.project.llm.service.ILotteryAdjustService;
import com.my.project.llm.service.ILotteryAnalysisService;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.config.LotteryModelConfig;
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
import com.my.project.service.support.FileUtils;
import com.my.project.service.support.LotteryFeatureStatsUtils;
import com.my.project.service.support.LotteryTrendUtils;
import com.my.project.service.support.LotteryTrendUtils.TrendAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * LotteryFeatureAnalysisServiceImpl
 *
 * <p>从历史开奖记录中取最近 N 期一等奖号码（双色球即 6 红球 + 1 蓝球），
 * 组装为 {@link LotteryAnalysisReqBo} 后调用 {@link ILotteryAnalysisService}。 * <p>可选启用杀号功能：在 LLM 分析完成后，由
 * {@link IKillNumberService} 基于原始样本 * 计算杀号清单并挂到 {@link LotteryAnalysisRespBo#getKillNumbers()}。开关由请求级 *
 * {@link LotteryAnalysisReqBo#getEnableKillNumber()} 覆盖全局配置 * {@code lottery.llm.kill-number.enabled}。 * * <p>同时由
 * {@link IColdHotAnalysisService} 基于原始样本统计冷热温号码分类， * 挂到 {@link LotteryAnalysisRespBo#getColdHotAnalysis()}，供调优阶段 LLM
 * 直接引用。 * * <p>同时由 {@link IThreeZoneRatioPredictService} 基于「频率先验 + 马尔可夫转移」 * 混合模型预测下一期三区比，挂到
 * {@link LotteryAnalysisRespBo#getPredictedThreeZoneRatio()}。
 *
 * @author 刘强
 * @version 2026/07/21 20:36
 **/
@Slf4j
@Service
@RequiredArgsConstructor
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

    private static final String CACHE_KEY_PREFIX = "feature.analysis.cache";

    private final LotteryModelConfig lotteryModelConfig;

    @Value("${lottery.llm.analysis.engine:java}")
    private String analysisEngine;

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public LotteryAnalysisRespBo analyzeLatest(int sampleSize) {
        int count = Math.max(sampleSize, 1);
        var latest = historyRecordService.getLatestRecords(1);
        if (CollectionUtils.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        String period = latest.getFirst().getPeriod();
        String cacheKey = CACHE_KEY_PREFIX + period + sampleSize;
        //        String cacheKey = CACHE_KEY_PREFIX + period + UUID.randomUUID().toString();
        var respBo = multiLevelCache.get(cacheKey, k -> doAnalyze(count));
        Assert.notNull(respBo, "特征数据获取异常，请稍后重试");
        return respBo;
    }

    private LotteryAnalysisRespBo doAnalyze(int sampleSize) {
        log.info("拉取最近 {} 期历史开奖记录用于 LLM 分析", sampleSize);
        ReentrantLock lock = lockMap.computeIfAbsent(String.valueOf(sampleSize), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.warn("Key [" + sampleSize + "] 已被其他线程锁定，立即返回");
            return null;
        }
        var records = historyRecordService.getLatestRecords(sampleSize);
        if (CollectionUtils.isEmpty(records)) {
            throw new IllegalStateException("无可用的历史开奖记录用于分析");
        }
        // 最近一期（降序首条）用于默认杀号
        var latestRecord = records.getFirst();
        // 下游杀号遗漏 / 三区比马尔可夫均要求期号升序（最旧→最新）
        var chronological = new ArrayList<>(records);
        Collections.reverse(chronological);
        var drawRecords = records.subList(0, Math.min(30, sampleSize)).stream().map(this::toDrawRecord)
            .collect(Collectors.toList());
        var reqBo = LotteryAnalysisReqBo.builder().sampleSize(drawRecords.size()).enableKillNumber(true)
            .defaultKillNumbers(toDrawRecord(latestRecord)).records(drawRecords).build();
        var result = Objects.equals(analysisEngine, "java") ? LotteryFeatureStatsUtils.analyze(reqBo.getRecords())
            : lotteryAnalysisService.analyze(reqBo);
        result.setKillNumbers(reqBo.getEnableKillNumber() ? killNumberService.calculate(reqBo.getRecords(),
            reqBo.getDefaultKillNumbers()) : null);
        result.setColdHotAnalysis(coldHotAnalysisService.calculate(reqBo.getRecords()));
        result.setPredictedThreeZoneRatio(threeZoneRatioPredictService.predict(reqBo.getRecords()));
        result.setTrendAnalysis(calcTrendAnalysis(drawRecords));

        //统计一下杀8 10 12 14 16 18 20 的成功率
        var calculate10 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 2);
        var calculate12 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 4);
        var calculate14 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 6);
        var calculate16 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 8);
        var calculate18 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 10);
        var calculate20 = killNumberService.calculate(reqBo.getRecords(), reqBo.getDefaultKillNumbers(), 12);

        FileUtil.writeString(JSON.toJSONString(calculate10),
            new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_10.json"),
            StandardCharsets.UTF_8);
        FileUtil.writeString(JSON.toJSONString(calculate12),
            new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_12.json"),
            StandardCharsets.UTF_8);
        FileUtil.writeString(JSON.toJSONString(calculate14),
            new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_14.json"),
            StandardCharsets.UTF_8);
        FileUtil.writeString(JSON.toJSONString(calculate16),
            new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_16.json"),
            StandardCharsets.UTF_8);
        FileUtil.writeString(JSON.toJSONString(calculate18),
            new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_18.json"),
            StandardCharsets.UTF_8);
        FileUtil.writeString(JSON.toJSONString(calculate20),
            new File(lotteryModelConfig.getPath() + "/kill/kill_" + latestRecord.getPeriod() + "_20.json"),
            StandardCharsets.UTF_8);
        return result;
        }



    /**
     * 计算红球 1-33 和蓝球 1-16 的趋势均线排列。
     * <p>基于反向指数的 MA5/MA10/MA20 均线：
     * <ul>
     *     <li>多头排列（MA5 > MA10 > MA20）→ 指数上升 → 号码趋热 → rising</li>
     *     <li>空头排列（MA5 < MA10 < MA20）→ 指数下降 → 号码趋冷 → falling</li>
     * </ul>
     */
    private LotteryAnalysisRespBo.TrendAnalysisBo calcTrendAnalysis(List<LotteryAnalysisReqBo.DrawRecord> records) {
        // 红球：每期 6 个红球转为 Set
        List<Set<Integer>> redDraws = new ArrayList<>();
        List<Set<Integer>> blueDraws = new ArrayList<>();
        for (var r : records) {
            redDraws.add(r.getRedBalls() != null ? new HashSet<>(r.getRedBalls()) : new HashSet<>());
            blueDraws.add(r.getBlueBall() != null ? Set.of(r.getBlueBall()) : new HashSet<>());
        }

        List<Integer> risingRed = new ArrayList<>();
        List<Integer> fallingRed = new ArrayList<>();
        for (int b = 1; b <= 33; b++) {
            TrendAnalysisResult result = LotteryTrendUtils.analyze(redDraws, b);
            int arr = result.getArrangement();
            if (arr == 1) {
                risingRed.add(b);
            } else if (arr == -1) {
                fallingRed.add(b);
            }
        }

        List<Integer> risingBlue = new ArrayList<>();
        List<Integer> fallingBlue = new ArrayList<>();
        for (int b = 1; b <= 16; b++) {
            TrendAnalysisResult result = LotteryTrendUtils.analyze(blueDraws, b);
            int arr = result.getArrangement();
            if (arr == 1) {
                risingBlue.add(b);
            } else if (arr == -1) {
                fallingBlue.add(b);
            }
        }

        log.info("趋势分析: 红球上升={}, 下降={}, 蓝球上升={}, 下降={}", risingRed, fallingRed, risingBlue,
            fallingBlue);

        return LotteryAnalysisRespBo.TrendAnalysisBo.builder().risingRedBalls(risingRed).fallingRedBalls(fallingRed)
            .risingBlueBalls(risingBlue).fallingBlueBalls(fallingBlue).build();
    }

    @Override
    public LotteryAdjustRespBo adjust(LLmAdjustDto dto) {
        var respBo = analyzeLatest(100);
        var tickets = CollectionUtils.emptyIfNull(dto.getDrawRecords()).stream().map(
                d -> LotteryAdjustReqBo.PredictTicket.builder().redBalls(d.getRedballs()).blueBall(d.getBlueball()).build())
            .toList();
        var adjustReqBo =
            LotteryAdjustReqBo.builder().analysisReportJson(JSONObject.toJSONString(respBo)).tickets(tickets)
                .count(dto.getCount()).build();
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
