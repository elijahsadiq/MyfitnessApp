-- Fitness API — seed data (phase 8b).
--
-- One test user + 9 workouts spread across the last 14 days + 5 goals
-- (one main + two other active + one completed + one ended), so a fresh
-- Docker install shows a fully populated app on first open.
--
-- All relative dates are DATE_SUB(CURDATE(), INTERVAL n DAY) / NOW() so
-- the seed stays "today-aware" — re-seeding after `docker compose down -v`
-- gives you a fresh window centred on whatever day you ran it on.

USE fitness_app;

-- ---------------------------------------------------------------------
-- Test user
--   email:     alex@example.com
--   password:  Password123   (bcrypt cost 10, "$2b$..." → password_verify-accepted)
--   Weight + height set so BMI, kcal and the insight card show real data.
-- ---------------------------------------------------------------------
INSERT INTO users (email, password_hash, full_name, weight_kg, height_cm) VALUES
    ('alex@example.com',
     '$2b$10$GWJez6pcfNE8FyCAMQ/6.uDiJWfRfEYbyYv4GoOCezBFV3jh.aLUu',
     'Alex Runner', 70.0, 178.0);

SET @alex_id = LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Workouts (feed goal progress + Home + Progress + Insights)
-- ---------------------------------------------------------------------
INSERT INTO workouts
    (user_id, client_uuid, activity_type, start_time, duration_seconds, distance_meters, notes)
VALUES
    (@alex_id, '11111111-1111-4111-8111-111111111101', 'RUNNING',
     DATE_SUB(NOW(), INTERVAL 1 DAY) - INTERVAL 2 HOUR, 1680, 5100.00,
     'Easy morning shakeout'),
    (@alex_id, '11111111-1111-4111-8111-111111111102', 'CYCLING',
     DATE_SUB(NOW(), INTERVAL 2 DAY) - INTERVAL 5 HOUR, 3720, 22400.00,
     'River loop'),
    (@alex_id, '11111111-1111-4111-8111-111111111104', 'RUNNING',
     DATE_SUB(NOW(), INTERVAL 4 DAY) - INTERVAL 10 HOUR, 2760, 8050.00,
     'Tempo, felt strong'),
    (@alex_id, '11111111-1111-4111-8111-111111111105', 'CYCLING',
     DATE_SUB(NOW(), INTERVAL 6 DAY) - INTERVAL 3 HOUR, 5700, 34800.00,
     'Coffee ride'),
    (@alex_id, '11111111-1111-4111-8111-111111111107', 'RUNNING',
     DATE_SUB(NOW(), INTERVAL 9 DAY) - INTERVAL 2 HOUR, 3840, 10500.00,
     'Long slow distance'),
    (@alex_id, '11111111-1111-4111-8111-111111111108', 'CYCLING',
     DATE_SUB(NOW(), INTERVAL 11 DAY) - INTERVAL 4 HOUR, 2400, 13600.00,
     'Commute');

INSERT INTO workouts
    (user_id, client_uuid, activity_type, start_time, duration_seconds, distance_meters, notes)
VALUES
    (@alex_id, '11111111-1111-4111-8111-111111111103', 'WEIGHTLIFTING',
     DATE_SUB(NOW(), INTERVAL 3 DAY) - INTERVAL 4 HOUR, 3120, NULL, 'Lower + push');
SET @w_lift1 = LAST_INSERT_ID();

INSERT INTO strength_sets (workout_id, set_order, exercise_name, reps, weight_kg) VALUES
    (@w_lift1, 1, 'Squat',       5, 100.00),
    (@w_lift1, 2, 'Squat',       5, 105.00),
    (@w_lift1, 3, 'Squat',       5, 110.00),
    (@w_lift1, 4, 'Bench Press', 8,  62.50),
    (@w_lift1, 5, 'Bench Press', 8,  62.50),
    (@w_lift1, 6, 'Row',         10, 55.00);

INSERT INTO workouts
    (user_id, client_uuid, activity_type, start_time, duration_seconds, distance_meters, notes)
