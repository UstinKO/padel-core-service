# register-players.ps1

$BASE_URL = "http://localhost:8081/api/tournaments"
$TOURNAMENT_ID = 1

Write-Host "Регистрация игроков на турнир ID: $TOURNAMENT_ID" -ForegroundColor Green
Write-Host "========================================"

for ($i = 1; $i -le 20; $i++) {
    Write-Host "Регистрация игрока $i..." -NoNewline
    try {
        $response = Invoke-WebRequest -Uri "$BASE_URL/$TOURNAMENT_ID/register?playerId=$i" `
                                      -Method POST `
                                      -UseBasicParsing
        Write-Host " -> Статус: $($response.StatusCode)" -ForegroundColor Green
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host " -> Статус: $statusCode" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 500
}

Write-Host "========================================"
Write-Host "Регистрация завершена!" -ForegroundColor Green