package com.my.project.infrastructure.persistence.migration;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.my.project.infrastructure.persistence.entity.DrawLearningPrediction;
import com.my.project.infrastructure.persistence.entity.DrawLearningReview;
import com.my.project.infrastructure.persistence.entity.DrawLearningState;
import com.my.project.infrastructure.persistence.mapper.DrawLearningMigrationMapper;
import com.my.project.infrastructure.persistence.mapper.DrawLearningPredictionMapper;
import com.my.project.infrastructure.persistence.mapper.DrawLearningReviewMapper;
import com.my.project.infrastructure.persistence.mapper.DrawLearningStateMapper;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
@DependsOn({"drawLearningMigrationMapper", "drawLearningStateMapper", "drawLearningPredictionMapper", "drawLearningReviewMapper"})
@RequiredArgsConstructor
public class DrawLearningSchemaMigration implements InitializingBean {
    private static final String MIGRATION_LOCK = "lottery_draw_learning_snowflake_ids";

    private final SqlSessionFactory sqlSessionFactory;

    @Override
    public void afterPropertiesSet() {
        // DDL 会隐式提交。所有 Mapper 共用一个会话，命名锁会一直持有到迁移结束。
        try (var session = sqlSessionFactory.openSession()) {
            var migration = session.getMapper(DrawLearningMigrationMapper.class);
            Integer acquired = migration.acquireLock(MIGRATION_LOCK);
            if (!Integer.valueOf(1).equals(acquired)) {
                throw new IllegalStateException("未能获得策略学习表迁移锁");
            }
            try {
                migrateState(migration, session.getMapper(DrawLearningStateMapper.class));
                migratePredictions(migration, session.getMapper(DrawLearningPredictionMapper.class));
                migrateReviews(migration, session.getMapper(DrawLearningReviewMapper.class));
                session.commit();
            } finally {
                migration.releaseLock(MIGRATION_LOCK);
            }
        }
    }

    private static void migrateState(DrawLearningMigrationMapper migration, DrawLearningStateMapper states) {
        if ("tinyint".equals(migration.findStateIdType())) migration.migrateStateKey();

        var state = states.selectOne(Wrappers.lambdaQuery(DrawLearningState.class)
            .select(DrawLearningState::getId)
            .eq(DrawLearningState::getStateKey, DrawLearningState.SINGLETON_KEY));
        if (state == null) {
            states.insert(DrawLearningState.builder().stateKey(DrawLearningState.SINGLETON_KEY).payload("{}").build());
        } else if (Long.valueOf(1).equals(state.getId())) {
            states.update(Wrappers.lambdaUpdate(DrawLearningState.class)
                .eq(DrawLearningState::getId, state.getId()).set(DrawLearningState::getId, IdWorker.getId()));
        }
    }

    private static void migratePredictions(DrawLearningMigrationMapper migration, DrawLearningPredictionMapper predictions) {
        var nullability = migration.findIdNullability("t_draw_learning_prediction");
        if ("NO".equals(nullability)) return;
        if (nullability == null) migration.addPredictionId();

        var rows = predictions.selectList(Wrappers.lambdaQuery(DrawLearningPrediction.class)
            .select(DrawLearningPrediction::getPeriod, DrawLearningPrediction::getScope)
            .isNull(DrawLearningPrediction::getId));
        for (var row : rows) {
            predictions.update(Wrappers.lambdaUpdate(DrawLearningPrediction.class)
                .eq(DrawLearningPrediction::getPeriod, row.getPeriod())
                .eq(DrawLearningPrediction::getScope, row.getScope())
                .isNull(DrawLearningPrediction::getId)
                .set(DrawLearningPrediction::getId, IdWorker.getId()));
        }
        migration.setPredictionPrimaryKey();
    }

    private static void migrateReviews(DrawLearningMigrationMapper migration, DrawLearningReviewMapper reviews) {
        var nullability = migration.findIdNullability("t_draw_learning_review");
        if ("NO".equals(nullability)) return;
        if (nullability == null) migration.addReviewId();

        // 只回填空 ID，重启可继续上次迁移。回填完成后再一次性切换主键和业务唯一键。
        var rows = reviews.selectList(Wrappers.lambdaQuery(DrawLearningReview.class)
            .select(DrawLearningReview::getPeriod).isNull(DrawLearningReview::getId));
        for (var row : rows) {
            reviews.update(Wrappers.lambdaUpdate(DrawLearningReview.class)
                .eq(DrawLearningReview::getPeriod, row.getPeriod())
                .isNull(DrawLearningReview::getId)
                .set(DrawLearningReview::getId, IdWorker.getId()));
        }
        migration.setReviewPrimaryKey();
    }
}
