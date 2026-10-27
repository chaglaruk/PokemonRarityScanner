$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDirectory "android_instrumentation_assertions.ps1")

Assert-AndroidInstrumentationSuccess `
    -Output @("INSTRUMENTATION_STATUS_CODE: 0", "OK (1 test)", "INSTRUMENTATION_CODE: -1") `
    -ExitCode 0 `
    -Description "Successful synthetic run"

$failedRunRejected = $false
try {
    Assert-AndroidInstrumentationSuccess `
        -Output @("INSTRUMENTATION_STATUS: stack=AssertionError", "FAILURES!!!", "Tests run: 1, Failures: 1") `
        -ExitCode 0 `
        -Description "Failed synthetic run"
} catch {
    $failedRunRejected = $true
}
if (-not $failedRunRejected) {
    throw "An assertion failure with a zero shell exit code was accepted."
}

$missingSummaryRejected = $false
try {
    Assert-AndroidInstrumentationSuccess `
        -Output @("INSTRUMENTATION_STATUS_CODE: 0") `
        -ExitCode 0 `
        -Description "Incomplete synthetic run"
} catch {
    $missingSummaryRejected = $true
}
if (-not $missingSummaryRejected) {
    throw "Instrumentation output without a JUnit summary was accepted."
}

Write-Output "Instrumentation result assertions passed: success, failed assertion, missing summary."
