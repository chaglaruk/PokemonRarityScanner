# Purpose: Apply local truth labels to exported telemetry screenshots.
param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,

    [Parameter(Mandatory = $true)]
    [string]$OverridesPath,

    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

function Read-JsonFile([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "File not found: $path"
    }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}

function Get-PropertyValue($object, [string]$name) {
    if ($null -eq $object) {
        return $null
    }

    $property = $object.PSObject.Properties[$name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Apply-Truth($truth, $source) {
    if ($null -eq $source) {
        return
    }

    foreach ($name in @("species", "is_shiny", "has_costume", "has_location_card", "has_special_form", "form")) {
        $value = Get-PropertyValue $source $name
        if ($null -ne $value -and -not [string]::IsNullOrWhiteSpace([string]$value)) {
            if ($null -eq $truth.PSObject.Properties[$name]) {
                $truth | Add-Member -MemberType NoteProperty -Name $name -Value ([string]$value)
            } else {
                $truth.$name = [string]$value
            }
        }
    }
}

function Has-Truth($truth) {
    foreach ($name in @("species", "is_shiny", "has_costume", "has_location_card", "has_special_form", "form")) {
        if (-not [string]::IsNullOrWhiteSpace([string](Get-PropertyValue $truth $name))) {
            return $true
        }
    }
    return $false
}

$manifestFullPath = (Resolve-Path -LiteralPath $ManifestPath).Path
$overrides = Read-JsonFile $OverridesPath
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path (Split-Path -Parent $manifestFullPath) "verified_manifest.jsonl"
}

if (Test-Path -LiteralPath $OutputPath) {
    Remove-Item -LiteralPath $OutputPath -Force
}

$updated = 0
$total = 0
$defaultTruth = Get-PropertyValue $overrides "defaults"
$bySpecies = Get-PropertyValue $overrides "by_species"
$byUploadId = Get-PropertyValue $overrides "by_upload_id"
$byId = Get-PropertyValue $overrides "by_id"

Get-Content -LiteralPath $manifestFullPath | ForEach-Object {
    if ([string]::IsNullOrWhiteSpace($_)) {
        return
    }

    $record = $_ | ConvertFrom-Json
    if ($null -eq $record.truth) {
        $record | Add-Member -MemberType NoteProperty -Name truth -Value ([PSCustomObject]@{})
    }

    Apply-Truth $record.truth $defaultTruth

    $species = [string]$record.predicted.species
    Apply-Truth $record.truth (Get-PropertyValue $bySpecies $species)

    $uploadId = [string]$record.upload_id
    Apply-Truth $record.truth (Get-PropertyValue $byUploadId $uploadId)

    $id = [string]$record.id
    Apply-Truth $record.truth (Get-PropertyValue $byId $id)

    $record.verified = Has-Truth $record.truth
    if ($record.verified) {
        $updated++
    }
    $total++

    ($record | ConvertTo-Json -Compress -Depth 8) | Add-Content -Path $OutputPath -Encoding UTF8
}

[PSCustomObject]@{
    manifest = $manifestFullPath
    overrides = (Resolve-Path -LiteralPath $OverridesPath).Path
    output = $OutputPath
    total = $total
    verified = $updated
} | ConvertTo-Json -Depth 4
