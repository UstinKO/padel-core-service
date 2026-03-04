-- ============================================
-- Добавление тестовых клубов
-- ============================================

INSERT INTO club_db (
    nombre,
    direccion,
    zona_ciudad,
    telefono_contacto,
    email_contacto,
    mapa_url,
    website_url,
    descripcion,
    logo_url,
    is_active,
    created_at
) VALUES
      ('Padel Indoor Madrid',
       'Calle de la Princesa 25',
       'Madrid Centro',
       '+34 912 345 678',
       'info@padelindormadrid.com',
       'https://goo.gl/maps/12345',
       'https://www.padelindormadrid.com',
       'El mejor club de pádel indoor de Madrid con 10 pistas de última generación',
       '/images/clubs/madrid-indoor.png',
       true,
       NOW()),

      ('Barcelona Padel Club',
       'Avinguda Diagonal 456',
       'Barcelona',
       '+34 933 456 789',
       'info@barcelonapadelclub.com',
       'https://goo.gl/maps/67890',
       'https://www.barcelonapadelclub.com',
       'Club de pádel en el corazón de Barcelona con pistas al aire libre y cubiertas',
       '/images/clubs/barcelona.png',
       true,
       NOW()),

      ('Valencia Padel Center',
       'Calle de la Paz 12',
       'Valencia',
       '+34 960 123 456',
       'info@valenciapadelcenter.com',
       'https://goo.gl/maps/54321',
       'https://www.valenciapadelcenter.com',
       'Moderno centro de pádel con 8 pistas, cafetería y tienda especializada',
       '/images/clubs/valencia.png',
       true,
       NOW()),

      ('Sevilla Padel & Sport',
       'Avenida de la Constitución 78',
       'Sevilla',
       '+34 954 789 123',
       'info@sevillapadelsport.com',
       'https://goo.gl/maps/98765',
       'https://www.sevillapadelsport.com',
       'Complejo deportivo especializado en pádel con academia para todas las edades',
       '/images/clubs/sevilla.png',
       true,
       NOW()),

      ('Málaga Padel Beach',
       'Paseo Marítimo 34',
       'Málaga',
       '+34 951 456 789',
       'info@malagapadelbeach.com',
       'https://goo.gl/maps/13579',
       'https://www.malagapadelbeach.com',
       'Único club de pádel frente al mar con pistas al aire libre y vistas espectaculares',
       '/images/clubs/malaga.png',
       true,
       NOW()),

      ('Bilbao Padel Indoor',
       'Gran Vía 89',
       'Bilbao',
       '+34 944 567 890',
       'info@bilbaopadelindoor.com',
       'https://goo.gl/maps/24680',
       'https://www.bilbaopadelindoor.com',
       'Pistas indoor de última generación en el centro de Bilbao',
       '/images/clubs/bilbao.png',
       true,
       NOW()),

      ('Zaragoza Padel Club',
       'Calle del Coso 56',
       'Zaragoza',
       '+34 976 345 678',
       'info@zaragozapadelclub.com',
       'https://goo.gl/maps/11223',
       'https://www.zaragozapadelclub.com',
       'El punto de encuentro de los amantes del pádel en Zaragoza',
       '/images/clubs/zaragoza.png',
       true,
       NOW());

-- ============================================
-- Добавление тестовых игроков
-- ============================================

INSERT INTO player_padel_db (
    nombre,
    apellido,
    email,
    password_hash,
    telefono,
    fecha_registro,
    email_confirmado,
    activo
) VALUES
      ('Анатолий', 'Иванов', 'anatoly.ivanov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (901) 123-45-67', NOW(), true, true),
      ('Михаил', 'Петров', 'mikhail.petrov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (902) 234-56-78', NOW(), true, true),
      ('Санти', 'Гарсия', 'santi.garcia@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (903) 345-67-89', NOW(), true, true),
      ('Матвей', 'Сидоров', 'matvey.sidorov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (904) 456-78-90', NOW(), true, true),
      ('Макс', 'Козлов', 'max.kozlov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (905) 567-89-01', NOW(), true, true),
      ('Евгений', 'Смирнов', 'evgeny.smirnov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (906) 678-90-12', NOW(), true, true),
      ('Кирилл', 'Васильев', 'kirill.vasiliev@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (907) 789-01-23', NOW(), true, true),
      ('Иван', 'Михайлов', 'ivan.mikhailov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (908) 890-12-34', NOW(), true, true),
      ('Денис', 'Федоров', 'denis.fedorov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (909) 901-23-45', NOW(), true, true),
      ('Роман', 'Алексеев', 'roman.alekseev@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (910) 012-34-56', NOW(), true, true),
      ('Вадим', 'Павлов', 'vadim.pavlov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (911) 123-45-67', NOW(), true, true),
      ('Айдар', 'Сафин', 'aidar.safin@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (912) 234-56-78', NOW(), true, true),
      ('Павел_1', 'Попов', 'pavel.popov1@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (913) 345-67-89', NOW(), true, true),
      ('Павел_2', 'Лебедев', 'pavel.lebedev@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (914) 456-78-90', NOW(), true, true),
      ('Максим', 'Соколов', 'maxim.sokolov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (915) 567-89-01', NOW(), true, true),
      ('Хуан', 'Перес', 'juan.perez@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (916) 678-90-12', NOW(), true, true),
      ('Сергей', 'Морозов', 'sergey.morozov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (917) 789-01-23', NOW(), true, true),
      ('Дмитрий', 'Волков', 'dmitry.volkov@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (918) 890-12-34', NOW(), true, true),
      ('Алексей', 'Зайцев', 'aleksey.zaytsev@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (919) 901-23-45', NOW(), true, true),
      ('Владимир', 'Соловьев', 'vladimir.soloviev@email.com', '$2a$10$X7VYx/h1XVJQsJk9KQxYQe8nX9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9', '+7 (920) 012-34-56', NOW(), true, true);

-- ============================================
-- Добавление начальных записей в рейтинг
-- ============================================

INSERT INTO ranking_db (player_id, puntos, torneos_jugados, torneos_ganados, partidos_ganados, partidos_perdidos, sets_ganados, sets_perdidos, nivel_actual, created_at, updated_at)
SELECT
    id,
    1000,
    0,
    0,
    0,
    0,
    0,
    0,
    'C9',
    NOW(),
    NOW()
FROM player_padel_db
    ON CONFLICT (player_id) DO NOTHING;

-- ============================================
-- Проверка результатов
-- ============================================

-- Проверяем клубы
SELECT 'CLUBS' as table_name, COUNT(*) as count FROM club_db
UNION ALL
-- Проверяем игроков
SELECT 'PLAYERS', COUNT(*) FROM player_padel_db
UNION ALL
-- Проверяем рейтинг
SELECT 'RANKING', COUNT(*) FROM ranking_db;