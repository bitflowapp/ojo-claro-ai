# Pre-Pilot Device Check (FASE 2) - read-only preflight for the human-assisted pilot.
#
# Verifies, BEFORE the pilot, that the phone is ready: device reachable, Estela
# installed, accessibility service enabled + bound, microphone permission granted,
# WhatsApp installed, and a basic ADB/storage sanity check.
#
# STRICTLY READ-ONLY. It never:
#   - installs or uninstalls anything
#   - grants or revokes permissions
#   - enables/disables the accessibility service
#   - writes any setting or file on the device
#   - prints PII (no phone numbers, contacts, chats, screen text)
#   - dumps full device state (only boolean checks via Select-String)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools/prepilot_device_check.ps1
#   ... -Strict    exit code != 0 if any [BLOCK]
#   ... -Quiet     print only the verdict, plus WARN/BLOCK lines and NEXT STEP
#   ... -NoColor   plain text, no ANSI colors
#
# Exit: 0 = ok (or warnings). With -Strict: 2 if any [BLOCK].
# ASCII-only on purpose (PS 5.1 mojibakes accents).

param(
    [switch]$Strict,
    [switch]$Quiet,
    [switch]$NoColor
)

$ErrorActionPreference = 'Stop'

# --- Constants (kept in sync with the app) ---------------------------------
$PkgEstela     = 'com.ojoclaro.android'
$A11yMarker    = 'OjoClaroAccessibilityService'
$PkgWhatsApp   = 'com.whatsapp'
$PkgWhatsAppB  = 'com.whatsapp.w4b'

# --- Result tracking -------------------------------------------------------
$script:Blocks = 0
$script:Warns  = 0
$script:Passes = 0
$script:Serial = $null

function Write-Status {
    param(
        [ValidateSet('PASS', 'WARN', 'BLOCK')] [string]$Level,
        [string]$Message
    )
    if ($Level -eq 'PASS') { $script:Passes++ }
    elseif ($Level -eq 'WARN') { $script:Warns++ }
    else { $script:Blocks++ }

    # In quiet mode, hide PASS lines (keep WARN/BLOCK so problems stay visible).
    if ($Quiet -and $Level -eq 'PASS') { return }

    $line = "[{0}] {1}" -f $Level, $Message
    if ($NoColor) {
        Write-Host $line
        return
    }
    $color = 'Green'
    if ($Level -eq 'WARN') { $color = 'Yellow' }
    elseif ($Level -eq 'BLOCK') { $color = 'Red' }
    Write-Host $line -ForegroundColor $color
}

