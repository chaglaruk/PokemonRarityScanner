<#
.SYNOPSIS
    Ask the active isolated QA scanner to capture the current Pokemon GO screen once.
.DESCRIPTION
    Sends one explicit shell broadcast to the QA-only receiver. It never grants
    MediaProjection, launches Pokemon GO, or injects game input.
.EXAMPLE
    ./scripts/qa_trigger_live_scan.ps1 -Serial 08131JEC202091 -ApplicationId com.pokerarity.scanner.sprint6qa.diag28
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Serial,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^com\.pokerarity\.scanner\.sprint6qa(?:\.[a-zA-Z0-9_]+)*$')]
    [string]$ApplicationId
)

$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -ErrorAction Stop).Source

$deviceState = ((& $adb -s $Serial get-state 2>&1) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
    throw "ADB serial '$Serial' is not connected and authorized."
}

$sdkText = ((& $adb -s $Serial shell getprop ro.build.version.sdk) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or -not ($sdkText -match '^\d+$') -or [int]$sdkText -lt 34) {
    throw "QA shell scan receiver requires Android API 34+."
}

$installedPaths = @(& $adb -s $Serial shell pm path $ApplicationId)
if ($LASTEXITCODE -ne 0 -or -not ($installedPaths | Where-Object { $_ -match '^package:' })) {
    throw "Isolated QA package '$ApplicationId' is not installed."
}

$activities = (@(& $adb -s $Serial shell dumpsys activity activities) -join [Environment]::NewLine)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read foreground activity; no scan requested.'
}
$foreground = @($activities -split [Environment]::NewLine | Where-Object {
    $_ -match 'mResumedActivity|topResumedActivity|ResumedActivity'
})
if (-not ($foreground | Where-Object { $_ -match 'com\.nianticlabs\.pokemongo/' })) {
    throw 'Pokemon GO is not verified in the foreground; no scan requested. Do not inject game input.'
}

Write-Host "Pokemon GO foreground verified. Requesting one scan from $ApplicationId."
Write-Host 'Foreground species/form and normal/Shiny ground truth still require independent verification.'
$component = "$ApplicationId/com.pokerarity.scanner.qa.QaScanOnceReceiver"
$logStart = ((& $adb -s $Serial shell "date +%m-%d_%H:%M:%S.%3N") | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or -not ($logStart -match '^\d{2}-\d{2}_\d{2}:\d{2}:\d{2}\.\d{3}$')) {
    throw 'Unable to establish a device-log time boundary; no scan requested.'
}
$logStart = $logStart -replace '_', ' '
$output = @(& $adb -s $Serial shell am broadcast --receiver-foreground -a com.pokerarity.scanner.QA_SCAN_ONCE -n $component 2>&1)
$exitCode = $LASTEXITCODE
$output | ForEach-Object { Write-Host $_ }

if ($exitCode -ne 0 -or ($output -join [Environment]::NewLine) -match 'Permission Denial|SecurityException|Exception occurred') {
    throw 'QA shell scan broadcast rejected; do not bypass permission checks.'
}

Start-Sleep -Milliseconds 750
$logs = @(& $adb -s $Serial logcat -d -T $logStart -s 'QaScanOnceReceiver:V' '*:S')
$logExitCode = $LASTEXITCODE
$logs | Where-Object { $_ -match 'QA capture request|QA scan request|QA capture refused' } |
    Select-Object -Last 12 | ForEach-Object { Write-Host $_ }
if ($logExitCode -ne 0) {
    throw 'Unable to read receiver logs after the request; no scan is counted.'
}

if (($logs -join [Environment]::NewLine) -match 'QA scan request rejected|QA capture refused') {
    throw 'QA receiver rejected the request; inspect the receiver diagnostics above. No scan is counted.'
}
if (-not (($logs -join [Environment]::NewLine) -match 'QA capture request accepted|QA capture request coalesced')) {
    throw 'No receiver acceptance was observed; no scan is counted.'
}

Write-Host 'Broadcast dispatched. Verify fresh requestId, capture/OCR/recognition logs,'
Write-Host 'and overlay/History parity before treating this as a successful live scan.'
