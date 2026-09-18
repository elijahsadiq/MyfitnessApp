<?php
/**
 * GET /me, PUT /me. Both require a valid Bearer token.
 */
final class UserController
{
    public static function me(array $_params, array $_body): void
    {
        $id = Auth::requireUser();
        $row = User::findById($id);
        if ($row === null) {
            // Token was valid but the user has been deleted since — clean up.
            Auth::revokeCurrentToken();
            Response::error('User not found', 404);
        }
        Response::success(User::toDto($row));
    }

    public static function updateMe(array $_params, array $body): void
    {
        $id = Auth::requireUser();
        $v = (new Validator($body))
            ->required('fullName')->maxLength('fullName', 120)
            ->numeric('weightKg')->inRange('weightKg', 20, 300)
            ->numeric('heightCm')->inRange('heightCm', 80, 260);
        if ($v->fails()) {
            Response::error('Validation failed', 422, $v->errors());
        }

        User::update(
            $id,
            (string) $body['fullName'],
            isset($body['weightKg']) ? (float) $body['weightKg'] : null,
            isset($body['heightCm']) ? (float) $body['heightCm'] : null
        );
        Response::success(User::toDto(User::findById($id) ?? []));
    }
}
