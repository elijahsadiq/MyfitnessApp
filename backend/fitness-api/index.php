<?php
declare(strict_types=1);

/**
 * Front controller. Every route defined below is dispatched from here.
 * Classes are autoloaded from core/, controllers/ and models/ via a
 * two-line spl_autoload_register — no Composer, no PSR-4.
 */

spl_autoload_register(static function (string $class): void {
    foreach (['core', 'controllers', 'models'] as $dir) {
        $file = __DIR__ . "/$dir/$class.php";
        if (is_file($file)) {
            require_once $file;
            return;
        }
    }
});

// Ensure JSON everywhere. Individual Response helpers set this header too;
// this is a fallback for uncaught-error paths.
header('Content-Type: application/json; charset=utf-8');

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$requestUri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';

// Strip the app prefix so routes work whether we're mounted at / (Docker
// root docroot) or at /fitness-api/ (XAMPP htdocs).
$scriptDir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/index.php')), '/');
if ($scriptDir !== '' && $scriptDir !== '/' && str_starts_with($requestUri, $scriptDir)) {
    $requestUri = substr($requestUri, strlen($scriptDir));
}
$path = '/' . ltrim($requestUri, '/');

// CORS preflight — respond and exit.
if ($method === 'OPTIONS') {
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
    header('Access-Control-Allow-Headers: Authorization, Content-Type');
    http_response_code(204);
    exit;
}

try {
    // Decode JSON body if there is one. GET/DELETE typically have none.
    $body = [];
    $rawBody = file_get_contents('php://input');
    if ($rawBody !== false && $rawBody !== '') {
        $decoded = json_decode($rawBody, true);
        if (json_last_error() !== JSON_ERROR_NONE) {
            Response::error('Invalid JSON body', 400);
        }
        $body = is_array($decoded) ? $decoded : [];
    }

    $router = new Router();

    // Health / meta
    $router->add('GET',    '/health',           [WorkoutController::class, 'health']);

    // Auth
    $router->add('POST',   '/auth/register',    [AuthController::class, 'register']);
    $router->add('POST',   '/auth/login',       [AuthController::class, 'login']);
    $router->add('POST',   '/auth/logout',      [AuthController::class, 'logout']);

    // User
    $router->add('GET',    '/me',               [UserController::class, 'me']);
    $router->add('PUT',    '/me',               [UserController::class, 'updateMe']);

    // Workouts
    $router->add('GET',    '/workouts',         [WorkoutController::class, 'index']);
    $router->add('POST',   '/workouts',         [WorkoutController::class, 'create']);
    $router->add('GET',    '/workouts/{id}',    [WorkoutController::class, 'show']);
    $router->add('DELETE', '/workouts/{id}',    [WorkoutController::class, 'destroy']);

    // Goals (phase 7) — definitions only; progress is client-side.
    $router->add('GET',    '/goals',                [GoalController::class, 'index']);
    $router->add('POST',   '/goals',                [GoalController::class, 'create']);
    $router->add('PUT',    '/goals/{id}',           [GoalController::class, 'update']);
    $router->add('DELETE', '/goals/{id}',           [GoalController::class, 'destroy']);
    $router->add('POST',   '/goals/{id}/notified',  [GoalController::class, 'markNotified']);

    $router->dispatch($method, $path, $body);
} catch (Throwable $e) {
    // Everything unrecognised is a 500. Log the trace to Apache's error log
    // so it can be inspected via `docker compose logs -f php`.
    error_log('[fitness-api] ' . $e::class . ': ' . $e->getMessage() . "\n" . $e->getTraceAsString());
    Response::error('Internal server error', 500);
}
