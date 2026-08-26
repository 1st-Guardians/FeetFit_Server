-- Adoption bridge for columns that may already have been created by Hibernate
-- ddl-auto=update before Flyway was enabled.

SET @feetfit_v4_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'daily_foot_analysis'
          AND column_name = 'left_plantar_footprint_image_url'
    ),
    'SELECT 1',
    'ALTER TABLE daily_foot_analysis ADD COLUMN left_plantar_footprint_image_url TEXT NULL'
);
PREPARE feetfit_v4_stmt FROM @feetfit_v4_sql;
EXECUTE feetfit_v4_stmt;
DEALLOCATE PREPARE feetfit_v4_stmt;

SET @feetfit_v4_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'daily_foot_analysis'
          AND column_name = 'right_plantar_footprint_image_url'
    ),
    'SELECT 1',
    'ALTER TABLE daily_foot_analysis ADD COLUMN right_plantar_footprint_image_url TEXT NULL'
);
PREPARE feetfit_v4_stmt FROM @feetfit_v4_sql;
EXECUTE feetfit_v4_stmt;
DEALLOCATE PREPARE feetfit_v4_stmt;

SET @feetfit_v4_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'daily_foot_analysis'
          AND column_name = 'plantar_footprint_analysis_text'
    ),
    'SELECT 1',
    'ALTER TABLE daily_foot_analysis ADD COLUMN plantar_footprint_analysis_text TEXT NULL'
);
PREPARE feetfit_v4_stmt FROM @feetfit_v4_sql;
EXECUTE feetfit_v4_stmt;
DEALLOCATE PREPARE feetfit_v4_stmt;

SET @feetfit_v4_sql = NULL;
