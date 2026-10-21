-- Apply before deploying if automatic schema updates are disabled.
-- Keep account.cleanup.encryption-key stable until all pending tasks have completed.
CREATE TABLE IF NOT EXISTS account_cleanup_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(30) NOT NULL,
    encrypted_payload TEXT NOT NULL,
    identity_hash VARCHAR(64),
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6),
    claim_token VARCHAR(36),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_account_cleanup_due (next_attempt_at),
    INDEX idx_account_cleanup_identity (identity_hash)
);