VALUES
    (@alex_id, '11111111-1111-4111-8111-111111111106', 'WEIGHTLIFTING',
     DATE_SUB(NOW(), INTERVAL 7 DAY) - INTERVAL 6 HOUR, 2880, NULL, 'Pull + press');
SET @w_lift2 = LAST_INSERT_ID();

INSERT INTO strength_sets (workout_id, set_order, exercise_name, reps, weight_kg) VALUES
    (@w_lift2, 1, 'Deadlift',        5, 120.00),
    (@w_lift2, 2, 'Deadlift',        3, 130.00),
    (@w_lift2, 3, 'Overhead Press',  6,  42.50),
    (@w_lift2, 4, 'Pull Up',         8,   0.00),
    (@w_lift2, 5, 'Pull Up',         8,   0.00);

INSERT INTO workouts
    (user_id, client_uuid, activity_type, start_time, duration_seconds, distance_meters, notes)
VALUES
    (@alex_id, '11111111-1111-4111-8111-111111111109', 'WEIGHTLIFTING',
     DATE_SUB(NOW(), INTERVAL 13 DAY) - INTERVAL 1 HOUR, 2100, NULL, 'Bench focus');
SET @w_lift3 = LAST_INSERT_ID();

INSERT INTO strength_sets (workout_id, set_order, exercise_name, reps, weight_kg) VALUES
    (@w_lift3, 1, 'Bench Press', 5, 65.00),
    (@w_lift3, 2, 'Bench Press', 5, 65.00),
    (@w_lift3, 3, 'Bench Press', 5, 65.00);

-- ---------------------------------------------------------------------
-- Goals (phase 8b shape — duration_days + start_date + last_day + is_main)
--
-- Five goals so a fresh install hits every state the Goals screen renders:
--
--   g1  MAIN · active     Run 50 km in 30 days      (started 7 days ago)
--   g2       · active     Ride 150 km in 21 days    (started 5 days ago)
--   g3       · active     Train 300 min in 14 days  (started 2 days ago)
--   g4       · completed  4 workouts in 7 days      (started 10 days ago, target met)
--   g5       · ended      Lift 5000 kg in 7 days    (started 20 days ago, target not met)
-- ---------------------------------------------------------------------
INSERT INTO goals
    (user_id, title, activity_type, goal_type,
     target_value, start_date, last_day, duration_days,
     completed_notified, is_main)
VALUES
    (@alex_id, 'Run 50 km in 30 days', 'RUNNING', 'distance',
     50.00,
     DATE_SUB(CURDATE(), INTERVAL 7 DAY),
     DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 7 DAY), INTERVAL 29 DAY),
     30,
     0, 1),

    (@alex_id, 'Ride 150 km in 21 days', 'CYCLING', 'distance',
     150.00,
     DATE_SUB(CURDATE(), INTERVAL 5 DAY),
     DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 5 DAY), INTERVAL 20 DAY),
     21,
     0, 0),

    (@alex_id, 'Train 300 min in 14 days', NULL, 'duration',
     300.00,
     DATE_SUB(CURDATE(), INTERVAL 2 DAY),
     DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 2 DAY), INTERVAL 13 DAY),
     14,
     0, 0),

    -- Completed: completed_notified = 1 so the client doesn't re-fire
    -- the celebration Snackbar on first open.
    (@alex_id, '4 workouts this week', NULL, 'count',
     4.00,
     DATE_SUB(CURDATE(), INTERVAL 10 DAY),
     DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 10 DAY), INTERVAL 6 DAY),
     7,
     1, 0),

    -- Ended: last_day = CURDATE() - 14, so it's firmly in the past.
    (@alex_id, 'Lift 5000 kg in 7 days', 'WEIGHTLIFTING', 'volume',
     5000.00,
     DATE_SUB(CURDATE(), INTERVAL 20 DAY),
     DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 20 DAY), INTERVAL 6 DAY),
     7,
     0, 0);
