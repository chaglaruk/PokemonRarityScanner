<?php

declare(strict_types=1);

require __DIR__ . '/bootstrap.php';

$config = telemetry_config();
telemetry_require_api_key($config);

$pdo = telemetry_pdo($config);
telemetry_ensure_feedback_table($pdo);
$limit = max(1, min(500, (int) ($_GET['limit'] ?? 100)));
$fetchLimit = max($limit, min(2000, (int) ($_GET['fetch_limit'] ?? 1000)));
$species = trim((string) ($_GET['species'] ?? ''));
$predictedHasCostume = isset($_GET['predicted_has_costume']) ? (int) $_GET['predicted_has_costume'] : null;
$predictedIsShiny = isset($_GET['predicted_is_shiny']) ? (int) $_GET['predicted_is_shiny'] : null;
$eventConfidence = trim((string) ($_GET['event_confidence'] ?? ''));
$mismatchGuard = isset($_GET['mismatch_guard']) ? (int) $_GET['mismatch_guard'] : null;
$hasFeedback = isset($_GET['has_feedback']) ? (int) $_GET['has_feedback'] : null;
$feedbackCategory = trim((string) ($_GET['feedback_category'] ?? ''));

$sql = 'SELECT * FROM scan_uploads';
$params = [];
if ($species !== '') {
    $sql .= ' WHERE predicted_species = :species';
    $params[':species'] = $species;
}
$sql .= ' ORDER BY id DESC LIMIT :limit';

$statement = $pdo->prepare($sql);
foreach ($params as $key => $value) {
    $statement->bindValue($key, $value);
}
$statement->bindValue(':limit', $fetchLimit, PDO::PARAM_INT);
$statement->execute();

$rows = $statement->fetchAll();
$uploadIds = array_values(array_filter(array_map(static fn(array $row): string => (string) ($row['upload_id'] ?? ''), $rows)));
$feedbackByUpload = [];
if ($uploadIds !== []) {
    $placeholders = implode(',', array_fill(0, count($uploadIds), '?'));
    $feedbackStatement = $pdo->prepare("SELECT upload_id, category, notes, created_at FROM scan_feedback WHERE upload_id IN ($placeholders) ORDER BY id DESC");
    foreach ($uploadIds as $index => $uploadId) {
        $feedbackStatement->bindValue($index + 1, $uploadId);
    }
    $feedbackStatement->execute();
    foreach ($feedbackStatement->fetchAll() as $feedbackRow) {
        $feedbackByUpload[$feedbackRow['upload_id']][] = $feedbackRow;
    }
}

$items = [];
foreach ($rows as $row) {
    $payload = json_decode((string) ($row['payload_json'] ?? ''), true);
    if (!is_array($payload)) {
        $payload = [];
    }
    $feedback = $feedbackByUpload[$row['upload_id']] ?? [];
    $eventConfidenceCode = (string) ($payload['debug']['eventConfidenceCode'] ?? '');
    $rowMismatchGuard = !empty($payload['debug']['mismatchGuard']);

    if ($predictedHasCostume !== null && (int) ($row['predicted_has_costume'] ?? 0) !== $predictedHasCostume) {
        continue;
    }
    if ($predictedIsShiny !== null && (int) ($row['predicted_is_shiny'] ?? 0) !== $predictedIsShiny) {
        continue;
    }
    if ($eventConfidence !== '' && $eventConfidenceCode !== $eventConfidence) {
        continue;
    }
    if ($mismatchGuard !== null && (int) $rowMismatchGuard !== $mismatchGuard) {
        continue;
    }
    if ($hasFeedback !== null) {
        $rowHasFeedback = $feedback !== [];
        if ((bool) $hasFeedback !== $rowHasFeedback) {
            continue;
        }
    }
    if ($feedbackCategory !== '') {
        $hasCategory = false;
        foreach ($feedback as $feedbackRow) {
            if (($feedbackRow['category'] ?? '') === $feedbackCategory) {
                $hasCategory = true;
                break;
            }
        }
        if (!$hasCategory) {
            continue;
        }
    }

    $row['payload'] = $payload;
    $row['feedback'] = $feedback;
    $items[] = $row;
    if (count($items) >= $limit) {
        break;
    }
}

telemetry_json_response([
    'ok' => true,
    'count' => count($items),
    'items' => $items,
]);
