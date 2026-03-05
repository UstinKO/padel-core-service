-- Очищаем таблицу игроков (если нужно начать с чистого листа)
-- TRUNCATE TABLE player_padel_db RESTART IDENTITY CASCADE;

-- Вставляем тестовых игроков
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

-- Создаем начальные записи в рейтинге для всех игроков
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