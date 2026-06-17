<#
.SYNOPSIS
    Preflight de solo lectura para la demo de Estela (V1.16 Sunday Demo Readiness).

.DESCRIPTION
    Verifica el entorno antes de probar a Estela con una persona no vidente.
    NO compila. NO borra nada. NO toca archivos del usuario. NO envia mensajes.

    Checks:
      - adb y dispositivo conectado
      - package com.ojoclaro.android instalado (+ version / versionCode)
      - permisos CAMERA y RECORD_AUDIO
      - AccessibilityService de Estela enabled / bound
      - backend /health del tunel ngrok
      - espacio libre en C:
      - rama y HEAD de git + working tree
      - imprime los comandos sugeridos para el smoke pre-demo

    Opcional: con -ClearLogcat (o respondiendo "s" al prompt) limpia el ring
    buffer de logcat. Eso NO es destructivo para la app ni para datos; solo
    descarta logs viejos para que el smoke quede limpio. Por defecto NO lo hace.

.EXAMPLE
    .\scripts\estela_demo_preflight.ps1

.EXAMPLE
    .\scripts\estela_demo_preflight.ps1 -ClearLogcat
#>

[CmdletBinding()]
param(
    [string]$Adb = "adb",
    [string]$Package = "com.ojoclaro.android",
    [string]$Device = "",
    [string]$BackendHealthUrl = "https://wieldiest-etha-unrippable.ngrok-free.dev/health",
    [switch]$ClearLogcat
)

$ErrorActionPreference = "Continue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# --- helpers ----------------------------------------------------------------

$script:Fails = 0
$script:Warns = 0

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
}

function Write-Status {
    param([ValidateSet("OK", "WARN", "FAIL", "INFO")][string]$Level, [string]$Message)
    $color = "Gray"
    if ($Level -eq "OK")   { $color = "Green" }
    if ($Level -eq "WARN") { $color = "Yellow"; $script:Warns++ }
    if ($Level -eq "FAIL") { $color = "Red";    $script:Fails++ }
    Write-Host ("[{0}] {1}" -f $Level, $Message) -ForegroundColor $color
}

# adb con -s <device> si se especifico; devuelve el output como string
function Invoke-AdbShell {
    param([string]$ShellCommand)
    if ([string]::IsNullOrWhiteSpace($Device)) {
        return (& $Adb shell $ShellCommand 2>$null | Out-String)
    }
    return (& $Adb -s $Device shell $ShellCommand 2>$null | Out-String)
}

# --- 1. adb / dispositivo ----------------------------------------------------

Write-Section "ADB / Dispositivo"
$adbOk = $false
try {
    $devicesRaw = (& $Adb devices -l 2>$null | Out-String)
    $deviceLines = @($devicesRaw -split "`n" | Where-Object { $_ -match "\sdevice(\s|$)" -and $_ -notmatch "List of devices" })
    if ($deviceLines.Count -ge 1) {
        $adbOk = $true
        Write-Status OK ("Dispositivo(s) conectado(s): " + $deviceLines.Count)
        foreach ($l in $deviceLines) { Write-Host ("    " + $l.Trim()) }
        if ([string]::IsNullOrWhiteSpace($Device)) {
            $first = ($deviceLines[0].Trim() -split "\s+")[0]
            $Device = $first
            Write-Status INFO ("Usando dispositivo: " + $Device)
        }
    } else {
        Write-Status FAIL "No hay dispositivos en estado 'device'. Conecta el Moto y habilita depuracion USB."
    }
} catch {
    Write-Status FAIL ("No se pudo ejecutar adb: " + $_.Exception.Message)
}

# --- 2. package instalado + version -----------------------------------------

Write-Section "Package $Package"
if ($adbOk) {
    $pkgDump = Invoke-AdbShell "dumpsys package $Package"
    if ($pkgDump -match "versionName=") {
        $verName = ([regex]::Match($pkgDump, "versionName=(\S+)")).Groups[1].Value
        $verCode = ([regex]::Match($pkgDump, "versionCode=(\d+)")).Groups[1].Value
        Write-Status OK ("Instalado. versionName=$verName versionCode=$verCode")
    } else {
        Write-Status FAIL "El package no parece instalado. Instala la APK antes de la demo."
    }
} else {
    Write-Status WARN "Salteado (sin dispositivo)."
}

