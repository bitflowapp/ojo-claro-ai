param(
    [switch]$BuildRelease
)

$ErrorActionPreference = "Stop"

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host "[gate] $Name"
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "Gate failed: $Name"
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $repoRoot
try {
    Run-Step "Proxy tests" {
        Push-Location "tools/ojo_claro_ai_proxy"
        try {
            node --test
        } finally {
            Pop-Location
        }
    }

    Run-Step "Android debug unit tests" {
        .\gradlew.bat :androidApp:testDebugUnitTest --console=plain
    }

    Run-Step "Android debug assemble" {
        .\gradlew.bat :androidApp:assembleDebug --console=plain
    }

    Run-Step "Shared tests" {
        .\gradlew.bat :shared:allTests --console=plain
    }

    Write-Host "[gate] Secret scan in Android sources"
    & rg -n "OPENAI_API_KEY|sk-" androidApp\src\main androidApp\src\test androidApp\build.gradle.kts
    if ($LASTEXITCODE -eq 0) {
        throw "Gate failed: Android sources contain a secret marker."
    } elseif ($LASTEXITCODE -ne 1) {
        throw "Gate failed: secret scan could not complete."
    }

    Write-Host "[gate] Forbidden Android permissions/actions scan"
    & rg -n "READ_CONTACTS|CALL_PHONE|ACTION_CALL|ACCESS_BACKGROUND_LOCATION" androidApp\src\main\AndroidManifest.xml
    if ($LASTEXITCODE -eq 0) {
        throw "Gate failed: manifest contains a forbidden permission/action."
    } elseif ($LASTEXITCODE -ne 1) {
        throw "Gate failed: manifest scan could not complete."
    }

    Write-Host "[gate] Mojibake scan"
    $mojibakePattern = ([char]0x00C3).ToString() + "|" +
        ([char]0x00C2).ToString() + "|" +
        ([char]0x00E2).ToString() + "|" +
        ([char]0xFFFD).ToString()
    & rg -n $mojibakePattern androidApp\src\main androidApp\src\test shared\src tools\ojo_claro_ai_proxy docs scripts `
        -g "*.kt" -g "*.kts" -g "*.md" -g "*.mjs" -g "*.js" -g "*.ps1" -g "*.html" -g "*.css" -g "*.xml" -g "*.json"
    if ($LASTEXITCODE -eq 0) {
        throw "Gate failed: possible mojibake markers found."
    } elseif ($LASTEXITCODE -ne 1) {
        throw "Gate failed: mojibake scan could not complete."
    }

    Write-Host "[gate] Git safety scan"
    $status = git status --short
    $forbiddenStatus = $status | Where-Object {
        $_ -match "keystore\.properties|ojo_claro_release\.keystore|\.env|dist/|dist\\|\.apk|\.jks|\.keystore"
    }
    if ($forbiddenStatus) {
        $forbiddenStatus | ForEach-Object { Write-Host $_ }
        throw "Gate failed: git status shows forbidden local artifacts."
    }

    if ($BuildRelease) {
        if (!(Test-Path ".\keystore.properties")) {
            throw "Release build requested, but keystore.properties is missing."
        }
        Run-Step "Signed release APK" {
            .\scripts\build_signed_apk.ps1
        }
    } elseif (Test-Path ".\keystore.properties") {
        Write-Host "[gate] keystore.properties exists. Run with -BuildRelease to also build the signed APK."
    }

    Write-Host "[gate] Alpha release gate passed."
} finally {
    Pop-Location
}
