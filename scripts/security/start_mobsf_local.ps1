[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 8000,

    [switch]$Stop,

    [switch]$NoBrowser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Image = "opensecurity/mobile-security-framework-mobsf:v4.5.1"
$ContainerName = "pokerarity-mobsf"
$LocalUrl = "http://127.0.0.1:$Port"

function Invoke-Docker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [switch]$AllowFailure
    )

    & docker @Arguments
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "Docker command failed with exit code $exitCode: docker $($Arguments -join ' ')"
    }
}

function Get-FirstOutputLine {
    param([object[]]$Value)

    $line = $Value |
        ForEach-Object { [string]$_ } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -First 1

    if ($null -eq $line) {
        return ""
    }

    return $line.Trim()
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Install and start Docker Desktop before running MobSF."
}

Invoke-Docker -Arguments @("info", "--format", "{{.ServerVersion}}") | Out-Null

$existingContainerOutput = @(
    & docker ps -a --filter "name=^/$ContainerName$" --format "{{.Names}}" 2>$null
)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to query existing Docker containers."
}
$existingContainer = Get-FirstOutputLine -Value $existingContainerOutput

if ($Stop) {
    if ($existingContainer -eq $ContainerName) {
        Invoke-Docker -Arguments @("rm", "--force", $ContainerName) | Out-Null
        Write-Host "Stopped and removed $ContainerName." -ForegroundColor Green
    }
    else {
        Write-Host "$ContainerName is not running." -ForegroundColor Yellow
    }
    return
}

if ($existingContainer -eq $ContainerName) {
    Write-Host "Removing the previous local MobSF container..." -ForegroundColor Yellow
    Invoke-Docker -Arguments @("rm", "--force", $ContainerName) | Out-Null
}

$portInUse = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($portInUse) {
    throw "TCP port $Port is already in use. Re-run with another port, for example -Port 8080."
}

Write-Host "Pulling pinned MobSF image $Image..." -ForegroundColor Cyan
Invoke-Docker -Arguments @("pull", $Image) | Out-Null

$repoDigestOutput = @(
    & docker image inspect --format "{{index .RepoDigests 0}}" $Image 2>$null
)
$repoDigest = if ($LASTEXITCODE -eq 0) {
    Get-FirstOutputLine -Value $repoDigestOutput
}
else {
    ""
}
if ($repoDigest) {
    Write-Host "Resolved image digest: $repoDigest" -ForegroundColor DarkGray
}

Write-Host "Starting MobSF on loopback only: $LocalUrl" -ForegroundColor Cyan
Invoke-Docker -Arguments @(
    "run",
    "--detach",
    "--rm",
    "--name", $ContainerName,
    "--publish", "127.0.0.1:${Port}:8000",
    "--security-opt", "no-new-privileges:true",
    "--pull", "never",
    $Image
) | Out-Null

$ready = $false
for ($attempt = 1; $attempt -le 90; $attempt++) {
    try {
        $response = Invoke-WebRequest -Uri $LocalUrl -UseBasicParsing -TimeoutSec 3
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
            $ready = $true
            break
        }
    }
    catch {
        # MobSF can take several minutes to initialize on the first image pull.
    }

    Start-Sleep -Seconds 2
}

if (-not $ready) {
    Write-Host "MobSF did not become ready. Recent container logs:" -ForegroundColor Red
    & docker logs --tail 100 $ContainerName
    Invoke-Docker -Arguments @("rm", "--force", $ContainerName) -AllowFailure | Out-Null
    throw "MobSF startup timed out."
}

Write-Host ""
Write-Host "MobSF is ready at $LocalUrl" -ForegroundColor Green
Write-Host "Default local login: mobsf / mobsf" -ForegroundColor Yellow
Write-Host "Upload only a locally built debug APK. Do not upload signing keys or local.properties." -ForegroundColor Yellow
Write-Host "Stop and erase the ephemeral container with:" -ForegroundColor Cyan
Write-Host ".\scripts\security\start_mobsf_local.ps1 -Stop" -ForegroundColor White

if (-not $NoBrowser) {
    Start-Process $LocalUrl
}
