# Privacy Logging Guard (FASE 0) — barrera practica anti-regresion.
# Falla si algun log de PRODUCCION (androidApp/src/main) interpola texto crudo
# sensible (STT/pantalla/OCR/WhatsApp/contactos/telefono) sin acotarlo a
# metadatos (.length/.size/len=/count/redact/shortHash/presencia).
#
# Uso:  powershell -File tools/check_privacy_logs.ps1
# Exit: 0 = limpio, 1 = encontro logs peligrosos.
# ASCII-only a proposito (PS 5.1 mojibakea acentos).

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$src = Join-Path $root 'androidApp/src/main'

$tokens = 'userText|normalizedText|rawText|rawRecognizedText|recognizedText|transcript|ocrText|visibleText|screenText|messageText|messageBody|contactName|chatName|phoneNumber'
$logcall = 'Log\.[diwev]\(|println\(|logBackground\(|logChatNav\(|logAgentCore\(|Timber\.[diwev]\('
$safe = '\.length|\.size|len=|Len=|count|Count|redact|shortHash|Present|!= null|== null|isBlank|isEmpty'
$rawToken = '\$\{?(' + $tokens + ')'

$offenders = New-Object System.Collections.Generic.List[string]
Get-ChildItem -Path $src -Recurse -Filter *.kt |
    Where-Object { $_.FullName -notmatch '\\build\\' } |
    ForEach-Object {
        $file = $_.FullName
        $i = 0
        foreach ($line in [System.IO.File]::ReadAllLines($file)) {
            $i++
            if (($line -match $logcall) -and ($line -match $rawToken) -and (-not ($line -match $safe))) {
                $rel = $file.Substring($root.Length).TrimStart('\','/')
                $offenders.Add(("{0}:{1}" -f $rel, $i))
            }
        }
    }

if ($offenders.Count -gt 0) {
    Write-Host "PRIVACY LOG GUARD: FAIL - raw sensitive text in production logs:"
    $offenders | ForEach-Object { Write-Host $_ }
    exit 1
}

Write-Host "PRIVACY LOG GUARD: PASS - no raw sensitive text in production logs."
