<?php
/**
 * POST /remove_friend.php   (Authorization: Bearer <token>)
 * Body: { "friend_code": "X7K2P9" }
 *
 * Removes the friendship in both directions, along with any recommendations exchanged between
 * the two devices.
 */
require __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    respond(405, ['error' => 'POST only']);
}

$pdo = db();
$me = require_device($pdo);
rate_limit($pdo, $me['token'], 'remove_friend', 60, 3600);

$in = body();
$code = isset($in['friend_code']) ? strtoupper(trim((string) $in['friend_code'])) : '';
if (!preg_match('/^[A-Z0-9]{6}$/', $code)) {
    respond(400, ['error' => 'invalid code']);
}

$stmt = $pdo->prepare('SELECT token FROM devices WHERE friend_code = ?');
$stmt->execute([$code]);
$other = $stmt->fetchColumn();
if (!$other) {
    respond(404, ['error' => 'no one with that code']);
}

$del = $pdo->prepare('DELETE FROM friends WHERE owner_token = ? AND friend_token = ?');
$del->execute([$me['token'], $other]);
$del->execute([$other, $me['token']]);

// Also drop recommendations exchanged either way, so a removed friend's picks disappear.
$pdo->prepare(
    'DELETE FROM recommendations
      WHERE (from_token = ? AND to_token = ?)
         OR (from_token = ? AND to_token = ?)'
)->execute([$me['token'], $other, $other, $me['token']]);

respond(200, ['ok' => true]);
