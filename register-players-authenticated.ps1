# register-players-authenticated.ps1

$BASE_URL = "http://localhost:8081"
$TOURNAMENT_ID = 1

# Данные для входа (администратор)
$USERNAME = "owner@padel-core.com"
$PASSWORD = "admin123"  # Замените на реальный пароль администратора

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "AUTHENTICATED REGISTRATION FOR TOURNAMENT ID: $TOURNAMENT_ID" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

# 1. Сначала получаем JSESSIONID (логинимся)
Write-Host "`n1. Logging in as admin..." -ForegroundColor Yellow

# Создаем сессию и сохраняем cookies
$loginBody = @{
    username = $USERNAME
    password = $PASSWORD
}

try {
    $loginResponse = Invoke-WebRequest -Uri "$BASE_URL/login" `
                                      -Method POST `
                                      -Body $loginBody `
                                      -SessionVariable session `
                                      -UseBasicParsing

    Write-Host "   ✅ Login successful" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Login failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "   Please check username and password" -ForegroundColor Yellow
    exit
}

# 2. Проверяем статус турнира
Write-Host "`n2. Checking tournament status..." -ForegroundColor Yellow
$statusCheck = docker exec padel-postgres psql -U postgres -d player_padel_db -t -c "SELECT estado FROM tournaments_db WHERE id = $TOURNAMENT_ID;"
$statusCheck = $statusCheck.Trim()
Write-Host "   Current tournament status: $statusCheck" -ForegroundColor Cyan

if ($statusCheck -ne "PUBLICADO" -and $statusCheck -ne "REGISTRO_ABIERTO") {
    Write-Host "   ❌ Tournament is not open for registration. Status: $statusCheck" -ForegroundColor Red
    Write-Host "   Do you want to change it to REGISTRO_ABIERTO? (Y/N)" -ForegroundColor Yellow

    $response = Read-Host
    if ($response -eq "Y" -or $response -eq "y") {
        docker exec padel-postgres psql -U postgres -d player_padel_db -c "UPDATE tournaments_db SET estado = 'REGISTRO_ABIERTO' WHERE id = $TOURNAMENT_ID;"
        Write-Host "   ✅ Status changed to REGISTRO_ABIERTO" -ForegroundColor Green
    } else {
        Write-Host "   Exiting..." -ForegroundColor Red
        exit
    }
}

# 3. Регистрируем игроков
Write-Host "`n3. Registering players..." -ForegroundColor Yellow
$successCount = 0
$errorCount = 0

for ($i = 1; $i -le 20; $i++) {
    Write-Host "   Registering player $i..." -NoNewline

    try {
        $response = Invoke-WebRequest -Uri "$BASE_URL/api/tournaments/$TOURNAMENT_ID/register?playerId=$i" `
                                      -Method POST `
                                      -WebSession $session `
                                      -UseBasicParsing

        if ($response.StatusCode -eq 200) {
            Write-Host " [OK]" -ForegroundColor Green
            $successCount++
        } else {
            Write-Host " [ERROR] (status: $($response.StatusCode))" -ForegroundColor Red
            $errorCount++
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 409) {
            Write-Host " [ALREADY REGISTERED]" -ForegroundColor Yellow
            $successCount++
        } else {
            Write-Host " [ERROR] $($_.Exception.Message)" -ForegroundColor Red
            $errorCount++
        }
    }

    Start-Sleep -Milliseconds 300
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "RESULTS:" -ForegroundColor Yellow
Write-Host "Successfully registered: $successCount" -ForegroundColor Green
Write-Host "Errors: $errorCount" -ForegroundColor Red
Write-Host "========================================" -ForegroundColor Cyan

# 4. Проверяем итоговое количество регистраций
Write-Host "`n4. Checking final registration count..." -ForegroundColor Yellow
$regCount = docker exec padel-postgres psql -U postgres -d player_padel_db -t -c "SELECT COUNT(*) FROM tournament_registrations_db WHERE tournament_id = $TOURNAMENT_ID;"
$regCount = $regCount.Trim()
Write-Host "   Total registrations in database: $regCount" -ForegroundColor Cyan