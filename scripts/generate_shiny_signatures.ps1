param(
    [Parameter(Mandatory = $true)]
    [string]$AssetsDir,
    [string]$SpeciesMap = "C:\Users\Caglar\Desktop\PokeRarityScanner\app\src\main\assets\data\pokemon_names.json",
    [string]$Out = "C:\Users\Caglar\Desktop\PokeRarityScanner\app\src\main\assets\data\shiny_signatures.json"
)

Add-Type -AssemblyName System.Drawing

function Get-AlphaBounds {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [int]$Step = 8,
        [int]$AlphaThreshold = 8
    )
    $minX = $Bitmap.Width
    $minY = $Bitmap.Height
    $maxX = -1
    $maxY = -1

    for ($y = 0; $y -lt $Bitmap.Height; $y += $Step) {
        for ($x = 0; $x -lt $Bitmap.Width; $x += $Step) {
            $pixel = $Bitmap.GetPixel($x, $y)
            if ($pixel.A -gt $AlphaThreshold) {
                if ($x -lt $minX) { $minX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }

    if ($maxX -lt 0 -or $maxY -lt 0) {
        return New-Object System.Drawing.Rectangle 0, 0, $Bitmap.Width, $Bitmap.Height
    }

    $width = [Math]::Max(1, ($maxX - $minX + 1))
    $height = [Math]::Max(1, ($maxY - $minY + 1))
    return New-Object System.Drawing.Rectangle $minX, $minY, $width, $height
}

function Crop-ToAlphaBounds {
    param([System.Drawing.Bitmap]$Bitmap)
    $rect = Get-AlphaBounds -Bitmap $Bitmap
    if ($rect.Width -eq $Bitmap.Width -and $rect.Height -eq $Bitmap.Height) {
        return $Bitmap
    }
    return $Bitmap.Clone($rect, $Bitmap.PixelFormat)
}

function Resize-Bitmap {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [int]$Width,
        [int]$Height
    )
    $scaled = New-Object System.Drawing.Bitmap $Width, $Height
    $gfx = [System.Drawing.Graphics]::FromImage($scaled)
    $gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBilinear
    $gfx.DrawImage($Bitmap, 0, 0, $Width, $Height)
    $gfx.Dispose()
    return $scaled
}

function Get-Luminance {
    param([System.Drawing.Color]$Color)
    return [int](0.299 * $Color.R + 0.587 * $Color.G + 0.114 * $Color.B)
}

function Bits-ToHex {
    param([bool[]]$Bits)
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $Bits.Length; $i += 4) {
        $val = 0
        for ($j = 0; $j -lt 4; $j++) {
            if ($Bits[$i + $j]) {
                $val = ($val -shl 1) -bor 1
            } else {
                $val = ($val -shl 1)
            }
        }
        [void]$sb.Append($val.ToString("x"))
    }
    return $sb.ToString()
}

function Get-AHash {
    param([System.Drawing.Bitmap]$Bitmap, [int]$Size = 8)
    $scaled = Resize-Bitmap -Bitmap $Bitmap -Width $Size -Height $Size
    $lums = New-Object int[] ($Size * $Size)
    $sum = 0
    for ($y = 0; $y -lt $Size; $y++) {
        for ($x = 0; $x -lt $Size; $x++) {
            $lum = Get-Luminance -Color $scaled.GetPixel($x, $y)
            $idx = $y * $Size + $x
            $lums[$idx] = $lum
            $sum += $lum
        }
    }
    $avg = [int]($sum / $lums.Length)
    $bits = New-Object bool[] ($lums.Length)
    for ($i = 0; $i -lt $lums.Length; $i++) {
        $bits[$i] = $lums[$i] -gt $avg
    }
    $scaled.Dispose()
    return Bits-ToHex -Bits $bits
}

function Get-DHash {
    param([System.Drawing.Bitmap]$Bitmap, [int]$Size = 8)
    $scaled = Resize-Bitmap -Bitmap $Bitmap -Width ($Size + 1) -Height $Size
    $bits = New-Object bool[] ($Size * $Size)
    $idx = 0
    for ($y = 0; $y -lt $Size; $y++) {
        for ($x = 0; $x -lt $Size; $x++) {
            $l1 = Get-Luminance -Color $scaled.GetPixel($x, $y)
            $l2 = Get-Luminance -Color $scaled.GetPixel($x + 1, $y)
            $bits[$idx] = $l1 -lt $l2
            $idx++
        }
    }
    $scaled.Dispose()
    return Bits-ToHex -Bits $bits
}

function Get-EdgeHistogram {
    param([System.Drawing.Bitmap]$Bitmap, [int]$Size = 48, [int]$Bins = 8)
    $scaled = Resize-Bitmap -Bitmap $Bitmap -Width $Size -Height $Size
    $hist = New-Object int[] $Bins
    $count = 0
    for ($y = 1; $y -lt ($Size - 1); $y++) {
        for ($x = 1; $x -lt ($Size - 1); $x++) {
            $lumL = Get-Luminance -Color $scaled.GetPixel($x - 1, $y)
            $lumR = Get-Luminance -Color $scaled.GetPixel($x + 1, $y)
            $lumU = Get-Luminance -Color $scaled.GetPixel($x, $y - 1)
            $lumD = Get-Luminance -Color $scaled.GetPixel($x, $y + 1)
            $grad = [Math]::Min(255, [Math]::Abs($lumR - $lumL) + [Math]::Abs($lumD - $lumU))
            $bin = [Math]::Min($Bins - 1, [int](($grad * $Bins) / 256))
            $hist[$bin]++
            $count++
        }
    }
    $scaled.Dispose()
    $out = @()
    if ($count -eq 0) { $count = 1 }
    for ($i = 0; $i -lt $Bins; $i++) {
        $out += [Math]::Round($hist[$i] / $count, 6)
    }
    return ,$out
}

