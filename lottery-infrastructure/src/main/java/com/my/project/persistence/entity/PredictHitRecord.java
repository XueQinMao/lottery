package com.my.project.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 彩票推荐结果表
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-29
 */
@TableName("t_predict_hit_record")
@Data
public class PredictHitRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 开奖日期
     */
    private LocalDate openDate;

//    @TableField(typeHandler = SetStringTypeHandler.class, value = "red_balls")
    private String redBalls;

    /**
     * 蓝球号码 - 范围为1-16
     */
    private Integer blueBall;

    /**
     * 总得分 - 综合考虑概率得分、组合得分和规则得分的加权总分
     */
    private BigDecimal totalScore;

    private Integer level;

    /**
     * 推荐解释 - 对该组合推荐理由的文本说明
     */
    private String explanation;

    /**
     * l来源
     */
    private String source;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
