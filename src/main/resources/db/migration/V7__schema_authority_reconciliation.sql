-- Reconcile the legacy Hibernate-managed schema with canonical Flyway names.
-- Guarded schema DDL only; protected shoe/review/lab rows are never rewritten.

-- Adopt and normalize the Hibernate-created V5 table. The NOT NULL conversion
-- intentionally fails if legacy timestamp data contains NULL; no value is invented.
SET @feetfit_v7_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'measurement_analysis_status'
          AND column_name IN (
              'photo_capture_completed', 'photo_analysis_completed',
              'pressure_capture_completed', 'pressure_analysis_completed',
              'environment_analysis_completed', 'metric_report_completed'
          )
          AND (
              LOWER(column_type) <> 'bit(1)'
              OR is_nullable <> 'NO'
              OR column_default IS NULL
              OR REPLACE(LOWER(column_default), ' ', '') NOT IN ('0', 'b''0''')
          )
    )
    OR (
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'measurement_analysis_status'
          AND column_name IN (
              'photo_capture_completed', 'photo_analysis_completed',
              'pressure_capture_completed', 'pressure_analysis_completed',
              'environment_analysis_completed', 'metric_report_completed'
          )
    ) <> 6,
    'ALTER TABLE measurement_analysis_status MODIFY COLUMN photo_capture_completed BIT(1) NOT NULL DEFAULT b''0'', MODIFY COLUMN photo_analysis_completed BIT(1) NOT NULL DEFAULT b''0'', MODIFY COLUMN pressure_capture_completed BIT(1) NOT NULL DEFAULT b''0'', MODIFY COLUMN pressure_analysis_completed BIT(1) NOT NULL DEFAULT b''0'', MODIFY COLUMN environment_analysis_completed BIT(1) NOT NULL DEFAULT b''0'', MODIFY COLUMN metric_report_completed BIT(1) NOT NULL DEFAULT b''0''',
    'SELECT 1'
);
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql;
EXECUTE feetfit_v7_stmt;
DEALLOCATE PREPARE feetfit_v7_stmt;

SET @feetfit_v7_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'measurement_analysis_status'
          AND column_name IN ('created_at', 'updated_at')
          AND (LOWER(column_type) <> 'datetime(6)' OR is_nullable <> 'NO')
    )
    OR (
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'measurement_analysis_status'
          AND column_name IN ('created_at', 'updated_at')
    ) <> 2,
    'ALTER TABLE measurement_analysis_status MODIFY COLUMN created_at DATETIME(6) NOT NULL, MODIFY COLUMN updated_at DATETIME(6) NOT NULL',
    'SELECT 1'
);
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql;
EXECUTE feetfit_v7_stmt;
DEALLOCATE PREPARE feetfit_v7_stmt;

