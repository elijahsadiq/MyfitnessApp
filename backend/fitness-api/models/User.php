<?php
/**
 * Thin PDO data mapper for the `users` table. Every method uses prepared
 * statements; nothing here builds SQL by concatenating input.
 */
final class User
{
    public static function findById(int $id): ?array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT id, email, full_name, weight_kg, height_cm, avatar_path, created_at
               FROM users WHERE id = ?'
        );
        $stmt->execute([$id]);
        $row = $stmt->fetch();
        return $row !== false ? $row : null;
    }

    public static function findByEmail(string $email): ?array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT id, email, password_hash, full_name, weight_kg, height_cm, avatar_path, created_at
               FROM users WHERE email = ?'
        );
        $stmt->execute([$email]);
        $row = $stmt->fetch();
        return $row !== false ? $row : null;
    }

    public static function create(
        string $email,
        string $fullName,
        string $passwordHash,
        ?float $weightKg,
        ?float $heightCm
    ): int {
        $stmt = Database::pdo()->prepare(
            'INSERT INTO users (email, full_name, password_hash, weight_kg, height_cm)
                  VALUES (?, ?, ?, ?, ?)'
        );
        $stmt->execute([$email, $fullName, $passwordHash, $weightKg, $heightCm]);
        return (int) Database::pdo()->lastInsertId();
    }

    public static function update(int $id, string $fullName, ?float $weightKg, ?float $heightCm): void
    {
        $stmt = Database::pdo()->prepare(
            'UPDATE users SET full_name = ?, weight_kg = ?, height_cm = ? WHERE id = ?'
        );
        $stmt->execute([$fullName, $weightKg, $heightCm, $id]);
    }

    /**
     * Set or clear the avatar: the public path the app loads and the JPEG
     * bytes behind it. Pass null for both to remove the photo.
     */
    public static function updateAvatar(int $id, ?string $path, ?string $jpeg): void
    {
        $stmt = Database::pdo()->prepare(
            'UPDATE users SET avatar_path = ?, avatar_jpeg = ? WHERE id = ?'
        );
        $stmt->bindValue(1, $path, $path === null ? PDO::PARAM_NULL : PDO::PARAM_STR);
        $stmt->bindValue(2, $jpeg, $jpeg === null ? PDO::PARAM_NULL : PDO::PARAM_LOB);
        $stmt->bindValue(3, $id, PDO::PARAM_INT);
        $stmt->execute();
    }

    /** JPEG bytes stored under a public avatar path, or null if none. */
    public static function avatarJpegByPath(string $path): ?string
    {
        $stmt = Database::pdo()->prepare(
            'SELECT avatar_jpeg FROM users WHERE avatar_path = ? AND avatar_jpeg IS NOT NULL'
        );
        $stmt->execute([$path]);
        $jpeg = $stmt->fetchColumn();
        return is_string($jpeg) ? $jpeg : null;
    }

    /**
     * Delete the account row. Phase 1's FKs cascade through auth_tokens,
     * workouts (then strength_sets + route_points), and goals, so a
     * single row delete tears down the whole user — including the
     * avatar, which is stored in the row itself.
     */
    public static function deleteById(int $id): bool
    {
        $stmt = Database::pdo()->prepare('DELETE FROM users WHERE id = ?');
        $stmt->execute([$id]);
        return $stmt->rowCount() > 0;
    }

    /**
     * Convert a row into the DTO shape the app consumes. Password hash is
     * intentionally NEVER included.
     *
     * @param array<string,mixed> $row
     * @return array<string,mixed>
     */
    public static function toDto(array $row): array
    {
        return [
            'id'         => (int) $row['id'],
            'email'      => $row['email'],
            'fullName'   => $row['full_name'],
            'weightKg'   => $row['weight_kg'] !== null ? (float) $row['weight_kg'] : null,
            'heightCm'   => $row['height_cm'] !== null ? (float) $row['height_cm'] : null,
            'avatarPath' => $row['avatar_path'] ?? null,
            'createdAt'  => self::isoUtc((string) $row['created_at']),
        ];
    }

    private static function isoUtc(string $mysqlDatetime): string
    {
        return (new DateTimeImmutable($mysqlDatetime, new DateTimeZone('UTC')))
            ->format('Y-m-d\TH:i:s\Z');
    }
}
