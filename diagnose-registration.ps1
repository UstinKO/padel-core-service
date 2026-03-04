# diagnose-registration.ps1

$BASE_URL = "http://localhost:8081"
$TOURNAMENT_ID = 1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "DIAGNOSTIC - TOURNAMENT REGISTRATION" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

# 1. Проверим доступность API
Write-Host "`n1. Checking API availability..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$BASE_URL/api/tournaments/$TOURNAMENT_ID" -Method GET -UseBasicParsing
    Write-Host "   ✅ API is accessible" -ForegroundColor Green
} catch {
    Write-Host "   ❌ API is not accessible: $($_.Exception.Message)" -ForegroundColor Red
}

# 2. Проверим статус турнира через SQL
Write-Host "`n2. Checking tournament status via SQL..." -ForegroundColor Yellow
$status = docker exec padel-postgres psql -U postgres -d player_padel_db -t -c "SELECT estado FROM tournaments_db WHERE id = $TOURNAMENT_ID;"
$status = $status.Trim()
Write-Host "   Tournament status: $status" -ForegroundColor Cyan

# 3. Проверим, есть ли уже регистрации
Write-Host "`n3. Checking existing registrations via SQL..." -ForegroundColor Yellow
$regCount = docker exec padel-postgres psql -U postgres -d player_padel_db -t -c "SELECT COUNT(*) FROM tournament_registrations_db WHERE tournament_id = $TOURNAMENT_ID;"
$regCount = $regCount.Trim()
Write-Host "   Existing registrations: $regCount" -ForegroundColor Cyan

# 4. Попробуем зарегистрировать одного игрока с детальным выводом
Write-Host "`n4. Testing registration for player 1..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "$BASE_URL/api/tournaments/$TOURNAMENT_ID/register?playerId=1" `
                                  -Method POST `
                                  -UseBasicParsing

    Write-Host "   Status Code: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response: $($response.Content)" -ForegroundColor Cyan

} catch {
    Write-Host "   ❌ Error occurred:" -ForegroundColor Red
    Write-Host "   Status Code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red

    # Пытаемся прочитать тело ответа
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.BaseStream.Position = 0
        $reader.DiscardBufferedData()
        $responseBody = $reader.ReadToEnd()
        Write-Host "   Response Body: $responseBody" -ForegroundColor Yellow
    } catch {
        Write-Host "   Could not read response body" -ForegroundColor Gray
    }
}

# 5. Проверим, есть ли нужный endpoint
Write-Host "`n5. Checking available endpoints..." -ForegroundColor Yellow
$endpoints = @(
    "/api/tournaments/$TOURNAMENT_ID/register",
    "/api/players/register",
    "/api/registrations",
    "/api/tournaments/$TOURNAMENT_ID/registrations"
)

foreach ($endpoint in $endpoints) {
    try {
        $response = Invoke-WebRequest -Uri "$BASE_URL$endpoint" -Method GET -UseBasicParsing
        Write-Host "   ✅ $endpoint - OK" -ForegroundColor Green
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 405) {
            Write-Host "   ⚠ $endpoint - Method Not Allowed (POST required)" -ForegroundColor Yellow
        } elseif ($_.Exception.Response.StatusCode.value__ -eq 404) {
            Write-Host "   ❌ $endpoint - Not Found" -ForegroundColor Red
        } else {
            Write-Host "   ❌ $endpoint - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "DIAGNOSTIC COMPLETE" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan