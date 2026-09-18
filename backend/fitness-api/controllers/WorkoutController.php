<?php
/**
 * /workouts endpoints. All queries are scoped by the authenticated user_id.
 * POST /workouts is upsert-on-client_uuid — the same device-generated UUID
 * can be posted twice safely (idempotent, ready for phase-4 sync).
 */
final class WorkoutController
{
    private const ALLOWED_TYPES = ['RUNNING', 'CYCLING', 'WEIGHTLIFTING'];

    public static function health(array $_params, array $_body): void
    {
        // No auth — used by clients / monitoring to check the API is up.
        Response::success(['status' => 'ok', 'time' => gmdate('c')]);
    }

    public static function index(array $_params, array $_body): void
    {
        $userId = Auth::requireUser();

        $type = self::stringOrNull($_GET['type'] ?? null);
        if ($type !== null && !in_array($type, self::ALLOWED_TYPES, true)) {
            Response::error('Invalid type filter', 400, ['type' => 'Must be RUNNING, CYCLING or WEIGHTLIFTING']);
        }
        $from = self::stringOrNull($_GET['from'] ?? null);
        $to   = self::stringOrNull($_GET['to']   ?? null);

        try {
            $rows = Workout::listFor($userId, $type, $from, $to);
        } catch (Throwable) {
            Response::error('Invalid date filter', 400, ['from' => 'ISO 8601 expected', 'to' => 'ISO 8601 expected']);
        }

        $out = array_map(static function (array $row) use ($userId): array {
            $sets = $row['activity_type'] === 'WEIGHTLIFTING'
                ? Workout::setsFor((int) $row['id'])
                : [];
            return Workout::toDto($row, $sets);
        }, $rows);
        Response::success($out);
    }

    public static function show(array $params, array $_body): void
    {
        $userId = Auth::requireUser();
        $row = Workout::findById((int) $params['id'], $userId);
        if ($row === null) {
            Response::error('Workout not found', 404);
        }
        $sets = $row['activity_type'] === 'WEIGHTLIFTING'
            ? Workout::setsFor((int) $row['id'])
            : [];
        Response::success(Workout::toDto($row, $sets));
    }

    public static function create(array $_params, array $body): void
    {
        $userId = Auth::requireUser();

        $v = (new Validator($body))
            ->required('clientUuid')->maxLength('clientUuid', 36)
            ->required('activityType')->inEnum('activityType', self::ALLOWED_TYPES)
            ->required('startTime')->isDatetime('startTime')
            ->required('durationSeconds')->integer('durationSeconds')->inRange('durationSeconds', 1, 86400)
            ->numeric('distanceMeters')->inRange('distanceMeters', 0, 300000);

        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $clientUuid   = (string) $body['clientUuid'];
        $activityType = (string) $body['activityType'];
        $sets         = is_array($body['sets'] ?? null) ? $body['sets'] : [];

        // Server-side rules that depend on activity type.
        if ($activityType === 'WEIGHTLIFTING') {
            if (empty($sets)) {
                Response::error('Validation failed', 422, ['sets' => 'At least one set is required']);
            }
            foreach ($sets as $i => $set) {
                $sv = (new Validator($set))
                    ->required('exerciseName')->maxLength('exerciseName', 120)
                    ->required('reps')->integer('reps')->inRange('reps', 1, 500)
                    ->required('weightKg')->numeric('weightKg')->inRange('weightKg', 0, 1000);
                if ($sv->fails()) {
                    Response::error('Validation failed', 422, ['sets' => "Set #" . ($i + 1) . " is invalid"]);
                }
            }
        } else {
            // Distance workouts must have a positive distance.
            if (!isset($body['distanceMeters']) || (float) $body['distanceMeters'] <= 0) {
                Response::error('Validation failed', 422, ['distanceMeters' => 'Distance is required for distance workouts']);
            }
        }

        // Upsert: if we already have a row with this (user_id, client_uuid),
        // return it — do NOT create a duplicate.
        $existing = Workout::findByClientUuid($userId, $clientUuid);
        if ($existing !== null) {
            $existingSets = $existing['activity_type'] === 'WEIGHTLIFTING'
                ? Workout::setsFor((int) $existing['id'])
                : [];
            Response::success(Workout::toDto($existing, $existingSets), 200);
        }

        try {
            $workoutId = Workout::create(
                $userId,
                $clientUuid,
                $activityType,
                (string) $body['startTime'],
                (int) $body['durationSeconds'],
                isset($body['distanceMeters']) ? (float) $body['distanceMeters'] : null,
                isset($body['notes']) ? (string) $body['notes'] : null,
                $sets
            );
        } catch (PDOException $e) {
            // Unique-key violation on (user_id, client_uuid) — race between
            // two POSTs; treat as "already created", return existing.
            if ($e->getCode() === '23000') {
                $again = Workout::findByClientUuid($userId, $clientUuid);
                if ($again !== null) {
                    $existingSets = $again['activity_type'] === 'WEIGHTLIFTING'
                        ? Workout::setsFor((int) $again['id'])
                        : [];
                    Response::success(Workout::toDto($again, $existingSets), 200);
                }
            }
            throw $e;
        }

        $row = Workout::findById($workoutId, $userId);
        $sets = ($row !== null && $row['activity_type'] === 'WEIGHTLIFTING')
            ? Workout::setsFor($workoutId)
            : [];
        Response::success(Workout::toDto($row ?? [], $sets), 201);
    }

    public static function destroy(array $params, array $_body): void
    {
        $userId = Auth::requireUser();
        $ok = Workout::deleteFor((int) $params['id'], $userId);
        if (!$ok) {
            Response::error('Workout not found', 404);
        }
        Response::success(null, 200);
    }

    private static function stringOrNull(mixed $v): ?string
    {
        if ($v === null) return null;
        $s = trim((string) $v);
        return $s === '' ? null : $s;
    }
}
