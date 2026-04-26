# Purpose: Export private telemetry screenshots into a local-only training/review dataset.
param(
    [int]$Limit = 250,
    [string]$OutputDir = "",
    [switch]$IncludeUnverified
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$localPropertiesPath = Join-Path $repoRoot "local.properties"

function Read-LocalProperties {
    $properties = @{}
    if (-not (Test-Path $localPropertiesPath)) {
        return $properties
    }

    Get-Content $localPropertiesPath | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $properties[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $properties
}

function Has-UserTruth($item) {
    return -not [string]::IsNullOrWhiteSpace([string]$item.user_truth_species) -or
        -not [string]::IsNullOrWhiteSpace([string]$item.user_truth_is_shiny) -or
        -not [string]::IsNullOrWhiteSpace([string]$item.user_truth_has_costume) -or
        -not [string]::IsNullOrWhiteSpace([string]$item.user_truth_form)
}

function Safe-FileName([string]$value) {
    $safe = $value -replace '[^A-Za-z0-9._-]', '_'
    if ([string]::IsNullOrWhiteSpace($safe)) {
        return "unknown"
    }
    return $safe
}

$properties = Read-LocalProperties
$baseUrl = [string]$properties["scanTelemetryBaseUrl"]
if ([string]::IsNullOrWhiteSpace($baseUrl)) {
    $baseUrl = [string]$env:SCAN_TELEMETRY_BASE_URL
}
$baseUrl = $baseUrl.Replace('\:', ':').TrimEnd('/')

$apiKey = [string]$properties["scanTelemetryApiKey"]
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $apiKey = [string]$env:SCAN_TELEMETRY_API_KEY
}
$apiKey = $apiKey.Trim()

if ([string]::IsNullOrWhiteSpace($baseUrl) -or [string]::IsNullOrWhiteSpace($apiKey)) {
    throw "Telemetry base URL and API key are required via local.properties or environment variables."
}

$publicRoot = $baseUrl -replace '/api$', ''
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutputDir = Join-Path $repoRoot "artifacts\dataset\telemetry_$stamp"
}

$imagesDir = Join-Path $OutputDir "images"
New-Item -ItemType Directory -Force -Path $imagesDir | Out-Null

$exportUri = "$publicRoot/api/scan-export.php?api_key=$apiKey&limit=$Limit"
$response = Invoke-RestMethod -Uri $exportUri
$items = @($response.items)

if (-not $IncludeUnverified) {
    $items = @($items | Where-Object { Has-UserTruth $_ })
}

$manifestPath = Join-Path $OutputDir "manifest.jsonl"
if (Test-Path $manifestPath) {
    Remove-Item -LiteralPath $manifestPath -Force
}

$downloaded = 0
foreach ($item in $items) {
    if ([string]::IsNullOrWhiteSpace([string]$item.screenshot_relative_path)) {
        continue
    }

    $species = Safe-FileName ([string]$item.predicted_species)
    $fileName = "$(Safe-FileName ([string]$item.id))_$species.png"
    $targetPath = Join-Path $imagesDir $fileName
    $screenshotUri = "$publicRoot/storage/$($item.screenshot_relative_path)"
    Invoke-WebRequest -Uri $screenshotUri -OutFile $targetPath

    $record = [ordered]@{
        id = [string]$item.id
        upload_id = [string]$item.upload_id
        image = "images/$fileName"
        created_at = [string]$item.created_at
        device_model = [string]$item.device_model
        predicted = [ordered]@{
            species = [string]$item.predicted_species
            is_shiny = [string]$item.predicted_is_shiny
            has_costume = [string]$item.predicted_has_costume
            has_location_card = [string]$item.predicted_has_location_card
            has_special_form = [string]$item.predicted_has_special_form
            cp = [string]$item.predicted_cp
            hp = [string]$item.predicted_hp
            rarity_score = [string]$item.predicted_rarity_score
            rarity_tier = [string]$item.predicted_rarity_tier
        }
        truth = [ordered]@{
            species = [string]$item.user_truth_species
            is_shiny = [string]$item.user_truth_is_shiny
            has_costume = [string]$item.user_truth_has_costume
            form = [string]$item.user_truth_form
        }
        verified = Has-UserTruth $item
    }
    ($record | ConvertTo-Json -Compress -Depth 6) | Add-Content -Path $manifestPath -Encoding UTF8
    $downloaded++
}

[PSCustomObject]@{
    outputDir = $OutputDir
    manifest = $manifestPath
    downloaded = $downloaded
    includeUnverified = [bool]$IncludeUnverified
} | ConvertTo-Json -Depth 4
