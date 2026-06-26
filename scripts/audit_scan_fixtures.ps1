param(
    [string]$CasesPath = "",
    [string]$FixtureRoot = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($CasesPath)) {
    $CasesPath = Join-Path $repoRoot "app\src\androidTest\assets\scan_regression_cases.json"
}
if ([string]::IsNullOrWhiteSpace($FixtureRoot)) {
    $FixtureRoot = Join-Path $repoRoot "app\src\androidTest\assets"
}

$cases = New-Object System.Collections.Generic.List[object]
foreach ($case in (Get-Content $CasesPath -Raw | ConvertFrom-Json)) {
    [void]$cases.Add($case)
}
$fixtures = @(Get-ChildItem (Join-Path $FixtureRoot "scan_fixtures") -Recurse -Filter *.png -File)

function Has-ExpectedValue($case, [string]$name) {
    if ($null -eq $case.expected) { return $false }
    $property = $case.expected.PSObject.Properties[$name]
    return $null -ne $property -and $null -ne $property.Value
}

function Has-AnyExpectedValue($case) {
    if ($null -eq $case.expected) { return $false }
    return @($case.expected.PSObject.Properties | Where-Object { $null -ne $_.Value }).Count -gt 0
}

function Has-ExpectedScreenType($case) {
    return (Has-ExpectedValue $case 'screenType') -or $null -ne $case.screenType
}

function Has-ExpectedAppraisal($case) {
    return (Has-ExpectedValue $case 'appraisalAttack') -or
        (Has-ExpectedValue $case 'appraisalDefense') -or
        (Has-ExpectedValue $case 'appraisalStamina')
}

function Has-ExpectedDecision($case) {
    return (Has-ExpectedValue $case 'confidenceDecision') -or
        (Has-ExpectedValue $case 'decision') -or
        (Has-ExpectedValue $case 'expectedDecision') -or
        $null -ne $case.expectedDecision
}

function Has-ExpectedMinConfidence($case) {
    return (Has-ExpectedValue $case 'minConfidence') -or
        (Has-ExpectedValue $case 'expectedMinConfidence') -or
        $null -ne $case.expectedMinConfidence
}

function Has-ExpectedMayShowOverlay($case) {
    return (Has-ExpectedValue $case 'mayShowOverlay') -or
        (Has-ExpectedValue $case 'expectedMayShowOverlay') -or
        $null -ne $case.expectedMayShowOverlay
}

function Has-ExpectedMaySaveScan($case) {
    return (Has-ExpectedValue $case 'maySaveScan') -or
        (Has-ExpectedValue $case 'expectedMaySaveScan') -or
        $null -ne $case.expectedMaySaveScan
}

$missingFixtureFiles = @(
    $cases | Where-Object {
        $assetPath = $_.assetPath
        [string]::IsNullOrWhiteSpace($assetPath) -or -not (Test-Path (Join-Path $FixtureRoot $assetPath))
    }
)
$missingExpectedScreenType = @($cases | Where-Object { -not (Has-ExpectedScreenType $_) })
$missingExpectedSpecies = @($cases | Where-Object { -not (Has-ExpectedValue $_ 'species') })
$missingExpectedForm = @($cases | Where-Object { -not (Has-ExpectedValue $_ 'form') })
$missingExpectedCp = @($cases | Where-Object { -not (Has-ExpectedValue $_ 'cp') })
$missingExpectedHp = @($cases | Where-Object { -not (Has-ExpectedValue $_ 'hp') })
$missingExpectedAppraisal = @($cases | Where-Object { -not (Has-ExpectedAppraisal $_) })
$missingExpectedDecision = @($cases | Where-Object { -not (Has-ExpectedDecision $_) })
$missingExpectedMinConfidence = @($cases | Where-Object { -not (Has-ExpectedMinConfidence $_) })
$missingExpectedMayShowOverlay = @($cases | Where-Object { -not (Has-ExpectedMayShowOverlay $_) })
$missingExpectedMaySaveScan = @($cases | Where-Object { -not (Has-ExpectedMaySaveScan $_) })
$priority1Missing = @($cases | Where-Object {
    -not (Has-ExpectedScreenType $_) -or
        -not (Has-ExpectedValue $_ 'species') -or
        -not (Has-ExpectedDecision $_)
})
$priority2Missing = @($cases | Where-Object {
    -not (Has-ExpectedValue $_ 'cp') -or
        -not (Has-ExpectedValue $_ 'hp') -or
        -not (Has-ExpectedMinConfidence $_) -or
        -not (Has-ExpectedMayShowOverlay $_) -or
        -not (Has-ExpectedMaySaveScan $_)
})
$priority3Missing = @($cases | Where-Object {
    -not (Has-ExpectedValue $_ 'form') -or
        -not (Has-ExpectedAppraisal $_)
})

