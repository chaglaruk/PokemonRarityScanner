param(
    [string]$CasesPath = "",
    [string]$FixtureRoot = "",
    [string]$OutDir = "",
    [switch]$Strict
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($CasesPath)) {
    $CasesPath = Join-Path $repoRoot "app\src\androidTest\assets\scan_regression_cases.json"
}
if ([string]::IsNullOrWhiteSpace($FixtureRoot)) {
    $FixtureRoot = Join-Path $repoRoot "app\src\androidTest\assets"
}
if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $repoRoot "build\reports\fixture_tools"
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

Add-Type -AssemblyName System.Drawing

function Get-Expected($case, [string]$name) {
    if ($null -ne $case.expected -and $null -ne $case.expected.PSObject.Properties[$name]) {
        return $case.expected.PSObject.Properties[$name].Value
    }
    return $null
}

function Has-Value($value) {
    return $null -ne $value -and -not ([string]::IsNullOrWhiteSpace([string]$value))
}

function Get-Split($case) {
    foreach ($name in @("split", "fixtureSplit", "dataSplit")) {
        if ($case.PSObject.Properties[$name] -and (Has-Value $case.PSObject.Properties[$name].Value)) {
            return ([string]$case.PSObject.Properties[$name].Value).ToLowerInvariant()
        }
    }
    return "unassigned"
}

$rawCases = Get-Content $CasesPath -Raw | ConvertFrom-Json
$cases = @($rawCases)
$fixtureFiles = @(Get-ChildItem (Join-Path $FixtureRoot "scan_fixtures") -Recurse -Filter *.png -File)
$referencedAssets = New-Object System.Collections.Generic.HashSet[string]
$duplicateIds = @(
    $cases |
        Group-Object id |
        Where-Object { $_.Count -gt 1 } |
        ForEach-Object { [pscustomobject]@{ id = $_.Name; count = $_.Count } }
)
$duplicateAssetPaths = @(
    $cases |
        Group-Object assetPath |
        Where-Object { $_.Name -and $_.Count -gt 1 } |
        ForEach-Object { [pscustomobject]@{ assetPath = $_.Name; count = $_.Count } }
)
$rows = New-Object System.Collections.Generic.List[object]
$decodeErrors = New-Object System.Collections.Generic.List[object]
$hashBySplit = @{}
foreach ($case in $cases) {
    $asset = Join-Path $FixtureRoot $case.assetPath
    [void]$referencedAssets.Add([string]$case.assetPath)
    $decodeStatus = "missing"
    $sha = ""
    if (Test-Path $asset) {
        try {
            $img = [System.Drawing.Image]::FromFile($asset)
            $img.Dispose()
            $decodeStatus = "decoded"
            $sha = (Get-FileHash -Algorithm SHA256 $asset).Hash
        } catch {
            $decodeStatus = "undecodable"
            $decodeErrors.Add([pscustomobject]@{ id = $case.id; assetPath = $case.assetPath; reason = $_.Exception.Message }) | Out-Null
        }
    } else {
        $decodeErrors.Add([pscustomobject]@{ id = $case.id; assetPath = $case.assetPath; reason = "missing_file" }) | Out-Null
    }

    $split = Get-Split $case
    if ($sha -ne "") {
        if (-not $hashBySplit.ContainsKey($sha)) { $hashBySplit[$sha] = New-Object System.Collections.Generic.HashSet[string] }
        [void]$hashBySplit[$sha].Add($split)
    }
    $rows.Add([pscustomobject]@{
        id = $case.id
        assetPath = $case.assetPath
        split = $split
        decodeStatus = $decodeStatus
        expectedSpecies = Get-Expected $case "species"
        expectedScreenType = Get-Expected $case "screenType"
        expectedDecision = Get-Expected $case "decision"
        expectedDate = Get-Expected $case "caughtDate"
        shiny = Get-Expected $case "shiny"
        shadow = Get-Expected $case "shadow"
        purified = Get-Expected $case "purified"
        lucky = Get-Expected $case "lucky"
        costume = Get-Expected $case "costume"
        locationCard = Get-Expected $case "locationCard"
    }) | Out-Null
}

