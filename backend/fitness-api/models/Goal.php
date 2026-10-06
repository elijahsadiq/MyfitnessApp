<?php
/**
 * Thin PDO data mapper for `goals`. Every read and write is scoped by
 * user_id so a bad request can never reveal or mutate another user's row.
 *
 * The server stores goal *definitions* only. Progress is computed client
 * side in Kotlin (see phase-7 brief and `GoalProgressCalculator`), so
 * this class never joins workouts.
 *
 * Phase-8b shape: goals cover a chosen number of days starting on
 * `start_date`, with `last_day` as the LAST day included and
 * `duration_days` as the span. `is_main` picks a single "featured" goal
 * per user (enforced via a transaction in setAsMain).
 */
final class Goal
{
    public const GOAL_TYPES     = ['distance', 'count', 'duration', 'volume'];
    public const ACTIVITY_TYPES = ['RUNNING', 'CYCLING', 'WEIGHTLIFTING'];
    public const DURATION_MIN   = 1;
    public const DURATION_MAX   = 365;

    /** Newest first. */
    public static function listFor(int $userId): array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT * FROM goals WHERE user_id = ? ORDER BY created_at DESC, id DESC'
        );
        $stmt->execute([$userId]);
        return $stmt->fetchAll();
    }

    public static function findById(int $id, int $userId): ?array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT * FROM goals WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([$id, $userId]);
        $row = $stmt->fetch();
        return $row !== false ? $row : null;
    }

    /**
     * Insert a new goal. `lastDate` is pre-computed by the controller as
     * `startDate + durationDays - 1` so the server does the arithmetic
     * once and the client just reads it back on the GET.
     */
    public static function create(
        int $userId,
        string $title,
        ?string $activityType,
        string $goalType,
        float $targetValue,
        string $startDate,
        string $lastDate,
        int $durationDays
    ): int {
        $pdo = Database::pdo();
        $stmt = $pdo->prepare(
            'INSERT INTO goals
                (user_id, title, activity_type, goal_type,
                 target_value, start_date, last_day, duration_days)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $userId,
            $title,
            $activityType, // null is fine — column is NULL-able
            $goalType,
            $targetValue,
            $startDate,
            $lastDate,
            $durationDays,
        ]);
        return (int) $pdo->lastInsertId();
    }

    /**
     * Update the fields the client is allowed to change after creation.
     * The `start_date` never changes (changing it would silently
     * invalidate the client-side progress computed against the original
     * window). `durationDays` can grow or shrink; the controller
     * recomputes `last_day` from the new span.
     */
    public static function update(
        int $id,
        int $userId,
        string $title,
        ?string $activityType,
        string $goalType,
        float $targetValue,
        int $durationDays,
        string $lastDate
    ): bool {
        $stmt = Database::pdo()->prepare(
            'UPDATE goals
                SET title = ?, activity_type = ?, goal_type = ?,
                    target_value = ?, duration_days = ?, last_day = ?
              WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([
            $title, $activityType, $goalType,
            $targetValue, $durationDays, $lastDate,
            $id, $userId,
        ]);
        return $stmt->rowCount() > 0;
    }

    /**
     * Make this goal the main goal, clearing is_main on every other goal
     * owned by the same user — all inside a single transaction so the
     * "at most one main per user" invariant holds even under concurrent
     * requests.
     */
    public static function setAsMain(int $id, int $userId): bool
    {
        $pdo = Database::pdo();
        $pdo->beginTransaction();
        try {
            // Verify ownership first; a 404 is cheaper to return than a
            // transaction we have to roll back.
            $stmt = $pdo->prepare(
                'SELECT id FROM goals WHERE id = ? AND user_id = ?'
            );
            $stmt->execute([$id, $userId]);
            if ($stmt->fetch() === false) {
                $pdo->rollBack();
                return false;
            }

            $pdo->prepare(
                'UPDATE goals SET is_main = 0 WHERE user_id = ? AND is_main = 1'
            )->execute([$userId]);

            $pdo->prepare(
                'UPDATE goals SET is_main = 1 WHERE id = ? AND user_id = ?'
            )->execute([$id, $userId]);

            $pdo->commit();
            return true;
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
    }

    /**
     * Extend `duration_days` by [$days] and recompute `last_day`.
     * Returns the new row on success. The controller enforces the
     * ≤ 365-day ceiling and the "not ended" guard before calling this.
     */
    public static function extend(int $id, int $userId, int $days): bool
    {
        $stmt = Database::pdo()->prepare(
            'UPDATE goals
                SET duration_days = duration_days + ?,
                    last_day      = DATE_ADD(last_day, INTERVAL ? DAY)
              WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([$days, $days, $id, $userId]);
        return $stmt->rowCount() > 0;
    }

    /** Flip the "we have celebrated this goal already" flag. */
    public static function markCompletedNotified(int $id, int $userId): bool
    {
        $stmt = Database::pdo()->prepare(
            'UPDATE goals SET completed_notified = 1 WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([$id, $userId]);
        return $stmt->rowCount() > 0;
    }

    public static function deleteFor(int $id, int $userId): bool
    {
        $stmt = Database::pdo()->prepare(
            'DELETE FROM goals WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([$id, $userId]);
        return $stmt->rowCount() > 0;
    }

    /**
     * @param array<string,mixed> $row
     * @return array<string,mixed>
     */
    public static function toDto(array $row): array
    {
        return [
            'id'                 => (int) $row['id'],
            'title'              => (string) $row['title'],
            'activityType'       => $row['activity_type'], // null passes through
            'goalType'           => (string) $row['goal_type'],
            'targetValue'        => (float) $row['target_value'],
            'startDate'          => (string) $row['start_date'],       // YYYY-MM-DD
            'lastDay'            => (string) $row['last_day'],         // inclusive
            'durationDays'       => (int) $row['duration_days'],
            'isMain'             => ((int) $row['is_main']) === 1,
            'completedNotified'  => ((int) $row['completed_notified']) === 1,
            'createdAt'          => self::isoUtc((string) $row['created_at']),
            'updatedAt'          => self::isoUtc((string) $row['updated_at']),
        ];
    }

    private static function isoUtc(string $mysqlDatetime): string
    {
        return (new DateTimeImmutable($mysqlDatetime, new DateTimeZone('UTC')))
            ->format('Y-m-d\TH:i:s\Z');
    }
}
