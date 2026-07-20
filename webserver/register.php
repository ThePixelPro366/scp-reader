<?php
/**
 * POST /register.php
 * Body: { "token": "<device-generated token>", "name": "<optional>" }
 *
 * The app generates its own token on first launch and registers it here. We mint a short
 * friend_code (the shareable handle) and hand it back. Idempotent: calling again with the
 * same token just returns the existing friend_code (and updates the name if provided).
 */
require __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    respond(405, ['error' => 'POST only']);
}

$pdo = db();
$in = body();
$token = isset($in['token']) ? trim((string) $in['token']) : '';
$name = isset($in['name']) ? trim((string) $in['name']) : '';

if (!preg_match('/^[A-Za-z0-9\-]{16,64}$/', $token)) {
    respond(400, ['error' => 'invalid token']);
}
if (mb_strlen($name) > 40) {
    $name = mb_substr($name, 0, 40);
}

// Already registered → return existing code (and refresh name if one was sent).
$stmt = $pdo->prepare('SELECT friend_code, name FROM devices WHERE token = ?');
$stmt->execute([$token]);
$existing = $stmt->fetch(PDO::FETCH_ASSOC);
if ($existing) {
    if ($name !== '' && $name !== $existing['name']) {
        $pdo->prepare('UPDATE devices SET name = ? WHERE token = ?')->execute([$name, $token]);
        $existing['name'] = $name;
    }
    respond(200, ['friend_code' => $existing['friend_code'], 'name' => $existing['name']]);
}

// New device: generate a unique friend code (unambiguous alphabet, no O/0/I/1).
$alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
$code = '';
for ($attempt = 0; $attempt < 12; $attempt++) {
    $code = '';
    for ($i = 0; $i < 6; $i++) {
        $code .= $alphabet[random_int(0, strlen($alphabet) - 1)];
    }
    $check = $pdo->prepare('SELECT 1 FROM devices WHERE friend_code = ?');
    $check->execute([$code]);
    if (!$check->fetchColumn()) {
        break;
    }
    $code = '';
}
if ($code === '') {
    respond(500, ['error' => 'could not allocate code']);
}

$now = time();
$pdo->prepare('INSERT INTO devices (token, friend_code, name, created_at, last_seen) VALUES (?, ?, ?, ?, ?)')
    ->execute([$token, $code, $name, $now, $now]);

respond(200, ['friend_code' => $code, 'name' => $name]);
