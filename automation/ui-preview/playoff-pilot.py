#!/usr/bin/env python3
"""Исходный и повторный браузерный прогон пилота в собственном preview."""
import argparse
import hashlib
import json
import os
import subprocess
import preview
import pilot_manual


def jwt_smoke(state, password):
    def curl(arguments, content=''):
        result = subprocess.run(preview.compose_arguments(state, 'exec', '-T', 'app', 'curl',
            '--silent', '--show-error', '--write-out', '\n%{http_code}', *arguments),
            cwd=preview.ROOT, env=preview.compose_environment(state), input=content,
            text=True, capture_output=True, check=True)
        body, status = result.stdout.rsplit('\n', 1)
        return int(status), body

    origin = 'http://127.0.0.1:8080'
    code, body = curl(['--header', 'Content-Type: application/json', '--data-binary', '@-',
        origin + '/api/auth/login'], json.dumps(dict(email='preview-player-0@example.test', password=password)))
    if code != 200:
        raise ValueError('JWT login отклонён')
    token = json.loads(body)['accessToken']
    config = 'header = ' + json.dumps('Authorization: Bearer ' + token) + '\n'
    code, body = curl(['--config', '-', origin + '/api/auth/me'], config)
    if code != 200 or json.loads(body).get('email') != 'preview-player-0@example.test':
        raise ValueError('JWT не позволяет прочитать своего игрока без session cookie')
    code, _ = curl(['--config', '-', origin + '/api/players/me/events'], config)
    if code != 200:
        raise ValueError('JWT кабинет мероприятий недоступен')
    code, _ = curl([origin + '/api/auth/me'])
    if code != 401:
        raise ValueError('Запрос без JWT/session не отклонён')
    return dict(login=200, me=200, events=200, anonymousMe=401, transport='inside-own-preview-container')