# --- 3. permisos CAMERA / RECORD_AUDIO --------------------------------------

Write-Section "Permisos runtime"
if ($adbOk) {
    $perm = Invoke-AdbShell "dumpsys package $Package"
    foreach ($p in @("android.permission.CAMERA", "android.permission.RECORD_AUDIO")) {
        $line = ($perm -split "`n" | Where-Object { $_ -match [regex]::Escape($p) -and $_ -match "granted=" } | Select-Object -First 1)
        if ($line -and $line -match "granted=true") {
            Write-Status OK ($p + " = granted")
        } elseif ($line -and $line -match "granted=false") {
            Write-Status FAIL ($p + " = DENEGADO. Concedelo en Ajustes > Apps > Estela > Permisos.")
        } else {
            Write-Status WARN ($p + " = no se pudo determinar.")
        }
    }
} else {
    Write-Status WARN "Salteado (sin dispositivo)."
}

# --- 4. AccessibilityService -------------------------------------------------

Write-Section "AccessibilityService (Estela)"
if ($adbOk) {
    $enabledFlag = (Invoke-AdbShell "settings get secure accessibility_enabled").Trim()
    $enabledServices = (Invoke-AdbShell "settings get secure enabled_accessibility_services").Trim()
    $svcComponent = "$Package/$Package.accessibility.OjoClaroAccessibilityService"

    if ($enabledFlag -eq "1") {
        Write-Status OK "Accesibilidad del sistema habilitada (flag=1)."
    } else {
        Write-Status WARN ("Flag accessibility_enabled = '" + $enabledFlag + "'.")
    }

    if ($enabledServices -match [regex]::Escape($svcComponent)) {
        Write-Status OK "Estela esta en la lista de servicios habilitados."
    } else {
        Write-Status FAIL "Estela NO esta habilitada. Activala en Ajustes > Accesibilidad."
    }

    $a11yDump = Invoke-AdbShell "dumpsys accessibility"
    if ($a11yDump -match "Bound services" -and $a11yDump -match "label=Estela") {
        Write-Status OK "Estela aparece como BOUND (servicio activo)."
    } else {
        Write-Status WARN "No se confirmo 'bound'. Si recien la activaste, abri la app una vez."
    }
} else {
    Write-Status WARN "Salteado (sin dispositivo)."
}

# --- 5. backend /health ------------------------------------------------------

