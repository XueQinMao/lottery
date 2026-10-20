package com.my.project.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DrawLearningMigrationMapper {
    @Select("SELECT GET_LOCK(#{name}, 30)")
    Integer acquireLock(@Param("name") String name);

    @Select("SELECT RELEASE_LOCK(#{name})")
    Integer releaseLock(@Param("name") String name);

    @Select("""
        SELECT DATA_TYPE FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_draw_learning_state' AND COLUMN_NAME = 'id'
        """)
    String findStateIdType();

    @Select("""
        SELECT IS_NULLABLE FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{table} AND COLUMN_NAME = 'id'
        """)
    String findIdNullability(@Param("table") String table);

    @Update("""
        ALTER TABLE t_draw_learning_state
          MODIFY COLUMN id BIGINT NOT NULL,
          ADD COLUMN state_key TINYINT NOT NULL DEFAULT 1,
          ADD UNIQUE KEY uk_draw_learning_state_key (state_key)
        """)
    void migrateStateKey();

    @Update("ALTER TABLE t_draw_learning_prediction ADD COLUMN id BIGINT NULL FIRST")
    void addPredictionId();

    @Update("""
        ALTER TABLE t_draw_learning_prediction
          DROP PRIMARY KEY, MODIFY COLUMN id BIGINT NOT NULL, ADD PRIMARY KEY (id),
          ADD UNIQUE KEY uk_draw_learning_prediction_period_scope (period, scope)
        """)
    void setPredictionPrimaryKey();

    @Update("ALTER TABLE t_draw_learning_review ADD COLUMN id BIGINT NULL FIRST")
    void addReviewId();

    @Update("""
        ALTER TABLE t_draw_learning_review
          DROP PRIMARY KEY, MODIFY COLUMN id BIGINT NOT NULL, ADD PRIMARY KEY (id),
          ADD UNIQUE KEY uk_draw_learning_review_period (period)
        """)
    void setReviewPrimaryKey();
}
