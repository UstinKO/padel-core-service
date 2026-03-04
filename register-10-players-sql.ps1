# register-10-players-sql.ps1

$TOURNAMENT_ID = 8
$PLAYER_COUNT = 20

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "DIRECT SQL REGISTRATION (10 PLAYERS)" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

$sql = @"
-- Проверяем текущее количество регистраций
SELECT COUNT(*) as current_count
FROM tournament_registrations_db
WHERE tournament_id = $TOURNAMENT_ID;

-- Регистрируем игроков 1-$PLAYER_COUNT
INSERT INTO tournament_registrations_db
    (tournament_id, player_id, registration_date, status, position, is_active)
SELECT
    $TOURNAMENT_ID,
    gs,
    NOW(),
    'CONFIRMED',
    gs,
    true
FROM generate_series(1, $PLAYER_COUNT) AS gs
ON CONFLICT (tournament_id, player_id)
DO UPDATE SET
    status = 'CONFIRMED',
    position = EXCLUDED.position,
    waitlist_position = NULL,
    is_active = true,
    registration_date = NOW();

-- Проверяем результат
SELECT
    status,
    COUNT(*) as count,
    MIN(position) as min_position,
    MAX(position) as max_position
FROM tournament_registrations_db
WHERE tournament_id = $TOURNAMENT_ID
GROUP BY status;
"@

Write-Host "`nExecuting SQL..." -ForegroundColor Yellow
docker exec -i padel-postgres psql -U postgres -d player_padel_db -c "$sql"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "DONE!" -ForegroundColor Green