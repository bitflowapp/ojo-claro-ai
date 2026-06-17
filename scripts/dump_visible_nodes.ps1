<#
.SYNOPSIS
    Dispara el volcado SANITIZADO de readVisibleNodeSummaries() de Estela y lo
    guarda en build/uber-captures/ (gitignored). Solo lectura: NO taps, NO viaje.

.DESCRIPTION
    Manda el broadcast debug com.ojoclaro.DEBUG_DUMP_VISIBLE_NODES; el servicio de
    accesibilidad loguea bajo el tag EstelaVisibleNodeDump un resumen sin datos
    sensibles (flags, longitudes, marcadores, tokens de categoria). El script
    captura ese log. Si no hay dispositivo, sale BLOCKED.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\dump_visible_nodes.ps1 -Stage uber_runtime_current
#>

[CmdletBinding()]
param(
    [string]$DeviceSerial = "ZY32LHS6PS",
    [string]$Stage = "manual",
    [string]$LogDir = "build/uber-captures",
    [string]$Adb = "adb",
    [string]$Action = "com.ojoclaro.DEBUG_DUMP_VISIBLE_NODES",
    [string]$Tag = "EstelaVisibleNodeDump"
)

$ErrorActionPreference = "Continue"
function Info($m) { Write-Host "[INFO] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[OK]   $m" -ForegroundColor Green }
function Fail($m) { Write-Host "[FAIL] $m" -ForegroundColor Red }

$adbExe = (Get-Command $Adb -ErrorAction SilentlyContinue).Source
if (-not $adbExe) { $adbExe = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adbExe)) { Fail "adb no encontrado"; exit 3 }

$devices = & $adbExe devices | Out-String
$visible = $false
foreach ($line in ($devices -split "`r?`n")) {
    if ($line -match ("^" + [regex]::Escape($DeviceSerial) + "\s+device(\s|$)")) { $visible = $true }
}
if (-not $visible) { Fail "BLOCKED: device $DeviceSerial not visible to adb"; exit 2 }

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }
$focusFile = Join-Path $LogDir "focus-$Stage.txt"
$dumpFile  = Join-Path $LogDir "visible-nodes-$Stage.txt"
$logFile   = Join-Path $LogDir "visible-nodes-$Stage.logcat.txt"

$focus = (& $adbExe -s $DeviceSerial shell dumpsys window | Select-String "mCurrentFocus" | Select-Object -First 1).ToString().Trim()
$focus | Out-File -Encoding utf8 $focusFile
Info "Foco: $focus"

# Mantener despierto y limpiar logcat antes del volcado.
& $adbExe -s $DeviceSerial shell svc power stayon true | Out-Null
& $adbExe -s $DeviceSerial shell logcat -c | Out-Null
Info "Disparando broadcast $Action ..."
& $adbExe -s $DeviceSerial shell am broadcast -a $Action | Out-Null
Start-Sleep -Seconds 3

$full = & $adbExe -s $DeviceSerial shell logcat -d | Out-String
$full | Out-File -Encoding utf8 $logFile
$dumpLines = ($full -split "`r?`n") | Where-Object { $_ -match $Tag }
($dumpLines -join "`r`n") | Out-File -Encoding utf8 $dumpFile

if ($dumpLines.Count -gt 0) {
    Ok ("Volcado: {0} lineas en {1}" -f $dumpLines.Count, $dumpFile)
    Write-Host "----- resumen (primeras lineas) -----" -ForegroundColor Magenta
    $dumpLines | Select-Object -First 4 | ForEach-Object { Write-Host $_ }
} else {
    Fail "Sin lineas $Tag. El servicio de accesibilidad puede estar inactivo o la build no es DEBUG."
}
Write-Host ("focus -> {0}" -f $focusFile)
Write-Host ("dump  -> {0}" -f $dumpFile)
exit 0
