<?php
/**
 * /goals endpoints — CRUD for goal *definitions*. Progress against a
 * goal is computed client-side (Kotlin), so no endpoint here joins
 * workouts.
 *
 * Every query is scoped by the authenticated user_id. A goal that
 * belongs to another user returns 404, never 403 — refusing to reveal
 * that the id even exists.
 *
 * ### Server-side validation (phase-8b)
 *   - title: required, ≤ 120 chars
 *   - goal_type: one of distance / count / duration / volume
 *   - target_value: > 0, ≤ per-type upper bound (mirrors the Kotlin UI)
 *   - duration_days: 1 to 365 (DURATION_MIN/DURATION_MAX on the model)
 *   - start_date: within ±1 day of the server's current date (DATE), so
 *     a user in UTC+3 creating a goal just after midnight gets the
 *     right start day while the containers run in UTC
 *   - activity_type × goal_type matrix:
 *       distance  ⇒ activity ∈ {RUNNING, CYCLING}
 *       volume    ⇒ activity = WEIGHTLIFTING
 *       duration  ⇒ activity ∈ {RUNNING, CYCLING, WEIGHTLIFTING, null}
 *       count     ⇒ activity ∈ {RUNNING, CYCLING, WEIGHTLIFTING, null}
 *     See validateActivityGoalTypeCombo for the matrix.
 */
final class GoalController
{
    /** Upper bounds mirror the client-side sanity limits. */
    private const TARGET_MAX = [
        'distance' => 1000.0,     // km
        'count'    => 100.0,      // sessions
        'duration' => 10000.0,    // minutes
        'volume'   => 500000.0,   // kg total (reps × weight)
    ];

    private const START_DATE_WINDOW_DAYS = 1;

    public static function index(array $_params, array $_body): void
    {
        $userId = Auth::requireUser();
        $rows = Goal::listFor($userId);
        Response::success(array_map([Goal::class, 'toDto'], $rows));
    }

    public static function create(array $_params, array $body): void
    {
        $userId = Auth::requireUser();

        [$title, $activityType, $goalType, $targetValue,
            $startDate, $durationDays] = self::parseAndValidateCreate($body);

        // Server computes the last day from the client-supplied window.
        $lastDate = (new DateTimeImmutable($startDate))
            ->modify('+' . ($durationDays - 1) . ' days')
            ->format('Y-m-d');

        $id = Goal::create(
            $userId, $title, $activityType, $goalType,
            $targetValue, $startDate, $lastDate, $durationDays
        );
        $row = Goal::findById($id, $userId);
        Response::success(Goal::toDto($row ?? []), 201);
    }

    public static function update(array $params, array $body): void
    {
        $userId = Auth::requireUser();
        $id = (int) $params['id'];

        $existing = Goal::findById($id, $userId);
        if ($existing === null) {
            Response::error('Goal not found', 404);
        }

        // Merge with current values so unchanged fields can be omitted.
        $body = array_merge([
            'title'        => $existing['title'],
            'activityType' => $existing['activity_type'],
            'goalType'     => $existing['goal_type'],
            'targetValue'  => (float) $existing['target_value'],
            'durationDays' => (int) $existing['duration_days'],
        ], $body);

        $v = (new Validator($body))
            ->required('title')->maxLength('title', 120)
            ->required('goalType')->inEnum('goalType', Goal::GOAL_TYPES)
            ->required('targetValue')->numeric('targetValue')
            ->required('durationDays')->integer('durationDays')
                ->inRange('durationDays', Goal::DURATION_MIN, Goal::DURATION_MAX);
        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $goalType     = (string) $body['goalType'];
        $target       = (float)  $body['targetValue'];
        $durationDays = (int)    $body['durationDays'];
        $activityType = self::stringOrNull($body['activityType'] ?? null);

        if ($activityType !== null && !in_array($activityType, Goal::ACTIVITY_TYPES, true)) {
            Response::error(
                'Validation failed',
                422,
                ['activityType' => 'Must be one of: ' . implode(', ', Goal::ACTIVITY_TYPES)]
            );
        }

        self::validateTargetRange($goalType, $target);
        self::validateActivityGoalTypeCombo($goalType, $activityType);

        // Recompute last_day from the (immutable) start_date + new span.
        $lastDate = (new DateTimeImmutable((string) $existing['start_date']))
            ->modify('+' . ($durationDays - 1) . ' days')
            ->format('Y-m-d');

        Goal::update(
            $id, $userId, (string) $body['title'],
            $activityType, $goalType, $target, $durationDays, $lastDate
        );
        $row = Goal::findById($id, $userId);
        Response::success(Goal::toDto($row ?? []));
    }

