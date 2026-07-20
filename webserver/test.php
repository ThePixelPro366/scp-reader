<?php
/**
 * Diagnostics — open https://thepixelpro.de/scpBackend/test.php in a browser.
 *
 * Confirms PHP version, that the pdo_sqlite driver exists, that the data directory is writable,
 * and — crucially — whether the Authorization header actually reaches PHP on this host.
 *
 * DELETE this file once everything checks out; it leaks environment detail otherwise.
 */
header('Content-Type: application/json');

$checks = [];

// 1. PHP version
$checks['php_version'] = PHP_VERSION;

// 2. pdo_sqlite driver (the app's default DB backend)
$checks['pdo_sqlite'] = extension_loaded('pdo_sqlite') ? 'OK' : 'MISSING';
$checks['pdo_drivers'] = class_exists('PDO') ? PDO::getAvailableDrivers() : [];

// 3. Can we actually open + write a SQLite DB here?
$checks['sqlite_write'] = 'not tested';
if (extension_loaded('pdo_sqlite')) {
    try {
        $dir = __DIR__ . '/data';
        if (!is_dir($dir)) {
            @mkdir($dir, 0700, true);
        }
        $pdo = new PDO('sqlite:' . $dir . '/selftest.sqlite');
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        $pdo->exec('CREATE TABLE IF NOT EXISTS t (x INTEGER)');
        $pdo->exec('INSERT INTO t (x) VALUES (1)');
        $checks['sqlite_write'] = 'OK';
        @unlink($dir . '/selftest.sqlite');
    } catch (Throwable $e) {
        $checks['sqlite_write'] = 'FAILED: ' . $e->getMessage();
    }
}

// 4. Data directory writability
$checks['data_dir_writable'] = is_writable(__DIR__) ? 'parent OK' : 'parent NOT writable';

// 5. Does the Authorization header survive to PHP?
//    Send a test header:  curl -H "Authorization: Bearer testtoken123456" .../test.php
$authSources = [
    'HTTP_AUTHORIZATION' => $_SERVER['HTTP_AUTHORIZATION'] ?? null,
    'REDIRECT_HTTP_AUTHORIZATION' => $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? null,
    'apache_request_headers' => null,
];
if (function_exists('apache_request_headers')) {
    foreach (apache_request_headers() as $k => $v) {
        if (strcasecmp($k, 'Authorization') === 0) {
            $authSources['apache_request_headers'] = $v;
        }
    }
}
$checks['authorization_header'] = $authSources;
$checks['authorization_visible'] = array_filter($authSources) ? 'YES' : 'NO (send one with curl to test)';

echo json_encode($checks, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
