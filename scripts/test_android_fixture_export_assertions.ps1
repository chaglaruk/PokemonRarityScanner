$ErrorActionPreference = 'Stop'
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDirectory 'android_fixture_export_assertions.ps1')

$caseId = 'fixture-case_01'
$manifest = @(
    [PSCustomObject]@{ assetPath = "scan_fixtures/$caseId/scan_one.png" },
    [PSCustomObject]@{ assetPath = "scan_fixtures/$caseId/scan_two.png" }
)
Assert-FixtureExportInventory -CaseId $caseId -ManifestEntries $manifest `
    -PngNames @('scan_one.png', 'scan_two.png')

function Assert-FixtureInventoryRejected {
    param(
        [object[]]$ManifestEntries,
        [string[]]$PngNames
    )

    $rejected = $false
    try {
        Assert-FixtureExportInventory -CaseId $caseId -ManifestEntries $ManifestEntries -PngNames $PngNames
    } catch {
        $rejected = $true
    }
    if (-not $rejected) {
        throw 'An inconsistent fixture export inventory was accepted.'
    }
}

Assert-FixtureInventoryRejected -ManifestEntries $manifest -PngNames @('scan_one.png')
Assert-FixtureInventoryRejected -ManifestEntries $manifest -PngNames @('scan_one.png', 'scan_two.png', 'extra.png')
Assert-FixtureInventoryRejected `
    -ManifestEntries @([PSCustomObject]@{ assetPath = "scan_fixtures/$caseId/../other.png" }) `
    -PngNames @('other.png')

Write-Output 'Fixture export inventory assertions passed: exact inventory, missing/extra PNG, and unsafe path.'
