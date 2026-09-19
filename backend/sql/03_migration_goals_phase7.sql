-- Fitness API — Phase 7 (Goals) migration.
--
-- The phase-1 `goals` table left several columns to be filled in later.
-- Phase 7 needs these four:
--
--   title              — user-facing headline (Kotlin `Goal.title`).
--   period             — enum WEEK | MONTH so the API never has to infer
--                        the window kind from `(period_start, period_end)`.
--   completed_notified — flipped to 1 the first time the client fires a
--                        completion event, so re-opening the app doesn't
--                        celebrate the same goal again.
--   updated_at         — mirrors the `workouts` table so
--                        PUT /goals/{id} leverages ON UPDATE CURRENT_TIMESTAMP.
--
-- Idempotency: MySQL 8.x does NOT support `ADD COLUMN IF NOT EXISTS`
-- (that's a MariaDB extension). Instead, each column is added by a
-- prepared statement that only runs when INFORMATION_SCHEMA reports the
-- column is absent, so this file is safe to re-run on either engine.

USE fitness_app;

-- ---- helper: add a column only when it doesn't already exist ----------
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

CALL add_col_if_absent('goals', 'title',
    "title VARCHAR(120) NOT NULL DEFAULT '' AFTER user_id");

CALL add_col_if_absent('goals', 'period',
    "period ENUM('WEEK','MONTH') NOT NULL DEFAULT 'WEEK' AFTER goal_type");

CALL add_col_if_absent('goals', 'completed_notified',
    "completed_notified TINYINT(1) NOT NULL DEFAULT 0");

CALL add_col_if_absent('goals', 'updated_at',
    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

DROP PROCEDURE add_col_if_absent;
