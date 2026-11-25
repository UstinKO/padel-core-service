-- Только для одноразовой локальной базы сценария.
\getenv qa_pass GUEST_CLAIM_QA_PASSWORD
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TEMPORARY TABLE fixture_password AS SELECT crypt(:'qa_pass', gen_salt('bf')) AS hash;
INSERT INTO owners_db(email,password,first_name,last_name,role,is_active)
SELECT 'qa-owner@example.test', hash, 'QA', 'Owner', 'SUPER_ADMIN', true FROM fixture_password;
INSERT INTO player_padel_db(nombre,apellido,email,password_hash,email_confirmado,activo,nivel_jugador,
                            preferred_locale,oauth2_user,telefono)
SELECT 'QA', suffix, email, hash, true, true, 'C7', 'es', false, phone
FROM fixture_password CROSS JOIN (VALUES
  ('Main', 'qa-main@example.test', NULL),
  ('Guest', 'qa-guest@1padel.guest', '+54 9 11 2233-7755'),
  ('Conflict', 'qa-conflict@example.test', NULL),
  ('ConflictGuest', 'qa-conflict@1padel.guest', '+54 9 11 2233-7766'),
  ('ManualMain', 'qa-manual@example.test', NULL),
  ('ManualGuest', 'qa-manual@1padel.guest', '+54 9 11 2233-7777')
) AS fixture(suffix, email, phone);

INSERT INTO club_db(nombre,direccion,created_by,is_active)
SELECT 'QA Guest Claim','Synthetic',id,true FROM owners_db WHERE email = 'qa-owner@example.test';
INSERT INTO tournaments_db(club_id,nombre,fecha_inicio,hora_inicio,genero_formato,categoria_nivel,
                           tipo,modalidad,cupo_max,precio,estado,contacto_organizador,is_active,created_by)
SELECT club.id, name, current_date + 7,'12:00','MIXTO','C7','AMERICANO','INDIVIDUAL',8,0,
       'REGISTRO_ABIERTO','qa-owner@example.test',true,club.created_by
FROM club_db club CROSS JOIN (VALUES ('QA claim origin'), ('QA claim conflict')) AS fixture(name)
WHERE club.nombre = 'QA Guest Claim';

INSERT INTO tournament_registrations_db(tournament_id,player_id,status,is_active,is_double_registration,
                                       position,registration_date)
SELECT t.id,p.id,'CONFIRMED',true,false, position,localtimestamp
FROM (VALUES ('QA claim origin','qa-guest@1padel.guest',1),
             ('QA claim conflict','qa-conflict@example.test',1),
             ('QA claim conflict','qa-conflict@1padel.guest',2)) AS fixture(event, player_email, position)
JOIN tournaments_db t ON t.nombre = event JOIN player_padel_db p ON p.email = player_email;
