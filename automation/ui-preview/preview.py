#!/usr/bin/env python3
"""Управление только собственным локальным контуром UI v2."""
import argparse
import base64
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = Path(__file__).resolve().parent
RUNTIME = DIRECTORY / 'runtime'


def secret_file(name, value):
    path = RUNTIME / name
    with path.open('x') as stream:
        stream.write(value)
    # Каталог доступен только владельцу; bind-mounted secrets читает непривилегированный Java-процесс.
    path.chmod(0o444)


def initialize(port):
    if not 1024 <= port <= 65535:
        raise ValueError('Порт должен находиться в диапазоне 1024..65535')
    RUNTIME.mkdir(mode=0o700, exist_ok=False)
    marker = str(uuid.uuid4())
    password = secrets.token_urlsafe(32)
    gateway_password = secrets.token_urlsafe(32)
    secret_file('instance_id', marker)
    secret_file('fixture_password', password)
    secret_file('database_password', secrets.token_urlsafe(32))
    secret_file('jwt_secret', secrets.token_urlsafe(48))
    encoded = subprocess.check_output(['openssl', 'passwd', '-apr1', '-stdin'],
                                     input=gateway_password + '\n', text=True).strip()
    secret_file('gateway_passwords', 'preview:' + encoded + '\n')
    state = dict(instance=marker, project='padel-ui-v2-' + marker.replace('-', '')[:12], port=port,
                 gateway_password=gateway_password, fixture_password=password)
    path = RUNTIME / 'state.json'
    path.write_text(json.dumps(state))
    path.chmod(0o600)
    write_environment(state)
    print('Контур подготовлен. Доступы сохранены только в automation/ui-preview/runtime/state.json')


def load():
    if RUNTIME.is_symlink() or (RUNTIME / 'state.json').is_symlink():
        raise ValueError('Runtime не должен быть символической ссылкой')
    state = json.loads((RUNTIME / 'state.json').read_text())
    marker = str(uuid.UUID(state['instance']))
    if state['project'] != 'padel-ui-v2-' + marker.replace('-', '')[:12]:
        raise ValueError('Имя проекта не соответствует маркеру контура')
    if not isinstance(state['port'], int) or not 1024 <= state['port'] <= 65535:
        raise ValueError('Неверный порт контура')
    if (RUNTIME / 'instance_id').read_text() != marker:
        raise ValueError('Маркер runtime не совпадает')
    return state


def write_environment(state):
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if len(revision) != 40 or any(char not in '0123456789abcdef' for char in revision):
        raise ValueError('Неверная ревизия')
    (RUNTIME / 'compose.env').write_text('PREVIEW_RUNTIME_DIR=' + str(RUNTIME) + '\n'
        + 'PREVIEW_PORT=' + str(state['port']) + '\nPREVIEW_REVISION=' + revision + '\n')
    (RUNTIME / 'compose.env').chmod(0o600)


def compose(state, *arguments):
    endpoint = os.environ.get('DOCKER_HOST') or subprocess.check_output(
        ['docker', 'context', 'inspect', '--format', '{{.Endpoints.docker.Host}}'], text=True).strip()
    if not endpoint.startswith('unix://'):
        raise ValueError('Управление preview разрешено только через локальный Docker socket')
    version = subprocess.check_output(['docker', 'version', '--format', '{{.Server.Version}}'], text=True).strip()
    if int(version.split('.')[0]) < 28:
        raise ValueError('Для изоляции сети и loopback требуется Docker Engine 28 или новее')
    return subprocess.run(compose_arguments(state, *arguments), cwd=ROOT, check=True, env=compose_environment(state))


