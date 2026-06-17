<#
Non-invasive backend smoke.

Checks local and ngrok /health endpoints. Does not read or print secrets.
#>

[CmdletBinding()]
param(
    [string]$LocalBaseUrl = "http://127.0.0.1:8000",
    [string]$NgrokBaseUrl = "https://wieldiest-etha-unrippable.ngrok-free.dev",
    [int]$TimeoutSec = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

function Test-HealthEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [hashtable]$Headers = @{}
    )

    $uri = "$($BaseUrl.TrimEnd('/'))/health"
    try {
        $response = Invoke-WebRequest `
            -Uri $uri `
            -Headers $Headers `
            -UseBasicParsing `
            -TimeoutSec $TimeoutSec

        [pscustomobject]@{
            Target = $Name
            Url = $uri
            Status = [int]$response.StatusCode
            Ok = ([int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 300)
            Body = $response.Content
            Error = ""
        }
    } catch {
        $status = $null
        $body = ""
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = [System.IO.StreamReader]::new($stream)
                    $body = $reader.ReadToEnd()
                }
            } catch {
                $body = ""
            }
        }
        [pscustomobject]@{
            Target = $Name
            Url = $uri
            Status = $status
            Ok = $false
            Body = $body
            Error = $_.Exception.Message
        }
    }
}

$results = @(
    Test-HealthEndpoint -Name "local" -BaseUrl $LocalBaseUrl
    Test-HealthEndpoint -Name "ngrok" -BaseUrl $NgrokBaseUrl -Headers @{ "ngrok-skip-browser-warning" = "1" }
)

$results | Format-Table Target, Status, Ok, Url -AutoSize

$failed = @($results | Where-Object { -not $_.Ok })
if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "Backend smoke failed:"
    $failed | Format-List Target, Status, Url, Error, Body
    exit 1
}

Write-Host "Backend smoke OK."
