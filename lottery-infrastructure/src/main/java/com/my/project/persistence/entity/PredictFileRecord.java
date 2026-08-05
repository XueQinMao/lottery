package com.my.project.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 预测/特征结果文件记录表
 * </p>
 *
 * <p>统一记录「号码推荐」与「特征预测」两类落盘文件的元数据，
 * 供前端历史列表查询与回看使用。
 *
 * @author 刘强
 * @since 2026-09-11
 */
@TableName("t_predict_file_record")
@Data
public class PredictFileRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（雪花算法）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 类型：RECOMMEND=号码推荐，ANALYSIS=特征预测
     */
    private String type;

    /**
     * 文件名（含后缀）
     */
    private String fileName;

    /**
     * 文件绝对路径
     */
    private String filePath;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
