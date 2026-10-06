-- Fitness API — seed data.
--
-- One test user + 9 workouts spread across the last 14 days, covering all
-- three activity types. Times are relative to the *first* container boot
-- (DATE_SUB(NOW(), INTERVAL X DAY)), so re-seeding after a `docker compose
-- down -v` gives you a fresh 14-day window centred on today.

USE fitness_app;

-- ---------------------------------------------------------------------
-- Test user
--   email:     alex@example.com
--   password:  Password123
--   The hash is a real bcrypt cost-10 digest of "Password123" generated
--   with Python's bcrypt library; PHP's password_verify() accepts both
--   $2b$ and $2y$ prefixes since PHP 5.5.
-- ---------------------------------------------------------------------
INSERT INTO users (email, password_hash, full_name, weight_kg, height_cm) VALUES
    ('alex@example.com',
     '$2b$10$GWJez6pcfNE8FyCAMQ/6.uDiJWfRfEYbyYv4GoOCezBFV3jh.aLUu',
     'Alex Runner', 70.0, 178.0);

SET @alex_id = LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Distance workouts (running + cycling)
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

-- ---------------------------------------------------------------------
-- Strength workouts
--   Each one is created, its LAST_INSERT_ID captured into a variable, then
--   its strength_sets are inserted against that variable.
-- ---------------------------------------------------------------------
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
-- Goals (phase 7 feature; seeded so a fresh clone shows the Goals tab
-- populated out of the box — the submission marker should see at least
-- two in-progress goals, which this gives them).
--
-- period_start/period_end are DATEs (midnight in the device zone), so we
-- derive them from CURDATE(). The anchors below guarantee the current
-- calendar week + month are covered regardless of what day the examiner
-- runs the seed.
-- ---------------------------------------------------------------------
INSERT INTO goals
    (user_id, title, activity_type, goal_type, period, target_value, period_start, period_end)
VALUES
    (@alex_id, 'Run 20 km this week', 'RUNNING', 'distance', 'WEEK',
     20.00,
     DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY),
     DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) + INTERVAL 7 DAY),
    (@alex_id, 'Lift 5000 kg this month', 'WEIGHTLIFTING', 'volume', 'MONTH',
     5000.00,
     DATE_SUB(CURDATE(), INTERVAL (DAY(CURDATE()) - 1) DAY),
     LAST_DAY(CURDATE()) + INTERVAL 1 DAY),
    (@alex_id, 'Move 4 times this week', NULL, 'count', 'WEEK',
     4.00,
     DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY),
     DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) + INTERVAL 7 DAY);