$unreferencedFixtures = @(
    $fixtureFiles |
        Where-Object {
            $relative = $_.FullName.Substring($FixtureRoot.Length).TrimStart('\', '/')
            -not $referencedAssets.Contains($relative)
        } |
        ForEach-Object {
            [pscustomobject]@{
                assetPath = $_.FullName.Substring($FixtureRoot.Length).TrimStart('\', '/')
                fullPath = $_.FullName
            }
        }
)

$decodableSpeciesHoldout = @($rows | Where-Object {
    $_.split -eq "holdout" -and $_.decodeStatus -eq "decoded" -and (Has-Value $_.expectedSpecies)
})

$variantChecks = @(
    @{ name = "shiny"; fields = @("shiny") },
    @{ name = "shadow"; fields = @("shadow") },
    @{ name = "purified"; fields = @("purified") },
    @{ name = "lucky"; fields = @("lucky") },
    @{ name = "costume"; fields = @("costume", "eventCostume") },
    @{ name = "special_background_location_card"; fields = @("specialBackground", "locationCard") },
    @{ name = "dynamax"; fields = @("dynamax") },
    @{ name = "gigantamax"; fields = @("gigantamax") },
    @{ name = "mega_primal"; fields = @("mega", "primal") },
    @{ name = "regional_form"; fields = @("regionalForm") },
    @{ name = "special_form"; fields = @("specialForm") },
    @{ name = "gender_visual_difference"; fields = @("genderDifference") },
    @{ name = "subtle_shiny"; fields = @("subtleShiny") }
)

$variantRows = New-Object System.Collections.Generic.List[object]
foreach ($check in $variantChecks) {
    $positive = 0
    $negative = 0
    foreach ($case in $cases) {
        if ((Get-Split $case) -ne "holdout") { continue }
        $asset = Join-Path $FixtureRoot $case.assetPath
        if (-not (Test-Path $asset)) { continue }
        $values = @()
        foreach ($field in $check.fields) { $values += (Get-Expected $case $field) }
        if ($values -contains $true) { $positive++ }
        if ($values -contains $false) { $negative++ }
    }
    $variantRows.Add([pscustomobject]@{
        variantFlag = $check.name
        holdoutPositive = $positive
        holdoutNegative = $negative
        ready = ($positive -ge 3 -and $negative -ge 3)
    }) | Out-Null
}

$leakRows = New-Object System.Collections.Generic.List[object]
foreach ($key in $hashBySplit.Keys) {
    $splits = @($hashBySplit[$key])
    $meaningful = @($splits | Where-Object { $_ -ne "unassigned" })
    if ($meaningful.Count -gt 1) {
        $leakRows.Add([pscustomobject]@{ sourceHash = $key; splits = ($meaningful -join ",") }) | Out-Null
    }
}

$descriptorReady = $decodableSpeciesHoldout.Count -ge 50 -and
    @($variantRows | Where-Object { $_.ready -ne $true }).Count -eq 0 -and
    $leakRows.Count -eq 0 -and
    $decodeErrors.Count -eq 0

$rows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_label_validation.csv")
$variantRows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_variant_holdout_coverage.csv")
$decodeErrors | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_decode_errors.csv")
$leakRows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_holdout_leakage.csv")
$duplicateIds | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_duplicate_ids.csv")
$duplicateAssetPaths | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_duplicate_assets.csv")
$unreferencedFixtures | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_unreferenced_assets.csv")

$report = New-Object System.Collections.Generic.List[string]
$report.Add("# Fixture Label Validation") | Out-Null
$report.Add("") | Out-Null
$report.Add("cases=$($cases.Count)") | Out-Null
$report.Add("decoded=$(@($rows | Where-Object { $_.decodeStatus -eq 'decoded' }).Count)") | Out-Null
$report.Add("decode_errors=$($decodeErrors.Count)") | Out-Null
$report.Add("species_labeled=$(@($rows | Where-Object { Has-Value $_.expectedSpecies }).Count)") | Out-Null
$report.Add("holdout_species_labeled_decodable=$($decodableSpeciesHoldout.Count)") | Out-Null
$report.Add("holdout_leakage_groups=$($leakRows.Count)") | Out-Null
$report.Add("duplicate_ids=$($duplicateIds.Count)") | Out-Null
$report.Add("duplicate_asset_paths=$($duplicateAssetPaths.Count)") | Out-Null
$report.Add("unreferenced_fixture_pngs=$($unreferencedFixtures.Count)") | Out-Null
$report.Add("descriptor_readiness=$(if ($descriptorReady) { 'READY' } else { 'NOT_READY' })") | Out-Null
$report.Add("") | Out-Null
$report.Add("Descriptor readiness requires at least 50 decodable species-labeled holdout screenshots and at least 3 positive plus 3 negative holdout screenshots for each major variant flag.") | Out-Null
$report.Add("") | Out-Null
$report.Add("## Variant Holdout Coverage") | Out-Null
foreach ($row in $variantRows) {
    $report.Add("* $($row.variantFlag): positive=$($row.holdoutPositive), negative=$($row.holdoutNegative), ready=$($row.ready)") | Out-Null
}
if ($decodeErrors.Count -gt 0) {
    $report.Add("") | Out-Null
    $report.Add("## Decode Errors") | Out-Null
    foreach ($err in ($decodeErrors | Select-Object -First 20)) {
        $report.Add("* $($err.id): $($err.assetPath) - $($err.reason)") | Out-Null
    }
}
$reportPath = Join-Path $OutDir "fixture_label_validation.md"
$report | Set-Content -Encoding UTF8 $reportPath

Write-Output "fixture_label_validation=$reportPath"
Write-Output "cases=$($cases.Count)"
Write-Output "decoded=$(@($rows | Where-Object { $_.decodeStatus -eq 'decoded' }).Count)"
Write-Output "decode_errors=$($decodeErrors.Count)"
Write-Output "holdout_species_labeled_decodable=$($decodableSpeciesHoldout.Count)"
Write-Output "descriptor_readiness=$(if ($descriptorReady) { 'READY' } else { 'NOT_READY' })"

if ($Strict -and -not $descriptorReady) {
    throw "Fixture descriptor readiness gate failed. See $reportPath"
}
