package com.my.project.service.record.pojo.enums;

/**
 * 预测文件记录类型
 *
 * @author 刘强
 * @version 2026/09/11
 **/
public enum PredictFileRecordType {

    /** 号码推荐 */
    RECOMMEND("号码推荐"),
    /** 特征预测 */
    ANALYSIS("特征预测");

    private final String label;

    PredictFileRecordType(String label) {
        this.label = label;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按 code 解析，非法值返回 null。
     */
    public static PredictFileRecordType of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (PredictFileRecordType t : values()) {
            if (t.name().equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
