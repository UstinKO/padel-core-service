#!/usr/bin/env python3
"""Приёмка кругового read-среза только в собственном синтетическом preview."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time

import pilot_manual
import preview


OUTPUT = preview.ROOT / 'target/round-robin-acceptance'
FIXTURES = preview.RUNTIME / 'round-robin-acceptance-fixtures.json'
CHECKPOINT = preview.RUNTIME / 'round-robin-acceptance-checkpoint.json'


def private_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_CREAT | os.O_TRUNC | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, 'w') as stream:
        json.dump(value, stream)


def validate_scope(state, status, database_identity):
    expected = 'padel_ui_v2_preview:' + state['instance']
    if status.get('environment') != 'preview' or database_identity != expected:
        raise ValueError('Приёмка разрешена только в собственном preview с совпадающим UUID БД')
    if not status.get('fixtures', {}).get('playerIds') or not status['fixtures'].get('tournamentIds'):
        raise ValueError('Отсутствуют синтетические preview-фикстуры')


def environment():
    state = preview.load()
    pilot_manual.ensure_idle(preview.RUNTIME / 'pilot-manual-session.json')
    client = preview.Client(state)
    client.login(state)
    status = json.loads(client.request('/api/internal/ui-preview'))

    def sql(query):
        return subprocess.check_output(preview.compose_arguments(state, 'exec', '-T', 'preview-db',
            'psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-U', 'preview',
            '-d', 'padel_ui_v2_preview', '-c', query), cwd=preview.ROOT,
            env=preview.compose_environment(state), text=True).strip()

    validate_scope(state, status, sql("SELECT current_database()||':'||instance_id FROM ui_preview.control;"))
    return state, client, status, sql


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def seed(state, client, status, sql):
    if FIXTURES.exists():
        saved = json.loads(FIXTURES.read_text())
        if saved['instance'] != state['instance']:
            raise ValueError('Фикстуры принадлежат другому preview')
    suffix = secrets.token_hex(8)
    sources = status['fixtures']

    def event(key, mode, count):
        patch = dict(nombre='RR acceptance ' + key, estado='CERRADO', tipo='AMERICANO_TEAMS',
                     modalidad='DOBLES', cupo_max=max(count, 2), precio=0, mostrar_nivel=True)
        tournament = int(sql("INSERT INTO tournaments_db SELECT (jsonb_populate_record(NULL::tournaments_db,"
            "to_jsonb(t)||" + literal(json.dumps(patch)) + "::jsonb||jsonb_build_object('id',"
            "nextval(pg_get_serial_sequence('tournaments_db','id'))))).* FROM tournaments_db t WHERE id="
            + str(int(sources['tournamentIds'][2])) + " RETURNING id;"))
        for index in range(count):
            pair = []
            for side in range(2):
                patch = dict(nombre='Acceptance', apellido=f'Player {index * 2 + side + 1}',
                    email=f'rr1378-{suffix}-{key}-{index}-{side}@example.test', telefono=None,
                    telegram_username=None, telegram_chat_id=None, google_id=None, oauth2_user=False,
                    nivel_jugador='C7', playing_side='EITHER')
                player = int(sql("INSERT INTO player_padel_db SELECT (jsonb_populate_record(NULL::player_padel_db,"
                    "to_jsonb(p)||" + literal(json.dumps(patch)) + "::jsonb||jsonb_build_object('id',"
                    "nextval(pg_get_serial_sequence('player_padel_db','id'))))).* FROM player_padel_db p WHERE id="
                    + str(int(sources['playerIds'][0])) + " RETURNING id;"))
                pair.append(player)
            for player, partner in [(pair[0], pair[1]), (pair[1], pair[0])]:
                sql("INSERT INTO tournament_registrations_db(tournament_id,player_id,main_player_id,partner_id,"
                    "status,is_active,is_double_registration,position,registration_date) VALUES ("
                    + ','.join(map(str, [tournament, player, pair[0], partner]))
                    + ",'CONFIRMED',true,true," + str(index + 1) + ",localtimestamp);")
        return dict(key=key, tournamentId=tournament, mode=mode, kind='extended')

    cases = [event('extended-' + mode.lower(), mode, 6) for mode in ['SETS', 'POINTS']]
    for mode in ['SETS', 'POINTS']:
        case = event('guest-' + mode.lower(), mode, 2)
        case['kind'] = 'guest'
        tournament = case['tournamentId']
        client.request('/tournaments/team-americano/initialize', dict(tournamentId=tournament,
            scoringMode=mode, courts=2, pointsPerMatch=32))
        assert sql('SELECT count(*) FROM americano_teams_db WHERE tournament_id=' + str(tournament)) == '2'
        first = int(sql('SELECT id FROM americano_teams_db WHERE tournament_id=' + str(tournament)
                        + ' AND team_number=1;'))
        sql("UPDATE player_padel_db SET nivel_jugador='SIN_ESPECIFICAR',playing_side='LEFT' WHERE id="
            + '(SELECT player1_id FROM americano_teams_db WHERE id=' + str(first) + ');')
        sql("UPDATE player_padel_db SET playing_side='RIGHT' WHERE id=(SELECT player2_id FROM americano_teams_db"
            + ' WHERE tournament_id=' + str(tournament) + ' AND team_number=2);')
        sql("UPDATE americano_teams_db SET player2_id=NULL,player2_name='Invitado QA 1378',"
            "player2_phone='+5491100000137' WHERE id=" + str(first) + ';')
        sql('UPDATE americano_teams_db SET current_position=9 WHERE tournament_id=' + str(tournament) + ';')
        cases.append(case)
    empty = event('empty', 'POINTS', 0)
    empty['kind'] = 'empty'
    cases.append(empty)
    private_json(FIXTURES, dict(instance=state['instance'], cases=cases))
    return cases


def all_cases():
    own = json.loads(FIXTURES.read_text())['cases']
    baseline = preview.ROOT / 'target/round-robin-engine/fixtures-private.json'
    if baseline.exists():
        own += [dict(case, key='baseline-' + case['mode'].lower()) for case in json.loads(baseline.read_text())]
    return own


def persisted(sql, cases):
    ids = ','.join(str(int(case['tournamentId'])) for case in cases)
    result = {}
    for table in ['americano_teams_db', 'americano_rounds_db', 'americano_matches_db',
                  'tournament_conduct_history', 'tournament_registrations_db']:
        result[table] = sql("SELECT coalesce(md5(string_agg(to_jsonb(t)::text,'|' ORDER BY id)),'empty') FROM "
                           + table + ' t WHERE tournament_id IN (' + ids + ');')
    result['tournaments'] = sql("SELECT md5(string_agg(to_jsonb(t)::text,'|' ORDER BY id)) FROM tournaments_db t WHERE id IN (" + ids + ');')
    result['players'] = sql("SELECT md5(string_agg(to_jsonb(p)::text,'|' ORDER BY id)) FROM player_padel_db p WHERE id IN "
        '(SELECT player_id FROM tournament_registrations_db WHERE tournament_id IN (' + ids + '));')
    result['schema'] = sql("""SELECT md5(
        (SELECT string_agg(concat(table_schema,'.',table_name,'.',column_name,':',data_type,':',
            is_nullable,':',column_default),'|' ORDER BY table_schema,table_name,ordinal_position)
            FROM information_schema.columns WHERE table_schema IN ('public','ui_preview')) ||
        (SELECT string_agg(n.nspname||'.'||r.relname||'.'||c.conname||':'||pg_get_constraintdef(c.oid),
            '|' ORDER BY n.nspname,r.relname,c.conname) FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=r.relnamespace WHERE n.nspname IN ('public','ui_preview')) ||
        (SELECT string_agg(indexdef,'|' ORDER BY schemaname,tablename,indexname) FROM pg_indexes
            WHERE schemaname IN ('public','ui_preview')) ||
        (SELECT string_agg(to_jsonb(c)::text,'|' ORDER BY orderexecuted) FROM databasechangelog c));""")
    return result


def capture(state, client, sql):
    cases = all_cases()
    before = persisted(sql, cases)
    data = {case['key']: dict(
        ranking=json.loads(client.request('/tournaments/team-americano/api/' + str(case['tournamentId']) + '/ranking')),
        rounds=json.loads(client.request('/api/tournaments/americano/' + str(case['tournamentId']) + '/rounds')))
        for case in cases}
    assert persisted(sql, cases) == before, 'GET изменил строки данных, историю или схему'
    return dict(instance=state['instance'], responses=data, persisted=before)


def browser(state, cases, readonly=False):
    path = preview.RUNTIME / 'round-robin-acceptance-input.json'
    private_json(path, dict(origin='http://127.0.0.1:' + str(state['port']), gatewayPassword=state['gateway_password'],
                           password=state['fixture_password'], cases=cases))
    try:
        subprocess.run([str(preview.ROOT / 'target/frontend-toolchain/node/node'),
            'tests/browser/modular-pilot/round-robin-acceptance.mjs', str(path)] + (['--read-only'] if readonly else []),
            cwd=preview.ROOT, env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(
                preview.ROOT / 'frontend/node_modules/@playwright/test/index.mjs')), check=True, timeout=300)
    finally:
        path.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['run', 'read-only', 'capture', 'compare'])
    command = parser.parse_args().command
    state, client, status, sql = environment()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if command == 'run':
        cases = seed(state, client, status, sql)
        browser(state, cases)
        # Независимые матрицы чтения не должны исчерпывать штатный GENERAL-бюджет 200/мин.
        time.sleep(60)
        before = capture(state, client, sql)
        browser(state, cases, readonly=True)
        assert capture(state, client, sql) == before, 'GET изменил ответы, строки данных, историю или схему'
        for case in cases:
            ranking = before['responses'][case['key']]['ranking']['ranking']
            for row in ranking:
                saved = int(sql('SELECT current_position FROM americano_teams_db WHERE id=' + str(int(row['id']))))
                assert saved == (9 if case['kind'] == 'guest' else row['currentPosition'])
        (OUTPUT / 'read-only.json').write_text(json.dumps(dict(result='PASS', cases=len(all_cases()),
            allRowsHistoryAndSchemaPreserved=True, finishedPositionsMatchApi=True, guestStoredSentinelPreserved=True)) + '\n')
    elif command == 'read-only':
        before = capture(state, client, sql)
        browser(state, json.loads(FIXTURES.read_text())['cases'], readonly=True)
        assert capture(state, client, sql) == before
    elif command == 'capture':
        private_json(CHECKPOINT, capture(state, client, sql))
    else:
        assert capture(state, client, sql) == json.loads(CHECKPOINT.read_text()), 'Состояние после перезапуска/отката изменилось'
    print('Круговой read-срез: ' + command + ' — PASS')


if __name__ == '__main__':
    main()
