<?php
/**
 * POST /add_friend.php   (Authorization: Bearer <token>)
 * Body: { "friend_code": "X7K2P9" }
 *
 * Creates a mutual friendship (both directions) immediately — no accept/reject step.
 */
require __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    respond(405, ['error' => 'POST only']);
}

$pdo = db();
$me = require_device($pdo);
rate_limit($pdo, $me['token'], 'add_friend', 30, 3600);

$in = body();
$code = isset($in['friend_code']) ? strtoupper(trim((string) $in['friend_code'])) : '';
if (!preg_match('/^[A-Z0-9]{6}$/', $code)) {
    respond(400, ['error' => 'invalid code']);
}
if ($code === $me['friend_code']) {
    respond(400, ['error' => "can't add yourself"]);
}

$stmt = $pdo->prepare('SELECT token, friend_code, name FROM devices WHERE friend_code = ?');
$stmt->execute([$code]);
$other = $stmt->fetch(PDO::FETCH_ASSOC);
if (!$other) {
    respond(404, ['error' => 'no one with that code']);
}

$now = time();
$ins = $pdo->prepare('INSERT OR IGNORE INTO friends (owner_token, friend_token, created_at) VALUES (?, ?, ?)');
$ins->execute([$me['token'], $other['token'], $now]);
$ins->execute([$other['token'], $me['token'], $now]);

respond(200, [
    'friend' => [
        'friend_code' => $other['friend_code'],
        'name' => $other['name'],
    ],
]);
