# reset-and-create-10-tournaments.ps1

$CLUB_ID = 1
$CREATED_BY = 1
$TOURNAMENT_COUNT = 10

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RESET & CREATE 10 TOURNAMENTS" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

$sql = @"
-- 1. Удаляем регистрации (если есть FK)
DELETE FROM tournament_registrations_db;

-- 2. Удаляем турниры
DELETE FROM tournaments_db;

-- 3. Сбрасываем sequence (важно!)
ALTER SEQUENCE tournaments_db_id_seq RESTART WITH 1;

-- 4. Создаем 10 турниров
INSERT INTO tournaments_db
(
    club_id,
    nombre,
    fecha_inicio,
    hora_inicio,
    duracion,
    genero_formato,
    categoria_nivel,
    tipo,
    cupo_max,
    precio,
    moneda,
    estado,
    deadline_cancelacion,
    info_detallada,
    contacto_organizador,
    faq_url,
    created_by,
    is_active,
    created_at,
    updated_at
)
SELECT
    $CLUB_ID,
    'Torneo Nuevo #' || gs,
    CURRENT_DATE + (gs || ' days')::interval,
    '19:00:00',
    '2 horas',
    'MIXTO',
    'INTERMEDIO',
    CASE
        WHEN gs % 2 = 0 THEN 'KING_OF_COURT'
        ELSE 'AMERICANA'
    END,
    20,
    12000.00,
    'ARS',
    'REGISTRO_ABIERTO',
    NOW() + interval '5 days',
    'Torneo generado automáticamente #' || gs,
    'organizador@test.com',
    'https://faq.test.com',
    $CREATED_BY,
    true,
    NOW(),
    NOW()
FROM generate_series(1, $TOURNAMENT_COUNT) AS gs;

-- 5. Проверка
SELECT id, nombre, tipo, estado
FROM tournaments_db
ORDER BY id;
"@

Write-Host "`nExecuting SQL..." -ForegroundColor Yellow
docker exec -i padel-postgres psql -U postgres -d player_padel_db -c "$sql"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "DONE!" -ForegroundColor Green