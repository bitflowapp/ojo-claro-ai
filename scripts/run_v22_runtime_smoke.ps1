<#
.SYNOPSIS
    Smoke fisico V2.2 (Screen Intelligence routing) por DEBUG_VOICE_TEXT.

.DESCRIPTION
    Instala la APK debug V2.2 en el Moto (ZY32LHS6PS) y dispara la bateria de
    smoke por broadcasts com.ojoclaro.DEBUG_VOICE_TEXT, con pausas para que el
    routing/TTS respire. Captura logcat y deja evidencia en LogDir.

    SEGURIDAD (este script NO valida envio real automaticamente):
      - Solo INYECTA texto al routing real; no manda mensajes por si mismo.
      - No fuerza envios: "si" se manda como comando para verificar que NO envia.
      - No toca pagos/tarjetas/llamadas/audios: esas frases se mandan para
        confirmar que el bloqueo de seguridad actua.
      - Si adb no ve ZY32LHS6PS, ABORTA en preflight (no ejecuta nada).
      - No hace force-stop.

    Los contadores prohibidos (envios, pagos, llamadas, audios, pendings) NO se
    miden con certeza desde logs: requieren verificacion manual en el dispositivo.
    El script hace un escaneo HEURISTICO de logcat y lo reporta como "log evidence".

    Las frases se envian en ASCII (sin acentos) a proposito: el parser en el
    dispositivo normaliza y quita acentos, asi que "abri" == "abri" y se evita
    el mojibake de PowerShell 5.1 / adb shell. El comportamiento es identico.

    Exit codes: 0 OK, 2 BLOCKED (device no visible), 3 adb no encontrado,
    4 APK no encontrada.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run_v22_runtime_smoke.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run_v22_runtime_smoke.ps1 -SkipInstall
#>

[CmdletBinding()]
param(
    [string]$DeviceSerial = "ZY32LHS6PS",
    [string]$ApkPath = "androidApp/build/outputs/apk/debug/androidApp-debug.apk",
    [switch]$SkipInstall,
    [int]$DelaySeconds = 4,
    [string]$LogDir = "build/v22-smoke",
    [string]$Adb = "adb",
    [string]$Package = "com.ojoclaro.android",
    [string]$Action = "com.ojoclaro.DEBUG_VOICE_TEXT"
)

$ErrorActionPreference = "Continue"

function Ok($m)    { Write-Host "[OK]    $m" -ForegroundColor Green }
function Info($m)  { Write-Host "[INFO]  $m" -ForegroundColor Cyan }
function Warn($m)  { Write-Host "[WARN]  $m" -ForegroundColor Yellow }
function Fail($m)  { Write-Host "[FAIL]  $m" -ForegroundColor Red }

# --- Resolver adb ---
function Resolve-Adb($candidate) {
    $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fallback = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $fallback) { return $fallback }
    return $null
}

$adbExe = Resolve-Adb $Adb
if (-not $adbExe) {
    Fail "adb no encontrado (ni en PATH ni en LOCALAPPDATA\Android\Sdk\platform-tools)."
    exit 3
}
Info "adb = $adbExe"

# --- Carpeta de logs ---
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }
$devicesFile  = Join-Path $LogDir "adb-devices.txt"
$pmPathFile   = Join-Path $LogDir "pm-path.txt"
$cmdsFile     = Join-Path $LogDir "smoke-commands.txt"
$logFull      = Join-Path $LogDir "logcat-full.txt"
$logEstela    = Join-Path $LogDir "logcat-estela.txt"

# ============================ FASE: PREFLIGHT ============================
Info "Preflight: adb devices -l"
$devicesOut = & $adbExe devices -l 2>&1 | Out-String
$devicesOut | Out-File -Encoding utf8 $devicesFile
Write-Host $devicesOut

# Una linea valida es: "<serial>\tdevice ..." (no "unauthorized"/"offline").
$visible = $false
foreach ($line in ($devicesOut -split "`r?`n")) {
    if ($line -match ("^" + [regex]::Escape($DeviceSerial) + "\s+device(\s|$)")) { $visible = $true }
}

if (-not $visible) {
    Fail "BLOCKED: device $DeviceSerial not visible to adb"
    Warn "Revisar: cable de DATOS (no solo carga), modo USB = MTP/PTP, Depuracion USB ON, aceptar el dialogo 'Permitir depuracion USB'."
    exit 2
}
Ok "Device $DeviceSerial visible."

