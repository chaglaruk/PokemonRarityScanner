param(
    [string]$CasesPath = "",
    [string]$FixtureRoot = "",
    [string]$OutDir = "",
    [int]$Columns = 4,
    [int]$ThumbWidth = 220,
    [int]$ThumbHeight = 390
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($CasesPath)) {
    $CasesPath = Join-Path $repoRoot "app\src\androidTest\assets\scan_regression_cases.json"
}
if ([string]::IsNullOrWhiteSpace($FixtureRoot)) {
    $FixtureRoot = Join-Path $repoRoot "app\src\androidTest\assets"
}
if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $repoRoot "build\reports\fixture_tools"
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

Add-Type -AssemblyName System.Drawing

$rawCases = Get-Content $CasesPath -Raw | ConvertFrom-Json
$cases = @($rawCases)
$labelHeight = 76
$cellWidth = $ThumbWidth + 24
$cellHeight = $ThumbHeight + $labelHeight + 24
$rows = [Math]::Max(1, [Math]::Ceiling($cases.Count / [double]$Columns))
$bitmap = [System.Drawing.Bitmap]::new($cellWidth * $Columns, $cellHeight * $rows)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.Clear([System.Drawing.Color]::White)
$font = [System.Drawing.Font]::new("Arial", 8)
$brush = [System.Drawing.Brushes]::Black
$badBrush = [System.Drawing.Brushes]::DarkRed
$pen = [System.Drawing.Pen]::new([System.Drawing.Color]::LightGray)

$indexRows = New-Object System.Collections.Generic.List[object]
$decoded = 0
$undecodable = 0
for ($i = 0; $i -lt $cases.Count; $i++) {
    $case = $cases[$i]
    $col = $i % $Columns
    $row = [Math]::Floor($i / $Columns)
    $x = $col * $cellWidth + 12
    $y = $row * $cellHeight + 12
    $asset = Join-Path $FixtureRoot $case.assetPath
    $status = "missing"
    $species = if ($case.expected -and $case.expected.species) { $case.expected.species } else { "TODO species" }
    $label = "$($case.id)`n$species"

    $graphics.DrawRectangle($pen, $x, $y, $ThumbWidth, $ThumbHeight)
    if (Test-Path $asset) {
        try {
            $img = [System.Drawing.Image]::FromFile($asset)
            try {
                $scale = [Math]::Min($ThumbWidth / [double]$img.Width, $ThumbHeight / [double]$img.Height)
                $drawW = [int]($img.Width * $scale)
                $drawH = [int]($img.Height * $scale)
                $drawX = $x + [int](($ThumbWidth - $drawW) / 2)
                $drawY = $y + [int](($ThumbHeight - $drawH) / 2)
                $graphics.DrawImage($img, $drawX, $drawY, $drawW, $drawH)
                $status = "decoded"
                $decoded++
            } finally {
                $img.Dispose()
            }
        } catch {
            $status = "undecodable"
            $undecodable++
            $graphics.DrawString("UNDECODABLE", $font, $badBrush, $x + 8, $y + 8)
        }
    } else {
        $graphics.DrawString("MISSING", $font, $badBrush, $x + 8, $y + 8)
    }
    $graphics.DrawString($label, $font, $brush, $x, $y + $ThumbHeight + 8)
    $indexRows.Add([pscustomobject]@{
        index = $i
        id = $case.id
        assetPath = $case.assetPath
        expectedSpecies = if ($case.expected) { $case.expected.species } else { $null }
        decodeStatus = $status
    }) | Out-Null
}

$contactSheet = Join-Path $OutDir "fixture_contact_sheet.png"
$bitmap.Save($contactSheet, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()
$indexRows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutDir "fixture_contact_sheet_index.csv")

Write-Output "fixture_contact_sheet=$contactSheet"
Write-Output "cases=$($cases.Count)"
Write-Output "decoded=$decoded"
Write-Output "undecodable=$undecodable"
