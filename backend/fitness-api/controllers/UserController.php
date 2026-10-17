<?php
/**
 * GET /me, PUT /me, POST /me/avatar, DELETE /me/avatar, DELETE /me —
 * all require a valid Bearer token. GET /avatars/{file} is public, like
 * any profile picture on the web.
 *
 * Email is deliberately never updatable: an email change is an identity
 * change that needs a verification flow the project does not have. Any
 * `email` field in a PUT /me body is silently ignored rather than
 * rejected, so a client that naively re-posts the full profile object
 * doesn't trip on a 422.
 */
final class UserController
{
    private const AVATAR_MAX_BYTES  = 2 * 1024 * 1024; // 2 MB
    private const AVATAR_DIM        = 512;             // re-encoded square, px
    private const AVATAR_URL_PREFIX = 'avatars/';      // served by avatarImage()
    private const AVATAR_JPEG_Q     = 85;

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
        // `email` is NEVER applied even if present in the body — silently
        // dropped (no 422) so naive clients re-posting a profile object
        // don't trip. Documented in the file-level comment.
        $v = (new Validator($body))
            ->required('fullName')->minLength('fullName', 2)->maxLength('fullName', 120)
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

    /**
     * POST /me/avatar — multipart/form-data with a single `file` part.
     *
     * Security notes (worth copying to the written report):
     *   - MIME is read from `getimagesize()`, NOT from `$_FILES['file']['type']`
     *     and NOT from the client-supplied filename extension. getimagesize
     *     opens the file and parses magic bytes, so a `.jpg` with a PHP
     *     payload inside is identified as `image/*` only if it really is one.
     *   - The uploaded bytes are re-encoded through GD into a fresh JPEG.
     *     Re-encoding strips any payload hidden inside the file (EXIF,
     *     polyglot JPEG-PHP, etc.) — the stored image is bytes we wrote.
     *   - The JPEG is stored in MySQL, never in the web root, so there is
     *     no uploaded file Apache could ever be tricked into executing.
     *     (It also survives restarts on hosts like Render, whose disk is
     *     wiped every time the server restarts.)
     *   - The public path is `avatars/` + `bin2hex(random_bytes(16))` +
     *     `.jpg`: unguessable, new on every upload. We never trust
     *     `$_FILES['file']['name']`.
     *   - 2 MB cap enforced BEFORE reading pixels, so an attacker cannot
     *     exhaust memory just by posting a huge file.
     */
    public static function uploadAvatar(array $_params, array $_body): void
    {
        $id = Auth::requireUser();

        $file = $_FILES['file'] ?? null;
        if (!is_array($file) || !isset($file['tmp_name']) || $file['tmp_name'] === '') {
            Response::error('Validation failed', 422,
                ['file' => 'Missing file part']);
        }
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
            Response::error('Validation failed', 422,
                ['file' => 'Upload failed (code ' . (int) $file['error'] . ')']);
        }
        if (!is_uploaded_file($file['tmp_name'])) {
            Response::error('Validation failed', 422,
                ['file' => 'Upload rejected']);
        }
        if ((int) $file['size'] > self::AVATAR_MAX_BYTES) {
            Response::error('Validation failed', 422,
                ['file' => 'File too large (max 2 MB)']);
        }

        // Inspect the file itself — do NOT trust the client's declared type.
        $info = @getimagesize($file['tmp_name']);
        if ($info === false) {
            Response::error('Validation failed', 422,
                ['file' => 'Not a readable image']);
        }
        // Cap pixel dimensions BEFORE decoding: a 2 MB "PNG bomb" can
        // decompress to gigabytes of pixel data and kill the PHP worker.
        $pixelW = (int) ($info[0] ?? 0);
        $pixelH = (int) ($info[1] ?? 0);
        if ($pixelW < 1 || $pixelH < 1 || ($pixelW * $pixelH) > 25_000_000) {
            Response::error('Validation failed', 422,
                ['file' => 'Image dimensions too large (max 25 megapixels)']);
        }
        $imageType = (int) ($info[2] ?? 0);
        $sourceImg = null;
        switch ($imageType) {
            case IMAGETYPE_JPEG:
                $sourceImg = @imagecreatefromjpeg($file['tmp_name']);
                break;
            case IMAGETYPE_PNG:
                $sourceImg = @imagecreatefrompng($file['tmp_name']);
                break;
            default:
                Response::error('Validation failed', 422,
                    ['file' => 'Only JPEG or PNG is accepted']);
        }
        if (!$sourceImg) {
            Response::error('Validation failed', 422,
                ['file' => 'Could not decode image']);
        }

