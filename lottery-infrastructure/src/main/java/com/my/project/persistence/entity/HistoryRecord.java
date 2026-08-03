package com.my.project.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 历史开奖记录
 *
 * @author liuqiang
 * @since 2025-07-17
 */
@TableName("t_history_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String type;

    private String period;

    private LocalDate openDate;

    private Integer num1;

    private Integer num2;

    private Integer num3;

    private Integer num4;

    private Integer num5;

    private Integer num6;

    private Integer special;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;


}
