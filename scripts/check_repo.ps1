Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Write-Host "== Backend tests =="
Set-Location "$PSScriptRoot\..\backend"
if (-not (Test-Path ".venv")) {
    python -m venv .venv
}
. ".\.venv\Scripts\Activate.ps1"
pip install -r requirements.txt
pytest

Write-Host "== Gradle checks =="
Set-Location "$PSScriptRoot\.."
gradle :shared:testDebugUnitTest
