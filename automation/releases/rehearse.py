#!/usr/bin/env python3
"""Репетиция двух синтетических выпусков настоящего приложения и HTTP-сбоев."""
import copy
import hashlib
import http.server
import json
from pathlib import Path
import subprocess
import threading
import urllib.error
import urllib.request
import zipfile
import artifact
import preview_release
import preview
import smoke
from transaction import RollbackFailure


def variant(source, target, marker):
    entry = 'window.__releaseProbe=' + json.dumps(marker) + ';'
    lazy = 'export default ' + json.dumps(marker) + ';'
    path = 'assets/release-probe-' + artifact.digest(entry.encode())[:16] + '.js'
    lazy_path = 'assets/release-probe-lazy-' + artifact.digest(lazy.encode())[:16] + '.js'
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, 'w') as result:
        for item in original.infolist():
            data = original.read(item)
            if item.filename == artifact.PREFIX + '/web/index.html':
                data = data.replace(b'</body>', ('<script type="module" src="/web/' + path + '"></script></body>').encode())
            if item.filename == artifact.PREFIX + '/web/.vite/manifest.json':
                graph = json.loads(data)
                graph['release-probe'] = dict(file=path, isEntry=True, dynamicImports=['release-lazy'])
                graph['release-lazy'] = dict(file=lazy_path, isDynamicEntry=True)
                data = json.dumps(graph).encode()
            result.writestr(item, data)
        result.writestr(artifact.PREFIX + '/web/' + path, entry)
        result.writestr(artifact.PREFIX + '/web/' + lazy_path, lazy)
    return '/web/' + lazy_path


class FaultProxy:
    def __init__(self, origin, mode):
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                failure = self.path == '/actuator/health/readiness' if mode == 'readiness' else self.path.startswith('/web/assets/')
                if failure:
                    self.send_response(503 if mode == 'readiness' else 404)
                    self.end_headers()
                    return
                try:
                    request = urllib.request.Request(origin + self.path,
                        headers={'Authorization': self.headers.get('Authorization', '')})
                    with urllib.request.urlopen(request, timeout=8) as response:
                        self.send_response(response.status)
                        for key, value in response.headers.items():
                            if key.lower() not in ('transfer-encoding', 'connection'):
                                self.send_header(key, value)
                        self.end_headers()
                        self.wfile.write(response.read())
                except (OSError, urllib.error.URLError):
                    self.send_response(502)
                    self.end_headers()
            def log_message(self, *_):
                pass
        self.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self):
        self.thread.start()
        return 'http://127.0.0.1:' + str(self.server.server_port)

    def __exit__(self, *_):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()


def main():
    state, adapter, releases, config = preview_release.open_environment()
    preview_release.bootstrap(adapter, releases, config)
    baseline = releases.read('current')
    root = preview.ROOT
    output = root / 'target/release-rehearsal'
    output.mkdir(parents=True, exist_ok=True)
    source = root / 'padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar'
    records = {}
    probes = {}
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    for marker in ['A', 'B']:
        jar = output / (marker + '.jar')
        probes[marker] = variant(source, jar, marker)
        synthetic = hashlib.sha1((revision + marker).encode()).hexdigest()
        records[marker] = preview_release.candidate(adapter, copy.deepcopy(config), jar, synthetic, rehearsal=True)
    client = preview.Client(state)
    input_path = preview.RUNTIME / 'rehearsal-browser.json'
    input_path.write_text(json.dumps(dict(origin=client.origin, gatewayPassword=state['gateway_password'],
        password=state['fixture_password'], previousLazy=probes['A'])))
    input_path.chmod(0o600)
    browser = None
    try:
        releases.deploy(records['A'])
        browser = subprocess.Popen(['node', 'tests/browser/release-rehearsal.browser.mjs', str(input_path)],
            cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        if browser.stdout.readline().strip() != 'A_READY':
            raise RuntimeError('Браузер не подтвердил выпуск A')
        releases.deploy(records['B'])
        browser.stdin.write('B\n'); browser.stdin.flush()
        if browser.stdout.readline().strip() != 'B_READY':
            raise RuntimeError('Открытая вкладка не загрузила прежний chunk')
        releases.rollback()
        assert releases.read('current')['id'] == records['A']['id']
        browser.stdin.write('A\n'); browser.stdin.flush()
        if browser.stdout.readline().strip() != 'ROLLBACK_READY':
            raise RuntimeError('Браузер не подтвердил откат')
        browser.wait(timeout=20)
        if browser.returncode:
            raise RuntimeError('Ошибка браузерной репетиции')
        ordinary = adapter.http
        ordinary_fetch = adapter.fetch
        ordinary_smoke = adapter.smoke
        for mode in ['chunk', 'readiness']:
            with FaultProxy(client.origin, mode) as origin:
                broken = smoke.Http(origin, client.authorization)
                adapter.fetch = lambda record, bad=broken: bad if record['id'] == records['B']['id'] else ordinary_fetch(record)
                def candidate_smoke(record):
                    adapter.timeout = (12 if mode == 'chunk' else 3) if record['id'] == records['B']['id'] else 120
                    return ordinary_smoke(record)
                adapter.smoke = candidate_smoke
                try:
                    releases.deploy(records['B'])
                    raise AssertionError('Выпуск с HTTP-сбоем принят')
                except RollbackFailure:
                    raise
                except (ValueError, RuntimeError):
                    pass
                finally:
                    adapter.fetch = ordinary_fetch
                    adapter.smoke = ordinary_smoke
                    adapter.http = ordinary
                    adapter.timeout = 120
            assert releases.read('current')['id'] == records['A']['id']
            assert releases.read('pending') is None
            assert (preview.RUNTIME / 'compose.env').read_bytes() == (adapter.folder(records['A']) / 'environment').read_bytes()
            adapter.smoke(records['A'])
            print('Репетиция: HTTP-сбой ' + mode + ', полный предыдущий выпуск восстановлен', flush=True)
        # Проверяем реальное восстановление после оставшегося журнала незавершённого переключения.
        from transaction import atomic
        atomic(releases.directory / 'pending.json', dict(previous=records['A'], candidate=records['B'], prior=records['B']))
        adapter.apply(records['B'])
        releases.recover()
        adapter.smoke(records['A'])
        report = dict(sourceRevision=revision, variants={key: value['revision'] for key, value in records.items()},
            successfulSwitch=True, previousLazyChunk=True, manualRollback=True, missingChunkRollback=True,
            readinessRollback=True, journalRecovery=True)
        (output / 'report.json').write_text(json.dumps(report, indent=2))
        print('Репетиция: два выпуска, старый lazy chunk, rollback и восстановление журнала прошли', flush=True)
    finally:
        if browser and browser.poll() is None:
            browser.terminate()
            try:
                browser.wait(timeout=5)
            except subprocess.TimeoutExpired:
                browser.kill(); browser.wait()
        input_path.unlink(missing_ok=True)
        releases.deploy(baseline)


if __name__ == '__main__':
    main()
