"""Фиксирует только проверенный выпуск и восстанавливает предыдущий после сбоя."""
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import signal
import tempfile


def atomic(path, value):
    descriptor, name = tempfile.mkstemp(prefix='.release-', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'w') as stream:
            os.fchmod(stream.fileno(), 0o600)
            json.dump(value, stream)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(name):
            os.unlink(name)


class RollbackFailure(RuntimeError):
    pass


class Releases:
    def __init__(self, directory, adapter):
        self.directory = Path(directory)
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.adapter = adapter

    def read(self, name):
        path = self.directory / (name + '.json')
        return json.loads(path.read_text()) if path.exists() else None

    @contextmanager
    def locked(self):
        with (self.directory / 'lock').open('a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise RuntimeError('Другой выпуск уже выполняется') from None
            yield

    def restore(self, journal):
        previous = journal['previous']
        if previous is None:
            try:
                self.adapter.stop(journal['candidate'])
            except BaseException as error:
                raise RollbackFailure('Первый выпуск не остановлен; журнал сохранён') from error
            (self.directory / 'pending.json').unlink()
            print('Первый выпуск не принят; предыдущего выпуска для отката нет', flush=True)
            raise RuntimeError('Первый выпуск не принят; предыдущего выпуска нет')
        try:
            self.adapter.apply(previous)
            self.adapter.smoke(previous)
            self.adapter.publish(previous)
            atomic(self.directory / 'current.json', previous)
            if journal.get('prior') is None:
                (self.directory / 'previous.json').unlink(missing_ok=True)
            else:
                atomic(self.directory / 'previous.json', journal['prior'])
        except BaseException as error:
            print('Восстановление не подтверждено; журнал сохранён для recover', flush=True)
            raise RollbackFailure('Восстановление не подтверждено; журнал сохранён') from error
        (self.directory / 'pending.json').unlink()
        print('Предыдущий полный выпуск восстановлен и проверен', flush=True)

    def recover(self):
        with self.locked():
            journal = self.read('pending')
            if journal:
                self.restore(journal)

    def deploy(self, candidate):
        with self.locked():
            self._deploy_locked(candidate)

    def _deploy_locked(self, candidate):
        journal = self.read('pending')
        if journal:
            self.restore(journal)
        self.adapter.validate(candidate)
        journal = dict(previous=self.read('current'), prior=self.read('previous'), candidate=candidate)
        atomic(self.directory / 'pending.json', journal)
        try:
            self.adapter.apply(candidate)
            self.adapter.smoke(candidate)
            self.adapter.publish(candidate)
            if journal['previous'] is not None:
                atomic(self.directory / 'previous.json', journal['previous'])
            atomic(self.directory / 'current.json', candidate)
            (self.directory / 'pending.json').unlink()
        except BaseException:
            self.restore(journal)
            raise

    def rollback(self, expected_revision=None):
        with self.locked():
            current = self.read('current')
            if expected_revision is not None and (not isinstance(current, dict) or current.get('revision') != expected_revision):
                raise RuntimeError('Текущий выпуск не совпадает с ожидаемым; откат запрещён')
            previous = self.read('previous')
            if previous is None:
                raise RuntimeError('Предыдущего подтверждённого выпуска нет')
            self._deploy_locked(previous)


def interrupted(_number, _frame):
    raise InterruptedError('Выпуск прерван')


def install_signals():
    for number in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(number, interrupted)
