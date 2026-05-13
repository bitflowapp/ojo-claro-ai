param(
    [string]$ProxyBaseUrl = "http://127.0.0.1:8787"
)

$ErrorActionPreference = "Continue"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$Results = New-Object System.Collections.Generic.List[object]

function Add-SmokeResult {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )

    $Results.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    Write-Host ("[{0}] {1} - {2}" -f $Status, $Name, $Detail)
}

function Invoke-SmokeStep {
    param(
        [string]$Name,
        [scriptblock]$Step
    )

    try {
        & $Step
        Add-SmokeResult -Name $Name -Status "PASS" -Detail "ok"
    } catch {
        Add-SmokeResult -Name $Name -Status "FAIL" -Detail $_.Exception.Message
    }
}

function Invoke-NativeChecked {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE"
    }
}

Push-Location $RepoRoot
try {
    Invoke-SmokeStep "git status --short" {
        Invoke-NativeChecked -FilePath "git" -Arguments @("status", "--short")
    }

    $Gradle = Join-Path $RepoRoot "gradlew.bat"
    Invoke-SmokeStep "assembleDebug" {
        Invoke-NativeChecked -FilePath $Gradle -Arguments @("assembleDebug", "--console=plain")
    }
    Invoke-SmokeStep "testDebugUnitTest" {
        Invoke-NativeChecked -FilePath $Gradle -Arguments @("testDebugUnitTest", "--console=plain")
    }

    Invoke-SmokeStep "proxy npm test" {
        Push-Location (Join-Path $RepoRoot "tools\ojo_claro_ai_proxy")
        try {
            Invoke-NativeChecked -FilePath "npm" -Arguments @("test")
        } finally {
            Pop-Location
        }
    }

    try {
        $Health = Invoke-RestMethod -Uri "$ProxyBaseUrl/health" -TimeoutSec 2
        Add-SmokeResult `
            -Name "proxy /health" `
            -Status "PASS" `
            -Detail ("ok={0} model={1} hasApiKey={2}" -f $Health.ok, $Health.model, [bool]$Health.hasApiKey)
    } catch {
        Add-SmokeResult -Name "proxy /health" -Status "PARTIAL" -Detail "proxy not running or unreachable"
    }

    try {
        $Metrics = Invoke-RestMethod -Uri "$ProxyBaseUrl/metrics" -TimeoutSec 2
        Add-SmokeResult `
            -Name "proxy /metrics" `
            -Status "PASS" `
            -Detail ("requests={0} lastIntent={1} hasApiKey={2}" -f $Metrics.totalInterpretRequests, $Metrics.lastIntent, [bool]$Metrics.hasApiKey)
    } catch {
        Add-SmokeResult -Name "proxy /metrics" -Status "PARTIAL" -Detail "proxy not running or unreachable"
    }

    Invoke-SmokeStep "diff secret scan" {
        $Diff = & git diff -- . ":(exclude).env" ":(exclude)tools/ojo_claro_ai_proxy/.env" 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "git diff failed"
        }

        $SecretRegex = "sk-[A-Za-z0-9_-]{20,}"
        $KeyAssignRegex = ("OPENAI" + "_API" + "_KEY\s*=\s*sk-[A-Za-z0-9_-]{6,}")
        $AllowedFixtureRegex = "DO-NOT-LEAK|VERY-SECRET|XYZSECRET|ABC123|bearer123secret|sk-\.\.\."
        $Hits = @($Diff -split "`r?`n" | Where-Object {
            (($_ -match $SecretRegex) -or ($_ -match $KeyAssignRegex)) -and
                ($_ -notmatch $AllowedFixtureRegex)
        })
        if ($Hits.Count -gt 0) {
            throw ("possible secret-like token in diff ({0} line(s))" -f $Hits.Count)
        }
    }
} finally {
    Pop-Location
}

$Failures = @($Results | Where-Object { $_.Status -eq "FAIL" })
$Partials = @($Results | Where-Object { $_.Status -eq "PARTIAL" })
$Overall = if ($Failures.Count -gt 0) {
    "FAIL"
} elseif ($Partials.Count -gt 0) {
    "PARTIAL"
} else {
    "PASS"
}

Write-Host ""
Write-Host ("ROBOT_LOCAL_SMOKE={0}" -f $Overall)

if ($Failures.Count -gt 0) {
    exit 1
}
exit 0
