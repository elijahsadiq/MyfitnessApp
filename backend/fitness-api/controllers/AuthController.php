<?php
/**
 * /auth/register, /auth/login, /auth/logout.
 *
 * All three endpoints return a `{ token, user }` payload on success so the
 * client never needs a follow-up GET /me right after login.
 */
final class AuthController
{
    public static function register(array $_params, array $body): void
    {
        $v = (new Validator($body))
            ->required('email')->email('email')->maxLength('email', 191)
            ->required('password')->minLength('password', 8)->maxLength('password', 72)
            ->required('fullName')->maxLength('fullName', 120)
            ->numeric('weightKg')->inRange('weightKg', 20, 300)
            ->numeric('heightCm')->inRange('heightCm', 80, 260);

        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $email = strtolower(trim((string) $body['email']));
        if (User::findByEmail($email) !== null) {
            Response::error('Email already registered', 409, ['email' => 'Email already registered']);
        }

        $hash = password_hash((string) $body['password'], PASSWORD_BCRYPT);
        try {
            $userId = User::create(
                $email,
                (string) $body['fullName'],
                $hash,
                isset($body['weightKg']) ? (float) $body['weightKg'] : null,
                isset($body['heightCm']) ? (float) $body['heightCm'] : null
            );
        } catch (PDOException $e) {
            // Concurrent register with the same email lost the race on
            // uq_users_email (SQLSTATE 23000) — same outcome as the pre-check.
            if ($e->getCode() === '23000') {
                Response::error('Email already registered', 409, ['email' => 'Email already registered']);
            }
            throw $e;
        }

        $cfg = require __DIR__ . '/../config.php';
        $token = Auth::issueToken($userId, (int) $cfg['token_ttl_seconds']);

        Response::success([
            'token' => $token,
            'user'  => User::toDto(User::findById($userId) ?? []),
        ], 201);
    }

    public static function login(array $_params, array $body): void
    {
        $v = (new Validator($body))
            ->required('email')->email('email')
            ->required('password');
        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        $email = strtolower(trim((string) $body['email']));
        $user = User::findByEmail($email);
        // Uniform message on both branches so the response can't be used to
        // enumerate valid emails.
        if ($user === null || !password_verify((string) $body['password'], $user['password_hash'])) {
            Response::error('Invalid email or password', 401);
        }

        $cfg = require __DIR__ . '/../config.php';
        $token = Auth::issueToken((int) $user['id'], (int) $cfg['token_ttl_seconds']);

        Response::success([
            'token' => $token,
            'user'  => User::toDto($user),
        ]);
    }

    public static function logout(array $_params, array $_body): void
    {
        Auth::revokeCurrentToken();
        Response::success(null, 200);
    }
}
