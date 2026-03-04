-- Отключаем проверку внешних ключей временно
SET session_replication_role = 'replica';

-- Удаляем все таблицы
DROP TABLE IF EXISTS court_teams_db CASCADE;
DROP TABLE IF EXISTS court_players_db CASCADE;
DROP TABLE IF EXISTS king_of_court_match_result_db CASCADE;
DROP TABLE IF EXISTS king_of_court_player_stats_db CASCADE;
DROP TABLE IF EXISTS king_of_court_court_db CASCADE;
DROP TABLE IF EXISTS king_of_court_round_db CASCADE;
DROP TABLE IF EXISTS tournament_king_of_court_db CASCADE;
DROP TABLE IF EXISTS matches_db CASCADE;
DROP TABLE IF EXISTS tournament_registrations_db CASCADE;
DROP TABLE IF EXISTS ranking_db CASCADE;
DROP TABLE IF EXISTS tournaments_db CASCADE;
DROP TABLE IF EXISTS owners_db CASCADE;
DROP TABLE IF EXISTS player_padel_db CASCADE;
DROP TABLE IF EXISTS club_db CASCADE;

-- Удаляем таблицы Liquibase
DROP TABLE IF EXISTS databasechangelog CASCADE;
DROP TABLE IF EXISTS databasechangeloglock CASCADE;

-- Включаем обратно проверку внешних ключей
SET session_replication_role = 'origin';

-- Проверяем результат
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';