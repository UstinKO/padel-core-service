"""Закрытая копия и репетиция разовой очистки. Рабочая БД только читается."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

ROOT = Path('/opt/padel-app/backups/black-cleanup-1260')
EVENTS = '191,193'
CONTAINER = 'padel-postgres'
APP_CONTAINER = 'padel-core-app'


def ident(value):
    if not re.fullmatch('[a-z_][a-z_0-9]*', value):
        raise ValueError('Unexpected identifier')
    return '"' + value + '"'


def docker(tool, args, container=CONTAINER):
    shell = 'exec ' + tool + ' -U "$POSTGRES_USER" "$@"'
    return ['docker', 'exec', '-i', container, 'sh', '-ec', shell, 'cleanup', *args]


class Session:
    def __init__(self, database='', container=CONTAINER):
        shell = 'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "${1:-$POSTGRES_DB}"'
        self.process = subprocess.Popen(['docker', 'exec', '-i', container, 'sh', '-ec', shell, 'cleanup', database],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)

    def sql(self, value):
        marker = 'END_' + uuid.uuid4().hex
        self.process.stdin.write(value + '\n\\echo ' + marker + '\n')
        self.process.stdin.flush()
        lines = []
        for line in self.process.stdout:
            if line.strip() == marker:
                return lines
            if line.strip():
                lines.append(line.strip())
        raise RuntimeError('SQL session stopped')

    def json(self, value):
        return json.loads(self.sql(value)[0])

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.close()
            self.process.wait(timeout=30)


def schema(session):
    data = session.json("""SELECT jsonb_build_object(
      'tables',(SELECT jsonb_agg(c.relname ORDER BY c.relname) FROM pg_class c WHERE c.relnamespace='public'::regnamespace AND c.relkind='r'),
      'event_tables',(SELECT jsonb_agg(table_name ORDER BY table_name) FROM information_schema.columns WHERE table_schema='public' AND column_name='tournament_id'),
      'foreign_keys',(SELECT jsonb_agg(jsonb_build_object('child',c.conrelid::regclass::text,'parent',c.confrelid::regclass::text)) FROM pg_constraint c WHERE c.contype='f' AND c.connamespace='public'::regnamespace));""")
    data['foreign_keys'].sort(key=lambda row: (row['child'], row['parent']))
    return data


def selectors(metadata):
    reg = 'SELECT id FROM tournament_registrations_db WHERE tournament_id IN (' + EVENTS + ')'
    pay = 'SELECT id FROM payments_db WHERE registration_id IN (' + reg + ')'
    kings = 'SELECT id FROM tournament_king_of_court_db WHERE tournament_id IN (' + EVENTS + ')'
    rounds = 'SELECT id FROM king_of_court_round_db WHERE tournament_king_id IN (' + kings + ')'
    courts = 'SELECT id FROM king_of_court_court_db WHERE round_id IN (' + rounds + ')'
    result = {name: 'tournament_id IN (' + EVENTS + ')' for name in metadata['event_tables']}
    result.update({
        'tournaments_db': 'id IN (' + EVENTS + ')',
        'payments_db': 'registration_id IN (' + reg + ')',
        'marketing_registrations_db': 'registration_id IN (' + reg + ')',
        'marketing_paid_slots_db': 'registration_id IN (' + reg + ')',
        'marketing_payment_progress_db': 'payment_id IN (' + pay + ')',
        'marketing_events_db': 'tournament_id IN (' + EVENTS + ') OR registration_id IN (' + reg + ') OR payment_id IN (' + pay + ')',
        'participation_requests_db': 'registration_id IN (' + reg + ')',
        'registration_cancellation_members': 'event_id IN (SELECT id FROM registration_cancellations WHERE tournament_id IN (' + EVENTS + '))',
        'king_of_court_round_db': 'tournament_king_id IN (' + kings + ')',
        'king_of_court_court_db': 'round_id IN (' + rounds + ')',
        'court_players_db': 'court_id IN (' + courts + ')',
        'court_teams_db': 'court_id IN (' + courts + ')',
        'king_of_court_match_result_db': 'court_id IN (' + courts + ')',
        'king_of_court_player_stats_db': 'tournament_king_id IN (' + kings + ')',
        'pair_proposal_responses_db': 'token IN (SELECT partner_registration_token FROM tournament_registrations_db WHERE tournament_id IN (' + EVENTS + ') AND partner_registration_token IS NOT NULL)',
    })
    assert not {'player_padel_db', 'owners_db', 'club_db', 'owner_club_access_db', 'ranking_db'} & result.keys()
    assert result.keys() <= set(metadata['tables'])
    return result


def guard(session):
    data = session.json("""SELECT jsonb_build_object(
      'owner_ok',EXISTS(SELECT 1 FROM owners_db WHERE id=5 AND role='CLUB_ADMIN' AND is_active AND club_id=1),
      'event_ids',(SELECT jsonb_agg(id ORDER BY id) FROM tournaments_db WHERE owner_id=5),
      'target_ok',(SELECT count(*)=2 AND bool_and(owner_id=5 AND club_id=1) FROM tournaments_db WHERE id IN (191,193)),
      'registrations',(SELECT count(*) FROM tournament_registrations_db WHERE tournament_id IN (191,193)),
      'teams',(SELECT count(*) FROM americano_teams_db WHERE tournament_id IN (191,193)),
      'matches',(SELECT count(*) FROM americano_matches_db WHERE tournament_id IN (191,193)),
      'legacy_matches',(SELECT count(*) FROM matches_db WHERE tournament_id IN (191,193)));""")
    assert data == {'owner_ok': True, 'event_ids': [191, 193], 'target_ok': True,
                    'registrations': 52, 'teams': 26, 'matches': 25, 'legacy_matches': 0}
    return data


def digest(session, table, where='true'):
    return session.json('SELECT jsonb_build_object(\'count\',count(*),\'hash\',md5(COALESCE(string_agg(md5(to_jsonb(t)::text),\'\' ORDER BY to_jsonb(t)::text),\'\'))) FROM public.' + ident(table) + ' t WHERE ' + where + ';')


def plan(session):
    metadata = schema(session)
    chosen = selectors(metadata)
    for table, predicate in chosen.items():
        session.sql('CREATE TEMP TABLE ' + ident('cleanup_' + table) + ' ON COMMIT DROP AS SELECT * FROM public.' + ident(table) + ' WHERE ' + predicate + ';')
        if table in metadata['event_tables']:
            assert session.json('SELECT count(*) FROM ' + ident('cleanup_' + table) + ' WHERE tournament_id NOT IN (' + EVENTS + ');') == 0
    selected = {table: session.json('SELECT jsonb_build_object(\'count\',count(*),\'hash\',md5(COALESCE(string_agg(md5(to_jsonb(t)::text),\'\' ORDER BY to_jsonb(t)::text),\'\'))) FROM ' + ident('cleanup_' + table) + ' t;') for table in chosen}
    outside = {table: digest(session, table, 'NOT EXISTS (SELECT 1 FROM ' + ident('cleanup_' + table) + ' d WHERE to_jsonb(d)=to_jsonb(t))' if table in chosen else 'true') for table in metadata['tables']}
    payload = {'selected': selected, 'outside': outside, 'guard': guard(session)}
    payload['fingerprint'] = hashlib.sha256(json.dumps({'selected': selected, 'guard': payload['guard'], 'schema': metadata}, sort_keys=True).encode()).hexdigest()
    return payload, metadata, chosen


def deletion_order(metadata, chosen):
    remaining = set(chosen)
    result = []
    while remaining:
        ready = sorted(parent for parent in remaining if not any(f['parent'] == parent and f['child'] in remaining and f['child'] != parent for f in metadata['foreign_keys']))
        if not ready:
            raise RuntimeError('Unexpected dependency cycle')
        result.extend(ready)
        remaining.difference_update(ready)
    return result


def clean(session, expected=None, commit=False):
    session.sql('BEGIN ISOLATION LEVEL REPEATABLE READ; SET LOCAL statement_timeout=\'60s\'; SET LOCAL lock_timeout=\'5s\';')
    session.sql('SELECT pg_advisory_xact_lock(1260); SELECT id FROM tournaments_db WHERE id IN (' + EVENTS + ') ORDER BY id FOR UPDATE;')
    before, metadata, chosen = plan(session)
    if expected:
        assert before['fingerprint'] == expected['fingerprint']
    for table in deletion_order(metadata, chosen):
        count = session.json('WITH gone AS (DELETE FROM public.' + ident(table) + ' t USING ' + ident('cleanup_' + table) + ' d WHERE to_jsonb(t)=to_jsonb(d) RETURNING 1) SELECT count(*) FROM gone;')
        assert count == before['selected'][table]['count']
    for table in metadata['tables']:
        assert digest(session, table) == before['outside'][table], table
    session.sql('COMMIT;' if commit else 'ROLLBACK;')
    if not commit:
        assert guard(session) == before['guard']
    return before


def rating_state():
    inspected = subprocess.run(['docker', 'inspect', APP_CONTAINER, '--format', '{{json .Config.Env}}'], check=True, capture_output=True, text=True)
    env = dict(item.split('=', 1) for item in json.loads(inspected.stdout) if '=' in item)
    enabled = env.get('APP_RATING_PILOT_ENABLED', '').lower() == 'true'
    automatic = env.get('APP_RATING_PILOT_AUTO_REFRESH', '').lower() == 'true'
    result = {'enabled': enabled, 'automatic': automatic, 'retained_archived_targets': []}
    if enabled:
        assert automatic
        path = env.get('APP_RATING_PILOT_HISTORY')
        assert path
        container_file = subprocess.run(['docker', 'exec', APP_CONTAINER, 'cat', path], check=True, capture_output=True, text=True)
        source = json.loads(container_file.stdout)
        managed = set(source['managedEvents'])
        base = {event['key'] for event in source['history']['events']}
        result['retained_archived_targets'] = sorted((base - managed) & {'S-191', 'S-193'})
        assert not result['retained_archived_targets']
    return result


def backup_restore(directory, container=CONTAINER, source=''):
    archive = directory / 'database.dump'
    command = ['docker', 'exec', container, 'sh', '-ec',
               'exec pg_dump -Fc --no-owner --no-privileges -U "$POSTGRES_USER" -d "${1:-$POSTGRES_DB}"', 'cleanup', source]
    with archive.open('xb') as output:
        subprocess.run(command, stdout=output, stderr=subprocess.DEVNULL, check=True, timeout=180)
    clone = 'black_cleanup_1260_' + uuid.uuid4().hex[:12]
    subprocess.run(docker('createdb', ['-T', 'template0', clone], container), check=True, stderr=subprocess.DEVNULL, timeout=30)
    try:
        with archive.open('rb') as source_file:
            restored = subprocess.run(docker('pg_restore', ['--no-owner', '--no-privileges', '-f', '-'], container), stdin=source_file, check=True, capture_output=True, text=True, timeout=180)
        marker = "SELECT pg_catalog.set_config('search_path', '', false);"
        assert marker in restored.stdout
        # Функции нормализации вызывают друг друга без имени схемы при создании индексов.
        sql = restored.stdout.replace(marker, "SELECT pg_catalog.set_config('search_path', 'public,pg_catalog', false);")
        subprocess.run(docker('psql', ['-X', '-q', '-v', 'ON_ERROR_STOP=1', '-d', clone], container), input=sql, check=True, text=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
    except BaseException:
        subprocess.run(docker('dropdb', [clone], container), check=True, stderr=subprocess.DEVNULL, timeout=30)
        raise
    return archive, clone


def main():
    os.umask(0o077)
    live = Session()
    try:
        live.sql('BEGIN READ ONLY; SET LOCAL statement_timeout=\'30s\';')
        guard(live)
        size = live.json('SELECT pg_database_size(current_database());')
        live.sql('COMMIT;')
    finally:
        live.close()
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    assert shutil.disk_usage(ROOT).free > size * 5 + 100_000_000
    directory = ROOT / uuid.uuid4().hex
    directory.mkdir(mode=0o700)
    archive, clone = backup_restore(directory)
    trial = Session(clone)
    try:
        report = clean(trial)
    finally:
        trial.close()
        subprocess.run(docker('dropdb', [clone]), check=True, stderr=subprocess.DEVNULL, timeout=30)
    with archive.open('rb') as stream:
        archive_hash = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            archive_hash.update(chunk)
    report.update({'backup': str(archive), 'backup_sha256': archive_hash.hexdigest(),
                   'rating': rating_state(),
                   'restore_verified': True, 'rehearsal_verified': True, 'profiles_deleted': 0})
    shutil.copyfile(__file__, directory / 'verified-helper.py')
    (directory / 'prepared.json').write_text(json.dumps(report, indent=2) + '\n')
    print('BLACK_CLEANUP_PREPARED=' + json.dumps(report, sort_keys=True, separators=(',', ':')), flush=True)


if __name__ == '__main__':
    try:
        main()
    except BaseException as error:
        print('Подготовка не завершена: ' + type(error).__name__, flush=True)
        raise SystemExit(1)
