param(
    [string]$TestClass = "com.pokerarity.scanner.ScanRegressionTest"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$localReport = Join-Path $root "build\scan_regression_report.json"
$remoteReport = "/sdcard/Android/data/com.pokerarity.scanner/files/scan_regression_report.json"

Push-Location $root
try {
    .\gradlew.bat connectedDebugAndroidTest --console=plain "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"

    if (Test-Path $localReport) {
        Remove-Item $localReport -Force
    }

    & $adb pull $remoteReport $localReport | Out-Null
    Write-Host "Regression report: $localReport"
} finally {
    Pop-Location
}
