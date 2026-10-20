package com.my.project.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_draw_learning_prediction")
public class DrawLearningPrediction {
    /** 由 MyBatis-Plus 生成的雪花主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预测对应的目标开奖期号。 */
    private String period;

    /** 策略范围，取特征编码或号码推荐标识 {@code numbers}，与期号组成业务唯一键。 */
    private String scope;

    /** 开奖前保存的预测快照 JSON，包含发布时间、策略版本和正式版及试验版的预测结果。 */
    private String payload;
}
