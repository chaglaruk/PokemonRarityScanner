param(
    [Parameter(Mandatory = $true)]
    [string]$CaseId
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $root ".codex_tmp\latest_scan"
$targetDir = Join-Path $root "app\src\androidTest\assets\scan_fixtures\$CaseId"

if (-not (Test-Path $sourceDir)) {
    throw "latest_scan directory not found: $sourceDir"
}

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
Copy-Item (Join-Path $sourceDir "*.png") $targetDir -Force

$copied = Get-ChildItem $targetDir -Filter *.png | Sort-Object Name
if (-not $copied) {
    throw "No PNG files copied to $targetDir"
}

Write-Host "Copied $($copied.Count) fixture images to $targetDir"
Write-Host ""
Write-Host "Manifest template:"

$templates = $copied | ForEach-Object {
@"
  {
    "id": "$CaseId/$($_.BaseName)",
    "assetPath": "scan_fixtures/$CaseId/$($_.Name)",
    "strict": false,
    "notes": "Fill expected values after manual label verification.",
    "expected": {
      "species": null,
      "cp": null,
      "hp": null,
      "maxHp": null,
      "shiny": null,
      "lucky": null,
      "costume": null,
      "locationCard": null,
      "datePresent": null
    }
  }
"@
}

Write-Host "["
Write-Host ($templates -join ",`n")
Write-Host "]"
