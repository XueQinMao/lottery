package com.my.project.api.pojo.req;

import lombok.Data;

/**
 * LLM 推荐异步任务提交请求。
 **/
@Data
public class LlmRecommendTaskReq {

    /** FEATURE=特征推荐，CACHE=缓存调优 */
    private String mode;

    private Integer count;

    /** 仅 CACHE 模式生效：true=评分最高，false=随机抽取 */
    private Boolean isTopN;

    private String userRequirement;
}
