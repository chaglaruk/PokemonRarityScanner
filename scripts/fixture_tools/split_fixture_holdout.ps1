param(
    [string]$CasesPath = "",
    [string]$FixtureRoot = "",
    [string]$OutPath = "",
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($CasesPath)) {
    $CasesPath = Join-Path $repoRoot "app\src\androidTest\assets\scan_regression_cases.json"
}
if ([string]::IsNullOrWhiteSpace($FixtureRoot)) {
    $FixtureRoot = Join-Path $repoRoot "app\src\androidTest\assets"
}
if ([string]::IsNullOrWhiteSpace($OutPath)) {
    $OutPath = Join-Path $repoRoot "build\reports\fixture_tools\fixture_holdout_split_preview.json"
}
New-Item -ItemType Directory -Force (Split-Path -Parent $OutPath) | Out-Null
Add-Type -AssemblyName System.Drawing

function Has-Species($case) {
    return $null -ne $case.expected -and
        $null -ne $case.expected.PSObject.Properties["species"] -and
        -not [string]::IsNullOrWhiteSpace([string]$case.expected.species)
}

$rawCases = Get-Content $CasesPath -Raw | ConvertFrom-Json
$cases = @($rawCases)
$eligible = New-Object System.Collections.Generic.List[object]
foreach ($case in $cases) {
    if (-not (Has-Species $case)) { continue }
    $asset = Join-Path $FixtureRoot $case.assetPath
    if (-not (Test-Path $asset)) { continue }
    try {
        $img = [System.Drawing.Image]::FromFile($asset)
        $img.Dispose()
    } catch {
        continue
    }
    $hash = (Get-FileHash -Algorithm SHA256 $asset).Hash
    $eligible.Add([pscustomobject]@{ case = $case; hash = $hash }) | Out-Null
}

$groups = $eligible | Group-Object hash | Sort-Object Name
$assignments = @{}
$groupIndex = 0
foreach ($group in $groups) {
    $bucket = $groupIndex % 10
    $split = if ($bucket -lt 6) { "training" } elseif ($bucket -lt 8) { "calibration" } else { "holdout" }
    foreach ($entry in $group.Group) {
        $assignments[$entry.case.id] = $split
    }
    $groupIndex++
}

$preview = foreach ($case in $cases) {
    [pscustomobject]@{
        id = $case.id
        assetPath = $case.assetPath
        expectedSpecies = if (Has-Species $case) { $case.expected.species } else { $null }
        proposedSplit = if ($assignments.ContainsKey($case.id)) { $assignments[$case.id] } else { "unassigned" }
        reason = if ($assignments.ContainsKey($case.id)) { "species_labeled_hash_grouped" } else { "missing_species_or_asset" }
    }
}
$preview | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $OutPath

if ($Apply) {
    foreach ($case in $cases) {
        $value = if ($assignments.ContainsKey($case.id)) { $assignments[$case.id] } else { "unassigned" }
        if ($case.PSObject.Properties["split"]) {
            $case.split = $value
        } else {
            $case | Add-Member -NotePropertyName "split" -NotePropertyValue $value
        }
    }
    $backup = "$CasesPath.bak_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    Copy-Item -LiteralPath $CasesPath -Destination $backup
    $cases | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $CasesPath
    Write-Output "applied=true"
    Write-Output "backup=$backup"
} else {
    Write-Output "applied=false"
}

Write-Output "split_preview=$OutPath"
Write-Output "eligible_species_labeled=$($eligible.Count)"
Write-Output "hash_groups=$($groups.Count)"
Write-Output "training=$(@($preview | Where-Object { $_.proposedSplit -eq 'training' }).Count)"
Write-Output "calibration=$(@($preview | Where-Object { $_.proposedSplit -eq 'calibration' }).Count)"
Write-Output "holdout=$(@($preview | Where-Object { $_.proposedSplit -eq 'holdout' }).Count)"
