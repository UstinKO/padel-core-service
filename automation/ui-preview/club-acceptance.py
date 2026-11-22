#!/usr/bin/env python3
"""Сравнивает клубный HTTP-контракт и браузер на двух jar в собственном preview."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

import pilot_manual
import preview

sys.path.insert(0, str(preview.ROOT / 'automation/releases'))
import preview_release

OUTPUT = preview.ROOT / 'target/club-acceptance'
FIELDS = {'id', 'name', 'address', 'city', 'description', 'logoUrl', 'mapUrl', 'websiteUrl', 'canonicalUrl'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Acceptance:
    def __init__(self):
        state = preview.load()
        pilot_manual.ensure_idle(preview.RUNTIME / 'pilot-manual-session.json')
        preview.compose(state, 'config', '--quiet')
        self.state, self.adapter, self.releases, self.config = preview_release.open_environment()
        self.client = preview.Client(self.state)
        self.client.login(self.state)
        status = json.loads(self.client.request('/api/internal/ui-preview'))
        if status['environment'] != 'preview':
            raise ValueError('Требуется собственный preview')
        identity = self.sql("SELECT current_database() || ':' || instance_id FROM ui_preview.control;")
        if identity != 'padel_ui_v2_preview:' + self.state['instance']:
            raise ValueError('БД/маркер не соответствуют собственному preview')
        preview_release.bootstrap(self.adapter, self.releases, self.config)
        self.current = self.releases.read('current')
        self.owned = []
        self.initial_active = []

    def sql(self, query):
        return subprocess.check_output(preview.compose_arguments(self.state, 'exec', '-T', 'preview-db',
            'psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-U', 'preview',
            '-d', 'padel_ui_v2_preview', '-c', query), cwd=preview.ROOT,
            env=preview.compose_environment(self.state), text=True).strip()

    def fixtures(self):
        self.initial_active = json.loads(self.sql('SELECT coalesce(json_agg(id ORDER BY id),\'[]\') FROM club_db WHERE is_active;'))
        marker = self.state['instance'][:8]
        values = self.sql("""
            WITH created AS (
                INSERT INTO club_db(nombre,direccion,zona_ciudad,descripcion,mapa_url,website_url,
                    telefono_contacto,email_contacto,is_active) VALUES
                ('Приёмка A <&> %s','Публичный адрес','Ciudad','Описание <&>',
                    'https://example.test/map','https://example.test/club','qa-private-contact','qa-private@example.test',true),
                ('Приёмка B null %s',null,null,null,null,null,'qa-private-contact','qa-private@example.test',true),
                ('Приёмка C inactive %s',null,null,null,null,null,'qa-private-contact','qa-private@example.test',false)
                RETURNING id,nombre,is_active)
            SELECT json_agg(created ORDER BY nombre) FROM created;
        """ % (marker, marker, marker))
        clubs = json.loads(values)
        self.owned = [club['id'] for club in clubs]
        self.full, self.nullable, self.hidden = clubs
        self.pilot = json.loads(self.client.request('/api/internal/ui-preview/playoff-pilot', {}))

    def response(self, path, language):
        time.sleep(1.1)
        request = urllib.request.Request(self.client.origin + path, headers={
            'Authorization': self.client.authorization, 'Accept-Language': language, 'Accept': 'application/json'})
        try:
            response = urllib.request.urlopen(request, timeout=40)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return dict(status=response.status, contentType=response.headers.get('Content-Type'),
                        cacheControl=response.headers.get('Cache-Control'), body=json.loads(response.read()))

    def snapshot(self):
        result = {}
        for scenario in self.pilot['scenarios']:
            value = self.response('/api/public/events/' + str(scenario['tournamentId']) + '/live', 'en')
            assert value['status'] == 200
            value['body']['metadata'].pop('generatedAt')
            result[scenario['key']] = value['body']
        return result

    def schema(self):
        return self.sql("""SELECT md5(
            (SELECT string_agg(table_schema||'.'||table_name||'.'||column_name||':'||data_type,','
                ORDER BY table_schema,table_name,ordinal_position) FROM information_schema.columns
                WHERE table_schema IN ('public','ui_preview')) ||
            (SELECT string_agg(id||':'||author||':'||filename||':'||coalesce(md5sum,''),','
                ORDER BY orderexecuted) FROM databasechangelog));""")

    def club_state(self):
        return self.sql('SELECT md5(string_agg(row_to_json(c)::text,\'|\' ORDER BY id)) FROM club_db c;')

    def contract(self):
        result = {}
        ordered = json.loads(self.sql('SELECT json_agg(id ORDER BY nombre,id) FROM club_db WHERE is_active;'))
        for language in ['ru', 'es', 'en']:
            cases = {'list': '/api/public/clubs', 'full': '/api/public/clubs/' + str(self.full['id']),
                     'nullable': '/api/public/clubs/' + str(self.nullable['id']),
                     'inactive': '/api/public/clubs/' + str(self.hidden['id']),
                     'unknown': '/api/public/clubs/9223372036854775807',
                     'zero': '/api/public/clubs/0', 'negative': '/api/public/clubs/-1',
                     'invalid': '/api/public/clubs/not-a-number'}
            result[language] = {key: self.response(path, language) for key, path in cases.items()}
            listing = result[language]['list']
            assert listing['status'] == 200 and listing['cacheControl'] == 'no-store'
            assert [club['id'] for club in listing['body']] == ordered
            for club in listing['body']:
                assert set(club) == FIELDS
                assert club['canonicalUrl'] == self.client.origin + '/clubs/' + str(club['id'])
            for key in ['full', 'nullable']:
                assert result[language][key]['status'] == 200
                assert set(result[language][key]['body']) == FIELDS
            for field in ['address', 'city', 'description', 'logoUrl', 'mapUrl', 'websiteUrl']:
                assert result[language]['nullable']['body'][field] is None
            for key in ['inactive', 'unknown', 'zero', 'negative']:
                problem = result[language][key]
                assert problem['status'] == 404 and problem['body']['code'] == 'CLUB_NOT_FOUND'
                assert problem['contentType'].startswith('application/problem+json')
            assert result[language]['invalid']['status'] == 400
            assert result[language]['invalid']['body']['code'] == 'INVALID_CATALOG_FILTER'
            assert 'qa-private' not in json.dumps(result[language], ensure_ascii=False)
        return result

    def active(self, ids):
        if ids:
            self.sql('UPDATE club_db SET is_active=true WHERE id IN (' + ','.join(map(str, ids)) + ');')

    def empty(self, label, browser=False):
        active = self.initial_active + [self.full['id'], self.nullable['id']]
        self.sql('UPDATE club_db SET is_active=false WHERE is_active;')
        try:
            cases = {language: self.response('/api/public/clubs', language) for language in ['ru', 'es', 'en']}
            assert all(value['status'] == 200 and value['body'] == [] for value in cases.values())
            if browser:
                self.browser(label, empty=True)
            return cases
        finally:
            self.active(active)

    def browser(self, label, empty=False):
        data = dict(origin=self.client.origin, gatewayPassword=self.state['gateway_password'],
                    password=self.state['fixture_password'], full=self.full, nullable=self.nullable,
                    hidden=self.hidden, empty=empty, label=label)
        path = preview.RUNTIME / 'club-browser-input.json'
        descriptor = os.open(path, os.O_CREAT | os.O_TRUNC | os.O_WRONLY, 0o600)
        os.fchmod(descriptor, 0o600)
        try:
            with os.fdopen(descriptor, 'w') as stream:
                json.dump(data, stream)
            subprocess.run([str(preview.ROOT / 'target/frontend-toolchain/node/node'),
                'tests/browser/club-read.browser.mjs', str(path)], cwd=preview.ROOT,
                env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(preview.ROOT / 'frontend/node_modules/@playwright/test/index.mjs')),
                check=True, timeout=300)
        finally:
            path.unlink(missing_ok=True)

    def cleanup(self):
        if self.releases.read('current')['id'] != self.current['id']:
            self.releases.deploy(self.current)
        if self.owned:
            self.active(self.initial_active)
            self.sql('DELETE FROM club_db WHERE id IN (' + ','.join(map(str, self.owned)) + ');')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-jar', type=Path, required=True)
    parser.add_argument('--baseline-revision', required=True)
    args = parser.parse_args()
    if not args.baseline_jar.is_file():
        parser.error('Отсутствует baseline jar')
    if len(args.baseline_revision) != 40 or any(c not in '0123456789abcdef' for c in args.baseline_revision):
        parser.error('Baseline revision должен быть полным SHA')
    with zipfile.ZipFile(args.baseline_jar) as baseline:
        if any(name.startswith(('BOOT-INF/lib/padel-application-', 'BOOT-INF/lib/padel-api-contract-'))
               for name in baseline.namelist()):
            parser.error('Baseline должен предшествовать обоим переносам application/contract')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    run = Acceptance()
    try:
        run.fixtures()
        initial, schema, clubs = run.snapshot(), run.schema(), run.club_state()
        print('club-acceptance: проверка текущего HTTP-контракта', flush=True)
        final = run.contract()
        final_empty = run.empty('current', browser=True)
        run.browser('current')
        before_container = run.adapter.container()['Id']
        preview.compose(run.state, 'restart', 'app')
        run.adapter.smoke(run.current)
        print('club-acceptance: приложение перезапущено', flush=True)
        assert run.adapter.container()['Id'] == before_container
        assert run.snapshot() == initial and run.club_state() == clubs
        assert run.contract() == final
        previous = preview_release.candidate(run.adapter, copy.deepcopy(run.config),
                                             args.baseline_jar.resolve(), args.baseline_revision)
        run.releases.deploy(previous)
        baseline = run.contract()
        assert baseline == final and run.empty('baseline') == final_empty
        assert run.snapshot() == initial and run.schema() == schema and run.club_state() == clubs
        run.releases.rollback()
        assert run.releases.read('current')['id'] == run.current['id']
        assert run.contract() == baseline
        assert run.snapshot() == initial and run.schema() == schema and run.club_state() == clubs
        (OUTPUT / 'http-baseline-private.json').write_text(json.dumps(baseline, ensure_ascii=False, indent=2) + '\n')
        summary = dict(result='PASS', currentRevision=run.current['revision'],
            currentJarSha256=sha(run.adapter.folder(run.current) / 'app.jar'),
            baselineRevision=args.baseline_revision, baselineJarSha256=sha(args.baseline_jar),
            locales=['ru', 'es', 'en'], cases=list(final['en']) + ['empty'], exactHttpJsonHeaders=True,
            arrayOrder='nombre,id', nullKeysPreserved=True, privateFieldsAbsent=True,
            restart=True, nativeRollback=True, schemaAndLiquibasePreserved=True,
            clubDataPreserved=True, tournamentSnapshotsPreserved=5)
        (OUTPUT / 'http-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
        print('club-acceptance: HTTP/JSON/browser/restart/rollback PASS', flush=True)
    finally:
        run.cleanup()


if __name__ == '__main__':
    main()
