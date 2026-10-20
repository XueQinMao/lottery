-- Upgrade existing forecast rows in place. Fresh installations already have the id column.
SET @feature_forecast_id_migration = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_feature_trend_forecast' AND COLUMN_NAME = 'id'
    ),
    'SELECT 1',
    'ALTER TABLE t_feature_trend_forecast
        DROP PRIMARY KEY,
        ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST,
        ADD PRIMARY KEY (id),
        ADD UNIQUE KEY uk_feature_forecast_source (source_period, feature_code)'
);
PREPARE feature_forecast_migration FROM @feature_forecast_id_migration;
EXECUTE feature_forecast_migration;
DEALLOCATE PREPARE feature_forecast_migration;
