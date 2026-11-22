-- Только для отдельной пустой учебной БД calendar1263_preview, не для рабочего сайта.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO club_db(id,nombre,is_active) VALUES
(90012620,'Black Padel — учебная площадка',true),(90012621,'Black Padel 2 — учебная площадка',true),(90012622,'Чужой клуб',true);
INSERT INTO owners_db(id,email,password,first_name,last_name,role,is_active,club_id) VALUES
(90012620,'calendar-root@example.test',crypt('CalendarDemo1263!',gen_salt('bf',10)),'Calendar','Admin','SUPER_ADMIN',true,null),
(90012621,'calendar-club@example.test',crypt('CalendarDemo1263!',gen_salt('bf',10)),'Calendar','Club','CLUB_ADMIN',true,90012620),
(90012622,'calendar-organizer@example.test',crypt('CalendarDemo1263!',gen_salt('bf',10)),'Calendar','Organizer','ORGANIZER',true,null),
(90012623,'calendar-empty@example.test',crypt('CalendarDemo1263!',gen_salt('bf',10)),'Calendar','Empty','CLUB_ADMIN',true,90012620);
INSERT INTO owner_club_access_db(owner_id,club_id) VALUES (90012621,90012620),(90012621,90012621),(90012623,90012620);
INSERT INTO tournaments_db(id,owner_id,club_id,nombre,fecha_inicio,hora_inicio,duration_minutes,time_zone,genero_formato,categoria_nivel,tipo,modalidad,cupo_max,precio,moneda,estado,contacto_organizador,visibilidad,is_active,is_test_tournament) VALUES
(90012630,90012621,90012620,'Учебное событие 1',CURRENT_DATE,'14:00',90,'America/Argentina/Buenos_Aires','MASCULINO','C7_C6','AMERICANO','DOBLES',16,0,'ARS','REGISTRO_ABIERTO','calendar-club@example.test','SOLO_POR_ENLACE',true,true),
(90012631,90012621,90012620,'Учебное событие 2',CURRENT_DATE,'15:00',90,'America/Argentina/Buenos_Aires','FEMENINO','D7_D8','KING_OF_COURT','INDIVIDUAL',16,0,'ARS','PUBLICADO','calendar-club@example.test','SOLO_POR_ENLACE',true,true),
(90012632,90012621,90012621,'Учебное событие 3',CURRENT_DATE,'16:00',90,'America/Argentina/Buenos_Aires','MIXTO','TODOS','PADEL_CLINIC','INDIVIDUAL',16,0,'ARS','BORRADOR','calendar-club@example.test','SOLO_POR_ENLACE',true,true),
(90012633,90012621,90012620,'Учебное событие 4',CURRENT_DATE,'18:00',90,'America/Argentina/Buenos_Aires','MIXTO','TODOS','CANCHA_ABIERTA','INDIVIDUAL',16,0,'ARS','CERRADO','calendar-club@example.test','SOLO_POR_ENLACE',true,true),
(90012634,90012621,90012620,'Учебное событие 5',CURRENT_DATE,'18:00',90,'America/Argentina/Buenos_Aires','MASCULINO','C9','AMERICANO','INDIVIDUAL',16,0,'ARS','FINALIZADO','calendar-club@example.test','SOLO_POR_ENLACE',true,true),
(90012635,90012621,90012620,'Отменённое учебное событие',CURRENT_DATE,'21:00',90,'America/Argentina/Buenos_Aires','MASCULINO','C7','AMERICANO_TEAMS','DOBLES',16,0,'ARS','CANCELADO','calendar-club@example.test','SOLO_POR_ENLACE',false,true),
(90012636,90012622,90012620,'Чужое мероприятие на той же площадке',CURRENT_DATE,'17:00',90,'America/Argentina/Buenos_Aires','MASCULINO','C7','AMERICANO_TEAMS','DOBLES',16,0,'ARS','PUBLICADO','calendar-organizer@example.test','SOLO_POR_ENLACE',true,true),
(90012637,90012622,90012622,'Чужая площадка',CURRENT_DATE,'17:00',90,'America/Argentina/Buenos_Aires','MASCULINO','C7','AMERICANO_TEAMS','DOBLES',16,0,'ARS','PUBLICADO','calendar-organizer@example.test','SOLO_POR_ENLACE',true,true),
(90012638,90012621,90012621,'Следующий месяц',(date_trunc('month',CURRENT_DATE)+interval '1 month 5 days')::date,'14:00',90,'America/Argentina/Buenos_Aires','MIXTO','TODOS','PADEL_CLINIC','INDIVIDUAL',16,0,'ARS','PUBLICADO','calendar-club@example.test','SOLO_POR_ENLACE',true,true),
(90012639,90012621,90012620,'Через границу месяца',(date_trunc('month',CURRENT_DATE)+interval '1 month -1 day')::date,'23:30',120,'America/Argentina/Buenos_Aires','MIXTO','TODOS','CANCHA_ABIERTA','INDIVIDUAL',16,0,'ARS','PUBLICADO','calendar-club@example.test','SOLO_POR_ENLACE',true,true);
COMMIT;
