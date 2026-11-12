"""Снимки Compose и неизменяемых образов; значения окружения не выводятся."""
import copy
import json
from pathlib import Path
import re
import shutil
import subprocess
import uuid
import artifact
import smoke
from transaction import atomic


def run(arguments, **kwargs):
    return subprocess.check_output(arguments, stderr=subprocess.PIPE, **kwargs)


def escaped(value):
    if isinstance(value, str):
        return value.replace('$', '$$')
    if isinstance(value, list):
        return [escaped(item) for item in value]
    if isinstance(value, dict):
        return {key: escaped(item) for key, item in value.items()}
    return value


class DockerAdapter:
    def __init__(self, root, store, project, service, fetch=None, timeout=120):
        self.root = Path(root)
        self.store = Path(store)
        self.store.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.project = project
        self.service = service
        self.http = fetch
        self.timeout = timeout

    def folder(self, record):
        if not re.fullmatch('[0-9a-f]{32}', record['id']):
            raise ValueError('Некорректный идентификатор выпуска')
        path = self.store / record['id']
        if path.is_symlink():
            raise ValueError('Каталог выпуска не должен быть symlink')
        return path

    def image(self, reference):
        return json.loads(run(['docker', 'image', 'inspect', reference]))[0]

    def prepare(self, jar, manifest, image, config, environment, managed=True):
        artifact.verify(jar, manifest)
        config = copy.deepcopy(config)
        service = config['services'][self.service]
        actual = service.get('environment', {})
        if actual.get('APP_SPA_ROUTES', '') not in ('', '[]'):
            raise ValueError('Публичное переключение SPA не разрешено')
        reference = self.image(image)['Id']
        if managed:
            labels = self.image(image)['Config'].get('Labels', {})
            if labels.get('com.padel.jar.sha256') != manifest['jarSha256'] or labels.get('org.opencontainers.image.revision') != manifest['revision']:
                raise ValueError('Образ не соответствует манифесту')
        record = dict(format=1, id=uuid.uuid4().hex, revision=manifest['revision'], image=reference, managed=managed)
        directory = self.folder(record)
        directory.mkdir(mode=0o700)
        shutil.copyfile(jar, directory / 'app.jar')
        (directory / 'app.jar').chmod(0o600)
        (directory / 'environment').write_bytes(environment)
        (directory / 'environment').chmod(0o600)
        service.pop('build', None)
        service['image'] = reference
        if managed:
            actual.update(APP_RELEASE_ENABLED='true', APP_RELEASE_REVISION=manifest['revision'],
                          APP_RELEASE_ASSETS_DIRECTORY='/opt/padel-retained-assets')
            if actual.get('SPRING_PROFILES_ACTIVE') == 'preview':
                actual['APP_PREVIEW_REVISION'] = manifest['revision']
            service['environment'] = actual
            volumes = [item for item in service.get('volumes', []) if item.get('target') != '/opt/padel-retained-assets']
            volumes.append(dict(type='bind', source=str(self.store / 'assets'), target='/opt/padel-retained-assets', read_only=True))
            service['volumes'] = volumes
        record['secrets'] = {}
        for name, secret in config.get('secrets', {}).items():
            if not re.fullmatch('[A-Za-z0-9_-]+', name) or 'file' not in secret:
                raise ValueError('Неподдерживаемый источник secret')
            target = directory / ('secret-' + name)
            shutil.copyfile(secret['file'], target)
            target.chmod(0o444)
            record['secrets'][target.name] = artifact.digest(target.read_bytes())
            secret['file'] = str(target)
        artifact.retain(jar, self.store / 'assets')
        atomic(directory / 'manifest.json', manifest)
        record['manifestSha256'] = artifact.digest((directory / 'manifest.json').read_bytes())
        atomic(directory / 'compose.json', escaped(config))
        record['configSha256'] = artifact.digest((directory / 'compose.json').read_bytes())
        record['environmentSha256'] = artifact.digest(environment)
        atomic(directory / 'record.json', record)
        return record

    def validate(self, record):
        if record.get('format') != 1:
            raise ValueError('Неподдерживаемый формат выпуска')
        directory = self.folder(record)
        if json.loads((directory / 'record.json').read_text()) != record:
            raise ValueError('Изменена запись выпуска')
        if artifact.digest((directory / 'compose.json').read_bytes()) != record['configSha256']:
            raise ValueError('Изменена конфигурация выпуска')
        if artifact.digest((directory / 'environment').read_bytes()) != record['environmentSha256']:
            raise ValueError('Изменено окружение выпуска')
        if artifact.digest((directory / 'manifest.json').read_bytes()) != record['manifestSha256']:
            raise ValueError('Изменён манифест выпуска')
        for name, checksum in record['secrets'].items():
            if not re.fullmatch('secret-[A-Za-z0-9_-]+', name) or artifact.digest((directory / name).read_bytes()) != checksum:
                raise ValueError('Изменён secret выпуска')
        artifact.verify(directory / 'app.jar', json.loads((directory / 'manifest.json').read_text()))
        self.image(record['image'])

    def command(self, record, *arguments):
        directory = self.folder(record)
        return ['docker', 'compose', '--project-directory', str(self.root), '--project-name', self.project,
                '--env-file', str(directory / 'environment'), '-f', str(directory / 'compose.json'), *arguments]

    def container(self):
        ids = run(['docker', 'ps', '-q', '--filter', 'label=com.docker.compose.project=' + self.project,
                   '--filter', 'label=com.docker.compose.service=' + self.service]).decode().split()
        if len(ids) != 1:
            raise ValueError('Не найден единственный контейнер приложения')
        return json.loads(run(['docker', 'inspect', ids[0]]))[0]

    def apply(self, record):
        self.validate(record)
        run(self.command(record, 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', self.service))

    def stop(self, record):
        run(self.command(record, 'stop', self.service))

    def fetch(self, record):
        if self.http is not None:
            return self.http
        container = self.container()['Id']
        def request(path):
            try:
                response = run(['docker', 'exec', container, 'curl', '--max-time', '8', '--silent', '--show-error',
                                '--include', 'http://127.0.0.1:8080' + path])
            except subprocess.CalledProcessError:
                return smoke.Response(503, {}, b'')
            head, body = response.split(b'\r\n\r\n', 1)
            lines = head.decode('iso-8859-1').split('\r\n')
            return smoke.Response(int(lines[0].split()[1]), dict(line.split(': ', 1) for line in lines[1:] if ': ' in line), body)
        return request

    def smoke(self, record):
        if self.container()['Image'] != record['image']:
            raise ValueError('Запущен другой образ')
        manifest = json.loads((self.folder(record) / 'manifest.json').read_text())
        smoke.check(manifest, self.fetch(record), record['managed'], self.timeout)

    def publish(self, record):
        pass

    def cleanup(self, protected):
        protected_ids = {item['id'] for item in protected if item}
        protected_images = {item['image'] for item in protected if item}
        records = []
        for directory in self.store.iterdir():
            if not re.fullmatch('[0-9a-f]{32}', directory.name) or directory.is_symlink() or not directory.is_dir():
                continue
            if not (directory / 'record.json').is_file():
                continue
            record = json.loads((directory / 'record.json').read_text())
            if record['id'] != directory.name:
                raise ValueError('Каталог не соответствует записи выпуска')
            records.append((directory, record))
            if not record['managed']:
                protected_images.add(record['image'])
        removable = []
        for directory, record in records:
            if record['id'] in protected_ids or not record['managed']:
                continue
            self.validate(record)
            removable.append((directory, record))
        for directory, record in removable:
            shutil.rmtree(directory)
        for image in {record['image'] for _, record in removable} - protected_images:
            # Docker без --force сам запрещает удалить образ, используемый другими контейнерами.
            try:
                run(['docker', 'image', 'rm', image])
            except subprocess.CalledProcessError:
                pass


def build(jar, manifest, dockerfile, directory):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jar, directory / 'app.jar')
    # Повторная сборка того же jar не должна переназначить tag сохранённого образа.
    tag = 'padel-release:' + manifest['revision'] + '-' + manifest['jarSha256'][:16] + '-' + uuid.uuid4().hex
    subprocess.run(['docker', 'build', '-f', str(Path(dockerfile).resolve()), '--build-arg',
        'RELEASE_REVISION=' + manifest['revision'], '--build-arg', 'RELEASE_JAR_SHA256=' + manifest['jarSha256'],
        '-t', tag, str(directory)], check=True)
    return tag


def image_jar(image, destination):
    container = run(['docker', 'create', image]).decode().strip()
    try:
        run(['docker', 'cp', container + ':/app/app.jar', str(destination)])
    finally:
        run(['docker', 'rm', container])
