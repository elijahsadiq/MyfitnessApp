<?php
/**
 * Bearer-token authentication.
 *
 * Tokens are 64-char hex strings generated with random_bytes(32). Only the
 * SHA-256 hash is stored in `auth_tokens`, so a database leak alone doesn't
 * yield valid credentials.
 */
final class Auth
{
    /**
     * Issue and persist a new token for the user. Returns the opaque token
     * the client should send back in the `Authorization: Bearer …` header.
     */
    public static function issueToken(int $userId, int $ttlSeconds): string
    {
        $token = bin2hex(random_bytes(32));
        $hash = hash('sha256', $token);
        $expires = gmdate('Y-m-d H:i:s', time() + $ttlSeconds);

        // Housekeeping: drop this user's expired tokens so the table does
        // not grow without bound (one cheap indexed DELETE per login).
        $prune = Database::pdo()->prepare(
            'DELETE FROM auth_tokens WHERE user_id = ? AND expires_at < UTC_TIMESTAMP()'
        );
        $prune->execute([$userId]);

        $stmt = Database::pdo()->prepare(
            'INSERT INTO auth_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)'
        );
        $stmt->execute([$userId, $hash, $expires]);

        return $token;
    }

    /**
     * Return the authenticated user id, or null if the request has no token,
     * an unrecognised token, or an expired token.
     */
    public static function currentUserId(): ?int
    {
        $header = self::authHeader();
        if ($header === null) return null;
        if (!preg_match('/^Bearer\s+([a-f0-9]{64})$/i', $header, $m)) return null;

        $hash = hash('sha256', strtolower($m[1]));
        $stmt = Database::pdo()->prepare(
            'SELECT user_id, expires_at FROM auth_tokens
              WHERE token_hash = ? AND expires_at > UTC_TIMESTAMP()
              LIMIT 1'
        );
        $stmt->execute([$hash]);
        $row = $stmt->fetch();
        if ($row === false) return null;

        // Sliding expiry: an actively used token never hits the 30-day
        // cliff (which would sign the app out and discard workouts logged
        // offline that hadn't synced yet). Only rewrite the row once the
        // token is into the second half of its life, so most requests
        // cost no UPDATE.
        $cfg = require __DIR__ . '/../config.php';
        $ttl = (int) $cfg['token_ttl_seconds'];
        $expiresAt = strtotime(((string) $row['expires_at']) . ' UTC');
        if ($expiresAt !== false && ($expiresAt - time()) < ($ttl / 2)) {
            $bump = Database::pdo()->prepare(
                'UPDATE auth_tokens SET expires_at = ? WHERE token_hash = ?'
            );
            $bump->execute([gmdate('Y-m-d H:i:s', time() + $ttl), $hash]);
        }

        return (int) $row['user_id'];
    }

    /** Convenience wrapper that emits 401 + exits when there is no valid user. */
    public static function requireUser(): int
    {
        $id = self::currentUserId();
        if ($id === null) {
            Response::error('Not authenticated', 401);
        }
        return $id;
    }

    /** Delete the row for the current request's token, if one exists. */
    public static function revokeCurrentToken(): void
    {
        $header = self::authHeader();
        if ($header === null) return;
        if (!preg_match('/^Bearer\s+([a-f0-9]{64})$/i', $header, $m)) return;
        $hash = hash('sha256', strtolower($m[1]));
        $stmt = Database::pdo()->prepare('DELETE FROM auth_tokens WHERE token_hash = ?');
        $stmt->execute([$hash]);
    }

    /**
     * Read the Authorization header from whichever place Apache made it
     * available. Some Apache configurations strip it into REDIRECT_… and
     * getallheaders() isn't guaranteed to exist under FastCGI.
     */
    private static function authHeader(): ?string
    {
        if (function_exists('getallheaders')) {
            foreach (getallheaders() as $k => $v) {
                if (strcasecmp($k, 'Authorization') === 0) {
                    return $v;
                }
            }
        }
        return $_SERVER['HTTP_AUTHORIZATION']
            ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
            ?? null;
    }
}
