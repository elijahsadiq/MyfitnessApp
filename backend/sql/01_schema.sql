-- Fitness API — schema (phase 9).
--
-- This file is the AUTHORITATIVE schema for a fresh install. The docker
-- compose service mounts `backend/sql/` as /docker-entrypoint-initdb.d,
-- so this file + 02_seed.sql are the only two files MySQL executes on
-- `docker compose down -v && docker compose up -d`. One-time migrations
-- that walked the historical shape live separately in
-- backend/sql/migrations/ and are NOT auto-run — they only exist so a
-- marker can rebuild the shape step-by-step if they want to inspect it.
--
-- Runs on both MySQL 8.4 (Docker) and MariaDB 10.x (XAMPP): utf8mb4
-- with utf8mb4_unicode_ci is declared explicitly on every table so the
-- XAMPP import doesn't fall back to MySQL 8's utf8mb4_0900_ai_ci default
-- (which MariaDB rejects).
--
-- XAMPP users: import 01_schema.sql then 02_seed.sql — nothing else.

CREATE DATABASE IF NOT EXISTS fitness_app
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE fitness_app;

-- ---------------------------------------------------------------------
-- users
--   avatar_path is the relative path the app loads the photo from
--   (`avatars/<random>.jpg`, served by GET /avatars/{file}); avatar_jpeg
--   holds the image itself. Keeping the bytes in MySQL means photos
--   survive restarts on hosts that wipe their disk (Render).
--   NULL = no custom avatar.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(191) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    weight_kg       DECIMAL(5,2) NULL,
    height_cm       DECIMAL(5,2) NULL,
    avatar_path     VARCHAR(255) NULL,
    avatar_jpeg     MEDIUMBLOB NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- auth_tokens
--   Stores only the SHA-256 hash of the issued token, not the token itself.
--   idx_auth_tokens_hash covers the lookup path AuthMiddleware uses.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_tokens (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT UNSIGNED NOT NULL,
    token_hash   CHAR(64) NOT NULL,
    expires_at   TIMESTAMP NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    KEY idx_auth_tokens_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- workouts
--   client_uuid is the device-generated identity. The unique
--   (user_id, client_uuid) key makes POST /workouts idempotent — the
--   same local UUID from the client can be POSTed twice and only the
--   first row survives.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workouts (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT UNSIGNED NOT NULL,
    client_uuid       CHAR(36) NOT NULL,
    activity_type     ENUM('RUNNING', 'CYCLING', 'WEIGHTLIFTING') NOT NULL,
    start_time        DATETIME NOT NULL,
    duration_seconds  INT UNSIGNED NOT NULL,
    distance_meters   DECIMAL(9,2) NULL,
    notes             TEXT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                      ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_workouts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    UNIQUE KEY uq_workouts_user_client (user_id, client_uuid),
    KEY idx_workouts_user_time (user_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- strength_sets
--   Set-by-set breakdown for a WEIGHTLIFTING workout. Cascade delete so
--   removing a workout removes its sets in the same transaction.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS strength_sets (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workout_id     BIGINT UNSIGNED NOT NULL,
    set_order      INT UNSIGNED NOT NULL,
    exercise_name  VARCHAR(120) NOT NULL,
    reps           INT UNSIGNED NOT NULL,
    weight_kg      DECIMAL(6,2) NOT NULL,
    CONSTRAINT fk_strength_sets_workout
        FOREIGN KEY (workout_id) REFERENCES workouts (id) ON DELETE CASCADE,
    KEY idx_strength_sets_workout (workout_id, set_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- route_points (phase 8a — geolocation)
--   Ordered polyline per GPS workout, cascade-deleted with its workout.
--   DECIMAL(9,6) gives ~11 cm precision at the equator.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- goals (phase 8b shape)
--   Goals cover a chosen number of days (duration_days: 1 to 365)
--   starting on start_date. last_day is the LAST day included — so a
--   7-day goal starting Mon has last_day = Sun of the same week.
--   is_main picks a single "featured" goal per user; the server enforces
--   uniqueness in a transaction (MySQL can't express "one row per user
--   where is_main = 1" as a constraint).
--
--   activity_type is nullable (NULL = any activity). goal_type is one
--   of 'distance', 'count', 'duration', 'volume' — the Kotlin side
--   splits that into DistanceGoal / WorkoutCountGoal / DurationGoal /
--   StrengthVolumeGoal subclasses.
--
--   completed_notified is flipped the first time the client celebrates
--   this goal reaching 100% so a cold-start fetch doesn't re-fire.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goals (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT UNSIGNED NOT NULL,
    title              VARCHAR(120) NOT NULL DEFAULT '',
    activity_type      ENUM('RUNNING', 'CYCLING', 'WEIGHTLIFTING') NULL,
    goal_type          VARCHAR(40) NOT NULL,
    target_value       DECIMAL(10,2) NOT NULL,
    start_date         DATE NOT NULL,
    last_day           DATE NOT NULL,
    duration_days      SMALLINT UNSIGNED NOT NULL,
    completed_notified TINYINT(1) NOT NULL DEFAULT 0,
    is_main            TINYINT(1) NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_goals_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    KEY idx_goals_user_days (user_id, start_date, last_day),
    KEY idx_goals_user_main (user_id, is_main)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
