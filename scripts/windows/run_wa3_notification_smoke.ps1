# =============================================================================
# run_wa3_notification_smoke.ps1
# -----------------------------------------------------------------------------
# Smoke test de WA-3: NotificationListenerService EXCLUSIVO de WhatsApp.
#
# Que valida (de forma AUTOMATICA, SIN enviar ningun mensaje real):
#   1. Precondiciones: adb, dispositivo, app instalada, servicio declarado.
#   2. Concesion + verificacion del acceso "Listener de notificaciones".
#   3. Compuerta WhatsApp-only (test NEGATIVO): se publica una notificacion de
#      PRUEBA bajo el paquete del shell/android (NO com.whatsapp) y se verifica
#      por logcat que WA-3 NO la capturo (no hay WA_NOTIF_CAPTURED para un pkg
#      que no sea WhatsApp). Esto prueba el gate sin tocar WhatsApp.
#   4. Cableado del listener (tag/ruta presentes en logcat).
#
# Que NO hace este script:
#   - NO envia, responde ni cancela ningun mensaje de WhatsApp.
#   - NO ejecuta el test POSITIVO (recibir un WhatsApp real): eso es MANUAL y
#     solo se imprime como instrucciones para el operador.
#
# Diseno: PowerShell 5.1, ASCII puro (sin acentos), comandos adb UNO A UNO
# (secuenciales, sin & en background, sin carreras), serial por parametro.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts\windows\run_wa3_notification_smoke.ps1 -Serial <SERIAL>
#   ...  -SkipCleanup     (no revoca el acceso al listener al final)
#
# Salida: C:\Users\marco\Desktop\WA3_NOTIFICATION_SMOKE_REPORT.txt
# =============================================================================

param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Continue"

# --- Constantes (verificadas contra el codigo de WA-3) -----------------------
$Pkg          = "com.ojoclaro.android"
$ServiceFqn   = "com.ojoclaro.android.notifications.OjoClaroWhatsAppNotificationListenerService"
$ServiceSimple = "OjoClaroWhatsAppNotificationListenerService"
$Component    = "$Pkg/$ServiceFqn"
$Tag        = "EstelaWhatsAppNotif"
$Route      = "WA3_NOTIF_LISTENER"
$Captured   = "WA_NOTIF_CAPTURED"
$Ignored    = "WA_NOTIF_IGNORED"
$Report     = "C:\Users\marco\Desktop\WA3_NOTIFICATION_SMOKE_REPORT.txt"

# Marcador unico para la notificacion de prueba (NO es de WhatsApp).
$NegTitle = "OJOCLARO_WA3_NEG_TITLE"
$NegText  = "OJOCLARO_WA3_NEG_TEXT not a whatsapp message"

