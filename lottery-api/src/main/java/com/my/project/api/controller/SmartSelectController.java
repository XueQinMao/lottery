package com.my.project.api.controller;

import com.my.project.api.pojo.resp.Result;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;
import com.my.project.service.selection.pojo.vo.FushiPlanVo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能选号控制器
 *
 * @author 刘强
 * @version 2025/11/03
 */
@RestController
@RequestMapping("/api/smart-select")
public class SmartSelectController {

    @Resource
    private ISmartSelectService smartSelectService;

    /**
     * 复式推荐（使用动态优化权重）
     * 推荐核心号码并扩展为均衡型复式
     *
     * @param openDate 开奖日期
     * @return 复式方案
     */
    @GetMapping("/recommend-fushi")
    public Result<List<FushiPlanVo>> recommendFushi(@RequestParam("openDate") LocalDate openDate) {
        try {
            return Result.success();
        } catch (Exception e) {
            return Result.error("复式推荐失败: " + e.getMessage());
        }
    }

    /**
     * 查看当前权重配置
     *
     * @return 权重配置详情
     */
    @GetMapping("/weight-configs")
    public Result<WeightConfigBo> getWeightConfigs() {
        return Result.success(smartSelectService.getWeightConfig());
    }

    /**
     * 手动刷新权重配置
     * 当有新的中奖数据时，可调用此接口重新计算权重
     *
     * @return 刷新结果
     */
    @PostMapping("/refresh-weights")
    public Result<String> refreshWeightConfig() {
        try {
            smartSelectService.refreshWeightConfig();
            WeightConfigBo config = smartSelectService.getWeightConfig();

            String message = String.format(
                "权重刷新完成！当前配置: 数值特征=%.3f, 离散特征=%.3f, 模型分数=%.3f",
                config.getNumFeatureWeight(),
                config.getCatFeatureWeight(),
                config.getModelScoreWeight());

            return Result.success(message);
        } catch (Exception e) {
            return Result.error("权重刷新失败: " + e.getMessage());
        }
    }
}
