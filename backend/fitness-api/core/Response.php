<?php
/**
 * JSON response envelope shared by every endpoint.
 *
 * Success shape: {"success": true,  "data": ...}
 * Error shape:   {"success": false, "error": {"message": ..., "fields": {...}}}
 *
 * Every helper calls exit — controllers never fall through after emitting
 * a response.
 */
final class Response
{
    public static function success(mixed $data = null, int $status = 200): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(
            ['success' => true, 'data' => $data],
            JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
        );
        exit;
    }

    /**
     * @param array<string,string> $fields Per-field validation messages.
     */
    public static function error(string $message, int $status, array $fields = []): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        $error = ['message' => $message];
        if (!empty($fields)) {
            $error['fields'] = $fields;
        }
        echo json_encode(
            ['success' => false, 'error' => $error],
            JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
        );
        exit;
    }
}
