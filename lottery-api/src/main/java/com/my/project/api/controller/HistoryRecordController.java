package com.my.project.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.api.pojo.resp.Result;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.history.pojo.dto.HistoryRecordDto;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * HistoryRecordController
 *
 * @author 刘强
 * @version 2025/07/17 16:09
 **/
@RestController
@RequestMapping("api/history")
public class HistoryRecordController {

    @Autowired
    private IHistoryRecordService historyRecordService;

    @PostMapping()
    public Result<Void> syncHistoryRecords() {
        try {
            historyRecordService.syncHistoryRecords();
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping()
    public Result<Page<HistoryRecordDto>> findPage(
        @RequestParam(required = false, defaultValue = "10") int pageSize,
        @RequestParam(required = false, defaultValue = "0") int pageNum) {
        try {
            return Result.success(historyRecordService.findPage(new Page<>(pageNum, pageSize)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 号码遗漏趋势分析。
     * <p>基于 {@code LotteryTrendUtils} 计算反向指数与 MA5/MA10/MA20，供前端趋势图展示。
     *
     * @param ballType   red / blue，默认 red
     * @param ball       号码（红 1-33，蓝 1-16），默认 1
     * @param sampleSize 最近期数，默认 100
     */
    @GetMapping("/trend")
    public Result<TrendAnalysisVo> analyzeTrend(
        @RequestParam(required = false, defaultValue = "red") String ballType,
        @RequestParam(required = false, defaultValue = "1") int ball,
        @RequestParam(required = false, defaultValue = "100") int sampleSize) {
        try {
            return Result.success(historyRecordService.analyzeTrend(ballType, ball, sampleSize));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

}
