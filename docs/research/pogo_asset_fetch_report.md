# Pokemon GO Asset Fetch Report

Date: 2026-06-27

Generated files:

* `build/reports/pogo_reference/asset_integrity_report.csv`
* `build/reports/pogo_reference/asset_fetch_coverage.md`

## Results

* indexed_assets=5,840
* downloaded_or_cached=5,840
* usable_for_descriptor=5,840
* raw asset cache=`.local/pogo_reference_cache/assets/pokeminers_pogo_assets/`
* raw assets committed=false
* default new-download cap=500 per run
* explicit full-cache command=`.\scripts\reference_pipeline\fetch_pogo_assets.ps1 -DownloadAll`

## Integrity Checks

The fetcher verifies local file existence, PNG decode through `System.Drawing`, dimensions, SHA-256, source path, and usability for descriptor generation.

## Readiness

Asset fetch coverage is complete for the indexed PokeMiners `Images/Pokemon - 256x256` tree in this local cache. The fetcher now falls back from `Invoke-WebRequest` to `curl.exe` for transient raw-GitHub download failures; the final full run cached and decoded all 5,840 indexed PNGs.

This does not remove legal risk. Raw external sprites remain local-only under `.local/` and must not be committed.
