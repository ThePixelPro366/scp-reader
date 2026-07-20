<?php
/**
 * GET /recommendations.php   (Authorization: Bearer <token>)
 * Returns recommendations sent TO me, newest first:
 * { "recommendations": [ { "id", "from_code", "from_name", "scp_url",
 *                          "scp_number", "scp_title", "note", "created_at" }, ... ] }
 */
require __DIR__ . '/db.php';

$pdo = db();
$me = require_device($pdo);

$stmt = $pdo->prepare(
    'SELECT r.id, r.scp_url, r.scp_number, r.scp_title, r.note, r.created_at,
            d.friend_code AS from_code, d.name AS from_name
       FROM recommendations r
       JOIN devices d ON d.token = r.from_token
      WHERE r.to_token = ?
      ORDER BY r.created_at DESC
      LIMIT 200'
);
$stmt->execute([$me['token']]);
$rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

// Normalize integer fields (PDO/SQLite returns strings).
foreach ($rows as &$r) {
    $r['id'] = (int) $r['id'];
    $r['created_at'] = (int) $r['created_at'];
}

respond(200, ['recommendations' => $rows]);
