param(
    [string]$SessionName = "",
    [string]$OutRoot = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($OutRoot)) {
    $OutRoot = Join-Path $repoRoot "build\reports\fixture_tools\live_sessions"
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
if ([string]::IsNullOrWhiteSpace($SessionName)) {
    $SessionName = "live_fixture_$timestamp"
}
$safeName = ($SessionName -replace '[^A-Za-z0-9_.-]', '_')
$sessionDir = Join-Path $OutRoot $safeName

$dirs = @(
    $sessionDir,
    (Join-Path $sessionDir "diagnostics"),
    (Join-Path $sessionDir "screenshots"),
    (Join-Path $sessionDir "crops"),
    (Join-Path $sessionDir "labels")
)
foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Force $dir | Out-Null
}

$readme = @(
    "# Live Fixture Session",
    "",
    "session=$safeName",
    "created=$((Get-Date).ToString('o'))",
    "",
    "Use this app-private/local-only folder for one live recognition capture pass.",
    "",
    "Suggested flow:",
    "1. Capture Pokemon detail/appraisal scans on device with local diagnostics enabled.",
    "2. Run scripts\fixture_tools\import_device_diagnostics.ps1 -OutDir `"$sessionDir\diagnostics`".",
    "3. Copy chosen screenshots/crops into screenshots/ or crops/ only if they are user-owned fixtures.",
    "4. Run scripts\fixture_tools\label_fixture_template.ps1 and label only visually certain fields.",
    "5. Run scripts\fixture_tools\validate_fixture_labels.ps1.",
    "6. Run scripts\fixture_tools\split_fixture_holdout.ps1 before descriptor evaluation.",
    "",
    "Do not commit app-private exports, screenshots, crops, or raw external assets."
)
$readme | Set-Content -Encoding UTF8 (Join-Path $sessionDir "README.md")

Write-Output "Live fixture session created"
Write-Output "session=$safeName"
Write-Output "path=$sessionDir"
