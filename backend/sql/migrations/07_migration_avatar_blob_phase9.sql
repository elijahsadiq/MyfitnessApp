-- Fitness API — Phase 9 migration: profile photos stored in MySQL.
--
-- Adds `users.avatar_jpeg`, the re-encoded 512×512 JPEG (~30–80 KB).
-- `avatar_path` keeps its job as the relative path the app loads, now
-- `avatars/<random>.jpg`, which GET /avatars/{file} serves from this
-- column.
--
-- Why: cloud hosts such as Render wipe the server's disk on every
-- restart, so photos saved as files disappeared. A row in MySQL
-- survives restarts and redeploys, and no uploaded file ever sits in
-- the web root.
--
-- Idempotent: `add_col_if_absent` guards the ALTER so re-running on a
-- seeded DB is safe.

USE fitness_app;

-- Shared "add column only if it doesn't exist" helper, redeclared so
-- this file can run stand-alone (same pattern as migrations 03–06).
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

-- Single quotes on purpose: under ANSI_QUOTES (Aiven's default SQL
-- mode) a double-quoted string is read as a column name.
CALL add_col_if_absent('users', 'avatar_jpeg',
    'avatar_jpeg MEDIUMBLOB NULL AFTER avatar_path');

DROP PROCEDURE add_col_if_absent;
