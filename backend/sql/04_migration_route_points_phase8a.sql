-- Fitness API — Phase 8a (Geolocation) migration.
--
-- Adds the `route_points` table so GPS-tracked workouts can store an
-- ordered polyline. Progress is still calculated client-side; this table
-- is for display + report evidence only (the detail-screen route and the
-- phpMyAdmin screenshot the written report cites).
--
-- Idempotent: CREATE TABLE IF NOT EXISTS lets the file run twice without
-- failing, so re-importing on a seeded DB is safe.
--
-- Cross-engine notes:
--   - DECIMAL(9,6) holds the full lat/lon range with sub-metre precision
--     (6 decimal places ≈ 11 cm at the equator).
--   - utf8mb4_unicode_ci on every column that could hold text — matches
--     the rest of the schema so MariaDB under XAMPP doesn't fall back to
--     MySQL 8's utf8mb4_0900_ai_ci default (which MariaDB rejects).
--   - Cascade delete so removing a workout drops its route in one
--     transaction, same pattern as `strength_sets`.
--   - KEY (workout_id, seq) is the lookup index the detail-screen fetch
--     uses; the UNIQUE constraint also stops a buggy client from
--     re-posting the same seq twice.

USE fitness_app;

CREATE TABLE IF NOT EXISTS route_points (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workout_id    BIGINT UNSIGNED NOT NULL,
    seq           INT UNSIGNED NOT NULL,
    latitude      DECIMAL(9,6) NOT NULL,
    longitude     DECIMAL(9,6) NOT NULL,
    elapsed_ms    BIGINT UNSIGNED NOT NULL,
    accuracy_m    DECIMAL(6,2) NULL,
    recorded_at   DATETIME NOT NULL,
    CONSTRAINT fk_route_points_workout
        FOREIGN KEY (workout_id) REFERENCES workouts (id) ON DELETE CASCADE,
    UNIQUE KEY uq_route_points_workout_seq (workout_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
