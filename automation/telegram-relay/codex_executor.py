"""Локальный запуск Codex в отдельном checkout без блокировки Telegram polling."""

import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid


def write_status(job, state, **fields):
    target = Path(job) / 'status.json'
    temporary = target.with_suffix('.tmp')
    temporary.write_text(json.dumps({'state': state, 'updated_at': time.time(), **fields}))
    temporary.replace(target)


class CodexExecutor:
    def __init__(self, spool, repository, timeout=6000, binary='codex'):
        self.spool = Path(spool).expanduser().resolve()
        self.repository = repository
        self.timeout = int(timeout)
        self.binary = binary
        if self.timeout < 60:
            raise ValueError('CODEX_TIMEOUT_SECONDS должен быть не меньше 60')

    def launch(self, text, execution_id=None):
        if not text.strip() or len(text) > 65536:
            return 'error', 'пустая задача или превышен лимит 65536 символов', None
        if not shutil.which(self.binary):
            return 'error', 'Codex CLI не установлен на воркере', None
        if not self.repository:
            return 'error', 'не задан CODEX_REPOSITORY', None
        execution_id = execution_id or uuid.uuid4().hex
        if not re.fullmatch(r'[a-f0-9]{32}', execution_id):
            return 'error', 'некорректный идентификатор исполнения', None
        self.spool.mkdir(parents=True, exist_ok=True, mode=0o700)
        with (self.spool / 'dispatch.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            return self._launch_locked(text, execution_id)

    def _launch_locked(self, text, execution_id):
        job = self.spool / execution_id
        if job.exists():
            # Ключ записан в SQLite до запуска. После сбоя relay принимаем тот же запуск,
            # включая уже завершившийся, а не создаём второй процесс.
            state = self.status(execution_id)
            if state == 'failed':
                return 'error', 'предыдущее исполнение завершилось с ошибкой; нужен явный перезапуск', None
            return 'ok', {'execution_id': execution_id, 'session_url': ''}, None
        # Учитываем процессы всех заданий, даже если GitHub уже перевёл карточку в answered/done.
        for previous in self.spool.iterdir():
            if previous.is_dir() and re.fullmatch(r'[a-f0-9]{32}', previous.name):
                if self.status(previous.name) in ('accepted', 'running'):
                    return 'retry', 'предыдущий процесс Codex ещё работает', 5
        process = None
        try:
            self.spool.mkdir(parents=True, exist_ok=True, mode=0o700)
            job.mkdir(mode=0o700)
            (job / 'prompt.txt').write_text(text)
            write_status(job, 'accepted', deadline=time.time() + self.timeout + 120)
            # Не передаём Telegram-токен и shared secret в среду дочернего агента.
            env = worker_environment()
            with (job / 'worker.log').open('ab') as log:
                process = subprocess.Popen(
                    [sys.executable, str(Path(__file__).with_name('codex_worker.py')),
                     '--job', str(job), '--repository', self.repository,
                     '--timeout', str(self.timeout), '--binary', self.binary],
                    stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                    start_new_session=True, env=env,
                )
                (job / 'launcher.json').write_text(json.dumps({'pid': process.pid}))
        except OSError as error:
            if process is not None:
                # Процесс уже запущен: сбой записи launcher.json не означает отказ запуска.
                # Сам worker запишет running/pid; прежний ключ не допускает второго запуска.
                return 'ok', {'execution_id': job.name, 'session_url': ''}, None
            if job.exists():
                write_status(job, 'failed', reason=type(error).__name__)
            return 'error', f'не удалось запустить воркер ({type(error).__name__})', None
        return 'ok', {'execution_id': job.name, 'session_url': ''}, None

    def status(self, execution_id):
        if not re.fullmatch(r'[a-f0-9]{32}', execution_id):
            return 'failed'
        try:
            data = json.loads((self.spool / execution_id / 'status.json').read_text())
        except (OSError, ValueError):
            return 'failed'
        state = data.get('state', 'failed')
        if state in ('accepted', 'running'):
            pid = data.get('pid')
            if not pid:
                try:
                    pid = json.loads((self.spool / execution_id / 'launcher.json').read_text())['pid']
                except (OSError, ValueError, KeyError):
                    return state if time.time() < data.get('deadline', 0) else 'failed'
            try:
                try:
                    completed, _ = os.waitpid(pid, os.WNOHANG)
                    if completed:
                        return 'failed'
                except ChildProcessError:
                    pass  # После рестарта relay этот worker уже не наш непосредственный потомок.
                os.kill(pid, 0)
            except ProcessLookupError:
                return 'failed'
            except PermissionError:
                return state
            # Deadline контролирует сам worker. Пока он жив, слот не освобождаем:
            # процесс может ещё завершать дочерние Maven/Node после SIGTERM.
        return state


def worker_environment():
    names = {
        'PATH', 'HOME', 'USER', 'LANG', 'LC_ALL', 'TMPDIR', 'CODEX_HOME',
        'XDG_CONFIG_HOME', 'XDG_CACHE_HOME', 'JAVA_HOME', 'PNPM_HOME',
        'PLAYWRIGHT_BROWSERS_PATH', 'SSH_AUTH_SOCK', 'GH_TOKEN', 'GITHUB_TOKEN',
        'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'CI',
    }
    env = {name: value for name, value in os.environ.items() if name in names}
    env['SPRING_PROFILES_ACTIVE'] = 'cloud'
    # Не направлять тестовые логи в реальные сервисы, даже при ошибке сетевой настройки.
    # Даже тестовый appender Logback может обращаться к URL при отключённой метрике.
    env['LOKI_ENABLED'] = 'false'
    env['LOKI_URL'] = 'http://127.0.0.1:1/loki/api/v1/push'
    env['SERVER_ADDRESS'] = '127.0.0.1'
    env['SERVER_PORT'] = '18080'
    env['PYTHONUNBUFFERED'] = '1'
    return env
