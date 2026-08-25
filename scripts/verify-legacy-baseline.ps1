param(
    [Parameter(Mandatory = $false)]
    [string]$LegacyRoot = 'C:\90\TaeAn\Kioskondevice_servering'
)

$ErrorActionPreference = 'Stop'

$expected = @{
    'app\src\main\assets\emotion-ferplus-8.onnx' = 'A2A2BA6A335A3B29C21ACB6272F962BD3D47F84952AAFFA03B60986E04EFA61C'
    'app\src\main\assets\face_landmarker.task' = '64184E229B263107BC2B804C6625DB1341FF2BB731874B0BCC2FE6544E0BC9FF'
    'app\build\outputs\apk\debug\app-debug.apk' = '3DA5351BC47FC351493C3BE8414F5476F1E69C19A5B99A223D7C736DFC8F118F'
}

$failed = $false

foreach ($relativePath in $expected.Keys) {
    $path = Join-Path $LegacyRoot $relativePath
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Error "Missing baseline file: $path"
        $failed = $true
        continue
    }

    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
    $matches = [string]::Equals($actual, $expected[$relativePath], [System.StringComparison]::OrdinalIgnoreCase)

    [PSCustomObject]@{
        File = $relativePath
        Matches = $matches
        ActualSha256 = $actual
    }

    if (-not $matches) {
        $failed = $true
    }
}

if ($failed) {
    exit 1
}

Write-Output 'Legacy baseline verification passed.'
