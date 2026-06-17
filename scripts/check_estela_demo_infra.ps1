<#
.SYNOPSIS
    Chequeo rapido de infraestructura para la demo de Estela.

.DESCRIPTION
    Verifica /health local (8080) y publico (ngrok), adb, package instalado y espacio en C:.
    NO toca nada. Imprime un veredicto: READY / PARTIAL / FAIL.
    Exit code: 0 READY, 1 PARTIAL, 2 FAIL.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\check_estela_demo_infra.ps1
#>

[CmdletBinding()]
param(
    [int]$Port = 8080,
    [string]$Domain = "wieldiest-etha-unrippable.ngrok-free.dev",
    [string]$Package = "com.ojoclaro.android",
    [string]$Adb = "adb"
)

$ErrorActionPreference = "Continue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$script:fail = 0
$script:warn = 0
function Ok($m)   { Write-Host "[OK]   $m" -ForegroundColor Green }
function Warn($m) { Write-Host "[WARN] $m" -ForegroundColor Yellow; $script:warn++ }
function Fail($m) { Write-Host "[FAIL] $m" -ForegroundColor Red;    $script:fail++ }

function Test-Health($url) {
    try {
        $r = Invoke-WebRequest -Uri $url -TimeoutSec 12 -UseBasicParsing `
            -Headers @{ "ngrok-skip-browser-warning" = "true" }
        return ($r.StatusCode -eq 200 -and $r.Content -notmatch "ERR_NGROK")
    } catch {
        return $false
    }
}

Write-Host "=== Estela demo infra check ===" -ForegroundColor Cyan

# 1. Backend local
$localUrl = "http://127.0.0.1:$Port/health"
if (Test-Health $localUrl) { Ok "Backend local $localUrl -> 200" }
else { Fail "Backend local $localUrl no responde 200. Corre start_estela_demo_backend.ps1" }

# 2. Publico (ngrok)
$pubUrl = "https://$Domain/health"
if (Test-Health $pubUrl) { Ok "ngrok publico $pubUrl -> 200" }
else { Fail "ngrok publico $pubUrl no responde 200. Corre start_estela_demo_ngrok.ps1" }

# 3. adb devices
$dev = $null
try {
    $raw = (& $Adb devices -l 2>$null | Out-String)
    $lines = @($raw -split "`n" | Where-Object { $_ -match "\sdevice(\s|$)" -and $_ -notmatch "List of devices" })
    if ($lines.Count -ge 1) {
        Ok ("adb: " + $lines.Count + " dispositivo(s)")
        foreach ($l in $lines) { Write-Host "       $($l.Trim())" }
        $dev = ($lines[0].Trim() -split "\s+")[0]
    } else {
        Fail "adb: no hay dispositivos conectados"
    }
} catch {
    Fail "adb no disponible: $($_.Exception.Message)"
}

# 4. Package instalado
if ($dev) {
    $pkg = (& $Adb -s $dev shell pm path $Package 2>$null | Out-String)
    if ($pkg -match "package:") { Ok "Package $Package instalado" }
    else { Fail "Package $Package NO instalado en $dev" }
} else {
    Warn "Package no verificado (sin dispositivo)"
}

# 5. Espacio en C:
try {
    $free = [math]::Round((Get-PSDrive C -ErrorAction Stop).Free / 1GB, 2)
    if ($free -lt 1)      { Warn "C: $free GB libres (critico < 1GB; la demo corre igual, liberar espacio)" }
    elseif ($free -lt 5)  { Warn "C: $free GB libres (bajo)" }
    else                  { Ok   "C: $free GB libres" }
} catch {
    Warn "No pude leer C: $($_.Exception.Message)"
}

# Veredicto
Write-Host ""
if ($script:fail -gt 0) {
    Write-Host ("RESULTADO: FAIL ({0} fail, {1} warn) - resolver antes de la demo." -f $script:fail, $script:warn) -ForegroundColor Red
    exit 2
} elseif ($script:warn -gt 0) {
    Write-Host ("RESULTADO: PARTIAL (0 fail, {0} warn) - la demo puede correr; revisar los warn." -f $script:warn) -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "RESULTADO: READY - todo verde." -ForegroundColor Green
    exit 0
}
