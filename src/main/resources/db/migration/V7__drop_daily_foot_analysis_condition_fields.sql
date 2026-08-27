SET @drop_condition_level = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE daily_foot_analysis DROP COLUMN condition_level',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'daily_foot_analysis'
      AND COLUMN_NAME = 'condition_level'
);
PREPARE drop_condition_level_stmt FROM @drop_condition_level;
EXECUTE drop_condition_level_stmt;
DEALLOCATE PREPARE drop_condition_level_stmt;

SET @drop_condition_comments = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE daily_foot_analysis DROP COLUMN condition_comments',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'daily_foot_analysis'
      AND COLUMN_NAME = 'condition_comments'
);
PREPARE drop_condition_comments_stmt FROM @drop_condition_comments;
EXECUTE drop_condition_comments_stmt;
DEALLOCATE PREPARE drop_condition_comments_stmt;
