"""Локальная блокировка автоматических сбросов на время ручной приёмки."""
import json
import os


def ensure_idle(path):
    if path.exists():
        raise ValueError('Идёт ручная приёмка: используйте resume-manual или явно end-manual перед сбросом')


def begin(path, project, checksum):
    # O_EXCL не позволяет двум CLI одновременно начать ручную сессию.
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        json.dump(dict(project=project, jarSha256=checksum), output)


def resume(path, project, checksum):
    saved = json.loads(path.read_text())
    if saved != dict(project=project, jarSha256=checksum):
        raise ValueError('Ручной стенд относится к другому проекту или артефакту')


def end(path, project):
    saved = json.loads(path.read_text())
    if saved['project'] != project:
        raise ValueError('Нельзя завершить ручную сессию другого проекта')
    path.unlink()
