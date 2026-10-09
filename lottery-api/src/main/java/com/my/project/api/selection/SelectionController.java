package com.my.project.api.selection;

import lombok.RequiredArgsConstructor;

import com.my.project.api.response.ApiResponse;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.selection.model.WeightConfigBo;
import com.my.project.application.selection.vo.FushiPlanVo;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能选号控制器
 *
 * @author 刘强
 * @version 2025/11/03
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/smart-select")
public class SelectionController {

    private final SmartSelectService smartSelectService;

    /**
     * 复式推荐（使用动态优化权重）
     * 推荐核心号码并扩展为均衡型复式
     *
     * @param openDate 开奖日期
     * @return 复式方案
     */
    @GetMapping("/recommend-fushi")
    public ApiResponse<List<FushiPlanVo>> recommendFushi(@RequestParam("openDate") LocalDate openDate) {
        try {
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error("复式推荐失败: " + e.getMessage());
        }
    }

    /**
     * 查看当前权重配置
     *
     * @return 权重配置详情
     */
    @GetMapping("/weight-configs")
    public ApiResponse<WeightConfigBo> getWeightConfigs() {
        return ApiResponse.success(smartSelectService.getWeightConfig());
    }

    /**
     * 手动刷新权重配置
     * 当有新的中奖数据时，可调用此接口重新计算权重
     *
     * @return 刷新结果
     */
    @PostMapping("/refresh-weights")
    public ApiResponse<String> refreshWeightConfig() {
        try {
            smartSelectService.refreshWeightConfig();
            WeightConfigBo config = smartSelectService.getWeightConfig();

            String message = String.format(
                "权重刷新完成！分层比例 high/mid/low=%.2f/%.2f/%.2f，概率区间 P5/P95=[%s, %s]",
                config.getHighScoreRatio(),
                config.getMidScoreRatio(),
                config.getLowScoreRatio(),
                config.getProbabilityMin(),
                config.getProbabilityMax());

            return ApiResponse.success(message);
        } catch (Exception e) {
            return ApiResponse.error("权重刷新失败: " + e.getMessage());
        }
    }
}
