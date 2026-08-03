package com.my.project.api.controller;

import com.my.project.api.pojo.req.LLmAnalysisReq;
import com.my.project.api.pojo.resp.Result;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.service.llm.ILotteryFeatureAnalysisService;
import com.my.project.service.llm.pojo.dto.LLmAdjustDto;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * LotteryLlmController
 *
 * <p>大模型号码特征分析接口。
 * <ul>
 *     <li>{@code POST /api/llm/analyze}：直接传入号码样本进行分析</li>
 *     <li>{@code GET /api/llm/analyze/latest?sampleSize=100}：自动拉取最近 N 期一等奖号码分析</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/07/21 20:40
 **/
@RestController
@RequestMapping("api/llm")
public class LotteryLlmController {

    @Autowired
    private ILotteryFeatureAnalysisService lotteryFeatureAnalysisService;

    /**
     * 自动拉取最近 {@code sampleSize} 期一等奖号码并分析。
     */
    @GetMapping("/analyze/latest")
    public Result<LotteryAnalysisRespBo> analyzeLatest(
            @RequestParam(required = false, defaultValue = "100") int sampleSize) {
        try {
            return Result.success(lotteryFeatureAnalysisService.analyzeLatest(sampleSize));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 便捷入口：直接传红球列表（每注 6 个红球）进行分析。
     */
    @PostMapping("/adjust")
    public Result<LotteryAdjustRespBo> analyzeByRedBalls(@RequestBody LLmAnalysisReq req) {
        try {
            var list = CollectionUtils.emptyIfNull(req.getDrawRecords()).stream().map(
                d -> LLmAdjustDto.DrawRecord.builder().redballs(d.getRedballs()).blueball(d.getBlueball())
                    .build()).toList();

            var lLmAdjustDto =
                LLmAdjustDto.builder().drawRecords(list).build();
            return Result.success(lotteryFeatureAnalysisService.adjust(lLmAdjustDto));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/adjust/{count}/{isTopN}")
    public Result<LotteryAdjustRespBo> adjustFromCacheTop(@PathVariable Integer count, @PathVariable boolean isTopN) {
        try {
            return Result.success(lotteryFeatureAnalysisService.adjust(count, isTopN));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
