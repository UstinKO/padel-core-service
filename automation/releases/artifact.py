"""Проверяет целостность jar, граф React и неизменность Expo без запуска приложения."""
import argparse
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path, PurePosixPath
import re
import tempfile
import zipfile

PREFIX = 'BOOT-INF/classes/static'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def safe_path(value):
    if not isinstance(value, str) or not value or '\\' in value or any(ord(c) < 32 for c in value):
        raise ValueError('Некорректный путь ресурса')
    path = PurePosixPath(value)
    if path.is_absolute() or '..' in path.parts or str(path) != value:
        raise ValueError('Ресурс выходит за пределы выпуска')
    return value


class IndexReferences(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []

    def handle_starttag(self, tag, attributes):
        values = dict(attributes)
        if tag == 'script' and values.get('src'):
            self.paths.append(values['src'])
        if tag == 'link' and values.get('rel') in ('stylesheet', 'modulepreload') and values.get('href'):
            self.paths.append(values['href'])


def inspect(jar, revision, expo_root=None, legacy=False, rehearsal=False):
    if not re.fullmatch('[0-9a-f]{40}', revision):
        raise ValueError('Некорректная ревизия')
    with zipfile.ZipFile(jar) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Повторные записи в jar')
        files = {}
        for name in archive.namelist():
            if not name.endswith('/') and name.startswith((PREFIX + '/web/', PREFIX + '/app/')):
                path = '/' + safe_path(name[len(PREFIX) + 1:])
                files[path] = digest(archive.read(name))
        if '/web/index.html' not in files or '/app/index.html' not in files:
            raise ValueError('В jar отсутствует React или Expo index')
        if not rehearsal and any(path.startswith('/web/assets/release-probe-') for path in files):
            raise ValueError('Артефакт репетиции запрещён для обычного выпуска')
        parser = IndexReferences()
        parser.feed(archive.read(PREFIX + '/web/index.html').decode())
        if not parser.paths or any(path not in files for path in parser.paths):
            raise ValueError('Index ссылается на отсутствующий ресурс')
        manifest_name = PREFIX + '/web/.vite/manifest.json'
        if manifest_name not in archive.namelist():
            if not legacy:
                raise ValueError('В jar отсутствует Vite manifest')
        else:
            graph = json.loads(archive.read(manifest_name))
            if not isinstance(graph, dict) or not any(item.get('isEntry') for item in graph.values()):
                raise ValueError('Пустой граф React')
            for item in graph.values():
                paths = [item['file']] + item.get('css', []) + item.get('assets', [])
                if any('/web/' + safe_path(path) not in files for path in paths):
                    raise ValueError('Отсутствует ресурс графа React')
                if any(key not in graph for key in item.get('imports', []) + item.get('dynamicImports', [])):
                    raise ValueError('Разорван граф динамических импортов')
        if expo_root is not None:
            expected = {'/app/' + path.relative_to(expo_root).as_posix(): digest(path.read_bytes())
                        for path in expo_root.rglob('*') if path.is_file()}
            actual = {name: value for name, value in files.items() if name.startswith('/app/')}
            if not expected or expected != actual:
                raise ValueError('Expo в jar не совпадает с исходной сборкой')
    return dict(format=1, revision=revision, jarSha256=file_digest(jar),
                files=dict(sorted(files.items())), spaRoutes=[], legacy=legacy, rehearsal=rehearsal)


def verify(jar, manifest):
    actual = inspect(jar, manifest['revision'], legacy=manifest.get('legacy', False), rehearsal=manifest.get('rehearsal', False))
    if any(actual[key] != manifest.get(key) for key in actual):
        raise ValueError('Манифест не соответствует артефакту')


def retain(jar, directory):
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o755)
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            prefix = PREFIX + '/web/assets/'
            if name.startswith(prefix) and not name.endswith(('/', '.map')):
                relative = safe_path(name[len(prefix):])
                target = directory / relative
                data = archive.read(name)
                if target.is_symlink() or (target.exists() and target.read_bytes() != data):
                    raise ValueError('Конфликт неизменяемого ресурса')
                target.parent.mkdir(parents=True, exist_ok=True)
                if not target.exists():
                    descriptor, temporary = tempfile.mkstemp(prefix='.asset-', dir=target.parent)
                    try:
                        with os.fdopen(descriptor, 'wb') as stream:
                            os.fchmod(stream.fileno(), 0o444)
                            stream.write(data)
                            stream.flush()
                            os.fsync(stream.fileno())
                        os.replace(temporary, target)
                    finally:
                        if os.path.exists(temporary):
                            os.unlink(temporary)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--expo-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(inspect(args.jar, args.revision, args.expo_root), indent=2))
