param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [string]$TestClass = "com.pokerarity.scanner.ScanRegressionTest"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$applicationId = "com.pokerarity.scanner.sprint6qa"
$testApplicationId = "$applicationId.test"
$testRunner = "androidx.test.runner.AndroidJUnitRunner"

$sdk = $env:ANDROID_HOME
if (-not $sdk -or -not (Test-Path (Join-Path $sdk "platform-tools\adb.exe"))) {
    $sdk = $env:ANDROID_SDK_ROOT
}
if (-not $sdk -or -not (Test-Path (Join-Path $sdk "platform-tools\adb.exe"))) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "Android SDK platform-tools were not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

$avdLines = & $adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0) {
    throw "Could not verify the selected Android emulator."
}
$avdName = ($avdLines | Where-Object { $_.Trim() -and $_.Trim() -ne "OK" } | Select-Object -First 1).Trim()
if (-not $avdName.StartsWith("PokemonRarityScanner_", [StringComparison]::Ordinal)) {
    throw "Refusing regression tests outside a dedicated PokemonRarityScanner emulator."
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$localReport = Join-Path $root ("build\scan_regression_report_{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss-fff"))
$remoteReport = "/sdcard/Android/data/$applicationId/files/scan_regression_report.json"
$instrumentationComponent = "$testApplicationId/$testRunner"

Push-Location $root
try {
    & .\gradlew.bat :app:assembleQa :app:assembleQaAndroidTest --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Could not build the isolated QA regression packages."
    }

    $appMetadataPath = Join-Path $root "app\build\outputs\apk\qa\output-metadata.json"
    $testMetadataPath = Join-Path $root "app\build\outputs\apk\androidTest\qa\output-metadata.json"
    $appMetadata = Get-Content -LiteralPath $appMetadataPath -Raw | ConvertFrom-Json
    $testMetadata = Get-Content -LiteralPath $testMetadataPath -Raw | ConvertFrom-Json
    if ($appMetadata.applicationId -ne $applicationId -or $testMetadata.applicationId -ne $testApplicationId) {
        throw "The QA APK metadata did not match the isolated package IDs."
    }

    $appApks = @(Get-ChildItem -LiteralPath (Join-Path $root "app\build\outputs\apk\qa") -Filter "*-qa.apk" -File)
    $testApks = @(Get-ChildItem -LiteralPath (Join-Path $root "app\build\outputs\apk\androidTest\qa") -Filter "*-qa-androidTest.apk" -File)
    if ($appApks.Count -ne 1 -or $testApks.Count -ne 1) {
        throw "Expected exactly one isolated QA app APK and instrumentation APK."
    }

    & $adb -s $Serial install -r $appApks[0].FullName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install the isolated QA app on the selected emulator."
    }
    & $adb -s $Serial install -r $testApks[0].FullName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install the isolated QA instrumentation package."
    }

    & $adb -s $Serial shell rm -f $remoteReport | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not prepare the isolated QA report path."
    }

    & $adb -s $Serial shell am instrument -w -r -e class $TestClass $instrumentationComponent | Out-Null
    $instrumentationExitCode = $LASTEXITCODE

    & $adb -s $Serial pull $remoteReport $localReport | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "The isolated QA regression report was not produced. Instrumentation exit code: $instrumentationExitCode"
    }
    Write-Host "Regression report: $localReport"
    if ($instrumentationExitCode -ne 0) {
        throw "Isolated QA regression tests failed with exit code $instrumentationExitCode."
    }
} finally {
    Pop-Location
}
