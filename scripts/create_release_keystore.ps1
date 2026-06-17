param(
    [string]$KeystoreFile = "ojo_claro_release.keystore",
    [string]$KeyAlias = "ojo_claro",
    [int]$ValidityDays = 10000
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$keystorePath = Join-Path $repoRoot $KeystoreFile
$keystorePropertiesPath = Join-Path $repoRoot "keystore.properties"

function Assert-LocalPath([string]$Path, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "$Label cannot be empty."
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $choice = Read-Host "$Label already exists at '$Path'. Type OVERWRITE to replace it or press Enter to cancel"
    if ($choice -ne "OVERWRITE") {
        throw "Cancelled to avoid overwriting existing $Label."
    }
}

Assert-LocalPath -Path $keystorePath -Label "Keystore"
Assert-LocalPath -Path $keystorePropertiesPath -Label "keystore.properties"

Write-Host "This will create a local Android release keystore and a gitignored keystore.properties file."
Write-Host "Alias: $KeyAlias"
Write-Host "Validity days: $ValidityDays"
Write-Host "Output: $keystorePath"

$storePasswordSecure = Read-Host "Store password" -AsSecureString
$keyPasswordSecure = Read-Host "Key password" -AsSecureString

$storePassword = [System.Net.NetworkCredential]::new("", $storePasswordSecure).Password
$keyPassword = [System.Net.NetworkCredential]::new("", $keyPasswordSecure).Password

try {
    $keytool = Get-Command keytool -ErrorAction Stop
    $dname = "CN=Ojo Claro, OU=Android, O=Ojo Claro AI, L=Buenos Aires, S=Buenos Aires, C=AR"

    & $keytool.Path `
        -genkeypair `
        -v `
        -keystore $keystorePath `
        -alias $KeyAlias `
        -keyalg RSA `
        -keysize 2048 `
        -validity $ValidityDays `
        -storepass $storePassword `
        -keypass $keyPassword `
        -dname $dname

    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE."
    }

    @(
        "storeFile=$KeystoreFile",
        "storePassword=$storePassword",
        "keyAlias=$KeyAlias",
        "keyPassword=$keyPassword"
    ) | Set-Content -Encoding ASCII -Path $keystorePropertiesPath

    Write-Host "Created:"
    Write-Host "  $keystorePath"
    Write-Host "  $keystorePropertiesPath"
    Write-Host "Do not commit either file."
    Write-Host "Gitignored files: keystore.properties, *.keystore, *.jks, .env, dist/"
}
finally {
    $storePassword = $null
    $keyPassword = $null
}
