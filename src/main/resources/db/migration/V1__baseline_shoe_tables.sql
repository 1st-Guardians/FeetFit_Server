-- Baseline for installations created before Flyway was introduced.
-- CREATE IF NOT EXISTS makes the migration safe for both the deployed schema
-- and an empty development schema; Phase A changes are applied in V2.

CREATE TABLE IF NOT EXISTS shoe (
    id BIGINT NOT NULL AUTO_INCREMENT,
    brand_name VARCHAR(255) NOT NULL,
    shoe_name VARCHAR(255) NOT NULL,
    model_code VARCHAR(255) NOT NULL,
    musinsa_url VARCHAR(255) NOT NULL,
    price INT NULL,
    image_url VARCHAR(255) NULL,
    overall_rating FLOAT NULL,
    click_count INT NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shoe_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shoe_id BIGINT NOT NULL,
    rating FLOAT NOT NULL,
    review_text TEXT NOT NULL,
    source VARCHAR(32) NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_shoe_review_shoe (shoe_id),
    CONSTRAINT fk_shoe_review_shoe FOREIGN KEY (shoe_id) REFERENCES shoe (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shoe_lab_measurement (
    shoe_lab_measurement_id BIGINT NOT NULL AUTO_INCREMENT,
    shoe_id BIGINT NOT NULL,
    source VARCHAR(255) NOT NULL,
    tested_size VARCHAR(255) NULL,
    internal_length_mm FLOAT NULL,
    width_mm FLOAT NULL,
    toebox_width_mm FLOAT NULL,
    toebox_height_mm FLOAT NULL,
    insole_thickness_mm FLOAT NULL,
    heel_stack_mm FLOAT NULL,
    forefoot_stack_mm FLOAT NULL,
    source_url VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (shoe_lab_measurement_id),
    INDEX idx_shoe_lab_measurement_shoe (shoe_id),
    CONSTRAINT fk_shoe_lab_measurement_shoe FOREIGN KEY (shoe_id) REFERENCES shoe (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shoe_recommendation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    shoe_id BIGINT NOT NULL,
    fit_score FLOAT NOT NULL,
    point_summary TEXT NOT NULL,
    analyzed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_shoe_recommendation_user (user_id),
    INDEX idx_shoe_recommendation_shoe (shoe_id),
    CONSTRAINT fk_shoe_recommendation_shoe FOREIGN KEY (shoe_id) REFERENCES shoe (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Needed by the Phase A recommendation trace FK before Hibernate schema update
-- runs on a new installation. Other measurement tables remain Hibernate-owned.
CREATE TABLE IF NOT EXISTS measurement_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL,
    measurement_duration_sec INT NULL,
    measured_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_measurement_session_user (user_id),
    INDEX idx_measurement_session_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shoe_recommendation_reason (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shoe_recommendation_id BIGINT NOT NULL,
    reason_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    review_summary TEXT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_shoe_recommendation_reason_rec (shoe_recommendation_id),
    CONSTRAINT fk_shoe_recommendation_reason_rec
        FOREIGN KEY (shoe_recommendation_id) REFERENCES shoe_recommendation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shoe_recommendation_reason_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reason_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reason_review (reason_id, review_id),
    CONSTRAINT fk_reason_review_reason FOREIGN KEY (reason_id)
        REFERENCES shoe_recommendation_reason (id),
    CONSTRAINT fk_reason_review_review FOREIGN KEY (review_id) REFERENCES shoe_review (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
