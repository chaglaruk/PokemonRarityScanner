param(
    [string]$PackageName = "com.pokerarity.scanner",
    [string]$OutDir = "",
    [switch]$IncludeScreenshots
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $repoRoot ("build\reports\fixture_tools\device_import_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

function Test-AdbAvailable {
    try {
        & adb version *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Copy-RunAsFile([string]$RemotePath, [string]$DestinationPath) {
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = "adb"
    $psi.Arguments = "exec-out run-as $PackageName cat `"$RemotePath`""
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($psi)
    $fs = [System.IO.File]::Open($DestinationPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $process.StandardOutput.BaseStream.CopyTo($fs)
    } finally {
        $fs.Close()
        $process.WaitForExit()
    }
    if ($process.ExitCode -ne 0) {
        Remove-Item -LiteralPath $DestinationPath -ErrorAction SilentlyContinue
        return $false
    }
    return $true
}

$report = New-Object System.Collections.Generic.List[string]
$report.Add("# Device Diagnostic Import") | Out-Null
$report.Add("") | Out-Null
$report.Add("package=$PackageName") | Out-Null
$report.Add("out_dir=$OutDir") | Out-Null
$report.Add("created=$((Get-Date).ToString('o'))") | Out-Null
$report.Add("") | Out-Null

if (-not (Test-AdbAvailable)) {
    $report.Add("status=NO_ADB") | Out-Null
    $report | Set-Content -Encoding UTF8 (Join-Path $OutDir "import_report.md")
    Write-Output "status=NO_ADB"
    Write-Output "report=$(Join-Path $OutDir "import_report.md")"
    exit 0
}

$devices = @(& adb devices | Select-String -Pattern "\tdevice$")
if ($devices.Count -eq 0) {
    $report.Add("status=NO_DEVICE") | Out-Null
    $report | Set-Content -Encoding UTF8 (Join-Path $OutDir "import_report.md")
    Write-Output "status=NO_DEVICE"
    Write-Output "report=$(Join-Path $OutDir "import_report.md")"
    exit 0
}

$patterns = @("*.json", "*.txt", "*.log")
if ($IncludeScreenshots) {
    $patterns += @("*.png", "*.webp", "*.jpg", "*.jpeg")
}
$findExpression = ($patterns | ForEach-Object { "-name '$($_)'" }) -join " -o "
$remoteFiles = @(& adb shell run-as $PackageName sh -c "find files cache -type f \( $findExpression \) 2>/dev/null" 2>$null)

$copied = 0
$failed = 0
$rows = New-Object System.Collections.Generic.List[object]
foreach ($remote in $remoteFiles) {
    $remote = ($remote | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($remote)) { continue }
    $safe = $remote -replace '[:\\/*?"<>|]', '_'
    $dest = Join-Path $OutDir $safe
    $ok = Copy-RunAsFile $remote $dest
    if ($ok) { $copied++ } else { $failed++ }
    $rows.Add([pscustomobject]@{
        remotePath = $remote
        localPath = if ($ok) { $dest } else { "" }
        copied = $ok
    }) | Out-Null
}

$rows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "imported_files.csv")
$report.Add("status=OK") | Out-Null
$report.Add("files_seen=$($remoteFiles.Count)") | Out-Null
$report.Add("files_copied=$copied") | Out-Null
$report.Add("files_failed=$failed") | Out-Null
$report.Add("") | Out-Null
$report.Add("Only app-private files available through run-as were copied. Nothing is uploaded.") | Out-Null
$report | Set-Content -Encoding UTF8 (Join-Path $OutDir "import_report.md")

Write-Output "status=OK"
Write-Output "files_seen=$($remoteFiles.Count)"
Write-Output "files_copied=$copied"
Write-Output "files_failed=$failed"
Write-Output "out_dir=$OutDir"
