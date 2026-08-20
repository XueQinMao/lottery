package com.my.project.api.controller;

import com.my.project.api.pojo.req.LLmAnalysisReq;
import com.my.project.api.pojo.resp.Result;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.service.llm.ILotteryFeatureAnalysisService;
import com.my.project.service.llm.pojo.dto.LLmAdjustDto;
import com.my.project.service.llm.pojo.vo.AdjustHistoryFileVo;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 调优 / 推荐入口。
     * <ul>
     *     <li>drawRecords 非空 → 调优模式</li>
     *     <li>drawRecords 为空或不传 → 推荐模式，仅按 count（默认 2，上限 10）生成号码组</li>
     * </ul>
     */
    @PostMapping("/adjust")
    public Result<LotteryAdjustViewBo> analyzeByRedBalls(@RequestBody(required = false) LLmAnalysisReq req) {
        try {
            if (req == null) {
                req = new LLmAnalysisReq();
            }
            var list = CollectionUtils.emptyIfNull(req.getDrawRecords()).stream().map(
                d -> LLmAdjustDto.DrawRecord.builder().redballs(d.getRedballs()).blueball(d.getBlueball())
                    .build()).toList();

            var lLmAdjustDto =
                LLmAdjustDto.builder().drawRecords(list).count(req.getCount()).build();
            return Result.success(lotteryFeatureAnalysisService.adjust(lLmAdjustDto));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 最近推荐文件名列表（按修改时间倒序）。
     */
    @GetMapping("/adjust/history")
    public Result<List<AdjustHistoryFileVo>> listAdjustHistory(
            @RequestParam(required = false, defaultValue = "20") int limit) {
        try {
            return Result.success(lotteryFeatureAnalysisService.listAdjustHistory(limit));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按文件名读取推荐详情。
     */
    @GetMapping("/adjust/history/{fileName:.+}")
    public Result<LotteryAdjustViewBo> loadAdjustHistory(@PathVariable String fileName) {
        try {
            return Result.success(lotteryFeatureAnalysisService.loadAdjustHistory(fileName));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/adjust/{count}/{isTopN}")
    public Result<LotteryAdjustViewBo> adjustFromCacheTop(@PathVariable Integer count, @PathVariable boolean isTopN) {
        try {
            return Result.success(lotteryFeatureAnalysisService.adjust(count, isTopN));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
