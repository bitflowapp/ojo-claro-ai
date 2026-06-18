<#
.SYNOPSIS
    Captura SEGURA de una pantalla de Uber pasajero para tunear UberScreenReasoner.

.DESCRIPTION
    Guarda en build/uber-captures/ (gitignored): foco actual, dump de uiautomator
    (XML crudo) y logcat filtrado. Opcional screencap PNG (NO commitear).
    NUNCA pide viaje, NUNCA toca botones: solo lee lo que ya esta en pantalla.

    Imprime un RESUMEN ESTRUCTURAL sanitizado (resource-ids, clases, presencia de
    marcadores, etiquetas-tipo SIN direcciones) para disenar la extraccion sin
    exponer datos sensibles. El XML crudo queda local para inspeccion cuidadosa.

    Privacidad: no commitear XML/PNG crudos (build/ ya esta gitignored). Las
    direcciones reales nunca se imprimen en el resumen.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\capture_uber_screen.ps1 -Stage home
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\capture_uber_screen.ps1 -Stage ride_options -Screencap
#>

[CmdletBinding()]
param(
    [ValidateSet("home","destination_search","route_review","ride_options","confirm_ride","other")]
    [string]$Stage = "other",
    [string]$DeviceSerial = "ZY32LHS6PS",
    [string]$OutDir = "build/uber-captures",
    [switch]$Screencap,
    [string]$Adb = "adb"
)

$ErrorActionPreference = "Continue"
function Info($m) { Write-Host "[INFO] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[OK]   $m" -ForegroundColor Green }
function Warn($m) { Write-Host "[WARN] $m" -ForegroundColor Yellow }

$adbExe = (Get-Command $Adb -ErrorAction SilentlyContinue).Source
if (-not $adbExe) { $adbExe = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adbExe)) { Warn "adb no encontrado"; exit 3 }

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

# Mantener la pantalla despierta para que no se re-lockee durante la captura.
& $adbExe -s $DeviceSerial shell svc power stayon true | Out-Null

$focus = (& $adbExe -s $DeviceSerial shell dumpsys window | Select-String "mCurrentFocus" | Select-Object -First 1).ToString().Trim()
Info "Foco: $focus"
$keyguard = (& $adbExe -s $DeviceSerial shell dumpsys window | Select-String "isKeyguardShowing=" | Select-Object -First 1)
if ("$keyguard" -match "isKeyguardShowing=true") { Warn "El dispositivo esta BLOQUEADO: desbloquealo y reintenta." ; exit 2 }
if ("$focus" -notmatch "com.ubercab(?!\.driver)") {
    Warn "El foco no es Uber pasajero (com.ubercab). Abri Uber y posiciona la pantalla. Foco actual arriba."
}

$xml = Join-Path $OutDir "window-$Stage.xml"
& $adbExe -s $DeviceSerial shell uiautomator dump /sdcard/uber_cap.xml | Out-Null
& $adbExe -s $DeviceSerial pull /sdcard/uber_cap.xml $xml | Out-Null
& $adbExe -s $DeviceSerial shell rm /sdcard/uber_cap.xml 2>$null | Out-Null
if (Test-Path $xml) { Ok "Dump crudo: $xml (NO commitear)" } else { Warn "No se pudo capturar el dump" }

$log = Join-Path $OutDir "logcat-$Stage.txt"
& $adbExe -s $DeviceSerial shell logcat -d 2>$null | Select-String "EstelaBackground|uberCopilot|screenIntel" | Set-Content -Encoding utf8 $log
Ok "Logcat filtrado: $log"

if ($Screencap) {
    $png = Join-Path $OutDir "screen-$Stage.png"
    & $adbExe -s $DeviceSerial exec-out screencap -p | Set-Content -Encoding Byte $png
    Warn "Screencap: $png (NO commitear; puede tener ubicacion real)"
}

# --- Resumen estructural SANITIZADO (sin direcciones) ---
if (Test-Path $xml) {
    $content = Get-Content $xml -Raw
    Write-Host ""
    Write-Host "===== RESUMEN ESTRUCTURAL ($Stage) =====" -ForegroundColor Magenta
    $clickable = ([regex]::Matches($content, 'clickable="true"')).Count
    $editable  = ([regex]::Matches($content, 'class="android.widget.EditText"')).Count
    $textNodes = ([regex]::Matches($content, 'text="[^"]+"')).Count
    $descNodes = ([regex]::Matches($content, 'content-desc="[^"]+"')).Count
    Write-Host ("nodos: clickable=$clickable editText=$editable conText=$textNodes conDesc=$descNodes")
    # Presencia de marcadores (cuenta, no contenido).
    $markers = @("origen","punto de partida","tu ubicacion","pickup","destino","a donde",
                 "where to","confirmar","solicitar","pedir","request","confirm",
                 "uberx","uber x","comfort","moto","taxi","flash","black",
                 "efectivo","tarjeta","mercado pago","\$","ars","min","precio","tarifa")
    Write-Host "marcadores presentes (text|content-desc):"
    foreach ($m in $markers) {
        $c = ([regex]::Matches($content, "(?i)(text|content-desc)=`"[^`"]*$m[^`"]*`"")).Count
        if ($c -gt 0) { Write-Host ("  '{0}' -> {1}" -f $m, $c) }
    }
    Write-Host "=========================================" -ForegroundColor Magenta
}
exit 0
