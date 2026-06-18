<#
Non-invasive conversation smoke.

Posts three fixed phrases to local and ngrok /api/v1/conversation.
Prints status and whether reply is non-empty. Does not print prompts,
API keys, or full model responses.
#>

[CmdletBinding()]
param(
    [string]$LocalBaseUrl = "http://127.0.0.1:8000",
    [string]$NgrokBaseUrl = "https://wieldiest-etha-unrippable.ngrok-free.dev",
    [int]$TimeoutSec = 45
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

function Invoke-ConversationSmoke {
    param(
        [Parameter(Mandatory = $true)][string]$Target,
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [Parameter(Mandatory = $true)][string]$Phrase,
        [string[]]$ShortMemory = @(),
        [hashtable]$Headers = @{}
    )

    $uri = "$($BaseUrl.TrimEnd('/'))/api/v1/conversation"
    $payload = @{
        user_text = $Phrase
        conversation_state = @{
            active_app = "UNKNOWN"
            route_active = $false
            whatsapp_pending = $false
            short_memory = $ShortMemory
        }
    } | ConvertTo-Json -Depth 8 -Compress

    try {
        $response = Invoke-WebRequest `
            -Uri $uri `
            -Method POST `
            -Headers $Headers `
            -ContentType "application/json" `
            -Body $payload `
            -UseBasicParsing `
            -TimeoutSec $TimeoutSec

        $json = $null
        try { $json = $response.Content | ConvertFrom-Json } catch { $json = $null }

        $reply = ""
        $safety = ""
        $detail = ""
        if ($json -and ($json.PSObject.Properties.Name -contains "reply")) {
            $reply = [string]$json.reply
        }
        if ($json -and ($json.PSObject.Properties.Name -contains "safety_level")) {
            $safety = [string]$json.safety_level
        }
        if ($json -and ($json.PSObject.Properties.Name -contains "detail")) {
            $detail = [string]$json.detail
        }

        [pscustomobject]@{
            Target = $Target
            Phrase = $Phrase
            Status = [int]$response.StatusCode
            ReplyPresent = -not [string]::IsNullOrWhiteSpace($reply)
            ReplyLength = $reply.Trim().Length
            Safety = $safety
            DetailPresent = -not [string]::IsNullOrWhiteSpace($detail)
            Error = ""
        }
    } catch {
        $status = $null
        $detail = ""
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = [System.IO.StreamReader]::new($stream)
                    $body = $reader.ReadToEnd()
                    $json = $body | ConvertFrom-Json
                    if ($json.PSObject.Properties.Name -contains "detail") {
                        $detail = [string]$json.detail
                    }
                }
            } catch {
                $detail = ""
            }
        }

        [pscustomobject]@{
            Target = $Target
            Phrase = $Phrase
            Status = $status
            ReplyPresent = $false
            ReplyLength = 0
            Safety = ""
            DetailPresent = -not [string]::IsNullOrWhiteSpace($detail)
            Error = $_.Exception.Message
        }
    }
}

$cases = @(
    @{ Phrase = "Estoy nervioso"; Memory = @() },
    @{ Phrase = "Hablame"; Memory = @("Usuario: Estoy nervioso") },
    @{ Phrase = "Es seguro cruzar?"; Memory = @() }
)

$results = @()
foreach ($case in $cases) {
    $results += Invoke-ConversationSmoke -Target "local" -BaseUrl $LocalBaseUrl -Phrase $case.Phrase -ShortMemory $case.Memory
    $results += Invoke-ConversationSmoke -Target "ngrok" -BaseUrl $NgrokBaseUrl -Phrase $case.Phrase -ShortMemory $case.Memory -Headers @{ "ngrok-skip-browser-warning" = "1" }
}

$results | Format-Table Target, Phrase, Status, ReplyPresent, ReplyLength, Safety, DetailPresent -AutoSize

$failed = @($results | Where-Object {
    $_.Status -lt 200 -or $_.Status -ge 300 -or -not $_.ReplyPresent
})
if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "Conversation smoke failed:"
    $failed | Format-List Target, Phrase, Status, ReplyPresent, ReplyLength, Safety, DetailPresent, Error
    exit 1
}

Write-Host "Conversation smoke OK."
