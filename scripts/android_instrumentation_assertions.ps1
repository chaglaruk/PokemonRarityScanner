function Assert-AndroidInstrumentationSuccess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [string[]]$Output,
        [Parameter(Mandatory = $true)]
        [int]$ExitCode,
        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $passedTestCount = $null
    $hasFailure = $false
    foreach ($line in $Output) {
        $value = [string]$line
        if ($value -match '^\s*OK \((\d+) tests?\)\s*$') {
            $passedTestCount = [int]$Matches[1]
        }
        if ($value -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=') {
            $hasFailure = $true
        }
    }

    if ($ExitCode -ne 0 -or $null -eq $passedTestCount -or $passedTestCount -lt 1 -or $hasFailure) {
        throw "$Description did not report a successful JUnit instrumentation run (exit code $ExitCode)."
    }
}
