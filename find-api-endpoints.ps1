# find-api-endpoints.ps1

$BASE_URL = "http://localhost:8081"
$TOURNAMENT_ID = 1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "FINDING API ENDPOINTS" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

# Список возможных endpoint'ов для регистрации
$endpoints = @(
    "/api/tournaments/$TOURNAMENT_ID/register",
    "/api/tournaments/register",
    "/api/registrations/tournament/$TOURNAMENT_ID",
    "/api/registrations",
    "/api/players/register/tournament/$TOURNAMENT_ID",
    "/api/tournament-registrations",
    "/api/tournament/$TOURNAMENT_ID/register",
    "/api/tournaments/$TOURNAMENT_ID/registrations"
)

Write-Host "`nTesting POST endpoints..." -ForegroundColor Yellow
foreach ($endpoint in $endpoints) {
    Write-Host "`nTesting: $endpoint" -ForegroundColor Cyan
    try {
        $response = Invoke-WebRequest -Uri "$BASE_URL$endpoint?playerId=1" `
                                      -Method POST `
                                      -UseBasicParsing
        Write-Host "  SUCCESS! Status: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "  Response: $($response.Content)" -ForegroundColor Green
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 404) {
            Write-Host "  404 Not Found" -ForegroundColor Red
        } elseif ($statusCode -eq 405) {
            Write-Host "  405 Method Not Allowed (endpoint exists but wrong method)" -ForegroundColor Yellow
        } elseif ($statusCode -eq 401 -or $statusCode -eq 403) {
            Write-Host "  $statusCode Authentication required" -ForegroundColor Magenta
        } else {
            Write-Host "  Error $statusCode : $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}