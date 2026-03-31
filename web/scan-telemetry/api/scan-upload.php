<?php

declare(strict_types=1);

require __DIR__ . '/bootstrap.php';

$config = telemetry_config();
telemetry_require_api_key($config);

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    telemetry_json_response(['ok' => false, 'error' => 'POST required'], 405);
}

$payloadJson = $_POST['payload_json'] ?? '';
if (!is_string($payloadJson) || trim($payloadJson) === '') {
    telemetry_json_response(['ok' => false, 'error' => 'payload_json is required'], 422);
}

$payload = json_decode($payloadJson, true);
if (!is_array($payload)) {
    telemetry_json_response(['ok' => false, 'error' => 'payload_json is invalid'], 422);
}

$uploadId = (string) ($payload['uploadId'] ?? '');
if ($uploadId === '') {
    telemetry_json_response(['ok' => false, 'error' => 'uploadId missing'], 422);
}

$storageRoot = rtrim($config['storage_dir'], DIRECTORY_SEPARATOR);
$datedDir = $storageRoot . DIRECTORY_SEPARATOR . 'scans' . DIRECTORY_SEPARATOR . date('Y') . DIRECTORY_SEPARATOR . date('m');
if (!is_dir($datedDir) && !mkdir($datedDir, 0775, true) && !is_dir($datedDir)) {
    telemetry_json_response(['ok' => false, 'error' => 'Failed to create storage directory'], 500);
}

$storedRelativePath = null;
if (isset($_FILES['screenshot']) && is_uploaded_file($_FILES['screenshot']['tmp_name'])) {
    $targetName = $uploadId . '.png';
    $targetPath = $datedDir . DIRECTORY_SEPARATOR . $targetName;
    if (!move_uploaded_file($_FILES['screenshot']['tmp_name'], $targetPath)) {
        telemetry_json_response(['ok' => false, 'error' => 'Failed to store screenshot'], 500);
    }
    $storedRelativePath = 'scans/' . date('Y') . '/' . date('m') . '/' . $targetName;
}

$prediction = $payload['prediction'] ?? [];
$debug = $payload['debug'] ?? [];
$app = $payload['app'] ?? [];
$device = $payload['device'] ?? [];

$pdo = telemetry_pdo($config);
$statement = $pdo->prepare(
    'INSERT INTO scan_uploads (
        upload_id, uploaded_at_epoch_ms, app_package, app_version_name, app_version_code,
        device_manufacturer, device_model, device_sdk_int,
        predicted_species, predicted_cp, predicted_hp, predicted_caught_date_epoch_ms,
        predicted_is_shiny, predicted_is_shadow, predicted_is_lucky,
        predicted_has_costume, predicted_has_special_form, predicted_has_location_card,
        predicted_rarity_score, predicted_rarity_tier, predicted_iv_estimate,
        raw_ocr_text, pipeline_ms, explanations_json, breakdown_json,
        screenshot_relative_path, payload_json, user_truth_species, user_truth_is_shiny,
        user_truth_has_costume, user_truth_form
    ) VALUES (
        :upload_id, :uploaded_at_epoch_ms, :app_package, :app_version_name, :app_version_code,
        :device_manufacturer, :device_model, :device_sdk_int,
        :predicted_species, :predicted_cp, :predicted_hp, :predicted_caught_date_epoch_ms,
        :predicted_is_shiny, :predicted_is_shadow, :predicted_is_lucky,
        :predicted_has_costume, :predicted_has_special_form, :predicted_has_location_card,
        :predicted_rarity_score, :predicted_rarity_tier, :predicted_iv_estimate,
        :raw_ocr_text, :pipeline_ms, :explanations_json, :breakdown_json,
        :screenshot_relative_path, :payload_json, NULL, NULL, NULL, NULL
    )'
);

$statement->execute([
    ':upload_id' => $uploadId,
    ':uploaded_at_epoch_ms' => $payload['uploadedAtEpochMs'] ?? null,
    ':app_package' => $app['packageName'] ?? null,
    ':app_version_name' => $app['versionName'] ?? null,
    ':app_version_code' => $app['versionCode'] ?? null,
    ':device_manufacturer' => $device['manufacturer'] ?? null,
    ':device_model' => $device['model'] ?? null,
    ':device_sdk_int' => $device['sdkInt'] ?? null,
    ':predicted_species' => $prediction['species'] ?? null,
    ':predicted_cp' => $prediction['cp'] ?? null,
    ':predicted_hp' => $prediction['hp'] ?? null,
    ':predicted_caught_date_epoch_ms' => $prediction['caughtDateEpochMs'] ?? null,
    ':predicted_is_shiny' => empty($prediction['isShiny']) ? 0 : 1,
    ':predicted_is_shadow' => empty($prediction['isShadow']) ? 0 : 1,
    ':predicted_is_lucky' => empty($prediction['isLucky']) ? 0 : 1,
    ':predicted_has_costume' => empty($prediction['hasCostume']) ? 0 : 1,
    ':predicted_has_special_form' => empty($prediction['hasSpecialForm']) ? 0 : 1,
    ':predicted_has_location_card' => empty($prediction['hasLocationCard']) ? 0 : 1,
    ':predicted_rarity_score' => $prediction['rarityScore'] ?? null,
    ':predicted_rarity_tier' => $prediction['rarityTier'] ?? null,
    ':predicted_iv_estimate' => $prediction['ivEstimate'] ?? null,
    ':raw_ocr_text' => $debug['rawOcrText'] ?? null,
    ':pipeline_ms' => $debug['pipelineMs'] ?? null,
    ':explanations_json' => json_encode($debug['explanations'] ?? [], JSON_UNESCAPED_SLASHES),
    ':breakdown_json' => json_encode($debug['breakdown'] ?? new stdClass(), JSON_UNESCAPED_SLASHES),
    ':screenshot_relative_path' => $storedRelativePath,
    ':payload_json' => $payloadJson,
]);

$screenshotUrl = $storedRelativePath
    ? rtrim($config['public_base_url'], '/') . '/storage/' . $storedRelativePath
    : null;

telemetry_json_response([
    'ok' => true,
    'upload_id' => $uploadId,
    'screenshot_url' => $screenshotUrl,
]);
