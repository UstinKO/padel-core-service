#!/usr/bin/env python3
"""Уведомляет о недоступном контейнере relay; вызывается таймером systemd."""

import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request


def inspect_worker():
    result = subprocess.run(
        ['docker', 'inspect', '--format', '{{.State.Running}} {{.RestartCount}}',
         'codex-worker-worker-1'], capture_output=True, text=True, timeout=15,
    )
    if result.returncode:
        return False, 0
    running, restarts = result.stdout.strip().split()
    switch = subprocess.run(['systemctl', 'is-active', '--quiet', 'padel-codex-switch.service'],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
    return running == 'true' and switch.returncode == 0, int(restarts)


def load_state(path):
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError):
        return {'misses': 0, 'notified': False, 'samples': []}


def save_state(path, state):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(state))
    os.chmod(tmp, 0o600)
    tmp.replace(path)


def notify(env):
    text = ('🔴 Codex relay недоступен или многократно перезапускается. '
            'Проверьте контейнер codex-worker-worker-1 и его журнал.')
    body = json.dumps({'chat_id': int(env['ALERT_CHAT_ID']),
                       'message_thread_id': int(env['ALERT_THREAD_ID']),
                       'text': text}).encode()
    request = urllib.request.Request('https://api.telegram.org/bot' + env['TELEGRAM_BOT_TOKEN'] + '/sendMessage',
                                     data=body, headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(request, timeout=15) as response:
        if not json.load(response).get('ok'):
            raise ValueError('Telegram did not accept alert')


def check(path, env, now=time.time, probe=inspect_worker, send=notify):
    state = load_state(path)
    try:
        running, restarts = probe()
    except (OSError, ValueError, subprocess.SubprocessError):
        running, restarts = False, 0
    current = now()
    samples = [s for s in state.get('samples', []) if current - s[0] <= 300]
    samples.append([current, restarts])
    burst = len(samples) > 1 and restarts - samples[0][1] >= 5
    state.update(samples=samples, misses=0 if running else state.get('misses', 0) + 1)
    failed = state['misses'] >= 2 or burst
    if failed and not state.get('notified'):
        try:
            send(env)
            state['notified'] = True
        except (OSError, ValueError, KeyError):
            print('Не удалось отправить аварийное уведомление; повтор на следующей проверке')
    elif running and not burst:
        state['notified'] = False
    save_state(path, state)
    return failed


if __name__ == '__main__':
    check(Path('/var/lib/padel-codex-monitor/state.json'), os.environ)
