param(
    [Parameter(Mandatory = $true)]
    [string]$CaseId,
    [Parameter(Mandatory = $true)]
    [string]$Serial
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$applicationId = "com.pokerarity.scanner.sprint6qa"
$testApplicationId = "$applicationId.test"
$testClass = "com.pokerarity.scanner.ScanFixtureExportTest#exportCase"
$testRunner = "androidx.test.runner.AndroidJUnitRunner"
. (Join-Path $PSScriptRoot "android_instrumentation_assertions.ps1")
if ($CaseId -notmatch "^[A-Za-z0-9_-]{1,80}$") {
    throw "CaseId must contain only letters, numbers, underscores, or hyphens."
}

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

$remoteBase = "/storage/emulated/0/Android/data/$applicationId/files/fixtures/$CaseId"
$remoteExists = (& $adb -s $Serial shell "[ -d '$remoteBase' ] && echo ok").Trim()
if ($remoteExists -eq "ok") {
    throw "CaseId already exists on the selected device. Choose a new CaseId to preserve earlier exports."
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
Push-Location $root
try {
    & .\gradlew.bat :app:assembleQa :app:assembleQaAndroidTest --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Could not build the isolated QA app and instrumentation packages."
    }

    $appMetadataPath = Join-Path $root "app\build\outputs\apk\qa\output-metadata.json"
    $testMetadataPath = Join-Path $root "app\build\outputs\apk\androidTest\qa\output-metadata.json"
    $appMetadata = Get-Content -LiteralPath $appMetadataPath -Raw | ConvertFrom-Json
    $testMetadata = Get-Content -LiteralPath $testMetadataPath -Raw | ConvertFrom-Json
    if ($appMetadata.applicationId -ne $applicationId) {
        throw "The QA app APK metadata did not match the isolated app package ID."
    }
    if ($testMetadata.applicationId -ne $testApplicationId) {
        throw "The QA instrumentation APK metadata did not match the isolated test package ID."
    }

    $appApks = @(Get-ChildItem -LiteralPath (Join-Path $root "app\build\outputs\apk\qa") -Filter "*-qa.apk" -File)
    $testApks = @(Get-ChildItem -LiteralPath (Join-Path $root "app\build\outputs\apk\androidTest\qa") -Filter "*-qa-androidTest.apk" -File)
    if ($appApks.Count -ne 1 -or $testApks.Count -ne 1) {
        throw "Expected exactly one isolated QA app APK and instrumentation APK."
    }

    & $adb -s $Serial install -r $appApks[0].FullName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install the isolated QA app while preserving its existing app data."
    }

    & $adb -s $Serial install -r $testApks[0].FullName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install the isolated QA instrumentation package."
    }

    $installedApp = & $adb -s $Serial shell pm path $applicationId
    if ($LASTEXITCODE -ne 0 -or -not $installedApp) {
        throw "The isolated QA app package was not installed on the selected device."
    }
    $installedTest = & $adb -s $Serial shell pm path $testApplicationId
    if ($LASTEXITCODE -ne 0 -or -not $installedTest) {
        throw "The isolated QA instrumentation package was not installed on the selected device."
    }

    $instrumentationComponent = "$testApplicationId/$testRunner"
    $instrumentationOutput = @(& $adb -s $Serial shell am instrument -w -r -e class $testClass -e case_id $CaseId $instrumentationComponent 2>&1)
    $instrumentationExitCode = $LASTEXITCODE
    Assert-AndroidInstrumentationSuccess `
        -Output $instrumentationOutput `
        -ExitCode $instrumentationExitCode `
        -Description "The isolated QA fixture export"

    $remoteExists = (& $adb -s $Serial shell "[ -d '$remoteBase' ] && echo ok").Trim()
    if ($remoteExists -ne "ok") {
        throw "The isolated scanner did not produce a fixture export. Instrumentation exit code: $instrumentationExitCode"
    }

    $exportRoot = Join-Path $env:LOCALAPPDATA "PokemonRarityScanner\exported_fixtures"
    $localTarget = Join-Path $exportRoot ("{0}-{1}" -f $CaseId, (Get-Date -Format "yyyyMMdd-HHmmss-fff"))
    New-Item -ItemType Directory -Force -Path $localTarget | Out-Null
    & $adb -s $Serial pull $remoteBase $localTarget | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not retrieve the exported scan fixture files."
    }
    $exportedCase = Join-Path $localTarget $CaseId
    $manifestPath = Join-Path $exportedCase "manifest_template.json"
    $exportedPngs = @(Get-ChildItem -LiteralPath $exportedCase -Filter "*.png" -File -ErrorAction SilentlyContinue)
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf) -or $exportedPngs.Count -eq 0) {
        throw "The isolated QA fixture export did not contain its manifest and at least one PNG."
    }
    Write-Host "Exported fixture directory:"
    Write-Host $localTarget
} finally {
    Pop-Location
}
