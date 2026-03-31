<?php

declare(strict_types=1);

require __DIR__ . '/bootstrap.php';

$config = telemetry_config();
telemetry_require_api_key($config);

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    telemetry_json_response(['ok' => false, 'error' => 'POST required'], 405);
}

$uploadId = trim((string) ($_POST['upload_id'] ?? ''));
$category = trim((string) ($_POST['category'] ?? ''));
$notes = trim((string) ($_POST['notes'] ?? ''));

if ($uploadId === '' || $category === '') {
    telemetry_json_response(['ok' => false, 'error' => 'upload_id and category are required'], 422);
}

$pdo = telemetry_pdo($config);
telemetry_ensure_feedback_table($pdo);

$statement = $pdo->prepare(
    'INSERT INTO scan_feedback (upload_id, category, notes)
     VALUES (:upload_id, :category, :notes)'
);
$statement->execute([
    ':upload_id' => $uploadId,
    ':category' => $category,
    ':notes' => $notes !== '' ? $notes : null,
]);

telemetry_json_response([
    'ok' => true,
    'upload_id' => $uploadId,
    'category' => $category,
]);
