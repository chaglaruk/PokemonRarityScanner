function Assert-FixtureExportInventory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$CaseId,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]]$ManifestEntries,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [string[]]$PngNames
    )

    if ($CaseId -notmatch '^[A-Za-z0-9_-]{1,80}$') {
        throw 'The fixture case ID is invalid.'
    }
    if ($ManifestEntries.Count -eq 0 -or $PngNames.Count -eq 0) {
        throw 'The exported fixture must contain a manifest entry and at least one PNG.'
    }

    $assetPrefix = "scan_fixtures/$CaseId/"
    $manifestNames = @(
        foreach ($entry in $ManifestEntries) {
            $assetPath = [string]$entry.assetPath
            if (-not $assetPath.StartsWith($assetPrefix, [System.StringComparison]::Ordinal)) {
                throw 'A fixture manifest entry points outside the requested case.'
            }

            $name = $assetPath.Substring($assetPrefix.Length)
            if ([string]::IsNullOrWhiteSpace($name) -or
                $name.Contains('/') -or
                $name.Contains('\') -or
                -not $name.EndsWith('.png', [System.StringComparison]::OrdinalIgnoreCase)) {
                throw 'A fixture manifest entry does not name a PNG file.'
            }
            $name
        }
    )
    $actualNames = @($PngNames)
    $uniqueManifestNames = @($manifestNames | Sort-Object -Unique -CaseSensitive)
    $uniqueActualNames = @($actualNames | Sort-Object -Unique -CaseSensitive)
    if ($uniqueManifestNames.Count -ne $manifestNames.Count -or
        $uniqueActualNames.Count -ne $actualNames.Count) {
        throw 'The exported fixture contains duplicate PNG entries.'
    }

    $differences = Compare-Object -ReferenceObject $uniqueManifestNames `
        -DifferenceObject $uniqueActualNames -CaseSensitive
    if ($differences) {
        throw 'The exported PNG inventory does not match its manifest.'
    }
}
