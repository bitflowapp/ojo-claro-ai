<#
.SYNOPSIS
Runs the non-invasive Estela V1.10 release smoke.

.DESCRIPTION
Default behavior is safe and free of model calls:
1. Runs backend /health checks.
2. Skips conversation model checks unless -RunPaidModelChecks is passed.
3. Runs ADB smoke only when a device is connected and -SkipAdb is not passed.

Safety:
- Does not print secrets.
- Does not use force-stop.
- Does not use adb reverse.
- Does not send a real WhatsApp message.
- Does not loop.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts\qa\release-v110-smoke.ps1

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts\qa\release-v110-smoke.ps1 -RunPaidModelChecks -ShowAdbLogs
#>

[CmdletBinding()]
param(
    [string]$LocalBaseUrl = "http://127.0.0.1:8000",
    [string]$NgrokBaseUrl = "https://wieldiest-etha-unrippable.ngrok-free.dev",
    [string]$Adb = "adb",
    [int]$TimeoutSec = 10,
    [int]$AdbDelaySec = 8,
    [switch]$RunPaidModelChecks,
    [switch]$SkipAdb,
    [switch]$ShowAdbLogs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$checkBackend = Join-Path $scriptDir "check-backend.ps1"
$checkConversation = Join-Path $scriptDir "check-conversation.ps1"
$adbSmoke = Join-Path $scriptDir "adb-smoke-estela.ps1"

foreach ($script in @($checkBackend, $checkConversation, $adbSmoke)) {
    if (-not (Test-Path $script)) {
        throw "Required smoke script not found: $script"
    }
}

Write-Host "== Estela V1.10 release smoke =="
Write-Host "Local backend: $LocalBaseUrl"
Write-Host "Ngrok backend: $NgrokBaseUrl"
Write-Host ""

Write-Host "== 1. Backend health =="
& $checkBackend `
    -LocalBaseUrl $LocalBaseUrl `
    -NgrokBaseUrl $NgrokBaseUrl `
    -TimeoutSec $TimeoutSec

Write-Host ""
if ($RunPaidModelChecks) {
    Write-Host "== 2. Conversation smoke =="
    Write-Host "RunPaidModelChecks enabled: this can call the configured model once per test case."
    & $checkConversation `
        -LocalBaseUrl $LocalBaseUrl `
        -NgrokBaseUrl $NgrokBaseUrl `
        -TimeoutSec 45
} else {
    Write-Host "== 2. Conversation smoke skipped =="
    Write-Host "Pass -RunPaidModelChecks to run conversation checks that may call the configured model."
}

Write-Host ""
if ($SkipAdb) {
    Write-Host "== 3. ADB smoke skipped =="
    Write-Host "SkipAdb enabled."
} else {
    Write-Host "== 3. ADB smoke =="
    $devices = & $Adb devices
    $connectedDevices = @($devices | Select-String -Pattern "`tdevice")
    if ($connectedDevices.Count -lt 1) {
        Write-Host "No adb device connected. Skipping ADB smoke."
    } else {
        & $adbSmoke `
            -Adb $Adb `
            -DelaySec $AdbDelaySec `
            -ShowLogs:$ShowAdbLogs
    }
}

Write-Host ""
Write-Host "Release smoke finished."