function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @()
    if ($env:ANDROID_HOME)     { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe') }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools/adb.exe') }
    if ($env:LOCALAPPDATA)     { $candidates += (Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe') }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    return $null
}

# Run "adb -s <serial> <args>" read-only and return combined output as one string.
function Invoke-AdbShell {
    param([string[]]$AdbArgs)
    $all = @()
    if ($script:Serial) { $all += @('-s', $script:Serial) }
    $all += $AdbArgs
    try {
        $out = & $script:Adb @all 2>$null
        return ($out | Out-String)
    } catch {
        return ''
    }
}

# --- Header ----------------------------------------------------------------
Write-Host ''
Write-Host 'PRE-PILOT DEVICE CHECK'
Write-Host '----------------------'

# --- 0. ADB available ------------------------------------------------------
$script:Adb = Resolve-Adb
if (-not $script:Adb) {
    Write-Status BLOCK 'ADB not found. Install Android platform-tools or add adb to PATH.'
    Write-Host ''
    Write-Host 'NEXT STEP:'
    Write-Host '  Connect the phone, enable USB debugging, and install platform-tools, then re-run.'
    if ($Strict) { exit 2 } else { exit 0 }
}

# --- 1. Device connected ---------------------------------------------------
$devicesRaw = ''
try { $devicesRaw = (& $script:Adb devices 2>$null | Out-String) } catch { $devicesRaw = '' }
$serials = @()
$problemStates = @()
foreach ($line in ($devicesRaw -split "`r?`n")) {
    if ($line -match '^(\S+)\s+device$')                  { $serials += $matches[1] }
    elseif ($line -match '^(\S+)\s+(offline|unauthorized)$') { $problemStates += ("{0} ({1})" -f $matches[1], $matches[2]) }
}

if ($serials.Count -eq 0) {
    if ($problemStates.Count -gt 0) {
        Write-Status BLOCK ("Device present but not ready: {0}. Reconnect / accept the USB debugging prompt." -f ($problemStates -join ', '))
    } else {
        Write-Status BLOCK 'No device connected. Plug in the phone and enable USB debugging.'
    }
    Write-Host ''
    Write-Host 'NEXT STEP:'
    Write-Host '  Connect the phone over USB, accept "Allow USB debugging", then re-run this check.'
    if ($Strict) { exit 2 } else { exit 0 }
}

if ($serials.Count -gt 1) {
    Write-Status WARN ("Multiple devices connected; using the first: {0}. Disconnect the others to be sure." -f $serials[0])
}
$script:Serial = $serials[0]
Write-Status PASS ('Device connected ({0})' -f $script:Serial)

# --- 2. Estela installed ---------------------------------------------------
$pkgList = Invoke-AdbShell @('shell', 'pm', 'list', 'packages', $PkgEstela)
$estelaInstalled = ($pkgList -match [regex]::Escape("package:$PkgEstela"))
if ($estelaInstalled) {
    Write-Status PASS 'Estela installed'
} else {
    Write-Status BLOCK 'Estela is NOT installed. Install the agreed pilot build (do not reinstall right before the run).'
}

# --- 3. Accessibility service enabled + bound ------------------------------
$enabledServices = Invoke-AdbShell @('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
$a11yMaster      = (Invoke-AdbShell @('shell', 'settings', 'get', 'secure', 'accessibility_enabled')).Trim()
$serviceListed   = ($enabledServices -match [regex]::Escape($PkgEstela)) -and ($enabledServices -match $A11yMarker)

if ($serviceListed -and $a11yMaster -eq '1') {
    # Confirm it is actually bound/known by the accessibility manager (no full dump).
    $a11yDump = Invoke-AdbShell @('shell', 'dumpsys', 'accessibility')
    if ($a11yDump -match $A11yMarker) {
        Write-Status PASS 'Accessibility service enabled and bound'
    } else {
        Write-Status WARN 'Accessibility enabled but not confirmed bound. Toggle Estela off/on in Accessibility settings.'
    }
} elseif ($serviceListed) {
    Write-Status WARN 'Estela accessibility service listed but master accessibility flag is off. Toggle it on.'
} else {
    Write-Status BLOCK 'Estela accessibility service is OFF. Enable it: Settings > Accessibility > Estela > On.'
}

# --- 4. Microphone permission ----------------------------------------------
$pkgDump = Invoke-AdbShell @('shell', 'dumpsys', 'package', $PkgEstela)
$micLines = ($pkgDump -split "`r?`n") | Where-Object { $_ -match 'RECORD_AUDIO' }
$micGranted = @($micLines | Where-Object { $_ -match 'granted=true' }).Count -gt 0
$micDenied  = @($micLines | Where-Object { $_ -match 'granted=false' }).Count -gt 0
if ($micGranted) {
    Write-Status PASS 'Microphone permission granted'
} elseif ($micDenied) {
    Write-Status BLOCK 'Microphone permission DENIED. Grant it: open Estela and accept the mic prompt, or Settings > Apps > Estela > Permissions.'
} else {
    Write-Status WARN 'Could not confirm microphone permission. Open Estela once and accept the mic prompt.'
}

# --- 5. Estela process (informational) -------------------------------------
$estelaPid = (Invoke-AdbShell @('shell', 'pidof', $PkgEstela)).Trim()
if ($estelaPid) {
    Write-Status PASS 'Estela is running'
} else {
    Write-Status PASS 'Estela not running yet (normal - it starts when you open it)'
}

# --- 6. WhatsApp installed -------------------------------------------------
$waList  = Invoke-AdbShell @('shell', 'pm', 'list', 'packages', $PkgWhatsApp)
$waBList = Invoke-AdbShell @('shell', 'pm', 'list', 'packages', $PkgWhatsAppB)
$waInstalled = ($waList -match [regex]::Escape("package:$PkgWhatsApp")) -or ($waBList -match [regex]::Escape("package:$PkgWhatsAppB"))
if ($waInstalled) {
    Write-Status PASS 'WhatsApp installed'
} else {
    Write-Status WARN 'WhatsApp not detected. WhatsApp flows cannot be tested; read-screen and camera still work.'
}

# --- 7. Storage / ADB sanity ----------------------------------------------
try {
    $df = Invoke-AdbShell @('shell', 'df', '/data')
    $dataLine = ($df -split "`r?`n") | Where-Object { $_ -match '/data' } | Select-Object -First 1
    $avail = $null
    if ($dataLine) {
        $cols = ($dataLine -split '\s+') | Where-Object { $_ -ne '' }
        # Prefer the explicit percent-used column when present.
        $pct = ($cols | Where-Object { $_ -match '^\d+%$' } | Select-Object -First 1)
        if ($pct) { $avail = [int]($pct.TrimEnd('%')) }
    }
    if ($null -ne $avail -and $avail -ge 97) {
        Write-Status WARN ("Storage almost full ({0}% used). Free some space before the pilot." -f $avail)
    } else {
        Write-Status PASS 'Storage and ADB link OK'
    }
} catch {
    Write-Status WARN 'Could not read storage info (non-blocking).'
}

# --- 8. Backend (cannot be verified from the device side) -------------------
Write-Status WARN 'Backend URL not verified by device preflight. Confirm connectivity in-app: tap "Probar conexion".'

# --- Verdict ---------------------------------------------------------------
Write-Host ''
if ($script:Blocks -gt 0) {
    $msg = ("VERDICT: BLOCKED ({0} block, {1} warn). Resolve the [BLOCK] items above before the pilot." -f $script:Blocks, $script:Warns)
    if ($NoColor) { Write-Host $msg } else { Write-Host $msg -ForegroundColor Red }
} elseif ($script:Warns -gt 0) {
    $msg = ("VERDICT: READY WITH WARNINGS ({0} warn). Review the [WARN] items; pilot can proceed." -f $script:Warns)
    if ($NoColor) { Write-Host $msg } else { Write-Host $msg -ForegroundColor Yellow }
} else {
    $msg = 'VERDICT: READY. All checks passed.'
    if ($NoColor) { Write-Host $msg } else { Write-Host $msg -ForegroundColor Green }
}

Write-Host ''
Write-Host 'NEXT STEP:'
Write-Host '  Open Estela, tap "Escuchar", say "ayuda", and wait for a spoken response.'
Write-Host ''

if ($Strict -and $script:Blocks -gt 0) { exit 2 }
exit 0
