param(
    [Parameter(Mandatory = $false)]
    [string]$ConfigPath = 'contracts\examples\project-config.json'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "Project configuration not found: $ConfigPath"
}

$config = Get-Content -Raw -Encoding UTF8 -LiteralPath $ConfigPath | ConvertFrom-Json
$errors = New-Object System.Collections.Generic.List[string]

if ($config.schema_version -ne 1) {
    $errors.Add('schema_version must be 1')
}

if ($config.supported_languages -notcontains $config.default_language) {
    $errors.Add('default_language must be included in supported_languages')
}

$emotionCodes = @($config.emotion_profiles | ForEach-Object { $_.code })
$locationIds = @($config.locations | ForEach-Object { $_.id })
$itemIds = @($config.items | ForEach-Object { $_.id })
$sourceLabels = @($config.analysis_mappings | ForEach-Object { $_.source_label })

foreach ($mapping in $config.analysis_mappings) {
    if ($emotionCodes -notcontains $mapping.emotion_code) {
        $errors.Add("analysis mapping $($mapping.source_label) references unknown emotion $($mapping.emotion_code)")
    }
}

foreach ($item in $config.items) {
    if ($locationIds -notcontains $item.location_id) {
        $errors.Add("item $($item.id) references unknown location $($item.location_id)")
    }
}

foreach ($rule in $config.rules) {
    if ($emotionCodes -notcontains $rule.emotion_code) {
        $errors.Add("rule references unknown emotion $($rule.emotion_code)")
    }
    if ($itemIds -notcontains $rule.item_id) {
        $errors.Add("rule references unknown item $($rule.item_id)")
    }
}

foreach ($language in $config.supported_languages) {
    if (-not $config.theme.name.PSObject.Properties[$language]) {
        $errors.Add("theme.name is missing language $language")
    }

    foreach ($emotion in $config.emotion_profiles) {
        if (-not $emotion.name.PSObject.Properties[$language]) {
            $errors.Add("emotion $($emotion.code) name is missing language $language")
        }
        if (-not $emotion.message.PSObject.Properties[$language]) {
            $errors.Add("emotion $($emotion.code) message is missing language $language")
        }
    }

    foreach ($location in $config.locations) {
        if (-not $location.name.PSObject.Properties[$language]) {
            $errors.Add("location $($location.code) name is missing language $language")
        }
    }

    foreach ($item in $config.items) {
        if (-not $item.name.PSObject.Properties[$language]) {
            $errors.Add("item $($item.id) name is missing language $language")
        }
    }
}

if (($emotionCodes | Select-Object -Unique).Count -ne $emotionCodes.Count) {
    $errors.Add('emotion codes must be unique')
}

if (($locationIds | Select-Object -Unique).Count -ne $locationIds.Count) {
    $errors.Add('location ids must be unique')
}

if (($itemIds | Select-Object -Unique).Count -ne $itemIds.Count) {
    $errors.Add('item ids must be unique')
}

if (($sourceLabels | Select-Object -Unique).Count -ne $sourceLabels.Count) {
    $errors.Add('analysis mapping source labels must be unique')
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

[PSCustomObject]@{
    ProjectCode = $config.project_code
    ConfigVersion = $config.config_version
    Languages = ($config.supported_languages -join ',')
    Emotions = $config.emotion_profiles.Count
    Locations = $config.locations.Count
    Items = $config.items.Count
    Rules = $config.rules.Count
}

Write-Output 'Project configuration validation passed.'
