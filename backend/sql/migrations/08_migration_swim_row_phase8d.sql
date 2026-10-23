-- Fitness API — Phase 8d (Swimming, Rowing, stored calories) migration.
--
-- Adds two activities and three nullable workout columns:
--
--   workouts.activity_type  ENUM gains SWIMMING and ROWING (appended at
--                           the end so existing rows keep their values;
--                           still NOT NULL).
--   goals.activity_type     Same two values (still NULL-able, where
--                           NULL means "any activity").
--   workouts.pool_length_m  TINYINT UNSIGNED, swims only (25 or 50 m in
--                           the app; the API accepts 10 to 100).
--   workouts.laps           SMALLINT UNSIGNED, swims only (1 to 400).
--                           The app sends distance_meters = laps × pool
--                           length, so every distance query still works.
--   workouts.calories_kcal  SMALLINT UNSIGNED, any activity. The app
--                           works the value out from the profile weight
--                           when the workout is saved, so history does not
--                           change if the user later edits their weight.
--                           NULL on rows saved before this phase; the app
--                           falls back to computing those on the fly.
--
-- Idempotent: MODIFY of an ENUM is naturally safe to re-run, and each
-- ADD COLUMN goes through `add_col_if_absent` (INFORMATION_SCHEMA check
-- + PREPARE), the same pattern as migrations 03, 05 and 07. MySQL 8.x
-- has no `ADD COLUMN IF NOT EXISTS` (that is MariaDB only).
--
-- Cross-engine notes:
--   - Runs on MySQL 8.4 (Docker, Aiven) and MariaDB 10.x (XAMPP).
--   - utf8mb4_unicode_ci is restated on both ENUM columns so MODIFY never
--     falls back to MySQL 8's utf8mb4_0900_ai_ci default (which MariaDB
--     rejects).
--   - Single-quoted strings only: under ANSI_QUOTES (Aiven's default SQL
--     mode) a double-quoted string is read as a column name.
--
-- Fresh installs do not need this file: backend/sql/01_schema.sql
-- already has the same final shape.

USE fitness_app;

-- ---------------------------------------------------------------------
-- 1. New activity values. Appending to the end of an ENUM keeps the
--    stored index of every existing value, so no row is rewritten.
-- ---------------------------------------------------------------------
ALTER TABLE workouts
    MODIFY activity_type
        ENUM('RUNNING', 'CYCLING', 'WEIGHTLIFTING', 'SWIMMING', 'ROWING')
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

ALTER TABLE goals
    MODIFY activity_type
        ENUM('RUNNING', 'CYCLING', 'WEIGHTLIFTING', 'SWIMMING', 'ROWING')
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

-- ---------------------------------------------------------------------
-- 2. New workout columns, placed after distance_meters.
-- ---------------------------------------------------------------------

-- Shared "add column only if it doesn't exist" helper, redeclared so
-- this file can run stand-alone (same pattern as migrations 03–07).
DROP PROCEDURE IF EXISTS add_col_if_absent;
DELIMITER //
CREATE PROCEDURE add_col_if_absent(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    DECLARE existing INT;
    SELECT COUNT(*) INTO existing
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = p_table
       AND COLUMN_NAME  = p_column;
    IF existing = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_col_if_absent('workouts', 'pool_length_m',
    'pool_length_m TINYINT UNSIGNED NULL AFTER distance_meters');

CALL add_col_if_absent('workouts', 'laps',
    'laps SMALLINT UNSIGNED NULL AFTER pool_length_m');

CALL add_col_if_absent('workouts', 'calories_kcal',
    'calories_kcal SMALLINT UNSIGNED NULL AFTER laps');

DROP PROCEDURE add_col_if_absent;
