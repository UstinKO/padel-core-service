"""Один процесс на задачу; результат CLI не заменяет GitHub-гейты конвейера."""

import argparse
import fcntl
import os
from pathlib import Path
import signal
import subprocess
import time

from codex_executor import write_status


def run_process(command, cwd, output, timeout, prompt=None):
    process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE if prompt is not None else subprocess.DEVNULL,
                               stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        process.communicate(input=prompt, timeout=timeout)
    finally:
        # Убиваем только собственную группу, включая дочерние Maven/Node-процессы.
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            pass
        except ProcessLookupError:
            pass
        finally:
            # Лидер может завершиться раньше потомков, игнорирующих SIGTERM.
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()
    if process.returncode:
        raise subprocess.CalledProcessError(process.returncode, command)


def execute(job, repository, timeout, binary):
    job = Path(job).resolve()
    # Второй уровень защиты общей тестовой БД, независимо от карточек и рестартов relay.
    with (job.parent / 'worker.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        return execute_locked(job, repository, timeout, binary)


def execute_locked(job, repository, timeout, binary):
    job = Path(job).resolve()
    deadline = time.time() + timeout
    write_status(job, 'running', deadline=deadline, pid=os.getpid())
    try:
        # Этот экземпляр БД заранее создаётся оператором специально для агента.
        # Не допускаем неявного подключения к дефолтной базе приложения.
        if not os.environ.get('DB_URL', '').endswith('_codex_test'):
            raise ValueError('DB_URL должен указывать на отдельную БД с суффиксом _codex_test')
        workspace = job / 'workspace'
        with (job / 'execution.log').open('ab') as output:
            run_process(['git', 'clone', '--no-local', '--branch', 'master', '--', repository, str(workspace)],
                        job, output, min(timeout, 300))
            if not (workspace / 'AGENTS.md').is_file() or not (workspace / '.agents/workflows/develop-feature.md').is_file():
                raise ValueError('В master ещё нет правил Codex')
            request = (job / 'prompt.txt').read_text()
            prompt = (
                'Ты — доверенный автономный Codex-воркер padel-core-service, режим cloud. '
                'Пользователь поручил полный SDD-конвейер. Прочитай AGENTS.md и '
                '.agents/skills/develop-feature/SKILL.md, выполни протокол '
                '.agents/workflows/develop-feature.md. Восстанавливай контекст из GitHub. '
                'Не отправляй сообщения в Telegram. Не публикуй временные ссылки и секреты. '
                'Для каждого прогона без своей реализации оставь новый pipeline-reply.\n\n'
                + request
            ).encode()
            remaining = max(1, int(deadline - time.time()))
            run_process([binary, 'exec', '--sandbox', 'workspace-write',
                         '-c', 'sandbox_workspace_write.network_access=true', '--json',
                         '--output-last-message', str(job / 'last-message.txt'), '-'],
                        workspace, output, remaining, prompt)
        write_status(job, 'succeeded')
        return 0
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        # Текст исключения может включать URL с токеном; в статус пишем только тип.
        write_status(job, 'failed', reason=type(error).__name__)
        return 1


if __name__ == '__main__':
    def interrupted(signum, frame):
        raise InterruptedError('Worker stopped')

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    parser = argparse.ArgumentParser()
    parser.add_argument('--job', required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--timeout', type=int, default=6000)
    parser.add_argument('--binary', default='codex')
    args = parser.parse_args()
    raise SystemExit(execute(args.job, args.repository, args.timeout, args.binary))
