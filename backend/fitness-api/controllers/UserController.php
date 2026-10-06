<?php
/**
 * GET /me, PUT /me, POST /me/avatar, DELETE /me/avatar, DELETE /me.
 * All require a valid Bearer token.
 *
 * Email is deliberately never updatable: an email change is an identity
 * change that needs a verification flow the project does not have. Any
 * `email` field in a PUT /me body is silently ignored rather than
 * rejected, so a client that naively re-posts the full profile object
 * doesn't trip on a 422.
 */
final class UserController
{
    private const AVATAR_MAX_BYTES = 2 * 1024 * 1024; // 2 MB
    private const AVATAR_DIM       = 512;             // re-encoded square, px
    private const AVATAR_DIR_REL   = 'uploads/avatars';
    private const AVATAR_JPEG_Q    = 85;

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
            ->required('fullName')->minLength('fullName', 2)->maxLength('fullName', 60)
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
     *     polyglot JPEG-PHP, etc.) — the stored file is bytes we wrote.
     *   - Filename is `bin2hex(random_bytes(16))` + `.jpg`. We never trust
     *     `$_FILES['file']['name']`.
     *   - Target folder has a `.htaccess` that disables PHP execution and
     *     restricts served MIME types to images.
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

        // Random filename — never reuse the client's.
        $filename = bin2hex(random_bytes(16)) . '.jpg';
        $absDir = self::absoluteAvatarDir();
        if (!is_dir($absDir)) {
            if (!mkdir($absDir, 0755, true) && !is_dir($absDir)) {
                imagedestroy($dstImg);
                Response::error('Server error', 500,
                    ['file' => 'Could not create upload directory']);
            }
        }
        $absPath = $absDir . DIRECTORY_SEPARATOR . $filename;
        if (!imagejpeg($dstImg, $absPath, self::AVATAR_JPEG_Q)) {
            imagedestroy($dstImg);
            Response::error('Server error', 500,
                ['file' => 'Could not store image']);
        }
        imagedestroy($dstImg);

        // Delete the previous avatar (if any) once the new one is on disk.
        $existing = User::findById($id);
        if ($existing !== null && !empty($existing['avatar_path'])) {
            self::unlinkAvatarIfPresent((string) $existing['avatar_path']);
        }

        $relative = self::AVATAR_DIR_REL . '/' . $filename;
        User::updateAvatarPath($id, $relative);
        Response::success(User::toDto(User::findById($id) ?? []));
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
            self::unlinkAvatarIfPresent((string) $row['avatar_path']);
            User::updateAvatarPath($id, null);
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
     * route_points), and goals when the user row goes. The disk-side
     * avatar file is removed first so a transaction rollback doesn't
     * leave an orphaned image.
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

        $row = User::findById($id);
        $avatarPath = $row['avatar_path'] ?? null;

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

        // Avatar removed AFTER the DB commit — safe to delete the file now
        // that the row referencing it is gone.
        if (is_string($avatarPath) && $avatarPath !== '') {
            self::unlinkAvatarIfPresent($avatarPath);
        }

        Auth::revokeCurrentToken();
        Response::success(null, 200);
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    private static function absoluteAvatarDir(): string
    {
        // `fitness-api/` is index.php's directory. The avatar folder lives
        // alongside controllers / models — a plain subdirectory of the
        // document root — so a reverse-proxy or sub-folder XAMPP install
        // still serves the file correctly.
        return dirname(__DIR__) . DIRECTORY_SEPARATOR . 'uploads' . DIRECTORY_SEPARATOR . 'avatars';
    }

    private static function unlinkAvatarIfPresent(string $relative): void
    {
        // Only delete files inside our own avatar dir; never trust the
        // stored path blindly in case a bug ever persisted something odd.
        $expectedPrefix = self::AVATAR_DIR_REL . '/';
        if (!str_starts_with($relative, $expectedPrefix)) {
            return;
        }
        $filename = basename($relative);
        $abs = self::absoluteAvatarDir() . DIRECTORY_SEPARATOR . $filename;
        if (is_file($abs)) {
            @unlink($abs);
        }
    }
}
