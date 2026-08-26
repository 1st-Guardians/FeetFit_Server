-- Adoption bridge for schemas where Hibernate ddl-auto=update added these
-- columns before Flyway became the schema authority. MySQL 8.4 does not offer
-- ADD COLUMN IF NOT EXISTS, so guard each DDL statement through
-- information_schema. The bridge is safe both for the deployed schema and for
-- a clean Flyway-managed schema.

SET @feetfit_v3_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'tina_pedis_analyses'
          AND column_name = 'sole_suspicious_area_map_image_url'
    ),
    'SELECT 1',
    'ALTER TABLE tina_pedis_analyses ADD COLUMN sole_suspicious_area_map_image_url VARCHAR(255) NULL'
);
PREPARE feetfit_v3_stmt FROM @feetfit_v3_sql;
EXECUTE feetfit_v3_stmt;
DEALLOCATE PREPARE feetfit_v3_stmt;

SET @feetfit_v3_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'tina_pedis_analyses'
          AND column_name = 'sole_original_foot_image_url'
    ),
    'SELECT 1',
    'ALTER TABLE tina_pedis_analyses ADD COLUMN sole_original_foot_image_url VARCHAR(255) NULL'
);
PREPARE feetfit_v3_stmt FROM @feetfit_v3_sql;
EXECUTE feetfit_v3_stmt;
DEALLOCATE PREPARE feetfit_v3_stmt;

SET @feetfit_v3_sql = NULL;
