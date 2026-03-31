<?php

declare(strict_types=1);

function telemetry_config(): array
{
    $configPath = dirname(__DIR__) . '/config.php';
    if (!file_exists($configPath)) {
        http_response_code(500);
        header('Content-Type: application/json');
        echo json_encode(['ok' => false, 'error' => 'Missing config.php']);
        exit;
    }
    return require $configPath;
}

function telemetry_pdo(array $config): PDO
{
    $db = $config['db'];
    $dsn = sprintf(
        'mysql:host=%s;port=%d;dbname=%s;charset=%s',
        $db['host'],
        $db['port'],
        $db['name'],
        $db['charset']
    );
    return new PDO($dsn, $db['user'], $db['pass'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
}

function telemetry_require_api_key(array $config): void
{
    $expected = trim((string) ($config['api_key'] ?? ''));
    if ($expected === '') {
        return;
    }
    $provided = trim((string) ($_POST['api_key'] ?? $_GET['api_key'] ?? ''));
    if (!hash_equals($expected, $provided)) {
        http_response_code(403);
        header('Content-Type: application/json');
        echo json_encode(['ok' => false, 'error' => 'Invalid api_key']);
        exit;
    }
}

function telemetry_json_response(array $payload, int $status = 200): void
{
    http_response_code($status);
    header('Content-Type: application/json');
    echo json_encode($payload, JSON_UNESCAPED_SLASHES);
    exit;
}

function telemetry_ensure_feedback_table(PDO $pdo): void
{
    $pdo->exec(
        'CREATE TABLE IF NOT EXISTS scan_feedback (
            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
            upload_id VARCHAR(64) NOT NULL,
            category VARCHAR(64) NOT NULL,
            notes TEXT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            KEY idx_scan_feedback_upload (upload_id),
            KEY idx_scan_feedback_category (category)
        )'
    );
}
