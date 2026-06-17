# =============================================================================
# run_whatsapp_anxiety_smoke.ps1
# -----------------------------------------------------------------------------
# Smoke test del sprint "WhatsApp Anxiety Hardening". Dispara cada comando de
# orientacion/seguridad por el harness debug (DebugCommandActivity), que enruta
# por el MISMO pipeline real de la voz (handleRecognizedText), y verifica con
# logcat que cada uno cayo en su ruta LOCAL nueva, que hablo por TTS y que
# NUNCA aparece un marcador de envio/borrador.
#
# SOLO comandos read-only: NO escribe borradores, NO envia, NO toca chats de
# terceros (regla absoluta #3 del sprint). El flujo de DOBLE confirmacion WA-5
# (paso 1/2, dry-run, "si" jamas envia) queda cubierto por los tests del
# resolver (WhatsAppReplyConfirmationResolverTest) porque el harness no puede
# mantener WhatsApp en foreground (la Activity de debug lo roba).
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts\windows\run_whatsapp_anxiety_smoke.ps1
#   ...  -Repeat 3
#
# Salida: C:\Users\marco\Desktop\ESTELA_WHATSAPP_ANXIETY_SMOKE_REPORT.txt
# =============================================================================

param(
    [int]$Repeat = 1,
    [string]$Only = ""
)

$ErrorActionPreference = "Continue"
$pkg = "com.ojoclaro.android"
$activity = "$pkg/.debug.DebugCommandActivity"
$svc = "$pkg/.global.GlobalAssistantService"
$report = "C:\Users\marco\Desktop\ESTELA_WHATSAPP_ANXIETY_SMOKE_REPORT.txt"

# Tokens SEGUROS que pueden ir al reporte (flags/outcomes; sin contenido).
$SafeTokens = @(
    "DEBUG_COMMAND_RECEIVED","DEBUG_COMMAND_DISPATCHED","debugVoiceTextInjected",
    "ROUTING_AUDIT","COMMAND_MATCHED","basicCommand","TTS_STARTED","TTS_COMPLETED",
    "WHATSAPP_STATE_QUERY_REQUESTED","WHATSAPP_STATE_QUERY_RESULT",
    "WHATSAPP_ANXIETY_MODE_ENABLED","WHATSAPP_SAFE_MODE_ENABLED","WHATSAPP_SAFE_MODE_DISABLED",
    "WHATSAPP_HELP_CONTEXTUAL","WHATSAPP_REPEAT_LAST_RESPONSE","WHATSAPP_RECOVERY_MESSAGE_SPOKEN",
    "WHATSAPP_NOT_SENT_REASSURANCE","WHATSAPP_EMERGENCY_CANCEL",
    "VOICE_WHERE_AM_I_MATCHED","VOICE_WHERE_AM_I_PIPELINE_START","outdoor fast path","fallbackReason"
)
$SafeRegex = ($SafeTokens | ForEach-Object { [regex]::Escape($_) }) -join "|"

# NUNCA deben aparecer: envio real, borrador escrito, dry-run disparado o fallback.
$ForbiddenRegex = "fallbackReason=no_local_match|whatsappSend outcome=|WHATSAPP_SEND_REAL|" +
    "WHATSAPP_SEND_DRY_RUN_BLOCKED|WHATSAPP_REPLY_DRAFTED|tapWhatsAppSend|tappedSend=true"

