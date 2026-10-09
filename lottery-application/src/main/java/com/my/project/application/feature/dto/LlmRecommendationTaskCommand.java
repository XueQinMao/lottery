package com.my.project.application.feature.dto;

import com.my.project.application.feature.enums.LlmAdjustTaskMode;
import lombok.Builder;
import lombok.Data;

/**
 * LLM 推荐异步任务提交参数。
 **/
@Data
@Builder
public class LlmRecommendationTaskCommand {

    private LlmAdjustTaskMode mode;

    private Integer count;

    /** 仅 CACHE 模式生效：true=评分最高，false=随机抽取 */
    private Boolean isTopN;

    private String userRequirement;
}
