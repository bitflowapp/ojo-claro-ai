# Monitor de salud para demo Estela sin cable (local + ngrok).
# Uso: powershell -ExecutionPolicy Bypass -File tools\demo_health_monitor.ps1
# Chequea cada 30 s. NO llama /api/vision (no gasta tokens).

$ngrokUrl = "https://wieldiest-etha-unrippable.ngrok-free.dev/health"
$localUrl = "http://127.0.0.1:8000/health"
$headers = @{ "ngrok-skip-browser-warning" = "true" }

while ($true) {
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

    try {
        $l = Invoke-WebRequest -Uri $localUrl -UseBasicParsing -TimeoutSec 10
        $localStatus = if ($l.StatusCode -eq 200 -and $l.Content -match '"ok":\s*true') { "OK" } else { "FAIL http=$($l.StatusCode)" }
    } catch { $localStatus = "FAIL ($($_.Exception.Message))" }

    try {
        $n = Invoke-WebRequest -Uri $ngrokUrl -Headers $headers -UseBasicParsing -TimeoutSec 15
        $ngrokStatus = if ($n.StatusCode -eq 200 -and $n.Content -match '"ok":\s*true') { "OK" } else { "FAIL http=$($n.StatusCode)" }
    } catch { $ngrokStatus = "FAIL ($($_.Exception.Message))" }

    $line = "$ts  local=$localStatus  ngrok=$ngrokStatus"
    if ($localStatus -ne "OK" -or $ngrokStatus -ne "OK") {
        $line = "$line  <<< ALERTA: revisar ventana de Uvicorn / proceso ngrok"
    }
    Write-Output $line
    Start-Sleep -Seconds 30
}
