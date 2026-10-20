package com.my.project.application.feature.vo;

import com.my.project.application.feature.enums.LlmAdjustTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * LLM 推荐异步任务快照。
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmAdjustTaskVo {

    private String taskId;

    private LlmAdjustTaskStatus status;
    private boolean useNumberTrend;
    private Phase phase;

    private String message;

    /** 成功后的落盘文件名 */
    private String fileName;

    private Instant createdAt;

    private Instant finishedAt;

    public enum Phase {
        ANALYZING_NUMBER_TREND,
        GENERATING_RECOMMENDATION
    }
}
