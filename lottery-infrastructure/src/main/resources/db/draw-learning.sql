CREATE TABLE IF NOT EXISTS t_draw_learning_state (
    id BIGINT NOT NULL PRIMARY KEY,
    state_key TINYINT NOT NULL DEFAULT 1,
    payload JSON NOT NULL,
    UNIQUE KEY uk_draw_learning_state_key (state_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_draw_learning_prediction (
    id BIGINT NOT NULL PRIMARY KEY,
    period CHAR(7) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    UNIQUE KEY uk_draw_learning_prediction_period_scope (period, scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_draw_learning_review (
    id BIGINT NOT NULL PRIMARY KEY,
    period CHAR(7) NOT NULL,
    payload JSON NOT NULL,
    UNIQUE KEY uk_draw_learning_review_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
