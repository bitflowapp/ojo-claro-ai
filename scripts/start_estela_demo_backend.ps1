<#
.SYNOPSIS
    Levanta el backend de vision de Estela para la demo (puerto 8080).

.DESCRIPTION
    Solo infraestructura de demo. NO instala dependencias, NO borra nada, NO compila Android.
    Fuerza OPENAI_MODEL=gpt-4.1-mini porque en la PC hay un OPENAI_MODEL global
    (openai-codex/...) que pisa el backend\.env y rompe la vision si no se fija a mano.

    Corre este script en una TERMINAL DEDICADA: uvicorn queda en primer plano.
    En OTRA terminal levanta ngrok con scripts\start_estela_demo_ngrok.ps1.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\start_estela_demo_backend.ps1
#>

[CmdletBinding()]
param(
    [int]$Port = 8080,
    [string]$VisionModel = "gpt-4.1-mini"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Estela demo backend ===" -ForegroundColor Cyan

$backendDir = Join-Path $PSScriptRoot "..\backend"
if (-not (Test-Path $backendDir)) {
    Write-Host "[FAIL] No existe el directorio backend: $backendDir" -ForegroundColor Red
    exit 1
}
$backendDir = (Resolve-Path $backendDir).Path
$python = Join-Path $backendDir ".venv\Scripts\python.exe"

if (-not (Test-Path $python)) {
    Write-Host "[FAIL] No existe el entorno virtual: $python" -ForegroundColor Red
    Write-Host "       NO instalo dependencias automaticamente (C: puede estar critico)."
    Write-Host "       Hay que crear backend\.venv antes (lo hizo quien preparo el equipo)."
    exit 1
}
if (-not (Test-Path (Join-Path $backendDir "app\main.py"))) {
    Write-Host "[FAIL] No encuentro app\main.py en el backend." -ForegroundColor Red
    exit 1
}
if (-not (Test-Path (Join-Path $backendDir ".env"))) {
    Write-Host "[WARN] No hay backend\.env: la vision puede fallar sin API key." -ForegroundColor Yellow
}

Set-Location $backendDir
$env:OPENAI_MODEL = $VisionModel

Write-Host ""
Write-Host ("OPENAI_MODEL forzado a: {0}  (pisa el global codex)" -f $env:OPENAI_MODEL) -ForegroundColor Green
Write-Host ("Levantando uvicorn en http://127.0.0.1:{0} ..." -f $Port) -ForegroundColor Green
Write-Host "DEJA ESTA VENTANA ABIERTA durante toda la demo (Ctrl+C para frenar)."
Write-Host "En OTRA terminal: powershell -ExecutionPolicy Bypass -File scripts\start_estela_demo_ngrok.ps1"
Write-Host "Verificar:        powershell -ExecutionPolicy Bypass -File scripts\check_estela_demo_infra.ps1"
Write-Host ""

& $python -m uvicorn app.main:app --host 127.0.0.1 --port $Port
