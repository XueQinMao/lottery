package com.my.project.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RecommendBuy
 *
 * @author 刘强
 * @version 2025/11/10 17:32
 **/
@TableName("t_buy_record")
@Data
@Builder
public class BuyRecord {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * manual 和 auto 两种
     */
    private String type;

    /**
     * 开奖日期
     */
    private LocalDate openDate;

    private String oriRedBalls;

    private String oriBlueBall;

    private String adjustedRedBalls;

    private String adjustedBlueBalls;

    private String redBalls;

    private String blueBalls;

    private String reason;

    private Integer totalBets;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
