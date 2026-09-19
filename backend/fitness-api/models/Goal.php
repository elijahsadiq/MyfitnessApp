<?php
/**
 * Thin PDO data mapper for `goals`. Every read and write is scoped by
 * user_id so a bad request can never reveal or mutate another user's row.
 *
 * The server stores goal *definitions* only. Progress is computed client
 * side in Kotlin (see phase-7 brief and `GoalProgressCalculator`), so
 * this class never joins workouts.
 */
final class Goal
{
    public const GOAL_TYPES     = ['distance', 'count', 'duration', 'volume'];
    public const PERIODS        = ['WEEK', 'MONTH'];
    public const ACTIVITY_TYPES = ['RUNNING', 'CYCLING', 'WEIGHTLIFTING'];

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

    public static function create(
        int $userId,
        string $title,
        ?string $activityType,
        string $goalType,
        string $period,
        float $targetValue,
        string $periodStartDate,
        string $periodEndDate
    ): int {
        $pdo = Database::pdo();
        $stmt = $pdo->prepare(
            'INSERT INTO goals
                (user_id, title, activity_type, goal_type, period,
                 target_value, period_start, period_end)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $userId,
            $title,
            $activityType, // null is fine — column is NULL-able
            $goalType,
            $period,
            $targetValue,
            $periodStartDate,
            $periodEndDate,
        ]);
        return (int) $pdo->lastInsertId();
    }

    /**
     * Update the fields the client is allowed to change after creation.
     * Period + dates are immutable — changing them would invalidate the
     * client-side progress that was computed against the original window.
     *
     * @return bool true if a row was actually updated.
     */
    public static function update(
        int $id,
        int $userId,
        string $title,
        ?string $activityType,
        float $targetValue
    ): bool {
        $stmt = Database::pdo()->prepare(
            'UPDATE goals
                SET title = ?, activity_type = ?, target_value = ?
              WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([$title, $activityType, $targetValue, $id, $userId]);
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
            'period'             => (string) $row['period'],
            'targetValue'        => (float) $row['target_value'],
            'periodStart'        => (string) $row['period_start'], // YYYY-MM-DD
            'periodEnd'          => (string) $row['period_end'],
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
