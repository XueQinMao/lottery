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
@TableName("t_draw_learning_state")
public class DrawLearningState {
    /** 全局策略状态行使用的固定业务键。 */
    public static final int SINGLETON_KEY = 1;

    /** 由 MyBatis-Plus 生成的雪花主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 全局策略状态的唯一业务键，用于查找和锁定状态行。 */
    private Integer stateKey;

    /** 各策略的正式版本、试验版本、累计成绩和最近评估决定，按 JSON 保存。 */
    private String payload;
}
