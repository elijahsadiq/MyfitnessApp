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
            'SELECT id, email, full_name, weight_kg, height_cm, created_at
               FROM users WHERE id = ?'
        );
        $stmt->execute([$id]);
        $row = $stmt->fetch();
        return $row !== false ? $row : null;
    }

    public static function findByEmail(string $email): ?array
    {
        $stmt = Database::pdo()->prepare(
            'SELECT id, email, password_hash, full_name, weight_kg, height_cm, created_at
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
     * Convert a row into the DTO shape the app consumes. Password hash is
     * intentionally NEVER included.
     *
     * @param array<string,mixed> $row
     * @return array<string,mixed>
     */
    public static function toDto(array $row): array
    {
        return [
            'id'        => (int) $row['id'],
            'email'     => $row['email'],
            'fullName'  => $row['full_name'],
            'weightKg'  => $row['weight_kg'] !== null ? (float) $row['weight_kg'] : null,
            'heightCm'  => $row['height_cm'] !== null ? (float) $row['height_cm'] : null,
            'createdAt' => self::isoUtc((string) $row['created_at']),
        ];
    }

    private static function isoUtc(string $mysqlDatetime): string
    {
        return (new DateTimeImmutable($mysqlDatetime, new DateTimeZone('UTC')))
            ->format('Y-m-d\TH:i:s\Z');
    }
}
