<#
.SYNOPSIS
    Levanta el tunel ngrok para la demo de Estela (dominio reservado).

.DESCRIPTION
    Solo infraestructura de demo. NO cambia la configuracion de ngrok, NO borra nada.
    Reenvia el puerto local 8080 al dominio reservado que ya usa la APK instalada.
    Corre primero scripts\start_estela_demo_backend.ps1 en otra terminal.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\start_estela_demo_ngrok.ps1
#>

[CmdletBinding()]
param(
    [int]$Port = 8080,
    [string]$Domain = "wieldiest-etha-unrippable.ngrok-free.dev"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Estela demo ngrok ===" -ForegroundColor Cyan

$ngrok = (Get-Command ngrok -ErrorAction SilentlyContinue).Source
if (-not $ngrok) {
    Write-Host "[FAIL] No encuentro 'ngrok' en el PATH." -ForegroundColor Red
    exit 1
}

Write-Host "ngrok: $ngrok"
Write-Host ("URL esperada: https://{0}  ->  http://localhost:{1}" -f $Domain, $Port) -ForegroundColor Green
Write-Host "DEJA ESTA VENTANA ABIERTA durante toda la demo (Ctrl+C para frenar)."
Write-Host "Antes, el backend tiene que estar corriendo (start_estela_demo_backend.ps1)."
Write-Host ("Verificar: curl https://{0}/health  ->  debe dar 200" -f $Domain)
Write-Host ""

& ngrok http $Port --domain=$Domain
