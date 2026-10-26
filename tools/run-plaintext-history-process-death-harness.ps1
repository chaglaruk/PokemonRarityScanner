# Build with `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest` and install
# both APKs on a disposable API 36 emulator using `adb -s <emulator serial> install <apk>`.
# Do not use connectedAndroidTest: this controller targets only the supplied emulator serial.
# Use a temporary application ID suffix and pass the resolved suffixed app/test package IDs.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Serial,

    [ValidatePattern('^[a-f0-9]{32}$')]
    [string]$RunId = [Guid]::NewGuid().ToString('N'),

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]*$')]
    [string]$TargetPackage,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]*$')]
    [string]$TestPackage,

    [ValidateRange(30, 240)]
    [int]$CheckpointTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$adbCommand = Get-Command adb -ErrorAction Stop
$adbPath = $adbCommand.Source
$testSelector = 'com.pokerarity.scanner.data.local.db.PlaintextHistoryProcessDeathHarnessTest#processDeathHarnessOptIn'
$runner = "$TestPackage/androidx.test.runner.AndroidJUnitRunner"
$phaseArgument = 'prs_history_harness_phase'
$runArgument = 'prs_history_harness_run'
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
        throw 'An operation failed on the selected emulator.'
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
    if ((Invoke-SelectedAdb @('shell', 'getprop', 'ro.kernel.qemu')) -ne '1' -or
        (Invoke-SelectedAdb @('shell', 'getprop', 'ro.build.version.sdk')) -ne '36') {
        throw 'The selected device must be an API 36 emulator.'
    }
    if (-not (Invoke-SelectedAdb @('shell', 'pm', 'path', $TargetPackage)) -or
        -not (Invoke-SelectedAdb @('shell', 'pm', 'path', $TestPackage))) {
        throw 'Install the Debug and DebugAndroidTest APKs on the disposable emulator first.'
    }

    $prepareArguments = @(
        '-s', $Serial, 'shell', 'am', 'instrument', '-w',
        '-e', 'class', $testSelector,
        '-e', $phaseArgument, 'prepare',
        '-e', $runArgument, $RunId,
        $runner
    )
    $firstRun = Start-Process -FilePath $adbPath -ArgumentList $prepareArguments -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($CheckpointTimeoutSeconds)
    $checkpointObserved = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($firstRun.HasExited) {
            throw 'The prepare invocation exited before the ready-stage checkpoint.'
        }
        $marker = & $adbPath -s $Serial shell run-as $TargetPackage cat $checkpointRelativePath 2>$null
        if ($LASTEXITCODE -eq 0 -and [string]::Join('', $marker).Trim() -ceq 'READY') {
            $checkpointObserved = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $checkpointObserved -or $firstRun.HasExited) {
        throw 'The prepare invocation did not remain paused at its checkpoint.'
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

    Write-Output 'Synthetic process-death recovery passed on the selected API 36 emulator.'
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