        $srcW = imagesx($sourceImg);
        $srcH = imagesy($sourceImg);

        // Centre-crop to a square, then scale to AVATAR_DIM.
        $size = min($srcW, $srcH);
        $srcX = (int) (($srcW - $size) / 2);
        $srcY = (int) (($srcH - $size) / 2);

        $dstImg = imagecreatetruecolor(self::AVATAR_DIM, self::AVATAR_DIM);
        imagecopyresampled(
            $dstImg, $sourceImg,
            0, 0, $srcX, $srcY,
            self::AVATAR_DIM, self::AVATAR_DIM,
            $size, $size,
        );
        imagedestroy($sourceImg);

        // Encode into memory; the bytes go into the user's row.
        ob_start();
        $encoded = imagejpeg($dstImg, null, self::AVATAR_JPEG_Q);
        $jpeg = (string) ob_get_clean();
        imagedestroy($dstImg);
        if (!$encoded || $jpeg === '') {
            Response::error('Server error', 500,
                ['file' => 'Could not store image']);
        }

        // Random name — never the client's. A new name on every upload
        // also means no cache can ever show the previous photo.
        $path = self::AVATAR_URL_PREFIX . bin2hex(random_bytes(16)) . '.jpg';
        User::updateAvatar($id, $path, $jpeg);
        Response::success(User::toDto(User::findById($id) ?? []));
    }

    /**
     * GET /avatars/{file} — streams a stored avatar. Public on purpose:
     * the app's image loader sends no token, and the 128-bit random name
     * is what keeps the URL private.
     */
    public static function avatarImage(array $params, array $_body): void
    {
        $file = (string) ($params['file'] ?? '');
        if (preg_match('/^[a-f0-9]{32}\.jpg$/', $file) !== 1) {
            Response::error('Not found', 404);
        }
        $jpeg = User::avatarJpegByPath(self::AVATAR_URL_PREFIX . $file);
        if ($jpeg === null) {
            Response::error('Not found', 404);
        }

        http_response_code(200);
        header('Content-Type: image/jpeg');
        header('Content-Length: ' . strlen($jpeg));
        header('X-Content-Type-Options: nosniff');
        // The bytes behind a given name never change (a new upload gets a
        // new name), so clients may cache the image for a year.
        header('Cache-Control: public, max-age=31536000, immutable');
        echo $jpeg;
        exit;
    }

    public static function deleteAvatar(array $_params, array $_body): void
    {
        $id = Auth::requireUser();
        $row = User::findById($id);
        if ($row === null) {
            Auth::revokeCurrentToken();
            Response::error('User not found', 404);
        }
        if (!empty($row['avatar_path'])) {
            User::updateAvatar($id, null, null);
        }
        Response::success(User::toDto(User::findById($id) ?? []));
    }

    /**
     * DELETE /me — permanent account removal. Requires the user to pass
     * `password` in the body; verified with `password_verify` before any
     * destructive action. Executes inside a transaction so a failure
     * mid-way leaves the account intact.
     *
     * Cascade story: the FKs declared in phase-1 + phase-7 + phase-8a
     * clean up auth_tokens, workouts (and their strength_sets +
     * route_points), and goals when the user row goes. The avatar lives
     * in the user row itself, so it goes too.
     */
    public static function deleteMe(array $_params, array $body): void
    {
        $id = Auth::requireUser();

        $password = $body['password'] ?? null;
        if (!is_string($password) || $password === '') {
            Response::error('Validation failed', 422,
                ['password' => 'Current password is required']);
        }
        $hash = User::passwordHashFor($id);
        if ($hash === null) {
            Auth::revokeCurrentToken();
            Response::error('User not found', 404);
        }
        if (!password_verify((string) $password, $hash)) {
            Response::error('Password did not match', 403,
                ['password' => 'Password did not match']);
        }

        $pdo = Database::pdo();
        $pdo->beginTransaction();
        try {
            $deleted = User::deleteById($id);
            if (!$deleted) {
                throw new RuntimeException('User row vanished mid-transaction');
            }
            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }

        Auth::revokeCurrentToken();
        Response::success(null, 200);
    }
}
