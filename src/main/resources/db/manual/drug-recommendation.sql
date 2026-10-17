CREATE TABLE IF NOT EXISTS drug_recommendation (
    report_id BIGINT NOT NULL,
    priorities_payload LONGTEXT NOT NULL,
    summary TEXT NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    model VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary_source VARCHAR(20) NOT NULL,
    generation_started_at DATETIME(6),
    generated_at DATETIME(6),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (report_id),
    CONSTRAINT fk_drug_recommendation_report FOREIGN KEY (report_id) REFERENCES report (id)
);
