package com.my.project.api.llm;

import com.my.project.application.report.LotteryReportService;

import lombok.RequiredArgsConstructor;

import com.my.project.api.request.LlmAdjustmentRequest;
import com.my.project.api.request.LlmRecommendationTaskRequest;
import com.my.project.api.response.ApiResponse;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.application.feature.LlmAdjustTaskService;
import com.my.project.application.feature.LotteryFeatureAnalysisService;
import com.my.project.application.report.AdjustHistoryFileVo;
import com.my.project.application.feature.vo.LlmAdjustTaskVo;
import com.my.project.application.report.PredictFileRecordVo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LlmController
 *
 * <p>大模型号码特征分析接口。
 * <ul>
 *     <li>{@code GET /api/llm/analyze/latest?sampleSize=30}：自动拉取最近30期红蓝球形态分析</li>
 *     <li>{@code POST /api/llm/adjust}：统一推荐、传入号码调优与缓存号码调优</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/07/21 20:40
 **/
@RequiredArgsConstructor
@RestController
@RequestMapping("api/llm")
public class LlmController {

    private final LotteryFeatureAnalysisService lotteryFeatureAnalysisService;

    private final LlmAdjustTaskService llmAdjustTaskService;

    private final LotteryReportService reportService;

    /**
     * 自动分析历史开奖；大模型分支固定最近30期，sampleSize兼容旧调用方。
     */
    @GetMapping("/analyze/latest")
    public ApiResponse<LotteryAnalysisRespBo> analyzeLatest(
            @RequestParam(required = false, defaultValue = "30") int sampleSize) {
        return ApiResponse.capture(() -> lotteryFeatureAnalysisService.analyzeLatest(sampleSize));
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
    public ApiResponse<LotteryAdjustViewBo> recommend(@RequestBody(required = false) LlmAdjustmentRequest request) {
        return ApiResponse.capture(() -> lotteryFeatureAnalysisService.recommend(
            LlmRequestMapper.recommendation(request)));
    }

    /**
     * 最近推荐文件名列表（按修改时间倒序）。
     */
    @GetMapping("/adjust/history")
    public ApiResponse<List<AdjustHistoryFileVo>> listAdjustHistory(
            @RequestParam(required = false, defaultValue = "4") int limit) {
        return ApiResponse.capture(() -> reportService.listAdjustHistory(limit));
    }

    /**
     * 按文件名读取推荐详情。
     */
    @GetMapping("/adjust/history/{fileName:.+}")
    public ApiResponse<LotteryAdjustViewBo> loadAdjustHistory(@PathVariable String fileName) {
        return ApiResponse.capture(() -> reportService.loadAdjustHistory(fileName));
    }

    @GetMapping("/adjust/{count}/{isTopN}")
    public ApiResponse<LotteryAdjustViewBo> adjustFromCacheTop(
            @PathVariable Integer count,
            @PathVariable boolean isTopN,
            @RequestParam(required = false) String userRequirement) {
        return ApiResponse.capture(() -> lotteryFeatureAnalysisService.adjust(count, isTopN, userRequirement));
    }

    /**
     * 提交 LLM 推荐异步任务，立即返回 taskId。
     */
    @PostMapping("/recommend-task")
    public ApiResponse<LlmAdjustTaskVo> submitRecommendTask(@RequestBody(required = false) LlmRecommendationTaskRequest request) {
        return ApiResponse.capture(() -> llmAdjustTaskService.submit(LlmRequestMapper.task(request)));
    }

    /**
     * 当前进行中的推荐任务；没有则 data 为 null。
     */
    @GetMapping("/recommend-task/running")
    public ApiResponse<LlmAdjustTaskVo> runningRecommendTask() {
        return ApiResponse.capture(() -> llmAdjustTaskService.running());
    }

    /**
     * 按任务 ID 查询推荐任务状态。
     */
    @GetMapping("/recommend-task/{taskId}")
    public ApiResponse<LlmAdjustTaskVo> getRecommendTask(@PathVariable String taskId) {
        try {
            LlmAdjustTaskVo vo = llmAdjustTaskService.get(taskId);
            if (vo == null) {
                return ApiResponse.error("任务不存在");
            }
            return ApiResponse.success(vo);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 预测/特征结果文件历史列表（按创建时间倒序）。
     *
     * @param type 类型：RECOMMEND=号码推荐，ANALYSIS=特征预测；为空查全部
     * @param limit 最多返回条数（默认 4，上限 100）
     */
    @GetMapping("/file-history")
    public ApiResponse<List<PredictFileRecordVo>> listFileHistory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "4") int limit) {
        return ApiResponse.capture(() -> reportService.listFileHistory(type, limit));
    }

    /**
     * 按文件名读取特征预测结果详情。
     */
    @GetMapping("/analyze/history/{fileName:.+}")
    public ApiResponse<LotteryAnalysisRespBo> loadAnalysisHistory(@PathVariable String fileName) {
        return ApiResponse.capture(() -> reportService.loadAnalysisHistory(fileName));
    }
}
