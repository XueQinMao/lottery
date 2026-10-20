CREATE TABLE IF NOT EXISTS t_feature_trend_forecast (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_period CHAR(7) NOT NULL,
    target_period CHAR(7) NOT NULL,
    feature_code VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    candidate_values JSON NOT NULL,
    detail_json JSON NOT NULL,
    generated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feature_forecast_source (source_period, feature_code),
    UNIQUE KEY uk_feature_forecast_target (target_period, feature_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