function Write-Report($s) { Add-Content -Path $report -Value $s -Encoding utf8 }
function Now() { return (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz") }

# --- Casos: comando + tokens requeridos / deseados / prohibidos extra --------
$allCases = @(
    @{ key="que paso"; cmd="que paso"; wait=6;
       require=@("ROUTING_AUDIT handler=whatsapp_anxiety_hardening","WHATSAPP_STATE_QUERY_REQUESTED kind=what_happened","TTS_STARTED");
       want=@("WHATSAPP_STATE_QUERY_RESULT","WHATSAPP_REPEAT_LAST_RESPONSE") },
    @{ key="que pasa en whatsapp"; cmd="que pasa en whatsapp"; wait=6;
       require=@("ROUTING_AUDIT handler=whatsapp_anxiety_hardening","WHATSAPP_STATE_QUERY_REQUESTED kind=explicit","WHATSAPP_STATE_QUERY_RESULT","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    @{ key="modo seguro"; cmd="modo seguro"; wait=6;
       require=@("WHATSAPP_ANXIETY_MODE_ENABLED","WHATSAPP_SAFE_MODE_ENABLED","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    @{ key="que puedo hacer aca"; cmd="que puedo hacer aca"; wait=6;
       require=@("WHATSAPP_HELP_CONTEXTUAL","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    @{ key="modo normal"; cmd="modo normal"; wait=6;
       require=@("WHATSAPP_SAFE_MODE_DISABLED","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    @{ key="ayuda"; cmd="ayuda"; wait=6;
       require=@("COMMAND_MATCHED basicCommand=HELP","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    @{ key="repeti"; cmd="repeti"; wait=6;
       require=@("COMMAND_MATCHED basicCommand=REPEAT","TTS_STARTED");
       want=@("TTS_COMPLETED") },
    # Gating: "estoy perdido" SIN WhatsApp en foreground NO debe ser secuestrado
    # por el handler de ansiedad (debe caer a Outdoor). Prueba que no hay hijack.
    @{ key="estoy perdido (gating)"; cmd="estoy perdido"; wait=8;
       require=@("ROUTING_AUDIT");
       want=@("VOICE_WHERE_AM_I_PIPELINE_START");
       forbidExtra=@("WHATSAPP_STATE_QUERY_REQUESTED") }
)
if ($Only -ne "") { $cases = $allCases | Where-Object { $_.key -eq $Only } } else { $cases = $allCases }

# --- Init reporte ------------------------------------------------------------
Set-Content -Path $report -Value "ESTELA WHATSAPP ANXIETY HARDENING - SMOKE REPORT" -Encoding utf8
Write-Report "================================================="
Write-Report ("Fecha: " + (Now))
Write-Report ("Repeticiones por comando: " + $Repeat)
Write-Report "Mecanismo: DebugCommandActivity -> ACTION_DEBUG_VOICE_TEXT (pipeline real)"
Write-Report "Solo read-only: no escribe borradores, no envia, no toca chats de terceros."
Write-Report ""

# --- ADB device --------------------------------------------------------------
$deviceLine = (& adb devices 2>$null | Select-String -Pattern "\tdevice$")
if (-not $deviceLine) {
    Write-Report "[ADB] device: NO CONECTADO - no se puede validar runtime."
    Write-Host "Sin dispositivo. Reporte en $report"
    exit 2
}
$serial = (($deviceLine -split "\t")[0]).Trim()
Write-Report ("[ADB] device: PASS ($serial)")
$acc = (& adb shell settings get secure enabled_accessibility_services 2>$null)
$accOn = ($acc -match "OjoClaroAccessibilityService")
Write-Report ("[ADB] accesibilidad Estela: " + $(if ($accOn) { "ACTIVA" } else { "APAGADA" }))
Write-Report ""

# --- Por comando -------------------------------------------------------------
$summary = @()
foreach ($case in $cases) {
    $passes = 0
    for ($i = 1; $i -le $Repeat; $i++) {
        # Reset limpio (stop propio del asistente): cierra turno, corta TTS y
        # limpia pendientes. Cada comando parte de IDLE.
        & adb shell "am startservice -n $svc -a com.ojoclaro.android.global.ACTION_STOP" 2>$null | Out-Null
        Start-Sleep -Seconds 2
        & adb logcat -c 2>$null
        & adb shell "am start -n $activity --es command '$($case.cmd)' --ei keepAliveMs $([int]($case.wait*1000))" 2>$null | Out-Null
        Start-Sleep -Seconds $case.wait

        $raw = (& adb logcat -d -v brief 2>$null)
        $safe = $raw | Select-String -Pattern $SafeRegex | ForEach-Object { $_.Line }
        $joined = ($safe -join "`n")
        $rawJoined = ($raw -join "`n")

        $missing = @()
        foreach ($req in $case.require) { if ($joined -notmatch [regex]::Escape($req)) { $missing += $req } }
        $forbiddenHit = ($rawJoined -match $ForbiddenRegex)
        $extraForbiddenHit = $false
        if ($case.ContainsKey("forbidExtra")) {
            foreach ($fb in $case.forbidExtra) { if ($joined -match [regex]::Escape($fb)) { $extraForbiddenHit = $true } }
        }
        $ok = ($missing.Count -eq 0) -and (-not $forbiddenHit) -and (-not $extraForbiddenHit)
        if ($ok) { $passes++ }

        Write-Report ("--- [" + $case.key + "] intento $i/$Repeat : " + $(if ($ok) { "PASS" } else { "FAIL" }) + " ---")
        if ($missing.Count -gt 0) { Write-Report ("   FALTAN tokens: " + ($missing -join ", ")) }
        if ($forbiddenHit) { Write-Report "   PROHIBIDO: aparecio un marcador de envio/borrador/fallback" }
        if ($extraForbiddenHit) { Write-Report "   PROHIBIDO: el handler de ansiedad secuestro una frase generica (debia caer a Outdoor)" }
        $wantHit = @(); foreach ($w in $case.want) { if ($joined -match [regex]::Escape($w)) { $wantHit += $w } }
        if ($wantHit.Count -gt 0) { Write-Report ("   extra OK: " + ($wantHit -join ", ")) }
        $evid = $safe | Select-Object -Last 12
        Write-Report "   evidencia (tokens seguros):"
        foreach ($e in $evid) { Write-Report ("     " + $e.Trim()) }
        Write-Report ""
        Start-Sleep -Seconds 1
    }
    $verdict = if ($passes -eq $Repeat) { "PASS" } elseif ($passes -gt 0) { "PARCIAL" } else { "FAIL" }
    $summary += [pscustomobject]@{ Comando = $case.key; Resultado = $verdict; Passes = "$passes/$Repeat" }
}

# --- Resumen -----------------------------------------------------------------
Write-Report "================================================="
Write-Report "RESUMEN"
foreach ($s in $summary) {
    Write-Report ("  {0,-26} {1,-8} ({2})" -f $s.Comando, $s.Resultado, $s.Passes)
}
$allPass = ($summary | Where-Object { $_.Resultado -ne "PASS" }).Count -eq 0
Write-Report ""
Write-Report ("VEREDICTO GENERAL: " + $(if ($allPass) { "PASS (todos los comandos en ruta local, 0 envios, 0 borradores)" } else { "REVISAR (ver detalles arriba)" }))

Write-Host ""
Write-Host "Smoke terminado. Reporte: $report"
$summary | Format-Table -AutoSize
if ($allPass) { exit 0 } else { exit 1 }
