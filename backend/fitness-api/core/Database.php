<?php
/**
 * PDO singleton. Every model layer method calls Database::pdo() to get the
 * shared connection. All queries go through prepared statements — there is
 * no string-concatenated SQL anywhere in this codebase.
 */
final class Database
{
    private static ?PDO $pdo = null;

    public static function pdo(): PDO
    {
        if (self::$pdo === null) {
            $cfg = require __DIR__ . '/../config.php';
            $db = $cfg['db'];
            $dsn = sprintf(
                'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
                $db['host'], $db['port'], $db['name']
            );
            self::$pdo = new PDO($dsn, $db['user'], $db['pass'], [
                PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                // EMULATE_PREPARES = false forces server-side prepares — the
                // driver won't paste values into a string on our behalf.
                PDO::ATTR_EMULATE_PREPARES   => false,
                // Force UTC + utf8mb4 for every connection so DATETIME
                // columns are unambiguous and text sorts consistently.
                PDO::MYSQL_ATTR_INIT_COMMAND =>
                    "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci, time_zone = '+00:00'",
            ]);
        }
        return self::$pdo;
    }
}
