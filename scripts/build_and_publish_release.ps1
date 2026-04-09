param(
    [string]$Repo = "chaglaruk/PokemonRarityScanner",
    [switch]$Draft,
    [switch]$Prerelease
)

$ErrorActionPreference = "Stop"

$gradlePropertiesPath = Join-Path $PSScriptRoot "..\\gradle.properties"
$gradleProperties = Get-Content $gradlePropertiesPath
$versionNameLine = $gradleProperties | Where-Object { $_ -match '^pokerarity\.versionName=' } | Select-Object -First 1
if (-not $versionNameLine) {
    throw "Could not find pokerarity.versionName in gradle.properties"
}

$versionName = $versionNameLine.Split('=', 2)[1].Trim()
$tag = "v$versionName"

Write-Host "Building release APK for $tag"
& "$PSScriptRoot\\..\\gradlew.bat" :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "assembleRelease failed"
}

Write-Host "Publishing release $tag"
& "$PSScriptRoot\\publish_github_release.ps1" -Repo $Repo -Tag $tag -Draft:$Draft -Prerelease:$Prerelease