function Get-HueHistogram {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [int]$Bins = 12,
        [int]$Step = 4
    )
    $hist = New-Object double[] $Bins
    $total = 0.0
    for ($y = 0; $y -lt $Bitmap.Height; $y += $Step) {
        for ($x = 0; $x -lt $Bitmap.Width; $x += $Step) {
            $c = $Bitmap.GetPixel($x, $y)
            if ($c.R -lt 12 -and $c.G -lt 12 -and $c.B -lt 12) { continue }
            $h = $c.GetHue()
            $s = $c.GetSaturation()
            $v = $c.GetBrightness()
            if ($s -lt 0.15 -or $v -lt 0.15) { continue }
            $bin = [Math]::Min($Bins - 1, [int](($h / 360.0) * $Bins))
            $w = [Math]::Min(1.0, $s * $v)
            $hist[$bin] += $w
            $total += $w
        }
    }
    if ($total -le 0) {
        $zeros = @()
        for ($i = 0; $i -lt $Bins; $i++) { $zeros += 0 }
        return $zeros
    }
    $out = @()
    for ($i = 0; $i -lt $Bins; $i++) {
        $out += [Math]::Round($hist[$i] / $total, 6)
    }
    return $out
}

if (-not (Test-Path $AssetsDir)) {
    throw "AssetsDir not found: $AssetsDir"
}
if (-not (Test-Path $SpeciesMap)) {
    throw "SpeciesMap not found: $SpeciesMap"
}

$speciesList = Get-Content -Raw $SpeciesMap | ConvertFrom-Json
if (-not ($speciesList -is [System.Array])) {
    $speciesList = @($speciesList)
}
$speciesByDex = @{}
for ($i = 0; $i -lt $speciesList.Count; $i++) {
    $speciesByDex[$i + 1] = $speciesList[$i]
}

$entries = New-Object System.Collections.Generic.List[object]
$files = Get-ChildItem -File $AssetsDir -Filter "pokemon_icon_*.png"
$normalFiles = $files | Where-Object { $_.Name -notmatch "_shiny" -and $_.Name -notmatch "_shadow" }

foreach ($file in $normalFiles) {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    if (-not $base.StartsWith("pokemon_icon_")) { continue }
    $parts = $base.Substring("pokemon_icon_".Length).Split("_")
    if ($parts.Length -lt 2) { continue }
    if ($parts[0] -notmatch '^\d+$') { continue }

    $dexId = [int]$parts[0]
    $formId = $parts[1]
    $variantId = $null
    if ($parts.Length -ge 3) { $variantId = $parts[2] }

    if ($variantId) { continue }
    if ($formId -ne "00") { continue }

    $shinyName = "$base`_shiny.png"
    $shinyPath = Join-Path $AssetsDir $shinyName
    if (-not (Test-Path $shinyPath)) { continue }

    $key = if ($variantId) { "{0:000}_{1}_{2}" -f $dexId, $formId, $variantId } else { "{0:000}_{1}" -f $dexId, $formId }
    $species = $speciesByDex[$dexId]
    if (-not $species) { continue }

    $bmpNormal = [System.Drawing.Bitmap]::FromFile($file.FullName)
    $croppedNormal = Crop-ToAlphaBounds -Bitmap $bmpNormal
    $normalSig = [pscustomobject]@{
        aHash = (Get-AHash -Bitmap $croppedNormal -Size 8)
        dHash = (Get-DHash -Bitmap $croppedNormal -Size 8)
        edge = (Get-EdgeHistogram -Bitmap $croppedNormal -Size 48 -Bins 8)
        color = (Get-HueHistogram -Bitmap $croppedNormal -Bins 12 -Step 4)
    }
    if ($croppedNormal -ne $bmpNormal) { $croppedNormal.Dispose() }
    $bmpNormal.Dispose()

    $bmpShiny = [System.Drawing.Bitmap]::FromFile($shinyPath)
    $croppedShiny = Crop-ToAlphaBounds -Bitmap $bmpShiny
    $shinySig = [pscustomobject]@{
        aHash = (Get-AHash -Bitmap $croppedShiny -Size 8)
        dHash = (Get-DHash -Bitmap $croppedShiny -Size 8)
        edge = (Get-EdgeHistogram -Bitmap $croppedShiny -Size 48 -Bins 8)
        color = (Get-HueHistogram -Bitmap $croppedShiny -Bins 12 -Step 4)
    }
    if ($croppedShiny -ne $bmpShiny) { $croppedShiny.Dispose() }
    $bmpShiny.Dispose()

    $entries.Add([pscustomobject]@{
        dex = $dexId
        species = $species
        form = if ($variantId) { "$formId`_$variantId" } else { $formId }
        key = $key
        normal = $normalSig
        shiny = $shinySig
        src = $file.Name
    }) | Out-Null
}

$payload = [pscustomobject]@{
    version = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    source = $AssetsDir
    count = $entries.Count
    entries = $entries
}

$dir = [System.IO.Path]::GetDirectoryName($Out)
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

$json = $payload | ConvertTo-Json -Depth 6
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($Out, $json, $utf8NoBom)

Write-Output "Wrote $($entries.Count) entries to $Out"