-- Canonical indexes and unique constraints must exist before canonical FKs are
-- added and Hibernate-generated equivalents are removed.
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_review' AND index_name = 'idx_shoe_review_shoe'), 'SELECT 1', 'ALTER TABLE shoe_review ADD INDEX idx_shoe_review_shoe (shoe_id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_lab_metric' AND index_name = 'idx_shoe_lab_metric_measurement'), 'SELECT 1', 'ALTER TABLE shoe_lab_metric ADD INDEX idx_shoe_lab_metric_measurement (shoe_lab_measurement_id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_import_audit' AND index_name = 'idx_shoe_import_audit_matched_shoe'), 'SELECT 1', 'ALTER TABLE shoe_import_audit ADD INDEX idx_shoe_import_audit_matched_shoe (matched_shoe_id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND index_name = 'idx_reason_review_review'), 'SELECT 1', 'ALTER TABLE shoe_recommendation_reason_review ADD INDEX idx_reason_review_review (review_id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND constraint_name = 'uq_reason_review' AND constraint_type = 'UNIQUE'), 'SELECT 1', 'ALTER TABLE shoe_recommendation_reason_review ADD CONSTRAINT uq_reason_review UNIQUE (reason_id, review_id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'measurement_analysis_status' AND constraint_name = 'uk_measurement_analysis_status_session' AND constraint_type = 'UNIQUE'), 'SELECT 1', 'ALTER TABLE measurement_analysis_status ADD CONSTRAINT uk_measurement_analysis_status_session UNIQUE (measurement_session_id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;

-- Add deterministic Flyway-owned FKs before removing generated equivalents.
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_review' AND constraint_name = 'fk_shoe_review_shoe' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_review ADD CONSTRAINT fk_shoe_review_shoe FOREIGN KEY (shoe_id) REFERENCES shoe (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_lab_measurement' AND constraint_name = 'fk_shoe_lab_measurement_shoe' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_lab_measurement ADD CONSTRAINT fk_shoe_lab_measurement_shoe FOREIGN KEY (shoe_id) REFERENCES shoe (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_lab_metric' AND constraint_name = 'fk_shoe_lab_metric_measurement' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_lab_metric ADD CONSTRAINT fk_shoe_lab_metric_measurement FOREIGN KEY (shoe_lab_measurement_id) REFERENCES shoe_lab_measurement (shoe_lab_measurement_id) ON DELETE CASCADE');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_import_audit' AND constraint_name = 'fk_shoe_import_audit_shoe' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_import_audit ADD CONSTRAINT fk_shoe_import_audit_shoe FOREIGN KEY (matched_shoe_id) REFERENCES shoe (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation' AND constraint_name = 'fk_shoe_recommendation_shoe' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_recommendation ADD CONSTRAINT fk_shoe_recommendation_shoe FOREIGN KEY (shoe_id) REFERENCES shoe (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation' AND constraint_name = 'fk_shoe_recommendation_measurement' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_recommendation ADD CONSTRAINT fk_shoe_recommendation_measurement FOREIGN KEY (measurement_session_id) REFERENCES measurement_session (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'users') AND NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation' AND constraint_name = 'fk_shoe_recommendation_user' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_recommendation ADD CONSTRAINT fk_shoe_recommendation_user FOREIGN KEY (user_id) REFERENCES users (id)', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation_reason' AND constraint_name = 'fk_shoe_recommendation_reason_rec' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_recommendation_reason ADD CONSTRAINT fk_shoe_recommendation_reason_rec FOREIGN KEY (shoe_recommendation_id) REFERENCES shoe_recommendation (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND constraint_name = 'fk_reason_review_reason' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_recommendation_reason_review ADD CONSTRAINT fk_reason_review_reason FOREIGN KEY (reason_id) REFERENCES shoe_recommendation_reason (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND constraint_name = 'fk_reason_review_review' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE shoe_recommendation_reason_review ADD CONSTRAINT fk_reason_review_review FOREIGN KEY (review_id) REFERENCES shoe_review (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'measurement_analysis_status' AND constraint_name = 'fk_measurement_analysis_status_session' AND constraint_type = 'FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE measurement_analysis_status ADD CONSTRAINT fk_measurement_analysis_status_session FOREIGN KEY (measurement_session_id) REFERENCES measurement_session (id)');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;

-- Remove only exact Hibernate-generated duplicates after canonical guards exist.
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_review' AND constraint_name = 'FK61rg2odvj9t5m8t8xcxppivg3' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_review DROP FOREIGN KEY FK61rg2odvj9t5m8t8xcxppivg3', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_lab_measurement' AND constraint_name = 'FKgfrj7ex89h8ntceywvyagainr' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_lab_measurement DROP FOREIGN KEY FKgfrj7ex89h8ntceywvyagainr', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_lab_metric' AND constraint_name = 'FKevp703q9p3i7255vt8c653jll' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_lab_metric DROP FOREIGN KEY FKevp703q9p3i7255vt8c653jll', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_import_audit' AND constraint_name = 'FKqa0t8cv9r3eri6dp3t3l7l71t' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_import_audit DROP FOREIGN KEY FKqa0t8cv9r3eri6dp3t3l7l71t', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation' AND constraint_name = 'FK6bk3mjrnbd7ot8eop4r2nvb4c' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_recommendation DROP FOREIGN KEY FK6bk3mjrnbd7ot8eop4r2nvb4c', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation' AND constraint_name = 'FKtguf7hhn2ir7x2acfy2kopj0i' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_recommendation DROP FOREIGN KEY FKtguf7hhn2ir7x2acfy2kopj0i', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation' AND constraint_name = 'FKlix3hcdg1jga18tqpyis3emdd' AND constraint_type = 'FOREIGN KEY') AND EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation' AND constraint_name = 'fk_shoe_recommendation_user' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_recommendation DROP FOREIGN KEY FKlix3hcdg1jga18tqpyis3emdd', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation_reason' AND constraint_name = 'FKlp8wkeb3ejvsu3v1g14wcs5f4' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_recommendation_reason DROP FOREIGN KEY FKlp8wkeb3ejvsu3v1g14wcs5f4', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND constraint_name = 'FK6kmylqmcatmsetatlt9f5rx68' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_recommendation_reason_review DROP FOREIGN KEY FK6kmylqmcatmsetatlt9f5rx68', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND constraint_name = 'FK87awerqnej4uk8gtsog11s17n' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE shoe_recommendation_reason_review DROP FOREIGN KEY FK87awerqnej4uk8gtsog11s17n', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'measurement_analysis_status' AND constraint_name = 'FKho5h94apn4v1vfkftdrhac5nn' AND constraint_type = 'FOREIGN KEY'), 'ALTER TABLE measurement_analysis_status DROP FOREIGN KEY FKho5h94apn4v1vfkftdrhac5nn', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;

-- Drop stale supporting indexes and duplicate unique indexes only after the
-- deterministic replacements above are present.
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_lab_metric' AND index_name = 'FKevp703q9p3i7255vt8c653jll'), 'ALTER TABLE shoe_lab_metric DROP INDEX FKevp703q9p3i7255vt8c653jll', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_import_audit' AND index_name = 'FKqa0t8cv9r3eri6dp3t3l7l71t'), 'ALTER TABLE shoe_import_audit DROP INDEX FKqa0t8cv9r3eri6dp3t3l7l71t', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND index_name = 'FK87awerqnej4uk8gtsog11s17n'), 'ALTER TABLE shoe_recommendation_reason_review DROP INDEX FK87awerqnej4uk8gtsog11s17n', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe' AND index_name = 'UKl9bet009cnqsj75iuhnkdurbq'), 'ALTER TABLE shoe DROP INDEX UKl9bet009cnqsj75iuhnkdurbq', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_lab_measurement' AND index_name = 'UKmli3024ly0tw35ours7r9xx1q'), 'ALTER TABLE shoe_lab_measurement DROP INDEX UKmli3024ly0tw35ours7r9xx1q', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;
SET @feetfit_v7_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'shoe_recommendation_reason_review' AND index_name = 'UKj1jwt73x0ay8qm2d8283e91s5'), 'ALTER TABLE shoe_recommendation_reason_review DROP INDEX UKj1jwt73x0ay8qm2d8283e91s5', 'SELECT 1');
PREPARE feetfit_v7_stmt FROM @feetfit_v7_sql; EXECUTE feetfit_v7_stmt; DEALLOCATE PREPARE feetfit_v7_stmt;

SET @feetfit_v7_sql = NULL;
