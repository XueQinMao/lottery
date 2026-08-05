package com.my.project.service.feature.pojo.dto;

import com.my.project.service.feature.enums.LlmAdjustTaskMode;
import lombok.Builder;
import lombok.Data;

/**
 * LLM 推荐异步任务提交参数。
 **/
@Data
@Builder
public class LlmAdjustTaskSubmitDto {

    private LlmAdjustTaskMode mode;

    private Integer count;

    /** 仅 CACHE 模式生效：true=评分最高，false=随机抽取 */
    private Boolean isTopN;

    private String userRequirement;
}
