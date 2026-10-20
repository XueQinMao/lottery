package com.my.project.api.history;

import lombok.RequiredArgsConstructor;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.api.response.ApiResponse;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.DrawSynchronizationService;
import com.my.project.application.history.dto.HistoryRecordDto;
import com.my.project.application.history.vo.ColdHotTrendVo;
import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.history.vo.NumberTrendVo;
import com.my.project.application.history.vo.FeatureStatsVo;
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
    private final DrawSynchronizationService synchronization;

    @GetMapping("/number-trends")
    public ApiResponse<NumberTrendVo> numberTrends(
        @RequestParam(defaultValue = "100") int sampleSize,
        @RequestParam(required = false) String endPeriod) {
        try {
            return ApiResponse.success(historyRecordService.analyzeNumberTrends(sampleSize, endPeriod));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/draw-trends")
    public ApiResponse<DrawTrendVo> drawTrends(
        @RequestParam(defaultValue = "100") int sampleSize,
        @RequestParam(required = false) String endPeriod) {
        try {
            return ApiResponse.success(historyRecordService.analyzeDrawTrends(sampleSize, endPeriod));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping()
    public ApiResponse<Void> syncHistoryRecords() {
        try {
            synchronization.synchronize();
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
