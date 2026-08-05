package com.my.project.service.feature;

import com.my.project.service.feature.pojo.dto.LlmAdjustTaskSubmitDto;
import com.my.project.service.feature.pojo.vo.LlmAdjustTaskVo;

/**
 * LLM 推荐异步任务：提交后立即返回，工作线程调用同步 {@code adjust}。
 **/
public interface ILlmAdjustTaskService {

    /**
     * 投递推荐任务。同时只允许一个进行中任务。
     */
    LlmAdjustTaskVo submit(LlmAdjustTaskSubmitDto dto);

    /**
     * 按任务 ID 查询；不存在返回 null。
     */
    LlmAdjustTaskVo get(String taskId);

    /**
     * 当前进行中任务（PENDING / RUNNING）；没有则 null。
     */
    LlmAdjustTaskVo running();
}