Write-Section "Backend / ngrok ($BackendHealthUrl)"
$backendOk = $false
try {
    $resp = Invoke-WebRequest -Uri $BackendHealthUrl -TimeoutSec 12 -UseBasicParsing `
        -Headers @{ "ngrok-skip-browser-warning" = "true" }
    if ($resp.StatusCode -eq 200 -and ($resp.Content -notmatch "ERR_NGROK")) {
        $backendOk = $true
        Write-Status OK "Backend responde 200. Escena de vision disponible."
    } else {
        Write-Status WARN ("Respuesta inesperada: HTTP " + $resp.StatusCode)
    }
} catch {
    $code = $null
    if ($_.Exception.Response -ne $null) {
        try { $code = [int]$_.Exception.Response.StatusCode } catch { $code = $null }
    }
    if ($code -ne $null) {
        Write-Status FAIL ("Backend NO disponible (HTTP " + $code + "). Tunel ngrok caido o backend apagado. La escena no funcionara.")
    } else {
        Write-Status FAIL ("Backend NO disponible: " + $_.Exception.Message)
    }
}

# --- 6. espacio en C: --------------------------------------------------------

Write-Section "Espacio en disco C:"
try {
    $c = Get-PSDrive C -ErrorAction Stop
    $freeGb = [math]::Round($c.Free / 1GB, 2)
    $usedGb = [math]::Round($c.Used / 1GB, 2)
    if ($freeGb -lt 1) {
        Write-Status FAIL ("Libre: $freeGb GB (usado $usedGb GB). CRITICO < 1 GB. NO compilar. Liberar espacio.")
    } elseif ($freeGb -lt 5) {
        Write-Status WARN ("Libre: $freeGb GB (usado $usedGb GB). Bajo. Evitar builds grandes.")
    } else {
        Write-Status OK ("Libre: $freeGb GB (usado $usedGb GB).")
    }
} catch {
    Write-Status WARN ("No se pudo leer C: " + $_.Exception.Message)
}

# --- 7. git ------------------------------------------------------------------

Write-Section "Git"
try {
    $branch = (& git rev-parse --abbrev-ref HEAD 2>$null).Trim()
    $head = (& git rev-parse --short HEAD 2>$null).Trim()
    $sb = (& git status -sb 2>$null | Select-Object -First 1)
    Write-Status INFO ("Rama: " + $branch + "  HEAD: " + $head)
    if ($sb) { Write-Host ("    " + $sb) }
    $tree = (& git status --short 2>$null)
    if ($tree) {
        Write-Status INFO "Working tree con cambios:"
        $tree | ForEach-Object { Write-Host ("    " + $_) }
    } else {
        Write-Status OK "Working tree limpio."
    }
} catch {
    Write-Status WARN ("No se pudo leer git: " + $_.Exception.Message)
}

# --- 8. comandos sugeridos para el smoke ------------------------------------

Write-Section "Smoke pre-demo sugerido (copiar/pegar, no destructivo)"
$dev = $Device
$prefix = "adb"
if (-not [string]::IsNullOrWhiteSpace($dev)) { $prefix = "adb -s $dev" }
$act = "com.ojoclaro.DEBUG_VOICE_TEXT"

Write-Host "  # 0) Limpiar estado (siempre primero):"
Write-Host ("  {0} shell `"am broadcast -a {1} --es goal 'cancelar'`"" -f $prefix, $act)
$smoke = @(
    "lee la pantalla",
    "que puedo tocar",
    "manda plata",
    "toca pagar",
    "lee el texto",
    "cerrar camara",
    "abri instagram",
    "abri el chat de Sofi en instagram",
    "mandale a Sofi por instagram que prueba pre demo",
    "si",
    "cancelar"
)
$i = 1
foreach ($s in $smoke) {
    Write-Host ("  # {0}) {1}" -f $i, $s)
    Write-Host ("  {0} shell `"am broadcast -a {1} --es goal '{2}'`"" -f $prefix, $act, $s)
    $i++
}
Write-Host "  # 12) (max 1 vez, gasta backend si esta vivo) describir escena:"
Write-Host ("  {0} shell `"am broadcast -a {1} --es goal 'describime que estoy apuntando'`"" -f $prefix, $act)
Write-Host ""
Write-Host "  Esperado: 'si' NO envia, pagos/tarjetas se niegan, camara cierra, sin crashes." -ForegroundColor DarkGray

# --- 9. logcat opcional ------------------------------------------------------

Write-Section "Logcat (opcional)"
$doClear = $false
if ($ClearLogcat) {
    $doClear = $true
} elseif ([Environment]::UserInteractive) {
    $answer = Read-Host "Limpiar el ring buffer de logcat para que el smoke quede limpio? (s/N)"
    if ($answer -match "^[sSyY]") { $doClear = $true }
}
if ($doClear -and $adbOk) {
    if ([string]::IsNullOrWhiteSpace($Device)) { & $Adb logcat -c } else { & $Adb -s $Device logcat -c }
    Write-Status OK "logcat limpiado."
} else {
    Write-Status INFO "logcat sin tocar."
}

# --- resumen -----------------------------------------------------------------

Write-Section "Resumen"
if ($script:Fails -gt 0) {
    Write-Host ("RESULTADO: NO LISTO - {0} FAIL, {1} WARN. Resolver los FAIL antes de la demo." -f $script:Fails, $script:Warns) -ForegroundColor Red
} elseif ($script:Warns -gt 0) {
    Write-Host ("RESULTADO: PARCIAL - 0 FAIL, {0} WARN. Revisar los WARN." -f $script:Warns) -ForegroundColor Yellow
} else {
    Write-Host "RESULTADO: LISTO - 0 FAIL, 0 WARN." -ForegroundColor Green
}
Write-Host ""
