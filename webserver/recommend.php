<?php
/**
 * POST /recommend.php   (Authorization: Bearer <token>)
 * Body: { "friend_code", "scp_url", "scp_number", "scp_title", "note" }
 *
 * Sends an SCP recommendation to a friend. Rejects if the recipient isn't actually a friend.
 */
require __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    respond(405, ['error' => 'POST only']);
}

$pdo = db();
$me = require_device($pdo);
rate_limit($pdo, $me['token'], 'recommend', 60, 3600);

$in = body();
$code = isset($in['friend_code']) ? strtoupper(trim((string) $in['friend_code'])) : '';
$url = isset($in['scp_url']) ? trim((string) $in['scp_url']) : '';
$number = isset($in['scp_number']) ? trim((string) $in['scp_number']) : '';
$title = isset($in['scp_title']) ? trim((string) $in['scp_title']) : '';
$note = isset($in['note']) ? trim((string) $in['note']) : '';

if (!preg_match('/^[A-Z0-9]{6}$/', $code)) {
    respond(400, ['error' => 'invalid code']);
}
if ($url === '' || !preg_match('#^https?://#i', $url) || mb_strlen($url) > 500) {
    respond(400, ['error' => 'invalid url']);
}
$number = mb_substr($number, 0, 40);
$title = mb_substr($title, 0, 200);
$note = mb_substr($note, 0, 280);

$stmt = $pdo->prepare('SELECT token FROM devices WHERE friend_code = ?');
$stmt->execute([$code]);
$to = $stmt->fetchColumn();
if (!$to) {
    respond(404, ['error' => 'no one with that code']);
}

// Only allow sending to an established friend.
$check = $pdo->prepare('SELECT 1 FROM friends WHERE owner_token = ? AND friend_token = ?');
$check->execute([$me['token'], $to]);
if (!$check->fetchColumn()) {
    respond(403, ['error' => 'not friends']);
}

$pdo->prepare(
    'INSERT INTO recommendations (from_token, to_token, scp_url, scp_number, scp_title, note, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)'
)->execute([$me['token'], $to, $url, $number, $title, $note, time()]);

respond(200, ['ok' => true]);
