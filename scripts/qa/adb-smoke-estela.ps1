<#
Non-invasive ADB smoke for Estela.

Uses the debug accessibility receiver `com.ojoclaro.DEBUG_VOICE_TEXT`
to inject recognized text into the real routing path.

Safety:
- Does not use force-stop.
- Does not use adb reverse.
- Does not say "envia".
- Does not send a real WhatsApp message.
#>

[CmdletBinding()]
param(
    [string]$Adb = "adb",
    [int]$DelaySec = 8,
    [switch]$ShowLogs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$action = "com.ojoclaro.DEBUG_VOICE_TEXT"
$extraName = "goal"
$phrases = @(
    "Estoy nervioso",
    "Que probamos",
    "Donde estoy",
    "Es seguro cruzar",
    "Mandale a Marco Luna que prueba estela qa",
    "Cancelar"
)

function Escape-SingleQuotedShellArg {
    param([Parameter(Mandatory = $true)][string]$Value)
    return $Value.Replace("'", "'\''")
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string]$Command)
    & $Adb shell $Command
}

$devices = & $Adb devices
$connectedDevices = @($devices | Select-String -Pattern "`tdevice")
if ($connectedDevices.Count -lt 1) {
    throw "No adb device is connected."
}

Write-Host "Injecting Estela QA phrases through $action"
Write-Host "This script does not send WhatsApp. The final phrase is Cancelar."

foreach ($phrase in $phrases) {
    $escaped = Escape-SingleQuotedShellArg -Value $phrase
    Write-Host "Injecting: $phrase"
    Invoke-Adb -Command "am broadcast -a $action --es $extraName '$escaped'"
    Start-Sleep -Seconds $DelaySec
}

Write-Host "ADB smoke injection finished."

if ($ShowLogs) {
    Write-Host ""
    Write-Host "Recent Estela logs:"
    & $Adb logcat -d -v time |
        Select-String -Pattern "EstelaBackground|EstelaAgentCore|EstelaOutdoor|EstelaWhatsAppNav|EstelaAccessibility|EstelaScreenDiagnostic|AndroidRuntime|FATAL EXCEPTION|whatsappSend|smartCompose"
}