    public static function destroy(array $params, array $_body): void
    {
        $userId = Auth::requireUser();
        $ok = Goal::deleteFor((int) $params['id'], $userId);
        if (!$ok) {
            Response::error('Goal not found', 404);
        }
        Response::success(null, 200);
    }

    /**
     * POST /goals/{id}/main — mark one goal as the user's main goal,
     * clearing is_main on every other goal of theirs. Transactional so
     * concurrent requests can't briefly leave two mains visible.
     */
    public static function makeMain(array $params, array $_body): void
    {
        $userId = Auth::requireUser();
        $id = (int) $params['id'];
        $ok = Goal::setAsMain($id, $userId);
        if (!$ok) {
            Response::error('Goal not found', 404);
        }
        $row = Goal::findById($id, $userId);
        Response::success(Goal::toDto($row ?? []));
    }

    /**
     * POST /goals/{id}/extend — add days to an active goal, up to the
     * 365-day cap. Ended goals (last_day < today) are rejected with a
     * clear message so the client knows to steer the user into "Try
     * again" (which creates a new goal) instead.
     */
    public static function extend(array $params, array $body): void
    {
        $userId = Auth::requireUser();
        $id = (int) $params['id'];

        $existing = Goal::findById($id, $userId);
        if ($existing === null) {
            Response::error('Goal not found', 404);
        }

        $v = (new Validator($body))
            ->required('days')->integer('days')->inRange('days', 1, Goal::DURATION_MAX);
        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $days = (int) $body['days'];

        $today = (new DateTimeImmutable('today', new DateTimeZone('UTC')))->format('Y-m-d');
        if ((string) $existing['last_day'] < $today) {
            Response::error(
                'Cannot extend an ended goal — try again creates a new goal',
                422,
                ['days' => 'This goal has ended; extending is not allowed']
            );
        }

        $newDuration = (int) $existing['duration_days'] + $days;
        if ($newDuration > Goal::DURATION_MAX) {
            Response::error(
                'Validation failed',
                422,
                ['days' => 'Extension pushes total above ' . Goal::DURATION_MAX . ' days']
            );
        }

        Goal::extend($id, $userId, $days);
        $row = Goal::findById($id, $userId);
        Response::success(Goal::toDto($row ?? []));
    }

    /**
     * Flip the `completed_notified` flag to 1. Called by the client the
     * first time it celebrates a goal reaching 100% so a subsequent
     * refresh (or a new session) doesn't fire the celebration again.
     */
    public static function markNotified(array $params, array $_body): void
    {
        $userId = Auth::requireUser();
        $ok = Goal::markCompletedNotified((int) $params['id'], $userId);
        if (!$ok) {
            Response::error('Goal not found', 404);
        }
        $row = Goal::findById((int) $params['id'], $userId);
        Response::success(Goal::toDto($row ?? []));
    }

    // ---------------------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------------------

