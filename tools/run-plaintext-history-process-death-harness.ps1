# Build with .\gradlew.bat :app:assembleQa :app:assembleQaAndroidTest
# and install both APKs on an API 36 emulator or the authorized Pixel 4a QA package using adb.
# Do not use connectedAndroidTest: this controller targets only the supplied device serial.
# Physical execution requires -AllowAuthorizedPixel, a matching -AuthorizedPixelSerial,
# and the isolated .sprint6qa package IDs.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string]$Serial,

    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string]$AuthorizedPixelSerial,

    [ValidatePattern('^[a-f0-9]{32}$')]
    [string]$RunId = [Guid]::NewGuid().ToString('N'),

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]*$')]
    [string]$TargetPackage,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]*$')]
    [string]$TestPackage,

    [switch]$AllowAuthorizedPixel,

    [ValidateRange(30, 240)]
    [int]$CheckpointTimeoutSeconds = 180,

    [ValidateSet('stage-ready', 'after-promotion', 'after-activation-marker', 'after-source-main-delete')]
    [string]$InterruptionPoint = 'stage-ready'
)

$ErrorActionPreference = 'Stop'
$adbCommand = Get-Command adb -ErrorAction Stop
$adbPath = $adbCommand.Source
$testSelector = 'com.pokerarity.scanner.data.local.db.PlaintextHistoryProcessDeathHarnessTest#processDeathHarnessOptIn'
$runner = "$TestPackage/androidx.test.runner.AndroidJUnitRunner"
$phaseArgument = 'prs_history_harness_phase'
$runArgument = 'prs_history_harness_run'
$interruptionArgument = 'prs_history_harness_interruption'
$expectedCheckpoint = switch ($InterruptionPoint) {
    'stage-ready' { 'READY' }
    'after-promotion' { 'PROMOTED' }
    'after-activation-marker' { 'ACTIVATED' }
    'after-source-main-delete' { 'SOURCE_MAIN_DELETED' }
}
$checkpointRelativePath = "files/sprint6-sqlcipher-process-death/$RunId/checkpoint"
$firstRun = $null
$recoveryRun = $null
Write-Output "run_id=$RunId"
$stdoutPath = Join-Path ([IO.Path]::GetTempPath()) ("prs-harness-" + [Guid]::NewGuid().ToString('N') + '.out')
$stderrPath = Join-Path ([IO.Path]::GetTempPath()) ("prs-harness-" + [Guid]::NewGuid().ToString('N') + '.err')
$recoveryStdoutPath = Join-Path ([IO.Path]::GetTempPath()) ("prs-harness-recovery-" + [Guid]::NewGuid().ToString('N') + '.out')
$recoveryStderrPath = Join-Path ([IO.Path]::GetTempPath()) ("prs-harness-recovery-" + [Guid]::NewGuid().ToString('N') + '.err')

function Invoke-SelectedAdb {
    param([string[]]$Arguments)

    $output = & $script:adbPath -s $script:Serial @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'An operation failed on the selected test device.'
    }
    return [string]::Join("`n", $output).Trim()
}

function Stop-HarnessPackages {
    foreach ($package in @($script:TargetPackage, $script:TestPackage) | Select-Object -Unique) {
        & $script:adbPath -s $script:Serial shell am force-stop $package 2>$null | Out-Null
    }
}

