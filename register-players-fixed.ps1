# register-players-simple.ps1

$BASE_URL = "http://localhost:8081/api/tournaments"
$TOURNAMENT_ID = 1

Write-Host "========================================"
Write-Host "REGISTRATION FOR TOURNAMENT ID: $TOURNAMENT_ID"
Write-Host "========================================"

$successCount = 0
$errorCount = 0

for ($i = 1; $i -le 20; $i++) {
    Write-Host "Registering player $i..." -NoNewline

    try {
        $response = Invoke-WebRequest -Uri "$BASE_URL/$TOURNAMENT_ID/register?playerId=$i" `
                                      -Method POST `
                                      -UseBasicParsing

        if ($response.StatusCode -eq 200) {
            Write-Host " [OK] (status: $($response.StatusCode))" -ForegroundColor Green
            $successCount++
        } else {
            Write-Host " [ERROR] (status: $($response.StatusCode))" -ForegroundColor Red
            $errorCount++
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 409) {
            Write-Host " [ALREADY REGISTERED] (status: 409)" -ForegroundColor Yellow
            $successCount++
        } elseif ($statusCode -eq 404) {
            Write-Host " [NOT FOUND] (status: 404)" -ForegroundColor Red
            $errorCount++
        } else {
            Write-Host " [ERROR] $($_.Exception.Message)" -ForegroundColor Red
            $errorCount++
        }
    }

    Start-Sleep -Milliseconds 300
}

Write-Host "========================================"
Write-Host "RESULTS:"
Write-Host "Successfully registered: $successCount" -ForegroundColor Green
Write-Host "Errors: $errorCount" -ForegroundColor Red
Write-Host "========================================"

Write-Host "`nChecking database state:" -ForegroundColor Yellow
try {
    $checkResponse = Invoke-WebRequest -Uri "http://localhost:8081/api/tournaments/$TOURNAMENT_ID/registrations" `
                                      -Method GET `
                                      -UseBasicParsing
    Write-Host "API available" -ForegroundColor Green
} catch {
    Write-Host "API not available" -ForegroundColor Yellow
}