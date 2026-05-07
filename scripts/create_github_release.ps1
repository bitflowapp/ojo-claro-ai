param(
    [string]$Tag = "ojo-claro-ai-beta-v0.1.0",
    [switch]$Publish
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$distDir = Join-Path $repoRoot "dist"
$apkPath = Join-Path $distDir "ojo-claro-ai-release.apk"
$sha256Path = "$apkPath.sha256.txt"

if (-not (Test-Path $apkPath)) {
    throw "Missing release APK at $apkPath. Run scripts/build_signed_apk.ps1 first."
}

if (-not (Test-Path $sha256Path)) {
    throw "Missing SHA256 file at $sha256Path. Run scripts/build_signed_apk.ps1 first."
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "GitHub CLI (gh) is not installed."
    Write-Host "Manual release steps:"
    Write-Host "1. Open the GitHub repository."
    Write-Host "2. Go to Releases."
    Write-Host "3. Draft a new release."
    Write-Host "4. Use tag: $Tag"
    Write-Host "5. Upload:"
    Write-Host "   $apkPath"
    Write-Host "   $sha256Path"
    Write-Host "6. Publish the release manually."
    return
}

Write-Host "Prepared release artifacts:"
Write-Host "  $apkPath"
Write-Host "  $sha256Path"
Write-Host "Tag suggestion: $Tag"

if (-not $Publish) {
    Write-Host "Dry run only. Re-run with -Publish after you confirm the tag and artifacts."
    Write-Host "Example:"
    Write-Host "  gh release create $Tag `"$apkPath`" `"$sha256Path`" --title `"$Tag`" --notes `"Beta APK for Ojo Claro AI.`""
    return
}

$confirmation = Read-Host "Type RELEASE to publish $Tag"
if ($confirmation -ne "RELEASE") {
    Write-Host "Cancelled."
    return
}

& gh release create $Tag $apkPath $sha256Path --title $Tag --notes "Beta APK for Ojo Claro AI."
