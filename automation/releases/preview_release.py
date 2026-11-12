"""Применяет тот же механизм выпуска только к собственному preview-проекту."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ui-preview'))
import preview
import artifact
from docker_adapter import DockerAdapter, build, image_jar, run
import smoke
from transaction import Releases, atomic, install_signals
from production import copy_atomic


class PreviewAdapter(DockerAdapter):
    def publish(self, record):
        directory = self.folder(record)
        # Этот файл содержит только путь runtime, порт и ревизию; паролей в нём нет.
        copy_atomic(directory / 'environment', preview.RUNTIME / 'compose.env')


def open_environment():
    state = preview.load()
    client = preview.Client(state)
    adapter = PreviewAdapter(preview.ROOT, preview.RUNTIME / 'releases', state['project'], 'app',
        smoke.Http(client.origin, client.authorization))
    releases = Releases(preview.RUNTIME / 'release-state', adapter)
    config = json.loads(run(preview.compose_arguments(state, 'config', '--format', 'json'),
                            env=preview.compose_environment(state)))
    return state, adapter, releases, config


def bootstrap(adapter, releases, config):
    with releases.locked():
        if releases.read('pending'):
            releases.restore(releases.read('pending'))
        if releases.read('current'):
            return
        container = adapter.container()
        config['services']['app']['environment'] = dict(value.split('=', 1) for value in container['Config']['Env'] if '=' in value)
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / 'app.jar'
            image_jar(container['Image'], jar)
            revision = preview.compose_environment(preview.load())['PREVIEW_REVISION']
            manifest = artifact.inspect(jar, revision, legacy=True)
            record = adapter.prepare(jar, manifest, container['Image'], config,
                                     (preview.RUNTIME / 'compose.env').read_bytes(), managed=False)
            adapter.smoke(record)
            atomic(releases.directory / 'current.json', record)


def candidate(adapter, config, jar, revision, rehearsal=False):
    manifest = artifact.inspect(jar, revision, preview.ROOT / 'src/main/resources/static/app', rehearsal=rehearsal)
    image = build(jar, manifest, preview.ROOT / 'automation/releases/Dockerfile',
                  preview.ROOT / 'target/release-images' / manifest['jarSha256'])
    environment = preview.compose_environment(preview.load())
    content = 'PREVIEW_RUNTIME_DIR=' + str(preview.RUNTIME) + '\nPREVIEW_PORT=' + environment['PREVIEW_PORT']
    content += '\nPREVIEW_REVISION=' + revision + '\n'
    return adapter.prepare(jar, manifest, image, config, content.encode())


def command(action):
    install_signals()
    state, adapter, releases, config = open_environment()
    if action == 'up':
        with releases.locked():
            journal = releases.read('pending')
            record = journal['previous'] if journal else releases.read('current')
            if record is None:
                raise ValueError('Отсутствует сохранённый выпуск для запуска')
            adapter.validate(record)
            preview.compose(state, 'up', '-d', '--no-deps', '--no-recreate', '--no-build', 'preview-db', 'preview-mail')
            adapter.apply(record)
            preview.compose(state, 'up', '-d', '--no-deps', '--no-recreate', '--no-build', 'gateway')
            if journal:
                releases.restore(journal)
            else:
                adapter.smoke(record)
                adapter.publish(record)
        print('Preview: сохранённый выпуск запущен и проверен')
        return
    bootstrap(adapter, releases, config)
    if action == 'release':
        revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=preview.ROOT, text=True).strip()
        record = candidate(adapter, config, preview.ROOT / 'target/padel-core-service-0.0.1-SNAPSHOT.jar', revision)
        releases.deploy(record)
        print('Preview: выпуск принят ' + revision)
    elif action == 'rollback':
        releases.rollback()
        print('Preview: предыдущий выпуск восстановлен')
    else:
        releases.recover()
        print('Preview: восстановление завершено')
