#!/usr/bin/env python3
"""Проверка только запущенного собственного preview-контура."""
import http.server
import json
import subprocess
import threading
import urllib.error
import uuid
import preview

state = preview.load()
client = preview.Client(state)
client.login(state)
fixtures = json.loads(client.request('/api/internal/ui-preview'))['fixtures']
prefix = state['project']
app = prefix + '-app-1'
database = prefix + '-preview-db-1'


def sql(statement):
    return subprocess.check_output(['docker', 'exec', '-i', database, 'psql', '-X', '-q', '-t', '-A',
        '-v', 'ON_ERROR_STOP=1', '-U', 'preview', '-d', 'padel_ui_v2_preview'], input=statement, text=True).strip()


def blocked(address, port):
    result = subprocess.run(['docker', 'exec', app, 'bash', '-c',
        'timeout 3 bash -c "exec 3<>/dev/tcp/$1/$2" -- "$1" "$2"', '--', address, str(port)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return result.returncode != 0


assert not blocked('preview-db', 5432), 'Сам тест подключения не работает'
assert blocked('1.1.1.1', 443), 'Есть исходящий маршрут'
class Canary(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(204)
        self.end_headers()
    def log_message(self, *_):
        pass
server = http.server.ThreadingHTTPServer(('0.0.0.0', 0), Canary)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
try:
    assert blocked('host.docker.internal', server.server_port), 'Есть доступ к сервисам хоста'
finally:
    server.shutdown()
    server.server_close()
    thread.join()
for service in ['app', 'preview-db', 'preview-mail']:
    item = json.loads(subprocess.check_output(['docker', 'inspect', prefix + '-' + service + '-1']))[0]
    assert not item['HostConfig']['PortBindings'], 'Опубликован внутренний порт'
    assert list(item['NetworkSettings']['Networks']) == [prefix + '_isolated'], 'Найдена внешняя сеть'
network = json.loads(subprocess.check_output(['docker', 'network', 'inspect', prefix + '_isolated']))[0]
assert network['Internal'] and network['Options']['com.docker.network.bridge.gateway_mode_ipv4'] == 'isolated'
for key, value, expected in [
        ('SPRING_DATASOURCE_URL', 'jdbc:postgresql://forbidden.invalid:5432/other', 'spring.datasource.url'),
        ('SPRING_LIQUIBASE_URL', 'jdbc:postgresql://forbidden.invalid:5432/other', 'spring.liquibase.url'),
        ('SPRING_PROFILES_ACTIVE', 'preview,prod', 'profiles'),
        ('APP_PREVIEW_INSTANCE_ID', str(uuid.uuid4()), 'Маркер базы preview не совпадает')]:
    result = subprocess.run(preview.compose_arguments(state, 'run', '--rm', '--no-deps', '-T', '-e', key + '=' + value, 'app'),
        cwd=preview.ROOT, env=preview.compose_environment(state), stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, timeout=45)
    assert result.returncode != 0 and expected in result.stdout, 'Нет раннего отказа: ' + key
print('Старт: внешние datasource/Liquibase URL, production-профиль и чужой маркер отклонены', flush=True)
mail = json.loads(client.request('/__preview/mail'))
assert mail['total'] >= 1
assert all(person['Address'].endswith('@example.test') for message in mail['messages'] for person in message['To'])
print('Сеть: внутреннее соединение работает, внешнее и сервис хоста недоступны; порты закрыты; письмо в Mailpit', flush=True)

output = preview.ROOT / 'target/ui-preview-qa'
output.mkdir(parents=True, exist_ok=True)
path = preview.RUNTIME / 'browser-input.json'
path.write_text(json.dumps(dict(origin=client.origin, gatewayPassword=state['gateway_password'], password=state['fixture_password'])))
path.chmod(0o600)
try:
    subprocess.run(['node', 'tests/browser/ui-preview.browser.mjs', str(path)], cwd=preview.ROOT, check=True)
finally:
    path.unlink(missing_ok=True)

canary = None
try:
    canary = sql("INSERT INTO tournaments_db(club_id,nombre,fecha_inicio,hora_inicio,genero_formato,categoria_nivel,tipo,modalidad,cupo_max,precio,estado,contacto_organizador,is_active) SELECT club_id,'Reset canary',fecha_inicio,hora_inicio,genero_formato,categoria_nivel,tipo,modalidad,cupo_max,precio,estado,contacto_organizador,is_active FROM tournaments_db WHERE id=" + str(fixtures['tournamentIds'][0]) + ' RETURNING id;')
    reset = json.loads(client.request('/api/internal/ui-preview/reset', {}))['fixtures']
    assert not set(fixtures['tournamentIds']).intersection(reset['tournamentIds'])
    assert sql('SELECT count(*) FROM tournaments_db WHERE id=' + canary) == '1', 'Reset удалил постороннюю запись'
    assert json.loads(client.request('/api/internal/ui-preview'))['fixtures'] == reset, 'GET изменил seed'
    before = sql('SELECT count(*) FROM tournaments_db')
    sql("UPDATE ui_preview.control SET instance_id='" + str(uuid.uuid4()) + "';")
    try:
        client.request('/api/internal/ui-preview/reset', {})
        raise AssertionError('Reset с неверным маркером выполнен')
    except urllib.error.HTTPError as error:
        assert error.code >= 400
    assert sql('SELECT count(*) FROM tournaments_db') == before
finally:
    sql("UPDATE ui_preview.control SET instance_id='" + state['instance'] + "';")
    if canary:
        sql('DELETE FROM tournaments_db WHERE id=' + canary)
print('Seed/reset: сохранены посторонние записи; повторный GET идемпотентен; неверный маркер запрещает запись')
print('Проверка закрытого контура прошла')