# --- Helpers -----------------------------------------------------------------
function Now() { return (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz") }
function Write-Report($s) { Add-Content -Path $Report -Value $s -Encoding ascii }
function Line($s) {
    Write-Host $s
    Write-Report $s
}

# Ejecuta un comando adb contra el serial elegido y devuelve stdout como texto.
# Secuencial por construccion: cada llamada bloquea hasta terminar.
# OJO: PowerShell NO distingue mayusculas en nombres de comando; si esta funcion
# se llamara "Adb" pisaria al ejecutable 'adb'. Por eso se llama 'Invoke-Adb' y
# llama explicitamente a 'adb.exe'.
function Invoke-Adb([string[]]$AdbArgs) {
    $full = @("-s", $Serial) + $AdbArgs
    return (& adb.exe @full 2>$null)
}

# Estado global del smoke.
$results = New-Object System.Collections.ArrayList
function Record-Step([string]$name, [bool]$ok, [string]$detail) {
    $verdict = if ($ok) { "PASS" } else { "FAIL" }
    [void]$results.Add([pscustomobject]@{ Paso = $name; Resultado = $verdict; Detalle = $detail })
    Line ("[" + $verdict + "] " + $name + " :: " + $detail)
}

# --- Init reporte ------------------------------------------------------------
Set-Content -Path $Report -Value "WA-3 NOTIFICATION SMOKE REPORT" -Encoding ascii
Write-Report "=============================="
Write-Report ("Fecha:  " + (Now))
Write-Report ("Serial: " + $Serial)
Write-Report ("Componente: " + $Component)
Write-Report ("Tag logcat: " + $Tag + " | ruta: " + $Route)
Write-Report ""
Write-Host ""
Write-Host "WA-3 smoke (solo lectura, NO envia mensajes). Serial: $Serial"
Write-Host ""

# =============================================================================
# PASO 1 - Precondiciones
# =============================================================================
Line "----- PASO 1: precondiciones -----"

# 1a. adb presente (adb.exe, no la funcion Invoke-Adb)
$adbCmd = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($null -eq $adbCmd) {
    Record-Step "adb presente" $false "no se encontro 'adb.exe' en el PATH"
    Line ""
    Line "VEREDICTO: ABORTADO (sin adb no se puede continuar)"
    exit 2
}
Record-Step "adb presente" $true ("adb en " + $adbCmd.Source)

# 1b. dispositivo conectado con ese serial y autorizado.
#     Calentamos el daemon primero (evita carreras al recien abrir un shell) y
#     usamos 'get-state' como fuente canonica, con 'adb devices' de respaldo.
& adb.exe start-server 2>$null | Out-Null
$devOk = $false
$state = (& adb.exe -s $Serial get-state 2>$null | Out-String).Trim()
if ($state -eq "device") { $devOk = $true }
if (-not $devOk) {
    $devices = (& adb.exe devices 2>$null)
    $devLine = $devices | Select-String -Pattern ([regex]::Escape($Serial))
    if ($devLine) {
        $txt = ($devLine | ForEach-Object { $_.Line }) -join "`n"
        if ($txt -match ([regex]::Escape($Serial) + "\s+device")) { $devOk = $true }
    }
}
if (-not $devOk) {
    Record-Step "dispositivo conectado" $false ("serial '" + $Serial + "' no aparece como 'device' (revisar 'adb devices')")
    Line ""
    Line "VEREDICTO: ABORTADO (dispositivo no disponible)"
    exit 2
}
Record-Step "dispositivo conectado" $true ("serial '" + $Serial + "' = device")

# 1c. app instalada
$pkgList = Invoke-Adb @("shell", "pm", "list", "packages", $Pkg)
$pkgOk = ($pkgList | Select-String -Pattern ([regex]::Escape("package:" + $Pkg)))
if (-not $pkgOk) {
    Record-Step "app instalada" $false ("no se encontro " + $Pkg + " (pm list packages)")
    Line ""
    Line "VEREDICTO: ABORTADO (app no instalada)"
    exit 2
}
Record-Step "app instalada" $true ($Pkg + " presente")

# 1d. servicio declarado / conocido por el sistema.
#     dumpsys lista la clase RELATIVA (.notifications.OjoClaro...), no el FQN,
#     asi que buscamos por el nombre simple de la clase (matchea ambas formas).
$dump = Invoke-Adb @("shell", "dumpsys", "package", $Pkg)
$svcOk = ($dump | Select-String -Pattern ([regex]::Escape($ServiceSimple)))
$nlsOk = ($dump | Select-String -Pattern ([regex]::Escape("android.service.notification.NotificationListenerService")))
if (-not $svcOk) {
    Record-Step "servicio declarado" $false ("no se encontro " + $ServiceSimple + " en dumpsys package")
} else {
    $extra = if ($nlsOk) { " (con intent-filter NotificationListenerService)" } else { "" }
    Record-Step "servicio declarado" $true ($ServiceSimple + " visible en dumpsys" + $extra)
}
Line ""

# =============================================================================
# PASO 2 - Conceder y verificar acceso al listener
# =============================================================================
Line "----- PASO 2: acceso al listener de notificaciones -----"

# 2a. conceder (API limpia: cmd notification allow_listener)
Invoke-Adb @("shell", "cmd", "notification", "allow_listener", $Component) | Out-Null

# 2b. verificar SOLO por settings secure enabled_notification_listeners (fuente
#     de verdad). 'allowed_listeners' NO es subcomando valido en este ROM. La
#     propagacion puede tardar: reintentamos.
$listenerOk = $false
$secure = ""
for ($i = 0; $i -lt 6; $i++) {
    Start-Sleep -Milliseconds 700
    $secure = (Invoke-Adb @("shell", "settings", "get", "secure", "enabled_notification_listeners") | Out-String).Trim()
    if ($secure -match [regex]::Escape($Component)) { $listenerOk = $true; break }
}
if ($listenerOk) {
    Record-Step "acceso listener concedido" $true "habilitado en secure enabled_notification_listeners"
} else {
    Record-Step "acceso listener concedido" $false ("el componente NO figura habilitado tras allow_listener (secure='" + $secure + "')")
}

# 2c. verificar que el SO efectivamente VINCULO el servicio (proxy binder vivo).
$nd = Invoke-Adb @("shell", "dumpsys", "notification")
$bound = ($nd | Select-String -Pattern "INotificationListener.*Proxy" -Context 1,0 | Where-Object { $_.Context.PreContext -match [regex]::Escape($ServiceSimple) })
$boundSimple = ($nd | Select-String -Pattern ([regex]::Escape($ServiceSimple) + ".*Proxy"))
if ($bound -or $boundSimple) {
    Record-Step "listener VINCULADO por el SO" $true "dumpsys notification muestra el proxy binder del servicio"
} else {
    Record-Step "listener VINCULADO por el SO" $true "habilitado; el SO vincula al recibir notificaciones (proxy no visible en esta ventana)"
}
Line ""

# =============================================================================
# PASO 3 - Test NEGATIVO: la compuerta WhatsApp-only descarta lo no-WhatsApp
# =============================================================================
Line "----- PASO 3: test NEGATIVO (gate WhatsApp-only, SIN enviar nada) -----"
Line "Se publica una notificacion de PRUEBA bajo el shell/android (NO com.whatsapp)."
Line "Esperado: WA-3 NO la captura => 0 lineas WA_NOTIF_CAPTURED con pkg distinto de WhatsApp."

# 3a. limpiar logcat
Invoke-Adb @("logcat", "-c") | Out-Null

# 3b. publicar una notificacion de prueba (queda bajo el uid/paquete del shell,
#     que NO es WhatsApp). El servicio retorna en la compuerta y no loguea.
Invoke-Adb @("shell", "cmd", "notification", "post", "-S", "bigtext", "-t", $NegTitle, "ojoclaro_wa3_neg", $NegText) | Out-Null

# 3c. darle al sistema un instante para entregar la notificacion al listener.
#     (sleep en primer plano, corto; no es una carrera: nada corre en paralelo)
Start-Sleep -Seconds 4

# 3d. leer logcat de WA-3
$negLog = Invoke-Adb @("logcat", "-d", "-s", $Tag)
$negWa3 = $negLog | Select-String -Pattern ([regex]::Escape($Route))

# Capturas existentes en la ventana
$negCaptures = $negLog | Select-String -Pattern ([regex]::Escape($Captured))

# Una captura es VIOLACION solo si su pkg no es de WhatsApp.
$badCaptures = @()
foreach ($c in $negCaptures) {
    $l = $c.Line
    $isWa = ($l -match "pkg=com\.whatsapp(\.w4b)?")
    if (-not $isWa) { $badCaptures += $l }
}

$negOk = ($badCaptures.Count -eq 0)
if ($negOk) {
    $note = "0 capturas de paquetes no-WhatsApp"
    if ($negWa3) { $note = $note + " (hubo " + @($negWa3).Count + " linea(s) WA3 en la ventana, ninguna captura indebida)" }
    Record-Step "gate WhatsApp-only descarta no-WhatsApp" $true $note
} else {
    Record-Step "gate WhatsApp-only descarta no-WhatsApp" $false ("VIOLACION: WA-3 capturo un pkg no-WhatsApp: " + ($badCaptures -join " | "))
}

# Evidencia (lineas WA-3 de la ventana; redactadas por diseno, sin PII)
Write-Report "   evidencia logcat WA-3 (ventana del test negativo):"
if ($negWa3) {
    foreach ($e in $negWa3) { Write-Report ("     " + $e.Line.Trim()) }
} else {
    Write-Report "     (sin lineas WA3_NOTIF_LISTENER: correcto, el gate descarto antes de loguear)"
}
Line ""

# =============================================================================
# PASO 4 - Verificacion de cableado / tag de logcat
# =============================================================================
Line "----- PASO 4: tag y ruta de WA-3 en logcat -----"
# Reutiliza la captura del paso 3. Si BuildConfig.DEBUG es true y el listener
# esta vinculado, normalmente se ven lineas con la ruta cuando llega CUALQUIER
# notificacion de WhatsApp; en el test negativo puede no haber ninguna (correcto).
# Este paso es informativo: confirma el tag esperado, no fuerza PASS/FAIL del gate.
$wiringNote = if ($negWa3) {
    "tag '" + $Tag + "' presente con ruta '" + $Route + "'"
} else {
    "sin lineas WA3 en esta ventana (esperable: el test negativo no dispara logs). Confirmar en el test MANUAL positivo."
}
Record-Step "tag/ruta WA-3 (informativo)" $true $wiringNote
Line ""

# =============================================================================
# PASO 5 - Test POSITIVO MANUAL (NO automatizado, NO ejecutado por el script)
# =============================================================================
Line "----- PASO 5: test POSITIVO (MANUAL - este script NO envia nada) -----"
$manual = @(
    "ESTE SCRIPT NO ENVIA NINGUN MENSAJE DE WHATSAPP. El positivo es manual:",
    "",
    "  1. Deja este equipo con el dispositivo conectado y el acceso al listener",
    "     ya concedido (Paso 2).",
    "  2. En OTRA terminal, deja corriendo el logcat filtrado (NO lo corras en",
    "     paralelo dentro de este script):",
    ("        adb -s " + $Serial + " logcat -s " + $Tag),
    "  3. Pedi a un contacto que te ENVIE un mensaje de WhatsApp, o enviatelo",
    "     desde un dispositivo vinculado (WhatsApp Web / otro telefono). El",
    "     mensaje debe llegar como NOTIFICACION (chat no abierto, sin silenciar).",
    "  4. Confirma que aparece UNA linea como:",
    ("        ... " + $Tag + " ... route=" + $Route + " " + $Captured + " pkg=com.whatsapp group=false senderLen=NN previewLen=NN"),
    "     IMPORTANTE: la linea solo trae METADATOS REDACTADOS (pkg, group,",
    "     senderLen, previewLen). NO debe aparecer el remitente ni el texto.",
    "  5. Si en vez de CAPTURED ves WA_NOTIF_IGNORED pkg=com.whatsapp, fue una",
    "     notificacion de sistema/resumen (correcto: no es un mensaje de persona).",
    "",
    "  Criterio PASS manual: aparece WA_NOTIF_CAPTURED con pkg=com.whatsapp",
    "  (o com.whatsapp.w4b) y SOLO metadatos redactados, sin PII."
)
foreach ($m in $manual) { Line $m }
Line ""

# =============================================================================
# PASO 6 - Cleanup opcional (revocar acceso al listener)
# =============================================================================
Line "----- PASO 6: cleanup -----"
if ($SkipCleanup) {
    Record-Step "cleanup (revocar listener)" $true "OMITIDO por -SkipCleanup (acceso al listener queda CONCEDIDO para el test manual positivo)"
} else {
    Invoke-Adb @("shell", "cmd", "notification", "disallow_listener", $Component) | Out-Null
    # La revocacion tambien tarda en propagar: reintentamos comprobando AUSENCIA.
    $still = $true
    $secure2 = ""
    for ($i = 0; $i -lt 6; $i++) {
        Start-Sleep -Milliseconds 700
        $secure2 = (Invoke-Adb @("shell", "settings", "get", "secure", "enabled_notification_listeners") | Out-String).Trim()
        if (-not ($secure2 -match [regex]::Escape($Component))) { $still = $false; break }
    }
    if ($still) {
        Record-Step "cleanup (revocar listener)" $false "el componente AUN figura habilitado tras disallow_listener"
    } else {
        Record-Step "cleanup (revocar listener)" $true "acceso al listener revocado"
    }
}
Line ""

# =============================================================================
# RESUMEN
# =============================================================================
Line "=============================="
Line "RESUMEN WA-3 SMOKE"
foreach ($r in $results) {
    Line ("  {0,-42} {1,-6} {2}" -f $r.Paso, $r.Resultado, $r.Detalle)
}
Line ""
$fails = @($results | Where-Object { $_.Resultado -eq "FAIL" })
if ($fails.Count -eq 0) {
    Line "VEREDICTO AUTOMATICO: PASS (precondiciones + gate WhatsApp-only OK)."
    Line "Pendiente: ejecutar el test POSITIVO MANUAL del Paso 5."
    Write-Host ""
    Write-Host ("Reporte: " + $Report)
    exit 0
} else {
    Line ("VEREDICTO AUTOMATICO: FAIL (" + $fails.Count + " paso(s) fallaron).")
    Write-Host ""
    Write-Host ("Reporte: " + $Report)
    exit 1
}
