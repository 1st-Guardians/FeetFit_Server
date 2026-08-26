-- Recommendations are immutable in scope: a recomputation updates the row for
-- the same measurement session and shoe, while a new session creates history.
ALTER TABLE shoe_recommendation
    MODIFY COLUMN measurement_session_id BIGINT NOT NULL,
    ADD CONSTRAINT uq_shoe_recommendation_session_shoe
        UNIQUE (measurement_session_id, shoe_id);

ALTER TABLE shoe_recommendation_reason
    ADD CONSTRAINT uq_shoe_recommendation_reason_type
        UNIQUE (shoe_recommendation_id, reason_type);

CREATE TABLE shoe_recommendation_run (
    shoe_recommendation_run_id BIGINT NOT NULL AUTO_INCREMENT,
    measurement_session_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    expected_count INT NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    failure_detail TEXT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (shoe_recommendation_run_id),
    CONSTRAINT uq_shoe_recommendation_run_session UNIQUE (measurement_session_id),
    INDEX idx_shoe_recommendation_run_current (status, completed_at, measurement_session_id),
    CONSTRAINT fk_shoe_recommendation_run_session
        FOREIGN KEY (measurement_session_id) REFERENCES measurement_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
