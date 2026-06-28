param(
    [string]$CacheRoot = ".local/pogo_reference_cache",
    [string]$ReportRoot = "build/reports/pogo_reference",
    [int]$MaxNewDownloads = 500,
    [switch]$IndexOnly,
    [switch]$DownloadAll,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$assetDir = Join-Path $CacheRoot "assets/pokeminers_pogo_assets"
$metadataDir = Join-Path $CacheRoot "metadata"
New-Item -ItemType Directory -Force -Path $assetDir, $metadataDir, $ReportRoot | Out-Null

$treePath = Join-Path $metadataDir "pokeminers_pogo_assets_tree.json"
$treeUrl = "https://api.github.com/repos/PokeMiners/pogo_assets/git/trees/master?recursive=1"
if ($Force -or !(Test-Path $treePath)) {
    Invoke-WebRequest -Uri $treeUrl -OutFile $treePath -TimeoutSec 90 | Out-Null
}
$tree = Get-Content -Raw -LiteralPath $treePath | ConvertFrom-Json
$allAssets = @($tree.tree | Where-Object {
    $_.type -eq "blob" -and
    $_.path -like "Images/Pokemon - 256x256/*.png" -and
    ($_.path -like "*.icon.png" -or $_.path -like "*pokemon_icon_*.png")
})
$newDownloadCount = 0

function Convert-ToRawUrl([string]$Path) {
    $escaped = ($Path -split "/" | ForEach-Object { [uri]::EscapeDataString($_) }) -join "/"
    return "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/$escaped"
}

function Save-Asset([string]$Url, [string]$Path) {
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Path -TimeoutSec 60 | Out-Null
        return
    } catch {
        $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
        if ($null -eq $curl) { throw }
        & $curl.Source -L -f -sS -o $Path $Url
        if ($LASTEXITCODE -ne 0) {
            throw "curl.exe failed with exit code $LASTEXITCODE"
        }
    }
}

function Test-PngDecode([string]$Path) {
    if (!(Test-Path $Path)) { return @{ ok = $false; width = 0; height = 0; reason = "missing" } }
    if ((Get-Item -LiteralPath $Path).Length -lt 100) { return @{ ok = $false; width = 0; height = 0; reason = "too_small" } }
    try {
        Add-Type -AssemblyName System.Drawing -ErrorAction SilentlyContinue
        $image = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $Path))
        try {
            $ok = $image.Width -gt 0 -and $image.Height -gt 0
            return @{ ok = $ok; width = $image.Width; height = $image.Height; reason = if ($ok) { "" } else { "invalid_dimensions" } }
        } finally {
            $image.Dispose()
        }
    } catch {
        return @{ ok = $false; width = 0; height = 0; reason = "decode_failed: $($_.Exception.Message)" }
    }
}

$rows = New-Object System.Collections.Generic.List[object]
foreach ($asset in $allAssets) {
    $relativePath = $asset.path
    $localPath = Join-Path $assetDir $relativePath
    $status = "index_only"
    $errorMessage = $null
    $canDownload = $DownloadAll -or $newDownloadCount -lt $MaxNewDownloads
    if (!$IndexOnly -and ($canDownload -or (Test-Path $localPath))) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $localPath) | Out-Null
        if ($Force -or !(Test-Path $localPath)) {
            try {
                Save-Asset -Url (Convert-ToRawUrl $relativePath) -Path $localPath
                $status = "downloaded"
                $newDownloadCount++
            } catch {
                $status = "download_failed"
                $errorMessage = $_.Exception.Message
            }
        } else {
            $status = "cached"
        }
    } elseif (!$IndexOnly) {
        $status = "not_attempted_download_cap"
    }
    $decode = Test-PngDecode $localPath
    $hash = if (Test-Path $localPath) { (Get-FileHash -Algorithm SHA256 -LiteralPath $localPath).Hash.ToLowerInvariant() } else { $null }
    $rows.Add([pscustomobject]@{
        catalogKey = [IO.Path]::GetFileNameWithoutExtension($relativePath)
        speciesId = ""
        speciesName = ""
        form = ""
        variantClass = ""
        assetExpected = $true
        assetFound = [bool](Test-Path $localPath)
        assetSource = "pokeminers:pogo_assets"
        localCachePath = $localPath
        sourcePath = $relativePath
        sourceHash = $hash
        sourceTimestamp = (Get-Date).ToUniversalTime().ToString("o")
        licenseRisk = "external_sprite_do_not_commit"
        usableForDescriptor = [bool]$decode.ok
        width = $decode.width
        height = $decode.height
        reasonIfMissing = if (Test-Path $localPath) { "" } else { $status }
        reasonIfUnusable = $decode.reason
        error = $errorMessage
    })
}

$rows | Export-Csv -NoTypeInformation -Path (Join-Path $ReportRoot "asset_integrity_report.csv")
$coverage = @(
    "# Asset Fetch Coverage",
    "",
    "Generated: $((Get-Date).ToUniversalTime().ToString('o'))",
    "",
    "- indexed_assets=$($allAssets.Count)",
    "- downloaded_or_cached=$(($rows | Where-Object { $_.assetFound }).Count)",
    "- usable_for_descriptor=$(($rows | Where-Object { $_.usableForDescriptor }).Count)",
    "- index_only=$([bool]$IndexOnly)",
    "- download_all=$([bool]$DownloadAll)",
    "- max_new_downloads=$MaxNewDownloads",
    "- new_downloads_this_run=$newDownloadCount",
    "",
    "Status: $(if (($rows | Where-Object { -not $_.assetFound }).Count -eq 0) { 'COMPLETE' } else { 'NOT READY - not all indexed assets are cached' })"
)
$coverage | Set-Content -Encoding UTF8 -Path (Join-Path $ReportRoot "asset_fetch_coverage.md")
Write-Host "asset_index_entries=$($allAssets.Count)"
Write-Host "asset_downloaded_or_cached=$(($rows | Where-Object { $_.assetFound }).Count)"
Write-Host "asset_usable=$(($rows | Where-Object { $_.usableForDescriptor }).Count)"
