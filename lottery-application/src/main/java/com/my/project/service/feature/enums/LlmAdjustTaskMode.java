package com.my.project.service.feature.enums;

/**
 * LLM 推荐异步任务模式。
 **/
public enum LlmAdjustTaskMode {

    /** 特征推荐：不传预选号码，按特征报告生成 */
    FEATURE,
    /** 缓存调优：从预测缓存取号后再调优 */
    CACHE;

    public static LlmAdjustTaskMode of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (LlmAdjustTaskMode mode : values()) {
            if (mode.name().equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        return null;
    }
}
