package com.my.project.service.feature.enums;

/**
 * LLM 推荐异步任务状态。
 **/
public enum LlmAdjustTaskStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
