<?php
/**
 * /goals endpoints — CRUD for goal *definitions*. Progress against a goal
 * is computed client-side (Kotlin), so no endpoint here joins workouts.
 *
 * Every query is scoped by the authenticated user_id. A goal that belongs
 * to another user returns 404, never 403 — refusing to reveal that the
 * id even exists.
 *
 * ### Server-side validation (mirrors phase-7 Kotlin invariants)
 *   - title: required, ≤ 120 chars
 *   - goal_type: one of distance / count / duration / volume
 *   - period: WEEK | MONTH
 *   - target_value: > 0, ≤ per-type upper bound (parallels the client)
 *   - period_end > period_start (dates)
 *   - activity_type × goal_type combos:
 *       distance ⇒ activity is null | RUNNING | CYCLING (never WEIGHTLIFTING)
 *       volume   ⇒ activity is null | WEIGHTLIFTING (never RUNNING/CYCLING)
 */
final class GoalController
{
    /** Upper bounds mirror the client-side sanity limits (phase-7 step 5). */
    private const TARGET_MAX = [
        'distance' => 1000.0,   // km
        'count'    => 1000.0,   // workouts
        'duration' => 100_000.0, // minutes
        'volume'   => 500_000.0, // kg total (reps × weight)
    ];

    public static function index(array $_params, array $_body): void
    {
        $userId = Auth::requireUser();
        $rows = Goal::listFor($userId);
        Response::success(array_map([Goal::class, 'toDto'], $rows));
    }

    public static function create(array $_params, array $body): void
    {
        $userId = Auth::requireUser();

        [$title, $activityType, $goalType, $period, $targetValue,
            $periodStart, $periodEnd] = self::parseAndValidate($body);

        $id = Goal::create(
            $userId, $title, $activityType, $goalType, $period,
            $targetValue, $periodStart, $periodEnd
        );

        $row = Goal::findById($id, $userId);
        Response::success(Goal::toDto($row ?? []), 201);
    }

    public static function update(array $params, array $body): void
    {
        $userId = Auth::requireUser();
        $id = (int) $params['id'];

        // 404 for a goal that doesn't belong to this user — no distinction
        // between "not yours" and "doesn't exist".
        $existing = Goal::findById($id, $userId);
        if ($existing === null) {
            Response::error('Goal not found', 404);
        }

        // Only title / activityType / target are editable. Period + dates are
        // immutable so historical progress stays meaningful.
        $goalType = (string) $existing['goal_type'];

        // Merge current values in so unchanged fields don't have to be sent.
        $body = array_merge([
            'title'        => $existing['title'],
            'activityType' => $existing['activity_type'],
            'targetValue'  => (float) $existing['target_value'],
        ], $body);

        $v = (new Validator($body))
            ->required('title')->maxLength('title', 120)
            ->required('targetValue')->numeric('targetValue');
        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $target = (float) $body['targetValue'];
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

        $activityType = self::stringOrNull($body['activityType'] ?? null);
        self::validateActivityGoalTypeCombo($goalType, $activityType);

        Goal::update($id, $userId, (string) $body['title'], $activityType, $target);
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
     * Flip the `completed_notified` flag to 1. Called by the client the
     * first time it celebrates a goal reaching 100%, so a subsequent
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
     * @return array{0:string,1:?string,2:string,3:string,4:float,5:string,6:string}
     */
    private static function parseAndValidate(array $body): array
    {
        $v = (new Validator($body))
            ->required('title')->maxLength('title', 120)
            ->required('goalType')->inEnum('goalType', Goal::GOAL_TYPES)
            ->required('period')->inEnum('period', Goal::PERIODS)
            ->required('targetValue')->numeric('targetValue')
            ->required('periodStart')
            ->required('periodEnd');
        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $title        = trim((string) $body['title']);
        $goalType     = (string) $body['goalType'];
        $period       = (string) $body['period'];
        $target       = (float)  $body['targetValue'];
        $periodStart  = self::parseDateOrFail('periodStart', (string) $body['periodStart']);
        $periodEnd    = self::parseDateOrFail('periodEnd',   (string) $body['periodEnd']);
        $activityType = self::stringOrNull($body['activityType'] ?? null);
        if ($activityType !== null && !in_array($activityType, Goal::ACTIVITY_TYPES, true)) {
            Response::error(
                'Validation failed',
                422,
                ['activityType' => 'Must be one of: ' . implode(', ', Goal::ACTIVITY_TYPES)]
            );
        }

        // target > 0 + per-type upper bound.
        if ($target <= 0) {
            Response::error('Validation failed', 422, ['targetValue' => 'Must be greater than 0']);
        }
        if ($target > self::TARGET_MAX[$goalType]) {
            Response::error(
                'Validation failed',
                422,
                ['targetValue' => 'Must be at most ' . self::TARGET_MAX[$goalType]]
            );
        }

        // period_end > period_start (both are already valid DATE strings).
        if (!($periodEnd > $periodStart)) {
            Response::error(
                'Validation failed',
                422,
                ['periodEnd' => 'Must be after periodStart']
            );
        }

        self::validateActivityGoalTypeCombo($goalType, $activityType);

        return [$title, $activityType, $goalType, $period, $target, $periodStart, $periodEnd];
    }

    /**
     * Enforce the phase-7 rule: a distance goal never on WEIGHTLIFTING, a
     * volume goal never on RUNNING/CYCLING. Mirrors the Kotlin `init`
     * requirements in `DistanceGoal` and `StrengthVolumeGoal`.
     */
    private static function validateActivityGoalTypeCombo(string $goalType, ?string $activityType): void
    {
        if ($goalType === 'distance' && $activityType === 'WEIGHTLIFTING') {
            Response::error(
                'Validation failed',
                422,
                ['activityType' => 'A distance goal cannot be scoped to WEIGHTLIFTING']
            );
        }
        if ($goalType === 'volume' && $activityType !== null && $activityType !== 'WEIGHTLIFTING') {
            Response::error(
                'Validation failed',
                422,
                ['activityType' => 'A volume goal must be WEIGHTLIFTING or any activity']
            );
        }
    }

    private static function parseDateOrFail(string $field, string $value): string
    {
        // Accept a bare DATE (YYYY-MM-DD) or an ISO datetime — for the latter
        // we trim to the date component. This keeps the client free to send
        // whatever `LocalDate.toString()` produced without extra formatting.
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
