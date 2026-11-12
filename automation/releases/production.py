"""Управляет только сервисом приложения в существующем Compose-проекте."""
import argparse
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import zipapp
import artifact
from docker_adapter import DockerAdapter, image_jar, run
from transaction import Releases, atomic, install_signals


def compose_config(root, environment):
    return json.loads(run(['docker', 'compose', '--project-directory', str(root), '--env-file', str(environment),
        '-f', str(root / 'docker-compose.yml'), 'config', '--format', 'json'], cwd=root))


def copy_atomic(source, destination, mode=0o600):
    descriptor, temporary = tempfile.mkstemp(prefix='.release-', dir=destination.parent)
    try:
        with os.fdopen(descriptor, 'wb') as stream:
            os.fchmod(stream.fileno(), mode)
            stream.write(source.read_bytes())
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, destination)
        directory = os.open(destination.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


class Production(DockerAdapter):
    def apply(self, record):
        self.validate(record)
        directory = self.folder(record)
        copy_atomic(directory / 'environment', self.root / '.env')
        copy_atomic(directory / 'app.jar', self.root / 'app.jar', 0o644)
        super().apply(record)

    def publish(self, record):
        directory = self.folder(record)
        version = directory / 'version.txt'
        version.write_text(record['revision'] + '\n')
        copy_atomic(version, self.root / 'version.txt')
        copy_atomic(version, self.root / 'current_version.txt')

    def smoke(self, record):
        super().smoke(record)
        if record['managed']:
            config = json.loads((self.folder(record) / 'compose.json').read_text())
            env = config['services'][self.service]['environment']
            meta = json.loads(self.fetch(record)('/api/marketing/config').body)
            if not env.get('META_CAPI_ACCESS_TOKEN') or not valid_meta(meta, env.get('META_PIXEL_ID')):
                raise ValueError('Не подтверждена действующая конфигурация Meta')
            whatsapp_url = env.get('GUEST_CLAIM_WHATSAPP_URL')
            if whatsapp_url:
                active_env = self.container()['Config']['Env']
                if 'GUEST_CLAIM_WHATSAPP_URL=' + whatsapp_url not in active_env:
                    raise ValueError('Не подтверждена ссылка WhatsApp в контейнере')
                print('Ссылка WhatsApp подтверждена в контейнере')


def valid_meta(config, pixel):
    return (isinstance(config, dict) and config.get('enabled') is True
            and config.get('testMode') is False and bool(pixel) and config.get('pixelId') == pixel)


def initialize(adapter, releases, root, config):
    if releases.read('current'):
        return
    container = adapter.container()
    config['services'][adapter.service]['environment'] = dict(line.split('=', 1) for line in container['Config']['Env'] if '=' in line)
    with tempfile.TemporaryDirectory() as temporary:
        jar = Path(temporary) / 'app.jar'
        image_jar(container['Image'], jar)
        # У исходного работающего выпуска может не быть Vite manifest и release header.
        revision = artifact.file_digest(jar)[:40]
        version = root / 'version.txt'
        if version.exists():
            first = version.read_text().splitlines()[0]
            if len(first) == 40 and all(char in '0123456789abcdef' for char in first):
                revision = first
        manifest = artifact.inspect(jar, revision, legacy=True)
        record = adapter.prepare(jar, manifest, container['Image'], config, (root / '.env').read_bytes(), managed=False)
        adapter.smoke(record)
        atomic(releases.directory / 'current.json', record)


def package(root, jar, revision, manifest, image):
    """Подготавливает только несекретные файлы доставки и автономный runner."""
    target = root / 'target'
    archive = target / ('release-' + revision + '.tar.gz')
    # Тег сохраняется при Docker save/load, даже если на сервере меняется представление ID.
    manifest = dict(manifest, image=image)
    image_tar = target / 'release-image.tar'
    subprocess.run(['docker', 'image', 'save', '-o', str(image_tar), image], check=True)
    encoded = json.dumps(manifest).encode()
    with tarfile.open(archive, 'w:gz') as bundle:
        entry = tarfile.TarInfo('manifest.json')
        entry.size = len(encoded)
        bundle.addfile(entry, io.BytesIO(encoded))
        bundle.add(image_tar, arcname='image.tar')
    image_tar.unlink()
    with tempfile.TemporaryDirectory() as directory:
        directory = Path(directory)
        for name in ['artifact.py', 'docker_adapter.py', 'smoke.py', 'transaction.py', 'production.py']:
            shutil.copyfile(Path(__file__).parent / name, directory / name)
        shutil.copyfile(root / '.github/bin/configure-meta-env.py', directory / 'configure_meta_env.py')
        zipapp.create_archive(directory, target / 'release-runner.pyz', main='production:entrypoint')
    return archive


def release_image_tag(manifest, revision):
    prefix = 'padel-release:' + revision + '-' + manifest['jarSha256'][:16] + '-'
    tag = manifest.get('image')
    if not isinstance(tag, str) or not re.fullmatch(re.escape(prefix) + r'[0-9a-f]{32}', tag):
        raise ValueError('Тег образа не соответствует манифесту выпуска')
    return tag


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['deploy', 'recover', 'rollback'])
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--revision')
    args = parser.parse_args()
    if args.root.is_symlink():
        raise ValueError('Корень выпуска не должен быть symlink')
    root = args.root.resolve()
    if root.is_symlink() or not (root / '.env').is_file() or not (root / 'docker-compose.yml').is_file():
        raise ValueError('Отсутствует существующая конфигурация выпуска')
    install_signals()
    os.umask(0o077)
    config = compose_config(root, root / '.env')
    project = config['name']
    adapter = Production(root, root / 'releases', project, 'padel-app')
    releases = Releases(root / 'release-state', adapter)
    if args.command == 'recover':
        releases.recover()
        print('Восстановление подтверждено')
        return
    if args.command == 'rollback':
        releases.rollback(args.revision)
        print('Предыдущий выпуск восстановлен')
        return
    if not args.bundle or not args.revision:
        raise ValueError('Не задан артефакт выпуска')
    if args.bundle.is_symlink() or args.bundle.resolve().parent != root / 'temp' or args.bundle.name != 'release-' + args.revision + '.tar.gz':
        raise ValueError('Пакет должен находиться в выделенном каталоге доставки')
    # Snapshot и подготовка также сериализованы; переключение использует ту же блокировку.
    with releases.locked():
        if releases.read('pending'):
            releases.restore(releases.read('pending'))
        initialize(adapter, releases, root, config)
        with tempfile.TemporaryDirectory(dir=root / 'releases') as temporary:
            temporary = Path(temporary)
            with tarfile.open(args.bundle) as bundle:
                if sorted(item.name for item in bundle.getmembers()) != ['image.tar', 'manifest.json']:
                    raise ValueError('Неожиданные файлы в пакете выпуска')
                if shutil.disk_usage(root).free < sum(item.size for item in bundle.getmembers()) * 2 + 256 * 1024 * 1024:
                    raise OSError('Недостаточно места для безопасной подготовки выпуска')
                for item in bundle.getmembers():
                    if not item.isfile():
                        raise ValueError('Некорректный тип файла пакета')
                    with bundle.extractfile(item) as source, (temporary / item.name).open('wb') as target:
                        shutil.copyfileobj(source, target, 1024 * 1024)
            manifest = json.loads((temporary / 'manifest.json').read_text())
            if manifest.get('revision') != args.revision or manifest.get('legacy') or manifest.get('rehearsal'):
                raise ValueError('Пакет не соответствует запрошенной ревизии')
            image = release_image_tag(manifest, args.revision)
            run(['docker', 'image', 'load', '-i', str(temporary / 'image.tar')])
            jar = temporary / 'app.jar'
            image_jar(image, jar)
            artifact.verify(jar, manifest)
            candidate_env = temporary / '.env'
            shutil.copyfile(root / '.env', candidate_env)
            from configure_meta_env import configure
            whatsapp_url = os.environ.get('GUEST_CLAIM_WHATSAPP_URL', '')
            configure(candidate_env, os.environ.get('META_CAPI_ACCESS_TOKEN', ''), whatsapp_url)
            candidate_config = compose_config(root, candidate_env)
            if whatsapp_url:
                # --env-file задаёт подстановки Compose, а env_file сервиса может читать прежний .env.
                candidate_config['services'][adapter.service].setdefault('environment', {})['GUEST_CLAIM_WHATSAPP_URL'] = whatsapp_url
            record = adapter.prepare(jar, manifest, image, candidate_config, candidate_env.read_bytes())
        releases._deploy_locked(record)
        # Сохраняются текущий/предыдущий выпуск и исходный baseline. Архив chunks не очищается.
        if not releases.read('pending'):
            try:
                adapter.cleanup([releases.read('current'), releases.read('previous')])
                args.bundle.unlink()
            except (OSError, ValueError, subprocess.CalledProcessError):
                print('Выпуск проверен; очистка старых собственных артефактов не завершена')
    print('Выпуск принят: ' + args.revision)


def entrypoint():
    try:
        main()
    except subprocess.CalledProcessError as error:
        # Выводим только тип операции из известного набора, без argv и stderr.
        command = error.cmd if isinstance(error.cmd, (list, tuple)) else ()
        operation = 'внешняя команда'
        if command and command[0] == 'docker' and len(command) > 1:
            if command[1] == 'compose':
                operation = next((name for name in ('config', 'up', 'stop') if name in command), 'compose')
            elif command[1] in ('create', 'cp', 'inspect', 'ps', 'rm', 'image', 'exec'):
                operation = command[1]
        raise SystemExit(f'Выпуск не принят: ошибка операции {operation}, код {error.returncode}')
    except Exception as error:
        # Содержимое Compose, HTTP и аргументы процессов могут включать секреты.
        raise SystemExit('Выпуск не принят: ' + type(error).__name__)


if __name__ == '__main__':
    entrypoint()
