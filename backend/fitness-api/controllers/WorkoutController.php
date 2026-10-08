<?php
/**
 * /workouts endpoints. All queries are scoped by the authenticated user_id.
 * POST /workouts is upsert-on-client_uuid — the same device-generated UUID
 * can be posted twice safely (idempotent, ready for phase-4 sync).
 */
final class WorkoutController
{
    private const ALLOWED_TYPES = ['RUNNING', 'CYCLING', 'WEIGHTLIFTING'];

    /** Hard cap so a malformed client cannot flood route_points (~2.5 h at 1 Hz). */
    private const MAX_ROUTE_POINTS = 10000;

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

        // List view omits route_points — the detail call fetches them when needed.
        $out = array_map(static function (array $row): array {
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
        $route = Workout::routeFor((int) $row['id']);
        Response::success(Workout::toDto($row, $sets, $route));
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
        $routePoints  = is_array($body['routePoints'] ?? null) ? $body['routePoints'] : [];

        // Validate the route payload once up front — easier for the client
        // than discovering errors deep inside the transaction.
        if (!empty($routePoints)) {
            if ($activityType === 'WEIGHTLIFTING') {
                Response::error('Validation failed', 422,
                    ['routePoints' => 'Weightlifting workouts cannot carry a route']);
            }
            if (count($routePoints) > self::MAX_ROUTE_POINTS) {
                Response::error('Validation failed', 422,
                    ['routePoints' => 'Too many points (max ' . self::MAX_ROUTE_POINTS . ')']);
            }
            $seen = [];
            foreach ($routePoints as $i => $pt) {
                $pv = (new Validator($pt))
                    ->required('seq')->integer('seq')->inRange('seq', 0, PHP_INT_MAX)
                    ->required('latitude')->numeric('latitude')->inRange('latitude', -90, 90)
                    ->required('longitude')->numeric('longitude')->inRange('longitude', -180, 180)
                    ->required('elapsedMs')->integer('elapsedMs')->inRange('elapsedMs', 0, PHP_INT_MAX)
                    ->required('recordedAt')->isDatetime('recordedAt');
                if (isset($pt['accuracyM']) && $pt['accuracyM'] !== null) {
                    $pv->numeric('accuracyM')->inRange('accuracyM', 0, 10000);
                }
                if ($pv->fails()) {
                    Response::error('Validation failed', 422,
                        ['routePoints' => 'Point #' . ($i + 1) . ' is invalid']);
                }
                $seq = (int) $pt['seq'];
                if (isset($seen[$seq])) {
                    Response::error('Validation failed', 422,
                        ['routePoints' => 'Duplicate seq ' . $seq]);
                }
                $seen[$seq] = true;
            }
        }

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
        // return it — do NOT create a duplicate. Route points on the retry
        // are intentionally ignored; the first successful create wins.
        $existing = Workout::findByClientUuid($userId, $clientUuid);
        if ($existing !== null) {
            $existingSets = $existing['activity_type'] === 'WEIGHTLIFTING'
                ? Workout::setsFor((int) $existing['id'])
                : [];
            $existingRoute = Workout::routeFor((int) $existing['id']);
            Response::success(Workout::toDto($existing, $existingSets, $existingRoute), 200);
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
                $sets,
                $routePoints
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
                    $existingRoute = Workout::routeFor((int) $again['id']);
                    Response::success(Workout::toDto($again, $existingSets, $existingRoute), 200);
                }
            }
            throw $e;
        }

        $row = Workout::findById($workoutId, $userId);
        $sets = ($row !== null && $row['activity_type'] === 'WEIGHTLIFTING')
            ? Workout::setsFor($workoutId)
            : [];
        $route = Workout::routeFor($workoutId);
        Response::success(Workout::toDto($row ?? [], $sets, $route), 201);
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

    /**
     * PATCH /workouts/{id} — partial update. For now only `notes` is
     * editable (phase 8c step 7 adds the Edit-notes flow on the finished
     * workout screen). Other fields are ignored so the endpoint can
     * safely grow.
     *
     * Body: { "notes": "..." } — pass `null` or omit to clear it.
     * Response: the updated workout DTO, same shape as GET /workouts/{id}.
     */
    public static function update(array $params, array $body): void
    {
        $userId = Auth::requireUser();
        $id = (int) $params['id'];

        if (!array_key_exists('notes', $body)) {
            Response::error('Validation failed', 422,
                ['notes' => 'Nothing to update']);
        }

        $notesRaw = $body['notes'];
        $notes = $notesRaw === null ? null : (string) $notesRaw;
        if ($notes !== null) {
            $v = (new Validator(['notes' => $notes]))->maxLength('notes', 2000);
            if ($v->fails()) {
                Response::error('Validation failed', 422, $v->errors());
            }
            // Empty string is treated as "clear the note".
            if (trim($notes) === '') {
                $notes = null;
            }
        }

        $ok = Workout::updateNotes($id, $userId, $notes);
        if (!$ok) {
            Response::error('Workout not found', 404);
        }

        $row = Workout::findById($id, $userId);
        $sets = ($row !== null && $row['activity_type'] === 'WEIGHTLIFTING')
            ? Workout::setsFor($id)
            : [];
        $route = Workout::routeFor($id);
        Response::success(Workout::toDto($row ?? [], $sets, $route));
    }

    private static function stringOrNull(mixed $v): ?string
    {
        if ($v === null) return null;
        $s = trim((string) $v);
        return $s === '' ? null : $s;
    }
}