try {
    if (-not $TargetPackage.StartsWith('com.pokerarity.scanner.', [StringComparison]::Ordinal) -or
        $TestPackage -ne "$TargetPackage.test") {
        throw 'Provide a suffixed app package and its matching test package.'
    }
    $isEmulator = (Invoke-SelectedAdb @('shell', 'getprop', 'ro.kernel.qemu')) -eq '1'
    $apiLevel = Invoke-SelectedAdb @('shell', 'getprop', 'ro.build.version.sdk')
    if ($apiLevel -ne '36') {
        throw 'The selected test device must run API 36.'
    }
    if (-not $isEmulator) {
        if (-not $AllowAuthorizedPixel -or [string]::IsNullOrWhiteSpace($AuthorizedPixelSerial) -or
            -not [string]::Equals($Serial, $AuthorizedPixelSerial, [StringComparison]::Ordinal)) {
            throw 'Physical-device execution requires explicit Pixel authorization and a matching authorized serial.'
        }
        $deviceProduct = Invoke-SelectedAdb @('shell', 'getprop', 'ro.product.device')
        $deviceModel = Invoke-SelectedAdb @('shell', 'getprop', 'ro.product.model')
        if ($deviceProduct -ne 'sunfish' -or $deviceModel -ne 'Pixel 4a' -or
            $TargetPackage -ne 'com.pokerarity.scanner.sprint6qa' -or
            $TestPackage -ne 'com.pokerarity.scanner.sprint6qa.test') {
            throw 'Physical execution is limited to the API 36 Pixel 4a and its isolated Sprint 6 QA packages.'
        }
    }
    if (-not (Invoke-SelectedAdb @('shell', 'pm', 'path', $TargetPackage)) -or
        -not (Invoke-SelectedAdb @('shell', 'pm', 'path', $TestPackage))) {
        throw 'Install the matching QA app and instrumentation APKs on the selected API 36 test device first.'
    }

    $prepareArguments = @(
        '-s', $Serial, 'shell', 'am', 'instrument', '-w',
        '-e', 'class', $testSelector,
        '-e', $phaseArgument, 'prepare',
        '-e', $runArgument, $RunId,
        '-e', $interruptionArgument, $InterruptionPoint,
        $runner
    )
    $firstRun = Start-Process -FilePath $adbPath -ArgumentList $prepareArguments -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($CheckpointTimeoutSeconds)
    $checkpointObserved = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($firstRun.HasExited) {
            $prepareOutput = Get-Content -LiteralPath $stdoutPath -Raw -ErrorAction SilentlyContinue
            $failureSummary = @(
                ($prepareOutput -split "`r?`n") |
                    Where-Object { $_ -match '^(FAILURES!!!|INSTRUMENTATION_RESULT: (shortMsg|stream)=|INSTRUMENTATION_STATUS: (Error|stack)=|java\.lang\.(AssertionError|IllegalStateException))' } |
                    Select-Object -First 8
            )
            if ($failureSummary.Count -gt 0) {
                throw "The prepare invocation exited before the selected checkpoint: $([string]::Join(' | ', $failureSummary))"
            }
            throw 'The prepare invocation exited before the selected checkpoint without a classified instrumentation error.'
        }
        $marker = & $adbPath -s $Serial shell run-as $TargetPackage cat $checkpointRelativePath 2>$null
        if ($LASTEXITCODE -eq 0 -and [string]::Join('', $marker).Trim() -ceq $expectedCheckpoint) {
            $checkpointObserved = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $checkpointObserved -or $firstRun.HasExited) {
        throw 'The prepare invocation did not remain paused at the selected checkpoint.'
    }

    Stop-HarnessPackages
    if (-not $firstRun.WaitForExit(30000)) {
        $firstRun.Kill()
        $firstRun.WaitForExit()
    }
    foreach ($package in @($TargetPackage, $TestPackage) | Select-Object -Unique) {
        $processIds = & $adbPath -s $Serial shell pidof $package 2>$null
        if ($LASTEXITCODE -eq 0 -and [string]::Join('', $processIds).Trim()) {
            throw 'A harness process remained after force-stop.'
        }
    }

    $recoveryArguments = @(
        '-s', $Serial, 'shell', 'am', 'instrument', '-w',
        '-e', 'class', $testSelector,
        '-e', $phaseArgument, 'recover',
        '-e', $runArgument, $RunId,
        '-e', $interruptionArgument, $InterruptionPoint,
        $runner
    )
    $recoveryRun = Start-Process -FilePath $adbPath -ArgumentList $recoveryArguments -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $recoveryStdoutPath -RedirectStandardError $recoveryStderrPath
    if (-not $recoveryRun.WaitForExit($CheckpointTimeoutSeconds * 1000)) {
        Stop-HarnessPackages
        if (-not $recoveryRun.WaitForExit(30000)) {
            $recoveryRun.Kill()
            $recoveryRun.WaitForExit()
        }
        throw 'The recovery invocation exceeded its timeout.'
    }
    $recoveryOutput = Get-Content -LiteralPath $recoveryStdoutPath -Raw -ErrorAction SilentlyContinue
    if ($recoveryRun.ExitCode -ne 0 -or $recoveryOutput -notmatch 'OK \(1 test\)') {
        throw 'The second instrumentation invocation did not report one passing recovery test.'
    }

    Write-Output "Synthetic process-death recovery passed at '$InterruptionPoint' on the selected API 36 test device."
} finally {
    if ($null -ne $recoveryRun -and -not $recoveryRun.HasExited) {
        Stop-HarnessPackages
        if (-not $recoveryRun.WaitForExit(15000)) {
            $recoveryRun.Kill()
            $recoveryRun.WaitForExit()
        }
    }
    if ($null -ne $firstRun) {
        Stop-HarnessPackages
        if (-not $firstRun.HasExited -and -not $firstRun.WaitForExit(15000)) {
            $firstRun.Kill()
            $firstRun.WaitForExit()
        }
    }
    Remove-Item -LiteralPath $stdoutPath, $stderrPath, $recoveryStdoutPath, $recoveryStderrPath `
        -Force -ErrorAction SilentlyContinue
}
