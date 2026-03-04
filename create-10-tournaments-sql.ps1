# create-10-tournaments-sql.ps1

$CLUB_ID = 1
$CREATED_BY = 1
$TOURNAMENT_COUNT = 10

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "CREATING 10 TOURNAMENTS (DIRECT SQL)" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

$sql = @"
-- Создаем 10 турниров
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
    'Torneo Test #' || gs,
    CURRENT_DATE + (gs || ' days')::interval,
    '18:00:00',
    '2 horas',
    'MIXTO',
    'INTERMEDIO',
    'ELIMINACION_SIMPLE',
    16,
    15000.00,
    'ARS',
    'REGISTRO_ABIERTO',
    NOW() + interval '7 days',
    'Torneo generado automáticamente para testing #' || gs,
    'organizador@test.com',
    'https://faq.test.com',
    $CREATED_BY,
    true,
    NOW(),
    NOW()
FROM generate_series(1, $TOURNAMENT_COUNT) AS gs;

-- Verificamos результат
SELECT id, nombre, estado, fecha_inicio
FROM tournaments_db
ORDER BY id DESC
LIMIT $TOURNAMENT_COUNT;
"@

Write-Host "`nExecuting SQL..." -ForegroundColor Yellow
docker exec -i padel-postgres psql -U postgres -d player_padel_db -c "$sql"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "DONE!" -ForegroundColor Green