    /**
     * @return array{0:string,1:?string,2:string,3:float,4:string,5:int}
     *   title, activityType, goalType, targetValue, startDate, durationDays
     */
    private static function parseAndValidateCreate(array $body): array
    {
        $v = (new Validator($body))
            ->required('title')->maxLength('title', 120)
            ->required('goalType')->inEnum('goalType', Goal::GOAL_TYPES)
            ->required('targetValue')->numeric('targetValue')
            ->required('startDate')
            ->required('durationDays')->integer('durationDays')
                ->inRange('durationDays', Goal::DURATION_MIN, Goal::DURATION_MAX);
        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $title        = trim((string) $body['title']);
        $goalType     = (string) $body['goalType'];
        $target       = (float)  $body['targetValue'];
        $startDate    = self::parseDateOrFail('startDate', (string) $body['startDate']);
        $durationDays = (int)    $body['durationDays'];
        $activityType = self::stringOrNull($body['activityType'] ?? null);

        if ($activityType !== null && !in_array($activityType, Goal::ACTIVITY_TYPES, true)) {
            Response::error(
                'Validation failed',
                422,
                ['activityType' => 'Must be one of: ' . implode(', ', Goal::ACTIVITY_TYPES)]
            );
        }

        self::validateTargetRange($goalType, $target);
        self::validateActivityGoalTypeCombo($goalType, $activityType);
        self::validateStartDateWindow($startDate);

        return [$title, $activityType, $goalType, $target, $startDate, $durationDays];
    }

    private static function validateTargetRange(string $goalType, float $target): void
    {
        if ($target <= 0) {
            Response::error('Validation failed', 422, ['targetValue' => 'Must be greater than 0']);
        }
        if (isset(self::TARGET_MAX[$goalType]) && $target > self::TARGET_MAX[$goalType]) {
            Response::error(
                'Validation failed',
                422,
                ['targetValue' => 'Must be at most ' . self::TARGET_MAX[$goalType]]
            );
        }
    }

    /**
     * Enforce the activity × goalType matrix from the brief:
     *
     *   distance  ⇒ RUNNING / CYCLING          (NOT null, NOT WEIGHTLIFTING)
     *   volume    ⇒ WEIGHTLIFTING              (NOT null, NOT RUNNING, NOT CYCLING)
     *   duration  ⇒ RUNNING / CYCLING / WEIGHTLIFTING / null
     *   count     ⇒ RUNNING / CYCLING / WEIGHTLIFTING / null
     */
    private static function validateActivityGoalTypeCombo(string $goalType, ?string $activityType): void
    {
        $distanceAllowed = ['RUNNING', 'CYCLING'];
        $volumeAllowed   = ['WEIGHTLIFTING'];

        if ($goalType === 'distance' && !in_array($activityType, $distanceAllowed, true)) {
            Response::error(
                'Validation failed',
                422,
                ['activityType' => 'Distance goals require Running or Cycling']
            );
        }
        if ($goalType === 'volume' && !in_array($activityType, $volumeAllowed, true)) {
            Response::error(
                'Validation failed',
                422,
                ['activityType' => 'Weight goals require Workout']
            );
        }
    }

    /**
     * Reject a start_date more than ±1 day from the server's today.
     * The containers run in UTC; a client in UTC+3 creating a goal just
     * after midnight will send tomorrow's date, which lands inside this
     * tolerance.
     */
    private static function validateStartDateWindow(string $startDate): void
    {
        $serverToday = new DateTimeImmutable('today', new DateTimeZone('UTC'));
        $diff        = $serverToday->diff(new DateTimeImmutable($startDate));
        if ($diff->days > self::START_DATE_WINDOW_DAYS) {
            Response::error(
                'Validation failed',
                422,
                ['startDate' => 'Must be within one day of today']
            );
        }
    }

    private static function parseDateOrFail(string $field, string $value): string
    {
        try {
            $dt = new DateTimeImmutable($value);
        } catch (Throwable) {
            Response::error('Validation failed', 422, [$field => 'Must be a date (YYYY-MM-DD)']);
        }
        return $dt->format('Y-m-d');
    }

    private static function stringOrNull(mixed $v): ?string
    {
        if ($v === null) return null;
        $s = trim((string) $v);
        return $s === '' ? null : $s;
    }
}