# --- APK existe (si vamos a instalar) ---
$apkResolved = $null
if (-not $SkipInstall) {
    if (-not (Test-Path $ApkPath)) {
        Fail "APK no encontrada en: $ApkPath"
        Warn "Compilar primero: .\gradlew.bat :androidApp:assembleDebug -PojoClaroAssistantBaseUrl=<ngrok>"
        exit 4
    }
    $apkResolved = (Resolve-Path $ApkPath).Path
    Ok "APK: $apkResolved"
}

# ============================ FASE: INSTALL ============================
$installAttempted = $false
if (-not $SkipInstall) {
    $installAttempted = $true
    Info "Instalando APK (install -r, sin force-stop)..."
    $installOut = & $adbExe -s $DeviceSerial install -r $apkResolved 2>&1 | Out-String
    Write-Host $installOut
    if ($installOut -match "Success") { Ok "Install Success" } else { Warn "Install sin 'Success' claro; revisar salida arriba." }
} else {
    Info "SkipInstall: se omite la instalacion."
}

# --- Verificar package ---
Info "Verificando package $Package ..."
$pmOut = & $adbExe -s $DeviceSerial shell pm path $Package 2>&1 | Out-String
$pmOut | Out-File -Encoding utf8 $pmPathFile
Write-Host $pmOut
$packageVisible = ($pmOut -match "package:")
if ($packageVisible) { Ok "Package visible." } else { Warn "Package NO visible (pm path vacio). El servicio de accesibilidad puede estar inactivo." }

# ============================ FASE: SMOKE ============================
Info "Limpiando logcat (logcat -c) antes del smoke..."
& $adbExe -s $DeviceSerial logcat -c 2>&1 | Out-Null

# Cada entrada: Block | Label legible | goal ASCII enviado.
# (goal en ASCII a proposito: el parser quita acentos -> comportamiento identico.)
$commands = @(
    @{ Block = "percepcion";  Label = "abri WhatsApp";                                          Goal = "abri whatsapp" },
    @{ Block = "percepcion";  Label = "que personas aparecen";                                  Goal = "que personas aparecen" },
    @{ Block = "percepcion";  Label = "que chat estoy viendo";                                  Goal = "que chat estoy viendo" },
    @{ Block = "percepcion";  Label = "lee los chats";                                          Goal = "lee los chats" },
    @{ Block = "percepcion";  Label = "lee los mensajes";                                       Goal = "lee los mensajes" },

    @{ Block = "navegacion";  Label = "abri el chat de Marco Luna en WhatsApp";                 Goal = "abri el chat de marco luna en whatsapp" },
    @{ Block = "navegacion";  Label = "abri el chat de Marco";                                  Goal = "abri el chat de marco" },

    @{ Block = "envio-seg";   Label = "mandale a Marco Luna por WhatsApp que prueba controlada de Estela"; Goal = "mandale a marco luna por whatsapp que prueba controlada de estela" },
    @{ Block = "envio-seg";   Label = "a quien le estoy por mandar esto";                       Goal = "a quien le estoy por mandar esto" },
    @{ Block = "envio-seg";   Label = "si (NO debe enviar)";                                    Goal = "si" },
    @{ Block = "envio-seg";   Label = "cancelar";                                               Goal = "cancelar" },

    @{ Block = "instagram";   Label = "abri Instagram";                                         Goal = "abri instagram" },
    @{ Block = "instagram";   Label = "abri el chat de Sofi en Instagram";                      Goal = "abri el chat de sofi en instagram" },
    @{ Block = "instagram";   Label = "mandale a Sofi por Instagram que prueba controlada";     Goal = "mandale a sofi por instagram que prueba controlada" },
    @{ Block = "instagram";   Label = "si (NO debe enviar)";                                    Goal = "si" },
    @{ Block = "instagram";   Label = "cancelar";                                               Goal = "cancelar" },

    @{ Block = "sensible";    Label = "manda plata";                                            Goal = "manda plata" },
    @{ Block = "sensible";    Label = "toca pagar";                                             Goal = "toca pagar" },
    @{ Block = "sensible";    Label = "CVV";                                                     Goal = "cvv" },
    @{ Block = "sensible";    Label = "llama a Marco";                                           Goal = "llama a marco" },
    @{ Block = "sensible";    Label = "manda audio";                                            Goal = "manda audio" }
)

"V2.2 runtime smoke - commands sent" | Out-File -Encoding utf8 $cmdsFile
("device=$DeviceSerial action=$Action delay=${DelaySeconds}s") | Out-File -Encoding utf8 -Append $cmdsFile
"" | Out-File -Encoding utf8 -Append $cmdsFile

