param(
    [string]$CasesPath = "",
    [string]$OutPath = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($CasesPath)) {
    $CasesPath = Join-Path $repoRoot "app\src\androidTest\assets\scan_regression_cases.json"
}
if ([string]::IsNullOrWhiteSpace($OutPath)) {
    $OutPath = Join-Path $repoRoot "build\reports\fixture_tools\fixture_label_template.json"
}
New-Item -ItemType Directory -Force (Split-Path -Parent $OutPath) | Out-Null

function Get-Expected($case, [string]$name) {
    if ($null -ne $case.expected -and $null -ne $case.expected.PSObject.Properties[$name]) {
        return $case.expected.PSObject.Properties[$name].Value
    }
    return $null
}

$fields = @(
    "screenType", "species", "form", "cp", "hp", "maxHp", "caughtDate",
    "datePresent", "shiny", "shadow", "purified", "lucky", "costume",
    "eventCostume", "locationCard", "specialBackground", "dynamax",
    "gigantamax", "mega", "primal", "regionalForm", "specialForm",
    "genderDifference", "subtleShiny", "decision", "scoreMin", "scoreMax",
    "maxLatencyMs", "descriptorTopCandidate", "descriptorMinMargin"
)

$rawCases = Get-Content $CasesPath -Raw | ConvertFrom-Json
$cases = @($rawCases)
$template = foreach ($case in $cases) {
    $expected = [ordered]@{}
    foreach ($field in $fields) {
        $expected[$field] = Get-Expected $case $field
    }
    [pscustomobject]@{
        id = $case.id
        assetPath = $case.assetPath
        split = if ($case.PSObject.Properties["split"]) { $case.split } else { $null }
        labelStatus = if ($case.expected -and $case.expected.species) { "partial" } else { "todo" }
        expected = $expected
        labelNotes = "Fill only visually certain fields. Leave unknown fields null."
    }
}

$template | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $OutPath
Write-Output "label_template=$OutPath"
Write-Output "cases=$($cases.Count)"
