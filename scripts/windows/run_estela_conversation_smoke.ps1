# =============================================================================
# run_estela_conversation_smoke.ps1
# -----------------------------------------------------------------------------
# Smoke test del ruteo conversacional de Estela (hardening Alexa-like).
# Dispara cada comando basico por el harness debug (DebugCommandActivity), que
# enruta por el MISMO pipeline real de la voz, y verifica con logcat que cada
# comando cayo en su ruta LOCAL (no en fallback/agente) y que hablo por TTS.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts\windows\run_estela_conversation_smoke.ps1
#   ...  -Repeat 3      (corre cada comando 3 veces, criterio de aceptacion)
#   ...  -Only "ayuda"  (corre un solo comando)
#
# Reglas: NO toca WhatsApp/Instagram, NO envia, NO llama, NO abre apps externas.
# NO imprime secretos ni ubicacion exacta: el reporte solo guarda tokens de
# diagnostico de una lista blanca segura.
#
# Salida: C:\Users\marco\Desktop\ESTELA_CONVERSATION_SMOKE_REPORT.txt
# =============================================================================

param(
    [int]$Repeat = 1,
    [string]$Only = "",
    [string]$Backend = "http://127.0.0.1:8080",
    [int]$AdbPort = 8080
)

$ErrorActionPreference = "Continue"
$pkg = "com.ojoclaro.android"
$activity = "$pkg/.debug.DebugCommandActivity"
$report = "C:\Users\marco\Desktop\ESTELA_CONVERSATION_SMOKE_REPORT.txt"

# Tokens SEGUROS que pueden ir al reporte (sin contenido de usuario/ubicacion).
$SafeTokens = @(
    "DEBUG_COMMAND_RECEIVED","DEBUG_COMMAND_DISPATCHED","DEBUG_COMMAND_RESULT",
    "DEBUG_COMMAND_ERROR","ROUTING_AUDIT","COMMAND_MATCHED","VOICE_STATE_CHANGED",
    "STT_PAUSED_FOR_TTS","STT_RESUMED_AFTER_TTS","TTS_STARTED","TTS_COMPLETED",
    "TTS_STOPPED","VOICE_DESCRIBE_ENV_MATCHED","VOICE_DESCRIBE_ENV_PIPELINE_START",
    "VOICE_WHERE_AM_I_MATCHED","VOICE_WHERE_AM_I_PIPELINE_START",
    "SCREEN_READ_PIPELINE_START","captureCompleted","visionHttpStatus",
    "analysisCompleted","safetyPolicyApplied","fallbackReason","screenQuery=local",
    "needsAccessibility","outdoor fast path","basicCommand",
    "cameraStarted","cameraBound","cameraUnbound","cameraProviderReady",
    "captureStarted","visionRequestStarted","visionResponsePresent","navigationState"
)
$SafeRegex = ($SafeTokens | ForEach-Object { [regex]::Escape($_) }) -join "|"
$ForbiddenRegex = "fallbackReason=no_local_match|agent_task_deflected|intent_runtime_next"

