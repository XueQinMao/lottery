package com.my.project.api.history;

import lombok.RequiredArgsConstructor;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.api.response.ApiResponse;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.dto.HistoryRecordDto;
import com.my.project.application.history.vo.ColdHotTrendVo;
import com.my.project.application.history.vo.FeatureStatsVo;
import com.my.project.application.history.vo.PatternTrendVo;
import com.my.project.application.history.vo.TrendAnalysisVo;
import org.springframework.web.bind.annotation.*;


/**
 * HistoryController
 *
 * @author 刘强
 * @version 2025/07/17 16:09
 **/
@RequiredArgsConstructor
@RestController
@RequestMapping("api/history")
public class HistoryController {

    private final HistoryRecordService historyRecordService;

    @PostMapping()
    public ApiResponse<Void> syncHistoryRecords() {
        try {
            historyRecordService.syncHistoryRecords();
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping()
    public ApiResponse<Page<HistoryRecordDto>> findPage(
        @RequestParam(required = false, defaultValue = "10") int pageSize,
        @RequestParam(required = false, defaultValue = "0") int pageNum) {
        try {
            return ApiResponse.success(historyRecordService.findPage(new Page<>(pageNum, pageSize)));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 号码遗漏趋势分析。
     * <p>基于 {@code LotteryTrendUtils} 计算反向指数与 MA5/MA10/MA20，供前端趋势图展示。
     *
     * @param ballType   red / blue，默认 red
     * @param ball       号码（红 1-33，蓝 1-16），默认 1
     * @param sampleSize 最近期数，默认 100（可选 30/50/100）
     * @param endPeriod  截止期号（含该期往前推 sampleSize）；不传则最新
     */
    @GetMapping("/trend")
    public ApiResponse<TrendAnalysisVo> analyzeTrend(
        @RequestParam(required = false, defaultValue = "red") String ballType,
        @RequestParam(required = false, defaultValue = "1") int ball,
        @RequestParam(required = false, defaultValue = "100") int sampleSize,
        @RequestParam(required = false) String endPeriod) {
        try {
            TrendAnalysisVo trendAnalysisVo = historyRecordService.analyzeTrend(ballType, ball, sampleSize, endPeriod);
            System.out.println("trend"+JSON.toJSONString(trendAnalysisVo));
            return ApiResponse.success(trendAnalysisVo);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 开奖形态统计：红球和值、跨度、和值相对上一期增长差值、质合比，以及红/蓝球奇偶比。
     *
     * @param sampleSize 最近期数，默认 100
     * @param endPeriod  截止期号（含）；不传则最新
     */
    @GetMapping("/feature-stats")
    public ApiResponse<FeatureStatsVo> analyzeFeatureStats(
        @RequestParam(required = false, defaultValue = "100") int sampleSize,
        @RequestParam(required = false) String endPeriod) {
        try {
            FeatureStatsVo featureStatsVo = historyRecordService.analyzeFeatureStats(sampleSize, endPeriod);
            System.out.println("feature-stats"+JSON.toJSONString(featureStatsVo));
            return ApiResponse.success(featureStatsVo);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 形态遗漏与超额指数。
     * <p>指数 = 实际出现次数 − 理论出现次数（n × p）。
     *
     * @param feature    oddEven / bigSmall / primeComp / ratio012 / span / sumRange /
     *                   sumTail / threeZone / zone1Count / zone2Count / zone3Count /
     *                   blueOddEven / blueBigSmall / blueBigSmallOddEven / blueRatio012
     * @param ratio      分桶键，如 1:5、0:4:2、21、73-78、奇、大、大奇、0路
     * @param sampleSize 最近期数，默认 100
     * @param endPeriod  截止期号（含）；不传则最新
     */
    @GetMapping("/pattern-trend")
    public ApiResponse<PatternTrendVo> analyzePatternTrend(
        @RequestParam(required = false, defaultValue = "oddEven") String feature,
        @RequestParam(required = false, defaultValue = "3:3") String ratio,
        @RequestParam(required = false, defaultValue = "100") int sampleSize,
        @RequestParam(required = false) String endPeriod) {
        try {
            PatternTrendVo patternTrendVo =
                historyRecordService.analyzePatternTrend(feature, ratio, sampleSize, endPeriod);
            System.out.println("pattern-trend"+JSON.toJSONString(patternTrendVo));
            return ApiResponse.success(patternTrendVo);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 每期开奖号码中"冷/温/热"号个数趋势。
     * <p>对样本内每一期，取其前 30 期作为子样本调用 {@code ColdHotAnalysisUtils.calculate}
     * 得到冷温热号清单，再统计本期实际开出的红球/蓝球落入各档的个数。
     *
     * @param sampleSize 展示期数，默认 100（每期需多取 30 期作为前置样本）
     * @param endPeriod  截止期号（含）；不传则最新
     */
    @GetMapping("/cold-hot-trend")
    public ApiResponse<ColdHotTrendVo> analyzeColdHotTrend(
        @RequestParam(required = false, defaultValue = "100") int sampleSize,
        @RequestParam(required = false) String endPeriod) {
        try {
            ColdHotTrendVo coldHotTrendVo = historyRecordService.analyzeColdHotTrend(sampleSize, endPeriod);
            System.out.println("cold-hot-trend" + JSON.toJSONString(coldHotTrendVo));
            return ApiResponse.success(coldHotTrendVo);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

}
