<?php
/**
 * Runtime config. Reads env vars if present (Docker Compose passes them
 * to the PHP container) and falls back to XAMPP defaults (root / no
 * password) so the marker can drop this straight into htdocs.
 */
return [
    'db' => [
        'host' => getenv('DB_HOST') ?: 'localhost',
        'port' => (int) (getenv('DB_PORT') ?: 3306),
        'name' => getenv('DB_NAME') ?: 'fitness_app',
        'user' => getenv('DB_USER') ?: 'root',
        'pass' => getenv('DB_PASS') !== false ? getenv('DB_PASS') : '',
    ],
    // Bearer tokens live 30 days by default.
    'token_ttl_seconds' => 60 * 60 * 24 * 30,
];