function Write-Report($s) { Add-Content -Path $report -Value $s -Encoding utf8 }
function Now() { return (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz") }

# --- Casos: comando + espera + tokens requeridos / deseados ------------------
$allCases = @(
    @{ key="describir entorno"; cmd="describir entorno"; keepAlive=9000; wait=16;
       require=@("VOICE_DESCRIBE_ENV_PIPELINE_START","captureCompleted=true","visionHttpStatus=200","analysisCompleted=true");
       want=@("safetyPolicyApplied=true","cameraUnbound=true") },
    @{ key="donde estoy"; cmd="donde estoy"; keepAlive=10000; wait=22;
       require=@("VOICE_WHERE_AM_I_PIPELINE_START","TTS_STARTED");
       want=@("TTS_COMPLETED","VOICE_WHERE_AM_I_MATCHED") },
    @{ key="leer pantalla"; cmd="leer pantalla"; keepAlive=5000; wait=9;
       require=@("SCREEN_READ_PIPELINE_START","TTS_STARTED");
       want=@("screenQuery=local","TTS_COMPLETED") },
    @{ key="ayuda"; cmd="ayuda"; keepAlive=5000; wait=10;
       require=@("COMMAND_MATCHED basicCommand=HELP","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    @{ key="repetir"; cmd="repetir"; keepAlive=5000; wait=10;
       require=@("COMMAND_MATCHED basicCommand=REPEAT","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    @{ key="cancelar"; cmd="cancelar"; keepAlive=5000; wait=9;
       require=@("COMMAND_MATCHED basicCommand=CANCEL","TTS_STARTED");
       want=@("TTS_COMPLETED") }
)
if ($Only -ne "") { $cases = $allCases | Where-Object { $_.key -eq $Only } }
else { $cases = $allCases }

# --- Init reporte ------------------------------------------------------------
Set-Content -Path $report -Value "ESTELA CONVERSATION SMOKE REPORT" -Encoding utf8
Write-Report "================================="
Write-Report ("Fecha: " + (Now))
Write-Report ("Repeticiones por comando: " + $Repeat)
Write-Report ""

# --- 1. Backend /health ------------------------------------------------------
$backendOk = $false
try {
    $h = Invoke-RestMethod -Uri "$Backend/health" -TimeoutSec 5
    if ($h.ok -eq $true) { $backendOk = $true }
} catch { $backendOk = $false }
Write-Report ("[BACKEND] /health: " + $(if ($backendOk) { "PASS ($Backend ok=true)" } else { "FAIL (sin respuesta en $Backend)" }))

# --- 2. ADB device + reverse -------------------------------------------------
$deviceLine = (& adb devices 2>$null | Select-String -Pattern "\tdevice$")
$deviceConnected = $false
if ($deviceLine) { $deviceConnected = $true }

$reverseOk = $false
if ($deviceConnected) {
    & adb reverse "tcp:$AdbPort" "tcp:$AdbPort" 2>$null | Out-Null
    $rev = (& adb reverse --list 2>$null | Select-String -Pattern "$AdbPort")
    if ($rev) { $reverseOk = $true }
    Write-Report ("[ADB] device: PASS (" + (($deviceLine -split "\t")[0]) + ")")
    Write-Report ("[ADB] reverse tcp:${AdbPort}: " + $(if ($reverseOk) { "PASS" } else { "FAIL" }))
    $acc = (& adb shell settings get secure enabled_accessibility_services 2>$null)
    $accOn = ($acc -match "OjoClaroAccessibilityService")
    Write-Report ("[ADB] accesibilidad Estela: " + $(if ($accOn) { "ACTIVA" } else { "APAGADA (leer pantalla pedira activarla)" }))
} else {
    Write-Report "[ADB] device: NO CONECTADO (se omiten pruebas en dispositivo)"
}
Write-Report ""

if (-not $deviceConnected) {
    Write-Report "RESULTADO: PARCIAL - sin dispositivo no se puede validar el ruteo en runtime."
    Write-Host "Sin dispositivo. Reporte en $report"
    exit 2
}

# --- 3. Por comando ----------------------------------------------------------
$summary = @()
foreach ($case in $cases) {
    $passes = 0
    for ($i = 1; $i -le $Repeat; $i++) {
        # Reset limpio del asistente (su PROPIO stop, no force-stop): cierra
        # cualquier turno previo, corta TTS y limpia pendientes (ej. el "a donde
        # queres ir" que deja "donde estoy"). Asi cada comando parte de IDLE.
        & adb shell "am startservice -n $pkg/.global.GlobalAssistantService -a com.ojoclaro.android.global.ACTION_STOP" 2>$null | Out-Null
        Start-Sleep -Seconds 2

        & adb logcat -c 2>$null
        # El valor del extra va entre comillas SIMPLES para que el shell del
        # dispositivo lo tome como un solo token (si no, "describir entorno" se
        # parte en dos y cae a fallback). Todo el am va como un unico string.
        $amCmd = "am start -n $activity --es command '$($case.cmd)' --ei keepAliveMs $($case.keepAlive)"
        & adb shell $amCmd 2>$null | Out-Null
        Start-Sleep -Seconds $case.wait

        $raw = (& adb logcat -d -v brief 2>$null)
        $safe = $raw | Select-String -Pattern $SafeRegex | ForEach-Object { $_.Line }
        $joined = ($safe -join "`n")

        $missing = @()
        foreach ($req in $case.require) { if ($joined -notmatch [regex]::Escape($req)) { $missing += $req } }
        $forbiddenHit = ($joined -match $ForbiddenRegex)
        $ok = ($missing.Count -eq 0) -and (-not $forbiddenHit)
        if ($ok) { $passes++ }

        Write-Report ("--- [" + $case.key + "] intento $i/$Repeat : " + $(if ($ok) { "PASS" } else { "FAIL" }) + " ---")
        if ($missing.Count -gt 0) { Write-Report ("   FALTAN tokens: " + ($missing -join ", ")) }
        if ($forbiddenHit) { Write-Report "   CAUSA: cayo en fallback/agente (token prohibido presente)" }
        $wantHit = @(); foreach ($w in $case.want) { if ($joined -match [regex]::Escape($w)) { $wantHit += $w } }
        if ($wantHit.Count -gt 0) { Write-Report ("   extra OK: " + ($wantHit -join ", ")) }
        # Evidencia: ultimas lineas seguras (sin contenido de usuario).
        $evid = $safe | Select-Object -Last 12
        Write-Report "   evidencia (tokens seguros):"
        foreach ($e in $evid) { Write-Report ("     " + $e.Trim()) }
        Write-Report ""

        # Gap corto extra (el reset al inicio del proximo comando hace la
        # limpieza pesada).
        Start-Sleep -Seconds 2
    }
    $verdict = if ($passes -eq $Repeat) { "PASS" } elseif ($passes -gt 0) { "PARCIAL" } else { "FAIL" }
    $summary += [pscustomobject]@{ Comando = $case.key; Resultado = $verdict; Passes = "$passes/$Repeat" }
}

# --- 4. Resumen --------------------------------------------------------------
Write-Report "================================="
Write-Report "RESUMEN"
Write-Report ("Backend: " + $(if ($backendOk) { "OK" } else { "FAIL" }) + " | ADB reverse: " + $(if ($reverseOk) { "OK" } else { "FAIL" }))
foreach ($s in $summary) {
    Write-Report ("  {0,-20} {1,-8} ({2})" -f $s.Comando, $s.Resultado, $s.Passes)
}
$allPass = ($summary | Where-Object { $_.Resultado -ne "PASS" }).Count -eq 0
Write-Report ""
Write-Report ("VEREDICTO GENERAL: " + $(if ($allPass -and $backendOk) { "LISTO (todos PASS)" } else { "NO LISTO (ver detalles arriba)" }))

Write-Host ""
Write-Host "Smoke terminado. Reporte: $report"
$summary | Format-Table -AutoSize
if ($allPass -and $backendOk) { exit 0 } else { exit 1 }
