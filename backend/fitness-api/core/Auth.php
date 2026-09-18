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
            'SELECT user_id FROM auth_tokens
              WHERE token_hash = ? AND expires_at > UTC_TIMESTAMP()
              LIMIT 1'
        );
        $stmt->execute([$hash]);
        $row = $stmt->fetch();
        return $row !== false ? (int) $row['user_id'] : null;
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
