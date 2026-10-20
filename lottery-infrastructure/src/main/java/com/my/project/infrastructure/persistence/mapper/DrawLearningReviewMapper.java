package com.my.project.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.my.project.infrastructure.persistence.entity.DrawLearningReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DrawLearningReviewMapper extends BaseMapper<DrawLearningReview> {
    @Select("""
        SELECT DISTINCT h.period
        FROM t_history_record h
        LEFT JOIN t_draw_learning_review r ON r.period = h.period
        WHERE r.period IS NULL AND (
            h.period >= (SELECT MIN(recent.period) FROM (
                SELECT period FROM t_history_record ORDER BY open_date DESC LIMIT #{initialWindow}
            ) recent)
            OR h.period > (SELECT MAX(period) FROM t_draw_learning_review)
            OR EXISTS (SELECT 1 FROM t_draw_learning_prediction p WHERE p.period = h.period)
        )
        ORDER BY h.period
        """)
    List<String> findPendingPeriods(@Param("initialWindow") int initialWindow);
}
