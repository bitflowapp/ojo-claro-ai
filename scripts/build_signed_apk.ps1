param(
    [string]$OutputApkName = "ojo-claro-ai-release.apk"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$keystorePropertiesPath = Join-Path $repoRoot "keystore.properties"
$distDir = Join-Path $repoRoot "dist"
$releaseApk = Join-Path $repoRoot "androidApp\build\outputs\apk\release\androidApp-release.apk"
$outputApk = Join-Path $distDir $OutputApkName
$sha256Path = "$outputApk.sha256.txt"

function Write-Info([string]$Message) {
    Write-Host "[release] $Message"
}

if (-not (Test-Path $keystorePropertiesPath)) {
    throw "Missing keystore.properties. Copy keystore.properties.example to keystore.properties and configure your local release keystore first."
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Write-Info "Running minimum debug tests before release build."

Push-Location $repoRoot
try {
    & .\gradlew.bat :androidApp:testDebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "testDebugUnitTest failed with exit code $LASTEXITCODE."
    }

    Write-Info "Building signed release APK."
    & .\gradlew.bat :androidApp:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "assembleRelease failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path $releaseApk)) {
    throw "Release APK not found at $releaseApk"
}

Copy-Item -Force $releaseApk $outputApk
$apkInfo = Get-Item $outputApk
if ($apkInfo.Length -le 0) {
    throw "Release APK exists but is empty: $outputApk"
}

$hash = Get-FileHash -Algorithm SHA256 $outputApk
@(
    $hash.Hash
    $outputApk
) | Set-Content -Encoding ASCII -Path $sha256Path

Write-Host ""
Write-Host "Signed APK ready:"
Write-Host "  APK: $outputApk"
Write-Host "  SHA256: $sha256Path"
Write-Host "  Size: $($apkInfo.Length) bytes"