def pilot_snapshots(client, fixtures):
    result = {}
    for scenario in fixtures['scenarios']:
        live = json.loads(client.request('/api/public/events/' + str(scenario['tournamentId']) + '/live'))
        live['metadata'].pop('generatedAt')
        result[scenario['key']] = live
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['prepare', 'run', 'smoke', 'extended',
        'manual', 'resume-manual', 'end-manual'])
    parser.add_argument('--reset', action='store_true', help='Пересоздать только пять турниров пилота')
    parser.add_argument('--record', action='store_true', help='Сохранить кандидата эталона в target; не менять принятый эталон')
    parser.add_argument('--rehearse', action='store_true', help='Повторить существующую репетицию выпуска/отката и сверить сохранённые турниры')
    args = parser.parse_args()
    if args.command in ('extended', 'resume-manual', 'end-manual') and (args.reset or args.record or args.rehearse):
        parser.error('Эта команда не поддерживает --reset, --record и --rehearse')
    if args.command == 'manual' and (not args.reset or args.record or args.rehearse):
        parser.error('Первый ручной проход: manual --reset; повторное открытие: resume-manual')
    state = preview.load()
    session = preview.RUNTIME / 'pilot-manual-session.json'
    if args.command == 'end-manual':
        pilot_manual.end(session, state['project'])
        print('Блокировка снята; результаты ручных проверок этим не подтверждаются')
        return
    if args.command != 'resume-manual':
        pilot_manual.ensure_idle(session)
    preview.compose(state, 'config', '--quiet')
    client = preview.Client(state)
    client.login(state)
    status = json.loads(client.request('/api/internal/ui-preview'))
    if status.get('environment') != 'preview':
        raise ValueError('Требуется собственный preview')
    jar = preview.ROOT / 'padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar'
    checksum = hashlib.sha256(jar.read_bytes()).hexdigest()
    running = subprocess.check_output(preview.compose_arguments(state, 'exec', '-T', 'app',
        'sha256sum', '/app/app.jar'), cwd=preview.ROOT, env=preview.compose_environment(state), text=True).split()[0]
    if running != checksum:
        raise ValueError('Запущенный jar отличается от проверяемого артефакта')
    if args.command == 'manual':
        pilot_manual.begin(session, state['project'], checksum)
    elif args.command == 'resume-manual':
        pilot_manual.resume(session, state['project'], checksum)
    path = '/api/internal/ui-preview/playoff-pilot'
    fixtures = json.loads(client.request(path + ('/reset' if args.reset or args.command == 'run' else ''), {}))
    output = preview.ROOT / 'target/modular-pilot'
    output.mkdir(parents=True, exist_ok=True)
    public = dict(sourceRevision=status['revision'], jarSha256=checksum, fixtures=fixtures)
    (output / 'fixture.json').write_text(json.dumps(public, indent=2))
    if args.command == 'prepare':
        print('Пилот подготовлен; ID и маршруты для ручной проверки — target/modular-pilot/fixture.json')
        return
    browser_input = preview.RUNTIME / 'pilot-browser-input.json'
    descriptor = os.open(browser_input, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(descriptor, 0o600)
    with os.fdopen(descriptor, 'w') as output_file:
        json.dump(dict(public, origin=client.origin, gatewayPassword=state['gateway_password'],
            password=state['fixture_password'], record=args.record), output_file)
    node = preview.ROOT / 'target/frontend-toolchain/node/node'
    module = preview.ROOT / 'frontend/node_modules/@playwright/test/index.mjs'
    try:
        if args.command in ('manual', 'resume-manual'):
            if args.command == 'manual':
                subprocess.run([str(node), 'tests/browser/modular-pilot/prepare-manual.mjs', str(browser_input)],
                    cwd=preview.ROOT, env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(module)),
                    check=True, timeout=300)
            print('Ручной стенд зафиксирован. Ссылки: target/modular-pilot/manual-plan.md', flush=True)
            subprocess.run([str(node), 'tests/browser/modular-pilot/manual.mjs', str(browser_input)],
                cwd=preview.ROOT, env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(module)), check=True)
            return
        if args.command == 'extended':
            subprocess.run([str(node), 'tests/browser/modular-pilot/run-early.mjs', str(browser_input)], cwd=preview.ROOT,
                env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(module)), check=True, timeout=300)
            if json.loads(client.request('/api/internal/ui-preview')) != status:
                raise ValueError('Прогон изменил общие preview-данные')
            print('Расширенная проверка раннего плей-офф завершена: target/modular-pilot/extended-report.json')
            return
        (preview.ROOT / 'target/ui-preview-qa').mkdir(parents=True, exist_ok=True)
        subprocess.run([str(node), 'tests/browser/ui-preview.browser.mjs', str(browser_input)], cwd=preview.ROOT,
            env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(module)), check=True, timeout=120)
        subprocess.run([str(node), 'tests/browser/modular-pilot/expo-smoke.mjs', str(browser_input)], cwd=preview.ROOT,
            env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(module)), check=True, timeout=120)
        if args.command == 'run':
            subprocess.run([str(node), 'tests/browser/modular-pilot/run.mjs', str(browser_input)], cwd=preview.ROOT,
                env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(module)), check=True, timeout=300)
        result = jwt_smoke(state, state['fixture_password'])
        (output / 'jwt-smoke.json').write_text(json.dumps(result, indent=2))
        if args.rehearse:
            fixtures = json.loads(client.request(path))
            before = pilot_snapshots(client, fixtures)
            subprocess.run(['python3', 'automation/releases/rehearse.py'], cwd=preview.ROOT, check=True, timeout=420,
                env=dict(os.environ, PLAYWRIGHT_MODULE_PATH=str(module), PATH=str(node.parent) + os.pathsep + os.environ.get('PATH', '')))
            client.login(state)
            if pilot_snapshots(client, fixtures) != before:
                raise ValueError('Выпуск/откат изменил сохранённое состояние пилота')
            (output / 'rollback.json').write_text(json.dumps(dict(result='PASS', scenarios=5, statePreserved=True)))
        if args.command == 'run':
            report_path = output / 'report.json'
            report = json.loads(report_path.read_text())
            report.update(artifactVerified=True, commonBrowserSmoke='PASS', jwt=result)
            report_path.write_text(json.dumps(report, indent=2))
    finally:
        browser_input.unlink(missing_ok=True)
    if json.loads(client.request('/api/internal/ui-preview')) != status:
        raise ValueError('Прогон изменил общие preview-данные')
    print('Браузерный прогон завершён; безопасный отчёт — target/modular-pilot/report.json')


if __name__ == '__main__':
    main()
