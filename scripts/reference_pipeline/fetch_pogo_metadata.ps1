param(
    [string]$CacheRoot = ".local/pogo_reference_cache",
    [string]$ReportRoot = "build/reports/pogo_reference",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$metadataDir = Join-Path $CacheRoot "metadata"
New-Item -ItemType Directory -Force -Path $metadataDir, $ReportRoot | Out-Null

$sources = @(
    @{ id = "pogoapi_api_hashes"; url = "https://pogoapi.net/api/v1/api_hashes.json"; file = "pogoapi_api_hashes.json"; required = $true },
    @{ id = "pogoapi_released"; url = "https://pogoapi.net/api/v1/released_pokemon.json"; file = "released_pokemon.json"; required = $true },
    @{ id = "pogoapi_forms"; url = "https://pogoapi.net/api/v1/pokemon_forms.json"; file = "pokemon_forms.json"; required = $true },
    @{ id = "pogoapi_shiny"; url = "https://pogoapi.net/api/v1/shiny_pokemon.json"; file = "shiny_pokemon.json"; required = $true },
    @{ id = "pogoapi_shadow"; url = "https://pogoapi.net/api/v1/shadow_pokemon.json"; file = "shadow_pokemon.json"; required = $false },
    @{ id = "pogoapi_mega"; url = "https://pogoapi.net/api/v1/mega_pokemon.json"; file = "mega_pokemon.json"; required = $false },
    @{ id = "pogoapi_stats"; url = "https://pogoapi.net/api/v1/pokemon_stats.json"; file = "pokemon_stats.json"; required = $true },
    @{ id = "pogoapi_alolan"; url = "https://pogoapi.net/api/v1/alolan_pokemon.json"; file = "alolan_pokemon.json"; required = $false },
    @{ id = "pogoapi_galarian"; url = "https://pogoapi.net/api/v1/galarian_pokemon.json"; file = "galarian_pokemon.json"; required = $false },
    @{ id = "pogoapi_hisuian"; url = "https://pogoapi.net/api/v1/hisuian_pokemon.json"; file = "hisuian_pokemon.json"; required = $false },
    @{ id = "pogoapi_paldean"; url = "https://pogoapi.net/api/v1/paldean_pokemon.json"; file = "paldean_pokemon.json"; required = $false },
    @{ id = "pokeminers_game_master"; url = "https://raw.githubusercontent.com/PokeMiners/game_masters/master/latest/latest.json"; file = "pokeminers_latest_game_master.json"; required = $true }
)

function Get-Sha256([string]$Path) {
    if (!(Test-Path $Path)) { return $null }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

$rows = foreach ($source in $sources) {
    $path = Join-Path $metadataDir $source.file
    $status = "skipped_existing"
    $errorMessage = $null
    if ($Force -or !(Test-Path $path)) {
        try {
            Invoke-WebRequest -Uri $source.url -OutFile $path -TimeoutSec 60 | Out-Null
            $status = "downloaded"
        } catch {
            $status = if ($source.required) { "failed_required" } else { "failed_optional" }
            $errorMessage = $_.Exception.Message
        }
    }
    [pscustomobject]@{
        id = $source.id
        url = $source.url
        cachePath = $path
        status = $status
        required = [bool]$source.required
        bytes = if (Test-Path $path) { (Get-Item -LiteralPath $path).Length } else { 0 }
        sha256 = Get-Sha256 $path
        fetchedAt = (Get-Date).ToUniversalTime().ToString("o")
        error = $errorMessage
    }
}

$rows | Export-Csv -NoTypeInformation -Path (Join-Path $ReportRoot "metadata_fetch_report.csv")
$rows | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path (Join-Path $ReportRoot "source_manifest.json")
$failedRequired = @($rows | Where-Object { $_.status -eq "failed_required" })
if ($failedRequired.Count -gt 0) {
    Write-Error "Required metadata downloads failed: $($failedRequired.id -join ', ')"
}
Write-Host "metadata_sources=$($rows.Count)"
Write-Host "metadata_downloaded=$(($rows | Where-Object { $_.status -eq 'downloaded' }).Count)"