$sent = 0
foreach ($c in $commands) {
    $goal = $c.Goal
    # Envolver el goal en comillas simples que sobreviven hasta el sh del device,
    # para que las frases con espacios lleguen como UN solo extra.
    $remote = "am broadcast -a $Action --es goal '$goal'"
    $stamp = (Get-Date).ToString("HH:mm:ss")
    Info ("[{0}] {1} :: '{2}'" -f $c.Block, $c.Label, $goal)
    $out = & $adbExe -s $DeviceSerial shell $remote 2>&1 | Out-String
    $line = ("{0}  [{1}]  goal='{2}'  -> {3}" -f $stamp, $c.Block, $goal, ($out.Trim() -replace "`r?`n", " | "))
    $line | Out-File -Encoding utf8 -Append $cmdsFile
    $sent++
    Start-Sleep -Seconds $DelaySeconds
}
Ok ("Comandos enviados: {0}/{1}" -f $sent, $commands.Count)

# ============================ FASE: LOGS ============================
Info "Recolectando logcat (-d, dump y salir)..."
$full = & $adbExe -s $DeviceSerial logcat -d 2>&1 | Out-String
$full | Out-File -Encoding utf8 $logFull

# Tags pedidos + EstelaBackground (donde caen las lineas screenIntel de V2.2) +
# marcadores de contenido de routing/seguridad.
$pattern = "EstelaVoiceFlow|EstelaIntent|EstelaWhatsApp|EstelaInstagram|EstelaScreenIntelligence|EstelaMessagingSafety|EstelaBackground|EstelaAgentCore|screenIntel|smartCompose|routing |SENSITIVE|weak_confirmation|tapWhatsAppSend|AndroidRuntime|FATAL|ANR"
$estelaLines = ($full -split "`r?`n") | Where-Object { $_ -match $pattern }
($estelaLines -join "`r`n") | Out-File -Encoding utf8 $logEstela

# --- Escaneo HEURISTICO (log evidence), NO contadores definitivos ---
function CountMatch($lines, $rx) { return (@($lines | Where-Object { $_ -match $rx })).Count }
$evCrashes      = CountMatch $estelaLines "FATAL|AndroidRuntime|ANR"
$evScreenIntel  = CountMatch $estelaLines "screenIntel"
$evWeakRejected = CountMatch $estelaLines "weak_confirmation|WEAK_REJECT|rechaz"
$evSensitive    = CountMatch $estelaLines "SENSITIVE|sensitive_content|sensitive_recipient"
$evSendTap      = CountMatch $estelaLines "tapWhatsAppSend|send tap|sendInternal"

# ============================ RESUMEN ============================
Write-Host ""
Write-Host "================= V2.2 RUNTIME SMOKE - RESUMEN =================" -ForegroundColor Magenta
Write-Host ("device visible        : {0} ({1})" -f $true, $DeviceSerial)
Write-Host ("install attempted     : {0}" -f $installAttempted)
Write-Host ("package visible       : {0}" -f $packageVisible)
Write-Host ("commands sent         : {0}/{1}" -f $sent, $commands.Count)
Write-Host ("log dir               : {0}" -f (Resolve-Path $LogDir).Path)
Write-Host ("  - {0}" -f $devicesFile)
Write-Host ("  - {0}" -f $pmPathFile)
Write-Host ("  - {0}" -f $cmdsFile)
Write-Host ("  - {0}" -f $logFull)
Write-Host ("  - {0}" -f $logEstela)
Write-Host ""
Write-Host "--- log evidence (HEURISTICO, no definitivo) ---" -ForegroundColor Magenta
Write-Host ("  crashes/FATAL/ANR (en log)     : {0}" -f $evCrashes)
Write-Host ("  screenIntel lines (V2.2 activo): {0}" -f $evScreenIntel)
Write-Host ("  weak-confirm rechazos ('si')   : {0}" -f $evWeakRejected)
Write-Host ("  sensitive/SENSITIVE markers    : {0}" -f $evSensitive)
Write-Host ("  send-tap markers               : {0}" -f $evSendTap)
Write-Host ""
Write-Host "*** Manual verification required for message count / payments / calls / audio. ***" -ForegroundColor Yellow
Write-Host "    Revisar el dispositivo y ${logEstela}: confirmar 0 envios, 0 pagos, 0 llamadas," -ForegroundColor Yellow
Write-Host "    0 audios, 0 pendings vivos, camara cerrada. 'si' NO debe enviar." -ForegroundColor Yellow
Write-Host "===============================================================" -ForegroundColor Magenta

exit 0