Write-Output "Scan fixture audit"
Write-Output "cases=$($cases.Count)"
Write-Output "fixtures=$($fixtures.Count)"
Write-Output "strict=$(@($cases | Where-Object { $_.strict -eq $true }).Count)"
Write-Output "all_null_exploratory=$(@($cases | Where-Object { -not (Has-AnyExpectedValue $_) }).Count)"
Write-Output "expected_species=$(@($cases | Where-Object { Has-ExpectedValue $_ 'species' }).Count)"
Write-Output "expected_form=$(@($cases | Where-Object { Has-ExpectedValue $_ 'form' }).Count)"
Write-Output "expected_cp=$(@($cases | Where-Object { Has-ExpectedValue $_ 'cp' }).Count)"
Write-Output "expected_hp=$(@($cases | Where-Object { Has-ExpectedValue $_ 'hp' }).Count)"
Write-Output "expected_appraisal_fields=$(@($cases | Where-Object { Has-ExpectedAppraisal $_ }).Count)"
Write-Output "expected_screen_type=$(@($cases | Where-Object { Has-ExpectedScreenType $_ }).Count)"
Write-Output "expected_confidence_decision=$(@($cases | Where-Object { Has-ExpectedDecision $_ }).Count)"
Write-Output "expected_min_confidence=$(@($cases | Where-Object { Has-ExpectedMinConfidence $_ }).Count)"
Write-Output "expected_may_show_overlay=$(@($cases | Where-Object { Has-ExpectedMayShowOverlay $_ }).Count)"
Write-Output "expected_may_save_scan=$(@($cases | Where-Object { Has-ExpectedMaySaveScan $_ }).Count)"
Write-Output "missing_expected_screen_type=$($missingExpectedScreenType.Count)"
Write-Output "missing_expected_species=$($missingExpectedSpecies.Count)"
Write-Output "missing_expected_form=$($missingExpectedForm.Count)"
Write-Output "missing_expected_cp=$($missingExpectedCp.Count)"
Write-Output "missing_expected_hp=$($missingExpectedHp.Count)"
Write-Output "missing_expected_appraisal_fields=$($missingExpectedAppraisal.Count)"
Write-Output "missing_expected_confidence_decision=$($missingExpectedDecision.Count)"
Write-Output "missing_expected_min_confidence=$($missingExpectedMinConfidence.Count)"
Write-Output "missing_expected_may_show_overlay=$($missingExpectedMayShowOverlay.Count)"
Write-Output "missing_expected_may_save_scan=$($missingExpectedMaySaveScan.Count)"
Write-Output "priority_1_missing_screen_species_decision=$($priority1Missing.Count)"
Write-Output "priority_2_missing_core_fields_gate_flags=$($priority2Missing.Count)"
Write-Output "priority_3_missing_form_appraisal=$($priority3Missing.Count)"
Write-Output "missing_fixture_files=$($missingFixtureFiles.Count)"
Write-Output "screen_type_label_recommendations="
$missingExpectedScreenType |
    Select-Object -First 10 |
    ForEach-Object {
        Write-Output "- $($_.id) asset=$($_.assetPath)"
    }
Write-Output "field_label_recommendations="
$cases |
    Where-Object {
        -not (Has-ExpectedScreenType $_) -or
            -not (Has-ExpectedValue $_ 'cp') -or
            -not (Has-ExpectedValue $_ 'hp') -or
            -not (Has-ExpectedValue $_ 'species') -or
            -not (Has-ExpectedAppraisal $_)
    } |
    Select-Object -First 10 |
    ForEach-Object {
        $missing = @()
        if (-not (Has-ExpectedScreenType $_)) { $missing += "expected_screen_type" }
        if (-not (Has-ExpectedValue $_ 'cp')) { $missing += "expected_cp" }
        if (-not (Has-ExpectedValue $_ 'hp')) { $missing += "expected_hp" }
        if (-not (Has-ExpectedValue $_ 'species')) { $missing += "expected_species" }
        if (-not (Has-ExpectedAppraisal $_)) { $missing += "expected_appraisal_fields" }
        if (-not (Has-ExpectedDecision $_)) { $missing += "expected_confidence_decision" }
        if (-not (Has-ExpectedMinConfidence $_)) { $missing += "expected_min_confidence" }
        if (-not (Has-ExpectedMayShowOverlay $_)) { $missing += "expected_may_show_overlay" }
        if (-not (Has-ExpectedMaySaveScan $_)) { $missing += "expected_may_save_scan" }
        Write-Output "- $($_.id) asset=$($_.assetPath) missing=$($missing -join ',')"
    }
Write-Output "species_form_resolver_label_recommendations="
$cases |
    Where-Object {
        -not (Has-ExpectedValue $_ 'species') -or
            -not (Has-ExpectedValue $_ 'form')
    } |
    Select-Object -First 10 |
    ForEach-Object {
        $missing = @()
        if (-not (Has-ExpectedValue $_ 'species')) { $missing += "expected_species" }
        if (-not (Has-ExpectedValue $_ 'form')) { $missing += "expected_form" }
        Write-Output "- $($_.id) asset=$($_.assetPath) missing=$($missing -join ',')"
    }
Write-Output "confidence_gate_label_recommendations="
$priority1Missing |
    Select-Object -First 10 |
    ForEach-Object {
        $missing = @()
        if (-not (Has-ExpectedScreenType $_)) { $missing += "expected_screen_type" }
        if (-not (Has-ExpectedValue $_ 'species')) { $missing += "expected_species" }
        if (-not (Has-ExpectedDecision $_)) { $missing += "expected_confidence_decision" }
        Write-Output "- $($_.id) asset=$($_.assetPath) missing=$($missing -join ',')"
    }
