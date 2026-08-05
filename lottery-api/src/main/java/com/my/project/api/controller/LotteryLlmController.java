package com.my.project.api.controller;

import com.my.project.api.pojo.req.LLmAnalysisReq;
import com.my.project.api.pojo.req.LlmRecommendTaskReq;
import com.my.project.api.pojo.resp.Result;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.service.feature.ILlmAdjustTaskService;
import com.my.project.service.feature.ILotteryFeatureAnalysisService;
import com.my.project.service.feature.enums.LlmAdjustTaskMode;
import com.my.project.service.feature.pojo.dto.LLmAdjustDto;
import com.my.project.service.feature.pojo.dto.LlmAdjustTaskSubmitDto;
import com.my.project.service.feature.pojo.vo.AdjustHistoryFileVo;
import com.my.project.service.feature.pojo.vo.LlmAdjustTaskVo;
import com.my.project.service.record.pojo.vo.PredictFileRecordVo;
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

    @Autowired
    private ILlmAdjustTaskService llmAdjustTaskService;

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
     *     <li>userRequirement 可选：用户附加要求提示词，拼入 Prompt 后与安全网求交，不得越界</li>
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
                LLmAdjustDto.builder().drawRecords(list)
                    .lastDrawRedBalls(req.getLastDrawRedBalls())
                    .lastDrawBlueBall(req.getLastDrawBlueBall())
                    .count(req.getCount())
                    .userRequirement(req.getUserRequirement())
                    .build();
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
            @RequestParam(required = false, defaultValue = "4") int limit) {
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
    public Result<LotteryAdjustViewBo> adjustFromCacheTop(
            @PathVariable Integer count,
            @PathVariable boolean isTopN,
            @RequestParam(required = false) String userRequirement) {
        try {
            return Result.success(lotteryFeatureAnalysisService.adjust(count, isTopN, userRequirement));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 提交 LLM 推荐异步任务，立即返回 taskId。
     */
    @PostMapping("/recommend-task")
    public Result<LlmAdjustTaskVo> submitRecommendTask(@RequestBody(required = false) LlmRecommendTaskReq req) {
        try {
            if (req == null) {
                req = new LlmRecommendTaskReq();
            }
            LlmAdjustTaskMode mode = LlmAdjustTaskMode.of(req.getMode());
            if (mode == null && (req.getMode() == null || req.getMode().isBlank())) {
                mode = LlmAdjustTaskMode.FEATURE;
            }
            var dto = LlmAdjustTaskSubmitDto.builder()
                    .mode(mode)
                    .count(req.getCount())
                    .isTopN(req.getIsTopN())
                    .userRequirement(req.getUserRequirement())
                    .build();
            return Result.success(llmAdjustTaskService.submit(dto));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 当前进行中的推荐任务；没有则 data 为 null。
     */
    @GetMapping("/recommend-task/running")
    public Result<LlmAdjustTaskVo> runningRecommendTask() {
        try {
            return Result.success(llmAdjustTaskService.running());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按任务 ID 查询推荐任务状态。
     */
    @GetMapping("/recommend-task/{taskId}")
    public Result<LlmAdjustTaskVo> getRecommendTask(@PathVariable String taskId) {
        try {
            LlmAdjustTaskVo vo = llmAdjustTaskService.get(taskId);
            if (vo == null) {
                return Result.error("任务不存在");
            }
            return Result.success(vo);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 预测/特征结果文件历史列表（按创建时间倒序）。
     *
     * @param type 类型：RECOMMEND=号码推荐，ANALYSIS=特征预测；为空查全部
     * @param limit 最多返回条数（默认 20，上限 100）
     */
    @GetMapping("/file-history")
    public Result<List<PredictFileRecordVo>> listFileHistory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "4") int limit) {
        try {
            return Result.success(lotteryFeatureAnalysisService.listFileHistory(type, limit));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按文件名读取特征预测结果详情。
     */
    @GetMapping("/analyze/history/{fileName:.+}")
    public Result<LotteryAnalysisRespBo> loadAnalysisHistory(@PathVariable String fileName) {
        try {
            return Result.success(lotteryFeatureAnalysisService.loadAnalysisHistory(fileName));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
