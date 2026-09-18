<?php
/**
 * A ~30-line HTTP router that maps `METHOD /path/pattern` to a callable.
 *
 * Patterns may include `{name}` segments which become named regex groups
 * and get passed to the handler as an associative array of path
 * parameters. Everything else (method mismatch, no match) falls through
 * to a 404.
 */
final class Router
{
    /** @var array<int, array{method:string, regex:string, handler:callable|array}> */
    private array $routes = [];

    public function add(string $method, string $pattern, callable|array $handler): void
    {
        $regex = preg_replace('/\{(\w+)\}/', '(?<$1>[^/]+)', $pattern);
        $this->routes[] = [
            'method' => strtoupper($method),
            'regex'  => '#^' . $regex . '$#',
            'handler' => $handler,
        ];
    }

    /**
     * @param array<string,mixed> $body Decoded JSON request body.
     */
    public function dispatch(string $method, string $path, array $body): void
    {
        $method = strtoupper($method);
        $normalized = '/' . ltrim($path, '/');

        foreach ($this->routes as $route) {
            if ($route['method'] !== $method) continue;
            if (preg_match($route['regex'], $normalized, $m)) {
                $params = [];
                foreach ($m as $k => $v) {
                    if (!is_int($k)) $params[$k] = $v;
                }
                call_user_func($route['handler'], $params, $body);
                return;
            }
        }
        Response::error('Not found', 404);
    }
}
