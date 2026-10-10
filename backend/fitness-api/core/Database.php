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
            $options = [
                PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                // EMULATE_PREPARES = false forces server-side prepares — the
                // driver won't paste values into a string on our behalf.
                PDO::ATTR_EMULATE_PREPARES   => false,
                // Force UTC + utf8mb4 + MySQL 8's default SQL mode for every
                // connection: DATETIME columns are unambiguous, text sorts
                // consistently, and SQL behaves the same on every host.
                // (Aiven defaults to ANSI mode, where "x" names a column and
                // || joins strings.)
                PDO::MYSQL_ATTR_INIT_COMMAND =>
                    "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci, time_zone = '+00:00', "
                    . "sql_mode = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,"
                    . "NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'",
            ];
            // Cloud database: encrypt the connection AND verify the
            // server's certificate against the provider's CA, so the API
            // can't be tricked into sending queries to an impostor.
            if (!empty($db['ssl_ca'])) {
                $options[PDO::MYSQL_ATTR_SSL_CA] = $db['ssl_ca'];
                $options[PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT] = true;
            }
            self::$pdo = new PDO($dsn, $db['user'], $db['pass'], $options);
        }
        return self::$pdo;
    }
}
