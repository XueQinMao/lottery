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
@TableName("t_draw_learning_review")
public class DrawLearningReview {
    /** 由 MyBatis-Plus 生成的雪花主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 已结算的开奖期号，每期对应一条归档。 */
    private String period;

    /** 当期复盘归档 JSON，包含实际开奖、预测命中情况和策略试验评估结果。 */
    private String payload;
}
