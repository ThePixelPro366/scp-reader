<?php
/**
 * GET /friends.php   (Authorization: Bearer <token>)
 * Returns { "friends": [ { "friend_code", "name" }, ... ] }
 */
require __DIR__ . '/db.php';

$pdo = db();
$me = require_device($pdo);

$stmt = $pdo->prepare(
    'SELECT d.friend_code, d.name
       FROM friends f
       JOIN devices d ON d.token = f.friend_token
      WHERE f.owner_token = ?
      ORDER BY d.name, d.friend_code'
);
$stmt->execute([$me['token']]);
$friends = $stmt->fetchAll(PDO::FETCH_ASSOC);

respond(200, ['friends' => $friends]);
