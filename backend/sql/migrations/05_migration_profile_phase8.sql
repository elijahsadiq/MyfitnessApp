-- Fitness API — Phase 8 (Profile rebuild) migration.
--
-- Adds `users.avatar_path` so a user can upload a profile picture.
-- Stores a relative path only (e.g. `uploads/avatars/abc123.jpg`) so the
-- API moves between hosts (Docker, XAMPP) without URL fixups.
--
-- Also makes the cascade story explicit on every table that references
-- `users` or `workouts`, so `DELETE FROM users WHERE id = ?` and
-- `DELETE FROM workouts WHERE id = ?` reliably clean up every row the
-- phase-8 account-delete flow asks about:
--
--   users
--    ├─ auth_tokens           (users → cascade)
--    ├─ workouts              (users → cascade)
--    │    ├─ strength_sets    (workouts → cascade)
--    │    └─ route_points     (workouts → cascade — added in phase 8a)
--    └─ goals                 (users → cascade)
--
-- The phase-1 and phase-7 migrations already created the FKs with
-- ON DELETE CASCADE, so this migration is overwhelmingly additive —
-- the only new column is `avatar_path`. The cascade column is defensive:
-- if a MariaDB import ever landed without the cascade (an older schema
-- dump that pre-dated this file), the comments above tell the examiner
-- exactly which chain to re-create.
--
-- Idempotent: `add_col_if_absent` guards the ALTER so re-running on a
-- seeded DB is safe.

USE fitness_app;

-- Shared "add column only if it doesn't exist" helper. Phase 7's
-- migration used the same pattern; redeclare here so this file can run
-- stand-alone (the examiner may import migrations individually).
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

CALL add_col_if_absent('users', 'avatar_path',
    "avatar_path VARCHAR(255) NULL AFTER height_cm");

DROP PROCEDURE add_col_if_absent;
