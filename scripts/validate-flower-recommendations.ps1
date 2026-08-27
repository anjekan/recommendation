param(
    [Parameter(Mandatory = $false)]
    [string]$CatalogPath = 'projects\taean-flower\flower-recommendations.json'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $CatalogPath)) {
    throw "Flower recommendation catalog not found: $CatalogPath"
}

$catalog = Get-Content -Raw -Encoding UTF8 -LiteralPath $CatalogPath | ConvertFrom-Json
$errors = [System.Collections.Generic.List[string]]::new()
$allowedEmotions = @('SERENITY', 'STABILITY', 'RELAXED', 'JOY', 'CALM', 'VITALITY', 'FOCUS', 'IMMERSION', 'ELEVATION', 'PASSION')

if ($catalog.schema_version -ne 1) { $errors.Add('schema_version must be 1') }
if ($catalog.project_code -ne 'TAEAN_FLOWER_2026') { $errors.Add('unexpected project_code') }
if ($catalog.recommendations.Count -ne 83) { $errors.Add("expected 83 recommendations, found $($catalog.recommendations.Count)") }

$ids = @($catalog.recommendations | ForEach-Object { $_.id })
$orders = @($catalog.recommendations | ForEach-Object { [int]$_.display_order })
if (($ids | Select-Object -Unique).Count -ne $ids.Count) { $errors.Add('recommendation IDs must be unique') }
if (($orders | Select-Object -Unique).Count -ne $orders.Count) { $errors.Add('display orders must be unique') }
if (($orders | Measure-Object -Minimum).Minimum -ne 1 -or ($orders | Measure-Object -Maximum).Maximum -ne 83) {
    $errors.Add('display orders must cover 1 through 83')
}

foreach ($item in $catalog.recommendations) {
    if ($allowedEmotions -notcontains $item.emotion_code) { $errors.Add("unknown emotion: $($item.emotion_code)") }
    if ($item.stress_min -lt 0 -or $item.stress_max -gt 100 -or $item.stress_min -gt $item.stress_max) {
        $errors.Add("invalid stress range at row $($item.display_order)")
    }
    foreach ($field in @('emotion_name', 'flower_name', 'flower_meaning', 'output_message', 'match_rationale')) {
        if ([string]::IsNullOrWhiteSpace($item.$field.ko)) { $errors.Add("missing $field.ko at row $($item.display_order)") }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

$summary = $catalog.recommendations | Group-Object emotion_code | Sort-Object Name | ForEach-Object {
    "$($_.Name)=$($_.Count)"
}
Write-Output "Flower recommendation validation passed: $($catalog.recommendations.Count) rows"
Write-Output ($summary -join ', ')
