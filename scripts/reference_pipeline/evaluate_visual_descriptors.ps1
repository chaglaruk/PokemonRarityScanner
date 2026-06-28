param(
    [string]$DescriptorDb = "build/reports/pogo_reference/visual_descriptor_db.json",
    [string]$RuntimeModel = "app/src/main/assets/data/variant_classifier_model.json",
    [string]$Cases = "app/src/androidTest/assets/scan_regression_cases.json",
    [string]$FixturesRoot = "app/src/androidTest/assets",
    [string]$AssetsDir = ".local/pogo_reference_cache/assets/pokeminers_pogo_assets",
    [string]$ReportRoot = "build/reports/pogo_reference"
)

$ErrorActionPreference = "Stop"
$python = Get-Command python -ErrorAction SilentlyContinue
if ($null -eq $python) {
    throw "python was not found on PATH"
}

& $python.Source ".\scripts\reference_pipeline\evaluate_visual_descriptors.py" `
    --descriptor-db $DescriptorDb `
    --runtime-model $RuntimeModel `
    --cases $Cases `
    --fixtures-root $FixturesRoot `
    --assets-dir $AssetsDir `
    --report-root $ReportRoot

$exit = $LASTEXITCODE
if ($exit -eq 2) {
    Write-Host "descriptor evaluation completed with NOT READY status; see $ReportRoot\descriptor_eval_summary.md"
    exit 0
}
exit $exit
