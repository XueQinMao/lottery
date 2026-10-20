package com.my.project.api.llm;

import com.my.project.api.response.ApiResponse;
import com.my.project.api.request.LlmRecommendationTaskRequest;
import com.my.project.application.feature.LlmAdjustTaskService;
import com.my.project.application.feature.vo.LlmAdjustTaskVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/llm")
public class LlmTaskController {
    private final LlmAdjustTaskService llmAdjustTaskService;

    /**
     * 提交 LLM 推荐异步任务，立即返回 taskId。
     */
    @PostMapping("/recommend-task")
    public ApiResponse<LlmAdjustTaskVo> submitRecommendTask(@RequestBody(required = false) LlmRecommendationTaskRequest request) {
        return ApiResponse.capture(() -> {
            if (request != null && request.mode() != null && !request.mode().isBlank()
                && !"HYBRID".equalsIgnoreCase(request.mode().strip())) {
                throw new IllegalArgumentException("号码推荐仅支持 HYBRID 模式");
            }
            return llmAdjustTaskService.submit(request != null && request.useNumberTrend());
        });
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
}
