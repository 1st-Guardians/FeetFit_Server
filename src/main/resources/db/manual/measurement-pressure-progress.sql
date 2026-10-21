-- Apply once before deploying when automatic schema updates are disabled.
ALTER TABLE measurement_session ADD COLUMN pressure_progress_at DATETIME(6) NULL;