def compose_environment(state):
    values = dict(line.split('=', 1) for line in (RUNTIME / 'compose.env').read_text().splitlines())
    if values.get('PREVIEW_RUNTIME_DIR') != str(RUNTIME) or values.get('PREVIEW_PORT') != str(state['port']):
        raise ValueError('Конфигурация Compose не соответствует runtime')
    revision = values.get('PREVIEW_REVISION', '')
    if len(revision) != 40 or any(char not in '0123456789abcdef' for char in revision):
        raise ValueError('Неверная ревизия Compose')
    return dict(os.environ, PREVIEW_RUNTIME_DIR=str(RUNTIME), PREVIEW_PORT=str(state['port']), PREVIEW_REVISION=revision)


def compose_arguments(state, *arguments):
    return ['docker', 'compose', '--project-name', state['project'],
        '--env-file', str(RUNTIME / 'compose.env'), '-f', str(DIRECTORY / 'compose.yml'),
        *arguments]


class LocalRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, url):
        current = urllib.parse.urlsplit(request.full_url)
        target = urllib.parse.urlsplit(url)
        if (current.scheme, current.netloc) != (target.scheme, target.netloc):
            raise ValueError('Внешний redirect preview запрещён')
        return super().redirect_request(request, fp, code, message, headers, url)


class Client:
    def __init__(self, state):
        self.origin = 'http://127.0.0.1:' + str(state['port'])
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(LocalRedirects(), urllib.request.HTTPCookieProcessor(self.cookies))
        token = base64.b64encode(('preview:' + state['gateway_password']).encode()).decode()
        self.authorization = 'Basic ' + token

    def request(self, path, data=None):
        headers = {'Authorization': self.authorization}
        for cookie in self.cookies:
            if cookie.name == 'XSRF-TOKEN':
                headers['X-XSRF-TOKEN'] = urllib.parse.unquote(cookie.value)
        body = urllib.parse.urlencode(data).encode() if data is not None else None
        with self.opener.open(urllib.request.Request(self.origin + path, data=body, headers=headers), timeout=8) as response:
            return response.read()

    def login(self, state):
        self.request('/login')
        self.request('/login', {'username': 'preview-admin@example.test', 'password': state['fixture_password']})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['init', 'config', 'build', 'up', 'status', 'reset', 'mail', 'stop', 'release', 'rollback', 'recover'])
    parser.add_argument('--port', type=int, default=18114)
    args = parser.parse_args()
    if args.command == 'init':
        initialize(args.port)
        return
    state = load()
    managed = (RUNTIME / 'release-state/current.json').exists()
    if args.command in ('release', 'rollback', 'recover') or (args.command == 'up' and managed):
        import sys
        sys.path.insert(0, str(ROOT / 'automation/releases'))
        from preview_release import command
        command(args.command)
        return
    if args.command == 'config':
        compose(state, 'config', '--quiet')
    elif args.command == 'build':
        subprocess.run(['./mvnw', '-DskipTests', 'package'], cwd=ROOT, check=True)
        if managed:
            print('Jar подготовлен; используйте release. Исходный образ для rollback сохранён')
            return
        write_environment(state)
        compose(state, 'build', 'app')
    elif args.command == 'up':
        compose(state, 'up', '-d', '--no-build')
        client = Client(state)
        for attempt in range(120):
            try:
                client.request('/actuator/health/readiness')
                print('Preview готов: ' + client.origin + '/__preview')
                return
            except (urllib.error.URLError, TimeoutError):
                time.sleep(1)
        raise RuntimeError('Readiness не достигнут; проверьте логи собственного Compose-проекта')
    elif args.command == 'stop':
        compose(state, 'down')
        print('Собственные контейнеры остановлены; выделенный volume и runtime сохранены')
    else:
        client = Client(state)
        if args.command == 'mail':
            result = client.request('/__preview/mail')
        else:
            client.login(state)
            result = client.request('/api/internal/ui-preview' + ('/reset' if args.command == 'reset' else ''),
                                    {} if args.command == 'reset' else None)
        print(json.dumps(json.loads(result), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, RuntimeError, urllib.error.URLError, subprocess.CalledProcessError) as error:
        # Ответ сервера, аргументы subprocess и runtime могут содержать доступы.
        raise SystemExit('Операция preview не выполнена: ' + type(error).__name__)
