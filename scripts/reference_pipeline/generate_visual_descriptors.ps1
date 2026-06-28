param(
    [string]$AssetRoot = ".local/pogo_reference_cache/assets/pokeminers_pogo_assets/Images/Pokemon - 256x256",
    [string]$ReportRoot = "build/reports/pogo_reference",
    [string]$Out = "build/reports/pogo_reference/visual_descriptor_db.json",
    [switch]$RegenerateFromAssets,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $ReportRoot | Out-Null

$manifestPath = Join-Path $ReportRoot "descriptor_generation_manifest.json"
$qualityPath = Join-Path $ReportRoot "descriptor_quality_report.md"

if (!(Test-Path $AssetRoot)) {
    [pscustomobject]@{
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        status = "blocked"
        reason = "asset_cache_missing"
        assetRoot = $AssetRoot
        output = $Out
        descriptorCount = 0
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path $manifestPath
    @(
        "# Descriptor Quality Report",
        "",
        "Status: NOT READY",
        "",
        "No local asset cache exists at `$AssetRoot`. Run `fetch_pogo_assets.ps1` first.",
        "",
        "No descriptor DB was generated."
    ) | Set-Content -Encoding UTF8 -Path $qualityPath
    Write-Error "Asset cache missing: $AssetRoot"
}

if ($RegenerateFromAssets -or $Force -or !(Test-Path $Out)) {
    python scripts/train_variant_prototypes.py --assets-dir $AssetRoot --out $Out
}

$rawHeader = Get-Content -Raw -LiteralPath $Out
$entryCount = if ($rawHeader -match '"entryCount"\s*:\s*(\d+)') { [int]$Matches[1] } else { 0 }
$speciesCount = if ($rawHeader -match '"speciesCount"\s*:\s*(\d+)') { [int]$Matches[1] } else { 0 }
$descriptorVersion = if ($rawHeader -match '"version"\s*:\s*(\d+)') { [int]$Matches[1] } else { 1 }
$qualityStatus = if ($entryCount -gt 0) { "GENERATED_DEV_ONLY" } else { "NOT_READY" }

[pscustomobject]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    status = if ($entryCount -gt 0) { "generated" } else { "empty" }
    reason = if ($entryCount -gt 0) { "" } else { "no_decodable_assets_or_metadata_matches" }
    assetRoot = $AssetRoot
    output = $Out
    descriptorVersion = $descriptorVersion
    descriptorCount = $entryCount
    speciesCount = $speciesCount
    generator = "scripts/train_variant_prototypes.py"
    committedToRuntimeAssets = $false
    legalRisk = "generated_from_external_sprites_keep_dev_only_until_reviewed"
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path $manifestPath

@(
    "# Descriptor Quality Report",
    "",
    "Status: $qualityStatus",
    "",
    "- descriptor_count=$entryCount",
    "- species_count=$speciesCount",
    "- output=$Out",
    "- source_asset_root=$AssetRoot",
    "- runtime_commit_status=dev_only_not_committed_to_app_assets",
    "",
    "This generator reuses the existing repo descriptor model: aHash, dHash, edge vector, full/head/upper/body hue histograms, foreground ratio, and aspect ratio.",
    "A stronger embedding/model path was not added because there is not enough labeled live holdout data to calibrate false positives safely."
) | Set-Content -Encoding UTF8 -Path $qualityPath

Write-Host "descriptors_generated=$entryCount"
Write-Host "descriptor_species=$speciesCount"
