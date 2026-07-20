<?php
/**
 * SQLite bootstrap for the SCP Reader friends backend.
 *
 * No secrets live here — the app never ships DB credentials. The SQLite file is created
 * next to these scripts on first run. If you'd rather use MySQL, swap the DSN below; the
 * schema is standard SQL.
 *
 * Keep this file (and the .sqlite it creates) OUTSIDE your public web root if your host
 * allows it, or at minimum rely on the .htaccess shipped alongside to block direct access
 * to the database file.
 */

// Store the DB one level above the scripts when possible so it isn't web-served directly.
$dbFile = __DIR__ . '/data/friends.sqlite';

function db(): PDO
{
    global $dbFile;
    static $pdo = null;
    if ($pdo !== null) {
        return $pdo;
    }
    $dir = dirname($dbFile);
    if (!is_dir($dir)) {
        mkdir($dir, 0700, true);
    }
    $pdo = new PDO('sqlite:' . $dbFile);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $pdo->exec('PRAGMA journal_mode = WAL;');
    $pdo->exec('PRAGMA foreign_keys = ON;');
    init_schema($pdo);
    return $pdo;
}

function init_schema(PDO $pdo): void
{
    $pdo->exec(file_get_contents(__DIR__ . '/schema.sql'));
}

/** Read + decode the JSON request body; returns [] when empty/invalid. */
function body(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || $raw === '') {
        return [];
    }
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

/** Send a JSON response and stop. */
function respond(int $status, array $payload): void
{
    http_response_code($status);
    header('Content-Type: application/json');
    echo json_encode($payload);
    exit;
}

/**
 * Read the Authorization header. Apache/CGI setups vary in where they expose it — many shared
 * hosts drop it from $_SERVER['HTTP_AUTHORIZATION'] entirely, so we check every known location.
 */
function bearer_header(): string
{
    if (!empty($_SERVER['HTTP_AUTHORIZATION'])) {
        return $_SERVER['HTTP_AUTHORIZATION'];
    }
    if (!empty($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        return $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
    }
    if (function_exists('apache_request_headers')) {
        foreach (apache_request_headers() as $k => $v) {
            if (strcasecmp($k, 'Authorization') === 0) {
                return $v;
            }
        }
    }
    return '';
}

/**
 * Pull the bearer token, look it up, and return the device row — or 401 out.
 * The token is generated on-device and is the ONLY credential; treat it like a password.
 */
function require_device(PDO $pdo): array
{
    $auth = bearer_header();
    if (stripos($auth, 'Bearer ') !== 0) {
        respond(401, ['error' => 'missing token']);
    }
    $token = trim(substr($auth, 7));
    if (!preg_match('/^[A-Za-z0-9\-]{16,64}$/', $token)) {
        respond(401, ['error' => 'bad token']);
    }
    $stmt = $pdo->prepare('SELECT * FROM devices WHERE token = ?');
    $stmt->execute([$token]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) {
        respond(401, ['error' => 'unknown token']);
    }
    // Touch last_seen so you can prune dormant devices later if you ever want to.
    $pdo->prepare('UPDATE devices SET last_seen = ? WHERE token = ?')
        ->execute([time(), $token]);
    return $row;
}

/**
 * Very small per-token rate limit: at most $max writes per $window seconds.
 * Cheap abuse guard for an anonymous, open-source backend — not a security boundary.
 */
function rate_limit(PDO $pdo, string $token, string $bucket, int $max, int $window): void
{
    $now = time();
    $pdo->prepare('DELETE FROM rate_limits WHERE ts < ?')->execute([$now - $window]);
    $stmt = $pdo->prepare('SELECT COUNT(*) FROM rate_limits WHERE token = ? AND bucket = ? AND ts >= ?');
    $stmt->execute([$token, $bucket, $now - $window]);
    if ((int) $stmt->fetchColumn() >= $max) {
        respond(429, ['error' => 'slow down']);
    }
    $pdo->prepare('INSERT INTO rate_limits (token, bucket, ts) VALUES (?, ?, ?)')
        ->execute([$token, $bucket, $now]);
}
