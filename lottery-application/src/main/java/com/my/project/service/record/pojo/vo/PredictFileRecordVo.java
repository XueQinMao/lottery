package com.my.project.service.record.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PredictFileRecordVo
 *
 * <p>预测/特征结果文件记录列表项，供前端历史列表展示与回看。
 *
 * @author 刘强
 * @version 2026/09/11
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictFileRecordVo {

    /** 主键ID */
    private Long id;

    /** 类型 code：RECOMMEND / ANALYSIS */
    private String type;

    /** 类型展示名：号码推荐 / 特征预测 */
    private String typeName;

    /** 文件名（含后缀） */
    private String fileName;

    /** 文件绝对路径 */
    private String filePath;

    /** 创建时间（epoch 毫秒，便于前端复用时间格式化） */
    private Long createTime;
}
