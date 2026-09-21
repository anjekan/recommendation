param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE '.android\recommendation-distribution\recommendation-release.jks')
)

$ErrorActionPreference = 'Stop'
$androidRoot = $PSScriptRoot
$keytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
$javaHome = Split-Path (Split-Path $keytool -Parent) -Parent
$androidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'

if (-not (Test-Path -LiteralPath $keytool)) { throw "keytool not found: $keytool" }
if (-not (Test-Path -LiteralPath $androidSdk)) { throw "Android SDK not found: $androidSdk" }

if (-not (Test-Path -LiteralPath $KeystorePath)) {
    $keyDirectory = Split-Path $KeystorePath -Parent
    New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
    Write-Host 'Creating a dedicated signing key. Enter a strong password at the keytool prompts.'
    & $keytool -genkeypair -keystore $KeystorePath -storetype PKCS12 -alias recommendation -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Recommendation Tablet App, O=90 Seconds, C=KR'
    if ($LASTEXITCODE -ne 0) { throw 'Signing key creation failed.' }
    Write-Host 'Back up the JKS file and its password separately. Losing either prevents future app updates.'
}

$securePassword = Read-Host 'Signing key password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:JAVA_HOME = $javaHome
    $env:ANDROID_HOME = $androidSdk
    $env:RECOMMENDATION_SIGNING_STORE_FILE = $KeystorePath
    $env:RECOMMENDATION_SIGNING_STORE_PASSWORD = $password
    $env:RECOMMENDATION_SIGNING_KEY_ALIAS = 'recommendation'
    $env:RECOMMENDATION_SIGNING_KEY_PASSWORD = $password
    Remove-Variable password

    Push-Location $androidRoot
    try {
        & .\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Distribution build failed.' }
    } finally {
        Pop-Location
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    Remove-Item Env:RECOMMENDATION_SIGNING_STORE_FILE,Env:RECOMMENDATION_SIGNING_STORE_PASSWORD,Env:RECOMMENDATION_SIGNING_KEY_ALIAS,Env:RECOMMENDATION_SIGNING_KEY_PASSWORD -ErrorAction SilentlyContinue
}

$apk = Join-Path $androidRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw "Release APK missing: $apk" }
Write-Host "Signed APK: $apk"
Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Select-Object Algorithm,Hash,Path | Format-List
