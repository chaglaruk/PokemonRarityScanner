param(
    [Parameter(Mandatory = $true)]
    [string]$CaseId
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

& $adb shell am broadcast `
    -n com.pokerarity.scanner/.ui.debug.ScanFixtureExportReceiver `
    -a com.pokerarity.scanner.EXPORT_SCAN_FIXTURES `
    --es case_id $CaseId | Out-Null

$remoteBase = "/storage/emulated/0/Android/data/com.pokerarity.scanner/files/fixtures/$CaseId"
$remoteExists = (& $adb shell "[ -d '$remoteBase' ] && echo ok").Trim()

if ($remoteExists -ne "ok") {
    throw "Could not resolve exported fixture directory for case '$CaseId'"
}

$localTarget = Join-Path $root "exported_fixtures\$CaseId"
New-Item -ItemType Directory -Force -Path $localTarget | Out-Null

& $adb pull $remoteBase $localTarget | Out-Null

Write-Host "Exported fixture directory:"
Write-Host $localTarget
