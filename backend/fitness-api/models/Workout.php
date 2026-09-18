<?php
/**
 * Thin PDO data mapper for `workouts` + `strength_sets`. Every read and
 * write goes through a prepared statement and is scoped by user_id so a
 * bad request can never reveal another user's data.
 */
final class Workout
{
    /**
     * List workouts for a user, newest first, with optional filters.
     */
    public static function listFor(
        int $userId,
        ?string $type = null,
        ?string $fromIso = null,
        ?string $toIso = null
    ): array {
        $sql = 'SELECT * FROM workouts WHERE user_id = ?';
        $params = [$userId];

        if ($type !== null) {
            $sql .= ' AND activity_type = ?';
            $params[] = $type;
        }
        if ($fromIso !== null) {
            $sql .= ' AND start_time >= ?';
            $params[] = self::toMysqlDatetime($fromIso);
        }
        if ($toIso !== null) {
            $sql .= ' AND start_time <= ?';
            $params[] = self::toMysqlDatetime($toIso);
        }
        $sql .= ' ORDER BY start_time DESC';

        $stmt = Database::pdo()->prepare($sql);
        $stmt->execute($params);
        return $stmt->fetchAll();
    }

    public static function findById(int $id, int $userId): ?array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT * FROM workouts WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([$id, $userId]);
        $row = $stmt->fetch();
        return $row !== false ? $row : null;
    }

    public static function findByClientUuid(int $userId, string $clientUuid): ?array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT * FROM workouts WHERE user_id = ? AND client_uuid = ?'
        );
        $stmt->execute([$userId, $clientUuid]);
        $row = $stmt->fetch();
        return $row !== false ? $row : null;
    }

    /** Read all strength_sets for a workout, ordered as originally logged. */
    public static function setsFor(int $workoutId): array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT * FROM strength_sets WHERE workout_id = ? ORDER BY set_order ASC'
        );
        $stmt->execute([$workoutId]);
        return $stmt->fetchAll();
    }

    /**
     * Insert a workout (and its strength sets, if any) inside a transaction.
     * Returns the new workout id.
     *
     * @param array<int, array{exerciseName:string, reps:int, weightKg:float}> $sets
     */
    public static function create(
        int $userId,
        string $clientUuid,
        string $activityType,
        string $startTimeIso,
        int $durationSeconds,
        ?float $distanceMeters,
        ?string $notes,
        array $sets
    ): int {
        $pdo = Database::pdo();
        $pdo->beginTransaction();
        try {
            $stmt = $pdo->prepare(
                'INSERT INTO workouts
                    (user_id, client_uuid, activity_type, start_time,
                     duration_seconds, distance_meters, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?)'
            );
            $stmt->execute([
                $userId,
                $clientUuid,
                $activityType,
                self::toMysqlDatetime($startTimeIso),
                $durationSeconds,
                $distanceMeters,
                $notes,
            ]);
            $workoutId = (int) $pdo->lastInsertId();

            if ($activityType === 'WEIGHTLIFTING' && !empty($sets)) {
                $ss = $pdo->prepare(
                    'INSERT INTO strength_sets
                        (workout_id, set_order, exercise_name, reps, weight_kg)
                        VALUES (?, ?, ?, ?, ?)'
                );
                $order = 1;
                foreach ($sets as $set) {
                    $ss->execute([
                        $workoutId,
                        $order++,
                        (string) $set['exerciseName'],
                        (int) $set['reps'],
                        (float) $set['weightKg'],
                    ]);
                }
            }

            $pdo->commit();
            return $workoutId;
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
    }

    /** Returns true if a row was actually deleted. */
    public static function deleteFor(int $id, int $userId): bool
    {
        $stmt = Database::pdo()->prepare(
            'DELETE FROM workouts WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([$id, $userId]);
        return $stmt->rowCount() > 0;
    }

    /**
     * @param array<string,mixed>          $row
     * @param array<int, array<string,mixed>> $sets
     * @return array<string,mixed>
     */
    public static function toDto(array $row, array $sets = []): array
    {
        return [
            'id'              => (int) $row['id'],
            'clientUuid'      => $row['client_uuid'],
            'activityType'    => $row['activity_type'],
            'startTime'       => self::isoUtc((string) $row['start_time']),
            'durationSeconds' => (int) $row['duration_seconds'],
            'distanceMeters'  => $row['distance_meters'] !== null ? (float) $row['distance_meters'] : null,
            'notes'           => $row['notes'],
            'sets'            => array_map(
                static fn (array $s): array => [
                    'setOrder'     => (int) $s['set_order'],
                    'exerciseName' => $s['exercise_name'],
                    'reps'         => (int) $s['reps'],
                    'weightKg'     => (float) $s['weight_kg'],
                ],
                $sets
            ),
            'createdAt'       => self::isoUtc((string) $row['created_at']),
            'updatedAt'       => self::isoUtc((string) $row['updated_at']),
        ];
    }

    private static function toMysqlDatetime(string $iso): string
    {
        // Accept both "2026-09-15T10:30:00Z" and "2026-09-15 10:30:00". Store
        // as UTC in the DATETIME column (SET time_zone = '+00:00' in the
        // PDO init makes DATETIME reads/writes unambiguous).
        return (new DateTimeImmutable($iso))
            ->setTimezone(new DateTimeZone('UTC'))
            ->format('Y-m-d H:i:s');
    }

    private static function isoUtc(string $mysqlDatetime): string
    {
        return (new DateTimeImmutable($mysqlDatetime, new DateTimeZone('UTC')))
            ->format('Y-m-d\TH:i:s\Z');
    }
}
