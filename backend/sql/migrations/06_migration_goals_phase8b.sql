-- Fitness API — Phase 8b (Goals redesign) migration.
--
-- Goals move from fixed "this week / this month" windows to a chosen
-- number of days starting on the goal's start_date. Column shape goes
-- from (period, period_start, period_end-exclusive) to (start_date,
-- last_day inclusive, duration_days). Also adds is_main so one goal per
-- user can be featured in the dark card.
--
-- Backfill rule (brief):
--   WEEK  → 7 days from period_start      → last_day = start + 6
--   MONTH → number of days in start month → last_day = LAST_DAY(start)
--
-- The old `period_end` was stored *exclusive* (e.g. 2026-10-12 for a
-- WEEK goal starting 2026-10-05 — Monday of the following week); the
-- new `last_day` is *inclusive* (the Sunday included), so the WEEK
-- branch uses DATE_ADD(period_start, INTERVAL 6 DAY). The MONTH branch
-- uses LAST_DAY() which is already inclusive.
--
-- MariaDB-safe: DATE_ADD, LAST_DAY and DAY() are all 10.0+ standard.
-- No MySQL-8-only syntax.
--
-- Living in backend/sql/migrations/ (not backend/sql/), so the
-- /docker-entrypoint-initdb.d bind mount doesn't re-run this on a
-- fresh install. Fresh installs land straight on the new shape via
-- the rewritten backend/sql/01_schema.sql.

USE fitness_app;

-- ---------------------------------------------------------------------
-- 1. Add the new columns (nullable so the backfill can run).
-- ---------------------------------------------------------------------
ALTER TABLE goals
    ADD COLUMN start_date    DATE                 NULL AFTER target_value,
    ADD COLUMN last_day      DATE                 NULL AFTER start_date,
    ADD COLUMN duration_days SMALLINT UNSIGNED    NULL AFTER last_day,
    ADD COLUMN is_main       TINYINT(1) NOT NULL  DEFAULT 0 AFTER completed_notified;

-- ---------------------------------------------------------------------
-- 2. Backfill from the old shape.
-- ---------------------------------------------------------------------
UPDATE goals
   SET start_date    = period_start,
       duration_days = CASE
                         WHEN period = 'WEEK'  THEN 7
                         WHEN period = 'MONTH' THEN DAY(LAST_DAY(period_start))
                       END,
       last_day      = CASE
                         WHEN period = 'WEEK'  THEN DATE_ADD(period_start, INTERVAL 6 DAY)
                         WHEN period = 'MONTH' THEN LAST_DAY(period_start)
                       END;

-- ---------------------------------------------------------------------
-- 3. Promote to NOT NULL now that the backfill is done.
-- ---------------------------------------------------------------------
ALTER TABLE goals
    MODIFY start_date    DATE              NOT NULL,
    MODIFY last_day      DATE              NOT NULL,
    MODIFY duration_days SMALLINT UNSIGNED NOT NULL;

-- ---------------------------------------------------------------------
-- 4. Add the new indices FIRST, before dropping the old shape.
--    `idx_goals_user_period` is the FK-backing index for fk_goals_user
--    on user_id — we can't drop it unless at least one other index
--    starts with user_id. Add the replacements, then drop the old.
--
--    `idx_goals_user_days` powers the "all goals for user, sorted by
--    last_day" query the Goals list makes.
--    `idx_goals_user_main` powers the "which goal is the main one"
--    lookup used by the dark card.
-- ---------------------------------------------------------------------
ALTER TABLE goals
    ADD KEY idx_goals_user_days (user_id, start_date, last_day),
    ADD KEY idx_goals_user_main (user_id, is_main);

-- ---------------------------------------------------------------------
-- 5. Drop the old shape + its index.
-- ---------------------------------------------------------------------
ALTER TABLE goals
    DROP INDEX idx_goals_user_period,
    DROP COLUMN period,
    DROP COLUMN period_start,
    DROP COLUMN period_end;
