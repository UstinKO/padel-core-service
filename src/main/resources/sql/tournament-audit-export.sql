WITH
event_rows AS (
  SELECT id, tipo AS format, modalidad AS modality, fecha_inicio AS event_date,
         estado AS status, is_active AS active
  FROM tournaments_db
), profile_rows AS (
  SELECT id, email LIKE '%@1padel.guest' AS guest, nivel_jugador AS current_level
  FROM player_padel_db
), identity_signals AS (
  SELECT id, NULLIF(lower(btrim(telegram_username)), '') AS telegram_key,
     CASE WHEN length(regexp_replace(coalesce(telefono,''), '[^0-9]', '', 'g')) >= 6
          THEN right(regexp_replace(telefono, '[^0-9]', '', 'g'), 10) END AS phone_key,
     NULLIF(lower(regexp_replace(btrim(concat_ws(' ',nombre,apellido)), '[[:space:]]+', ' ', 'g')), '') AS name_key
  FROM player_padel_db
), duplicate_rows AS (
  SELECT v.signal, array_agg(p.id ORDER BY p.id) AS player_ids
  FROM identity_signals p
  CROSS JOIN LATERAL (VALUES ('phone',p.phone_key), ('telegram',p.telegram_key), ('name',p.name_key)) v(signal,key)
  WHERE v.key IS NOT NULL
  GROUP BY v.signal,v.key HAVING count(*)>1
), registration_rows AS (
  SELECT id,tournament_id AS event_id,player_id,partner_id,main_player_id,status,is_active AS active,
         attended,participation_confirmed,waitlist_position,late_arrival
  FROM tournament_registrations_db
), king_runs AS (
  SELECT id AS run_id,tournament_id AS event_id,is_active AS active,is_finished AS finished,
         current_round,started_at,finished_at
  FROM tournament_king_of_court_db
), king_matches AS (
  SELECT m.id,k.event_id,k.run_id,r.id AS round_id,r.round_number,c.id AS court_id,c.court_number,
         ARRAY[m.winner1_id,m.winner2_id,m.loser1_id,m.loser2_id] AS players,
         ARRAY[m.winners_score,m.losers_score] AS score,m.created_at
  FROM king_of_court_match_result_db m
  JOIN king_of_court_court_db c ON c.id=m.court_id
  JOIN king_of_court_round_db r ON r.id=c.round_id
  JOIN king_runs k ON k.run_id=r.tournament_king_id
), americano_matches AS (
  SELECT m.id,m.tournament_id AS event_id,m.round_id,r.round_number,r.phase,m.match_number,m.court_number,
         m.playoff_stage AS stage,m.status,m.started_at,m.completed_at,m.next_match_id,
         ARRAY[m.team1_id,m.team2_id] AS team_ids,
         ARRAY[m.team1_player1_id,m.team1_player2_id,m.team2_player1_id,m.team2_player2_id] AS players,
         ARRAY[m.team1_score,m.team2_score] AS score,ARRAY[m.team1_games,m.team2_games] AS games,
         (to_jsonb(m)->>'playoff_play_started')::boolean AS play_started,
         (to_jsonb(m)->>'playoff_pair_changed')::boolean AS pair_changed,
         CASE WHEN upper(btrim(m.note)) IN ('BYE','WALKOVER','NO_SHOW','NO SHOW','FORFEIT','TECHNICAL')
              THEN upper(btrim(m.note)) END AS explicit_outcome_hint
  FROM americano_matches_db m LEFT JOIN americano_rounds_db r ON r.id=m.round_id
), teams AS (
  SELECT t.id,t.tournament_id AS event_id,t.team_number,t.player1_id,t.player2_id,t.status,t.attended,
         NULLIF(btrim(t.player2_name),'') IS NOT NULL AS player2_has_text,
         coalesce((SELECT jsonb_agg(jsonb_build_object('id',c.player_id,'active_registration',c.active_registration)
                                  ORDER BY c.player_id)
                   FROM (SELECT r.player_id,bool_or(r.active AND r.status<>'CANCELLED') AS active_registration
                         FROM registration_rows r JOIN identity_signals p ON p.id=r.player_id
                         WHERE r.event_id=t.tournament_id AND r.player_id<>t.player1_id
                           AND t.player2_id IS NULL
                           AND p.name_key=NULLIF(lower(regexp_replace(btrim(t.player2_name),'[[:space:]]+',' ','g')),'')
                         GROUP BY r.player_id) c), '[]'::jsonb) AS same_event_name_candidates
  FROM americano_teams_db t
), legacy_matches AS (
  SELECT id,tournament_id AS event_id,ronda AS round_number,partido_numero AS match_number,
         tipo AS format,estado AS status,ganador_id AS winner_id,perdedor_id AS loser_id,
         ARRAY[player1_id,player2_id,player3_id,player4_id] AS players,
         ARRAY[ARRAY[set1_equipo1,set1_equipo2],ARRAY[set2_equipo1,set2_equipo2],
               ARRAY[set3_equipo1,set3_equipo2],ARRAY[super_set_equipo1,super_set_equipo2]] AS sets,
         is_active AS active,fecha_inicio AS started_at,fecha_fin AS completed_at
  FROM matches_db
), versions AS (
  SELECT id,tournament_id AS event_id,recorded_at,action,subject_id,round_number,court_number,
         stage,restored_from,schema_version
  FROM tournament_conduct_history
), version_americano_matches AS (
  SELECT v.id AS version_id,v.tournament_id AS event_id,m->'id' AS id,m->'round_id' AS round_id,
     m->'match_number' AS match_number,m->'court_number' AS court_number,m->'status' AS status,
     m->'playoff_stage' AS stage,jsonb_build_array(m->'team1_id',m->'team2_id') AS team_ids,
     jsonb_build_array(m->'team1_player1_id',m->'team1_player2_id',m->'team2_player1_id',m->'team2_player2_id') AS players,
     jsonb_build_array(m->'team1_score',m->'team2_score') AS score,
     jsonb_build_array(m->'team1_games',m->'team2_games') AS games
  FROM tournament_conduct_history v
  CROSS JOIN LATERAL jsonb_array_elements(v.snapshot->'americano_matches_db') m
), version_king_matches AS (
  SELECT v.id AS version_id,v.tournament_id AS event_id,m->'id' AS id,r->'tournament_king_id' AS run_id,
     r->'id' AS round_id,r->'round_number' AS round_number,c->'court_number' AS court_number,
     jsonb_build_array(m->'winner1_id',m->'winner2_id',m->'loser1_id',m->'loser2_id') AS players,
     jsonb_build_array(m->'winners_score',m->'losers_score') AS score
  FROM tournament_conduct_history v
  CROSS JOIN LATERAL jsonb_array_elements(v.snapshot->'king_of_court_match_result_db') m
  LEFT JOIN LATERAL jsonb_array_elements(v.snapshot->'king_of_court_court_db') c ON c->>'id'=m->>'court_id'
  LEFT JOIN LATERAL jsonb_array_elements(v.snapshot->'king_of_court_round_db') r ON r->>'id'=c->>'round_id'
)
SELECT jsonb_build_object(
  'schemaVersion',1, 'capturedAt',transaction_timestamp(),
  'events',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM event_rows x),'[]'),
  'profiles',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM profile_rows x),'[]'),
  'duplicateGroups',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY signal,player_ids) FROM duplicate_rows x),'[]'),
  'registrations',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM registration_rows x),'[]'),
  'kingRuns',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY event_id,run_id) FROM king_runs x),'[]'),
  'kingMatches',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM king_matches x),'[]'),
  'americanoMatches',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM americano_matches x),'[]'),
  'teams',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM teams x),'[]'),
  'legacyMatches',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM legacy_matches x),'[]'),
  'versions',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY id) FROM versions x),'[]'),
  'versionAmericanoMatches',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY version_id,id) FROM version_americano_matches x),'[]'),
  'versionKingMatches',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY version_id,id) FROM version_king_matches x),'[]')
);
