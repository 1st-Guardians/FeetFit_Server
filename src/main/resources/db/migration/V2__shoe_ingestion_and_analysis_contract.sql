-- Stable MUSINSA identity and review idempotency.
ALTER TABLE shoe
    ADD COLUMN musinsa_goods_no VARCHAR(255) NULL AFTER model_code,
    ADD UNIQUE KEY uq_shoe_musinsa_goods_no (musinsa_goods_no);

ALTER TABLE shoe_review
    ADD COLUMN source_review_id VARCHAR(255) NULL AFTER review_text,
    ADD COLUMN content_hash VARCHAR(64) NULL AFTER source_review_id,
    ADD UNIQUE KEY uq_shoe_review_source_review_id (shoe_id, source, source_review_id),
    ADD UNIQUE KEY uq_shoe_review_source_content_hash (shoe_id, source, content_hash);

-- A capture is a source snapshot. Legacy rows remain nullable because neither
-- captured_at nor parser_version can be reconstructed without inventing data.
ALTER TABLE shoe_lab_measurement
    ADD COLUMN source_brand_name VARCHAR(255) NULL AFTER tested_size,
    ADD COLUMN source_shoe_name VARCHAR(255) NULL AFTER source_brand_name,
    ADD COLUMN source_model_code VARCHAR(255) NULL AFTER source_shoe_name,
    ADD COLUMN captured_at DATETIME(6) NULL AFTER source_model_code,
    ADD COLUMN parser_version VARCHAR(255) NULL AFTER captured_at,
    ADD COLUMN snapshot_key VARCHAR(64) NULL AFTER parser_version,
    MODIFY COLUMN source_url VARCHAR(2048) NOT NULL,
    ADD INDEX idx_shoe_lab_capture (shoe_id, source, captured_at),
    ADD UNIQUE KEY uq_shoe_lab_snapshot_key (snapshot_key);

CREATE TABLE shoe_lab_metric (
    shoe_lab_metric_id BIGINT NOT NULL AUTO_INCREMENT,
    shoe_lab_measurement_id BIGINT NOT NULL,
    canonical_characteristic VARCHAR(40) NOT NULL,
    source_metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(19,6) NULL,
    average_value DECIMAL(19,6) NULL,
    source_min_value DECIMAL(19,6) NULL,
    source_max_value DECIMAL(19,6) NULL,
    unit VARCHAR(50) NULL,
    tested_size VARCHAR(255) NULL,
    method_name VARCHAR(255) NULL,
    method_version VARCHAR(100) NULL,
    location VARCHAR(100) NULL,
    variant VARCHAR(100) NULL,
    comparison_sample_count INT NULL,
    comparison_cohort VARCHAR(255) NULL,
    raw_value_text TEXT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (shoe_lab_metric_id),
    INDEX idx_shoe_lab_metric_measurement (shoe_lab_measurement_id),
    INDEX idx_shoe_lab_metric_comparison
        (canonical_characteristic, unit, method_version, location, comparison_cohort),
    CONSTRAINT fk_shoe_lab_metric_measurement FOREIGN KEY (shoe_lab_measurement_id)
        REFERENCES shoe_lab_measurement (shoe_lab_measurement_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shoe_import_audit (
    shoe_import_audit_id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(20) NOT NULL,
    external_key VARCHAR(255) NULL,
    source_url VARCHAR(2048) NULL,
    source_brand_name VARCHAR(255) NULL,
    source_shoe_name VARCHAR(255) NULL,
    source_model_code VARCHAR(255) NULL,
    match_status VARCHAR(20) NOT NULL,
    matched_shoe_id BIGINT NULL,
    candidate_shoe_ids JSON NOT NULL,
    detail TEXT NULL,
    payload_hash VARCHAR(64) NULL,
    raw_payload LONGTEXT NULL,
    captured_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (shoe_import_audit_id),
    INDEX idx_shoe_import_audit_source_status (source, match_status),
    INDEX idx_shoe_import_audit_external_key (external_key),
    INDEX idx_shoe_import_audit_matched_shoe (matched_shoe_id),
    CONSTRAINT fk_shoe_import_audit_shoe FOREIGN KEY (matched_shoe_id) REFERENCES shoe (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Recommendation judgments may be stored before generated prose is ready.
ALTER TABLE shoe_recommendation
    ADD COLUMN measurement_session_id BIGINT NULL AFTER shoe_id,
    MODIFY COLUMN point_summary TEXT NULL,
    ADD INDEX idx_shoe_recommendation_measurement (measurement_session_id),
    ADD CONSTRAINT fk_shoe_recommendation_measurement
        FOREIGN KEY (measurement_session_id) REFERENCES measurement_session (id);

ALTER TABLE shoe_recommendation_reason
    MODIFY COLUMN review_summary TEXT NULL;
