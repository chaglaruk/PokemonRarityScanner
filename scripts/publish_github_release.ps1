param(
    [string]$Repo = "chaglaruk/PokemonRarityScanner",
    [string]$Tag,
    [string]$ApkPath,
    [string]$Token = $env:GITHUB_TOKEN,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Resolve-ApkPath {
    param([string]$Candidate)

    if ($Candidate) {
        $resolved = Resolve-Path -LiteralPath $Candidate -ErrorAction Stop
        return $resolved.Path
    }

    $releaseDir = Join-Path $PSScriptRoot "..\\app\\build\\outputs\\apk\\release"
    $apk = Get-ChildItem -Path $releaseDir -Filter "PokeRarityScanner-v*-release.apk" -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $apk) {
        throw "No release APK found under $releaseDir"
    }

    return $apk.FullName
}

function Resolve-Tag {
    param(
        [string]$Candidate,
        [string]$ResolvedApkPath
    )

    if ($Candidate) {
        return $Candidate
    }

    $fileName = Split-Path -Path $ResolvedApkPath -Leaf
    if ($fileName -match '^PokeRarityScanner-v(?<version>[0-9]+\.[0-9]+\.[0-9]+)-release\.apk$') {
        return "v$($matches.version)"
    }

    throw "Could not infer tag from APK filename: $fileName"
}

function Invoke-GitHubJson {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null
    )

    $headers = @{
        Authorization           = "Bearer $Token"
        Accept                  = "application/vnd.github+json"
        "X-GitHub-Api-Version"  = "2022-11-28"
        "User-Agent"            = "PokeRarityScanner-ReleaseUploader"
    }

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
    }

    $jsonBody = $Body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType "application/json" -Body $jsonBody
}

function Invoke-GitHubBinaryUpload {
    param(
        [string]$Uri,
        [string]$FilePath
    )

    $headers = @{
        Authorization           = "Bearer $Token"
        Accept                  = "application/vnd.github+json"
        "X-GitHub-Api-Version"  = "2022-11-28"
        "User-Agent"            = "PokeRarityScanner-ReleaseUploader"
        "Content-Type"          = "application/vnd.android.package-archive"
    }

    return Invoke-RestMethod -Method Post -Uri $Uri -Headers $headers -InFile $FilePath
}

$resolvedApkPath = Resolve-ApkPath -Candidate $ApkPath
$resolvedTag = Resolve-Tag -Candidate $Tag -ResolvedApkPath $resolvedApkPath
$apkName = Split-Path -Path $resolvedApkPath -Leaf
$repoApiBase = "https://api.github.com/repos/$Repo"

Write-Host "Repo: $Repo"
Write-Host "Tag: $resolvedTag"
Write-Host "APK: $resolvedApkPath"

if ($DryRun) {
    Write-Host "Dry run enabled. No GitHub API mutation will be performed."
    exit 0
}

if (-not $Token) {
    throw "GITHUB_TOKEN is required. Set it in the environment or pass -Token."
}

$release = $null
try {
    $release = Invoke-GitHubJson -Method Get -Uri "$repoApiBase/releases/tags/$resolvedTag"
    Write-Host "Existing release found for $resolvedTag"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -ne 404) {
        throw
    }

    Write-Host "No existing release found for $resolvedTag. Creating one."
    $release = Invoke-GitHubJson -Method Post -Uri "$repoApiBase/releases" -Body @{
        tag_name             = $resolvedTag
        name                 = $resolvedTag
        draft                = [bool]$Draft
        prerelease           = [bool]$Prerelease
        generate_release_notes = $true
    }
}

$existingAsset = $release.assets | Where-Object { $_.name -eq $apkName } | Select-Object -First 1
if ($existingAsset) {
    Write-Host "Deleting existing asset: $apkName"
    Invoke-GitHubJson -Method Delete -Uri "$repoApiBase/releases/assets/$($existingAsset.id)" | Out-Null
}

$uploadUrl = ($release.upload_url -replace '\{.*$', '') + "?name=$([Uri]::EscapeDataString($apkName))"
Write-Host "Uploading asset: $apkName"
$uploaded = Invoke-GitHubBinaryUpload -Uri $uploadUrl -FilePath $resolvedApkPath

Write-Host "Release URL: $($release.html_url)"
Write-Host "Asset URL: $($uploaded.browser_download_url)"
