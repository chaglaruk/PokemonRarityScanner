param(
    [string]$CacheRoot = ".local/pogo_reference_cache",
    [string]$ReportRoot = "build/reports/pogo_reference"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $ReportRoot | Out-Null
$metadataDir = Join-Path $CacheRoot "metadata"

function Read-JsonOrNull([string]$Path) {
    if (!(Test-Path $Path)) { return $null }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Count-Props($Object) {
    if ($null -eq $Object) { return 0 }
    return @($Object.PSObject.Properties).Count
}

$released = Read-JsonOrNull (Join-Path $metadataDir "released_pokemon.json")
$forms = Read-JsonOrNull (Join-Path $metadataDir "pokemon_forms.json")
$shiny = Read-JsonOrNull (Join-Path $metadataDir "shiny_pokemon.json")
$shadow = Read-JsonOrNull (Join-Path $metadataDir "shadow_pokemon.json")
$mega = Read-JsonOrNull (Join-Path $metadataDir "mega_pokemon.json")
$variantDb = Read-JsonOrNull "app/src/main/assets/data/authoritative_variant_db.json"
$variantEntries = @($variantDb.entries)

$releasedRows = foreach ($prop in @($released.PSObject.Properties)) {
    [pscustomobject]@{
        catalogKey = ("{0:000}_base" -f [int]$prop.Value.id)
        speciesId = [int]$prop.Value.id
        speciesName = [string]$prop.Value.name
        form = "base"
        variantClass = "base"
        shinyAvailable = $false
        shadowAvailable = $false
        purifiedPossible = $false
        sourceIds = @("pogoapi:released_pokemon")
        descriptorUnsupportedReason = ""
    }
}

$shinyIds = @{}
foreach ($prop in @($shiny.PSObject.Properties)) { $shinyIds[[string]$prop.Value.id] = $true }
$shadowIds = @{}
foreach ($prop in @($shadow.PSObject.Properties)) { $shadowIds[[string]$prop.Value.id] = $true }

foreach ($row in $releasedRows) {
    $row.shinyAvailable = [bool]$shinyIds[[string]$row.speciesId]
    $row.shadowAvailable = [bool]$shadowIds[[string]$row.speciesId]
    $row.purifiedPossible = $row.shadowAvailable
}

$projectVariantRows = foreach ($entry in $variantEntries) {
    [pscustomobject]@{
        catalogKey = [string]$entry.spriteKey
        speciesId = [int]$entry.dex
        speciesName = [string]$entry.species
        form = if ($entry.gameMasterFormName) { [string]$entry.gameMasterFormName } else { [string]$entry.formId }
        variantClass = [string]$entry.variantClass
        shinyAvailable = [bool]$entry.isShiny
        shadowAvailable = $false
        purifiedPossible = $false
        sourceIds = @("project:authoritative_variant_db")
        descriptorUnsupportedReason = if ($entry.assetPath) { "" } else { "missing_asset_path" }
    }
}

$catalog = @($releasedRows + $projectVariantRows | Sort-Object speciesId, catalogKey)
$payload = [pscustomobject]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    sources = @(
        "pogoapi:released_pokemon",
        "pogoapi:pokemon_forms",
        "pogoapi:shiny_pokemon",
        "pogoapi:shadow_pokemon",
        "pogoapi:mega_pokemon",
        "project:authoritative_variant_db"
    )
    entryCount = $catalog.Count
    entries = $catalog
}
$payload | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -Path (Join-Path $ReportRoot "complete_pogo_catalog.json")

$counts = [ordered]@{
    released_species = Count-Props $released
    unreleased_species_excluded = "unknown_from_current_sources"
    forms = Count-Props $forms
    regional_forms = "see alolan/galarian/hisuian/paldean metadata"
    shiny_available_species_forms = Count-Props $shiny
    shadow_available_species_forms = Count-Props $shadow
    purified_possible_species_forms = Count-Props $shadow
    lucky_generic_visual_state = 1
    costume_event_variants = @($projectVariantRows | Where-Object { $_.variantClass -eq "costume" }).Count
    mega_primal = Count-Props $mega
    dynamax = 0
    gigantamax = 0
    special_background_location_card = @($projectVariantRows | Where-Object { $_.variantClass -eq "locationCard" }).Count
    gender_visual_differences = "not_covered"
    unknown_unsupported_go_visual_states = "dynamax,gigantamax,purified_indicator,gender_visual_differences"
    variants_with_metadata_but_no_asset = @($variantEntries | Where-Object { -not $_.assetPath }).Count
    assets_with_no_metadata_match = "requires_fetch_pogo_assets"
    conflicts_between_sources = 0
    catalog_entries = $catalog.Count
}

$summary = @("# Pokemon GO Reference Coverage Summary", "", "Generated: $((Get-Date).ToUniversalTime().ToString('o'))", "")
foreach ($key in $counts.Keys) { $summary += "- $key=$($counts[$key])" }
$summary | Set-Content -Encoding UTF8 -Path (Join-Path $ReportRoot "coverage_summary.md")

@(
    "# Source Diff Report",
    "",
    "Status: NOT READY",
    "",
    "- pogoapi_vs_project=not_evaluated",
    "- detail=Catalog is a union of PoGoAPI released metadata and project authoritative variant DB; full source reconciliation remains manual.",
    "- conflicts_between_sources=0 recorded by this first-pass script, not a proof of no conflicts."
) | Set-Content -Encoding UTF8 -Path (Join-Path $ReportRoot "source_diff_report.md")

@(
    [pscustomobject]@{ category = "dynamax"; reason = "metadata source not integrated"; action = "research and add source mapping before score eligibility" },
    [pscustomobject]@{ category = "gigantamax"; reason = "metadata source not integrated"; action = "research and add source mapping before score eligibility" },
    [pscustomobject]@{ category = "purified_indicator"; reason = "generic state, no project-owned descriptor coverage"; action = "collect labeled live screenshots" },
    [pscustomobject]@{ category = "gender_visual_differences"; reason = "not represented in current descriptor catalog"; action = "add project-owned metadata and fixtures" }
) | Export-Csv -NoTypeInformation -Path (Join-Path $ReportRoot "unsupported_variant_categories.csv")

$missingMetadataRows = @($projectVariantRows | Where-Object { $_.descriptorUnsupportedReason } |
    Select-Object catalogKey,speciesId,speciesName,form,variantClass,descriptorUnsupportedReason
)
if ($missingMetadataRows.Count -eq 0) {
    $missingMetadataRows = @([pscustomobject]@{ catalogKey = ""; speciesId = ""; speciesName = ""; form = ""; variantClass = ""; descriptorUnsupportedReason = "" })
}
$missingMetadataRows | Export-Csv -NoTypeInformation -Path (Join-Path $ReportRoot "missing_variant_metadata.csv")

@([pscustomobject]@{ catalogKey = ""; reason = "requires_fetch_pogo_assets"; action = "run asset fetch and integrity report" }) |
    Export-Csv -NoTypeInformation -Path (Join-Path $ReportRoot "ambiguous_assets.csv")
@([pscustomobject]@{ catalogKey = ""; reason = "requires_fetch_pogo_assets"; action = "run asset fetch and join with catalog" }) |
    Export-Csv -NoTypeInformation -Path (Join-Path $ReportRoot "missing_reference_assets.csv")
Write-Host "catalog_entries=$($catalog.Count)"
Write-Host "released_species=$(Count-Props $released)"
