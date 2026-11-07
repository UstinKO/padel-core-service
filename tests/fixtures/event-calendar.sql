-- Только для отдельной тестовой БД; не запускать на пользовательской или боевой базе.
INSERT INTO club_db (id, nombre, direccion, zona_ciudad)
VALUES (9000808, 'Club Ñandú', 'Av. Córdoba 123', 'Buenos Aires');
INSERT INTO tournaments_db
(id, club_id, nombre, fecha_inicio, hora_inicio, duracion, duration_minutes, time_zone,
 genero_formato, categoria_nivel, tipo, modalidad, cupo_max, precio, contacto_organizador, estado, visibilidad)
VALUES
(9000808, 9000808, 'Мужской · C7', '2026-12-15', '18:30', '4 horas', 90, 'America/Argentina/Buenos_Aires', 'MASCULINO', 'C7', 'KING_OF_COURT', 'INDIVIDUAL', 16, 0, 'test-only', 'REGISTRO_ABIERTO', 'PUBLICO'),
(9000809, 9000808, 'Мужской · C8', '2026-12-15', '18:30', '2 horas', 120, 'America/Argentina/Buenos_Aires', 'MASCULINO', 'C8', 'AMERICANO', 'DOBLES', 16, 0, 'test-only', 'REGISTRO_ABIERTO', 'SOLO_POR_ENLACE'),
(9000810, 9000808, 'Cancha Abierta', '2026-12-15', '18:30', null, 60, 'America/Argentina/Buenos_Aires', 'MASCULINO', 'C7', 'CANCHA_ABIERTA', 'INDIVIDUAL', 16, 0, 'test-only', 'REGISTRO_ABIERTO', 'PUBLICO'),
(9000811, 9000808, 'Training', '2026-12-15', '18:30', null, 105, 'America/Argentina/Buenos_Aires', 'MASCULINO', 'C7', 'PADEL_CLINIC', 'INDIVIDUAL', 16, 0, 'test-only', 'REGISTRO_ABIERTO', 'PUBLICO'),
(9000812, 9000808, 'Americano equipos', '2026-12-15', '18:30', null, 135, 'America/Argentina/Buenos_Aires', 'MASCULINO', 'C7', 'AMERICANO_TEAMS', 'DOBLES', 16, 0, 'test-only', 'REGISTRO_ABIERTO', 'PUBLICO'),
(9000813, 9000808, 'Неизвестная длительность', '2026-12-15', '18:30', 'примерно вечером', null, 'America/Argentina/Buenos_Aires', 'MASCULINO', 'C7', 'AMERICANO', 'INDIVIDUAL', 16, 0, 'test-only', 'REGISTRO_ABIERTO', 'PUBLICO');
