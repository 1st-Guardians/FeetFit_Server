-- Adopt the Hibernate-created table when present. V7 normalizes the adopted
-- definition (defaults, timestamp nullability, and canonical FK/index names).
CREATE TABLE IF NOT EXISTS measurement_analysis_status (
    id BIGINT NOT NULL AUTO_INCREMENT,
    measurement_session_id BIGINT NOT NULL,
    photo_capture_completed BOOLEAN NOT NULL DEFAULT FALSE,
    photo_analysis_completed BOOLEAN NOT NULL DEFAULT FALSE,
    pressure_capture_completed BOOLEAN NOT NULL DEFAULT FALSE,
    pressure_analysis_completed BOOLEAN NOT NULL DEFAULT FALSE,
    environment_analysis_completed BOOLEAN NOT NULL DEFAULT FALSE,
    metric_report_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_measurement_analysis_status_session UNIQUE (measurement_session_id),
    CONSTRAINT fk_measurement_analysis_status_session
        FOREIGN KEY (measurement_session_id)
        REFERENCES measurement_session (id)
);
