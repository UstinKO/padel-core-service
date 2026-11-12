"""Проверяет реальные ответы выпуска, а не только наличие index в jar."""
from dataclasses import dataclass
import hashlib
from html.parser import HTMLParser
import time
import urllib.error
import urllib.parse
import urllib.request


@dataclass
class Response:
    status: int
    headers: dict
    body: bytes


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None


class Http:
    def __init__(self, origin, authorization=None):
        self.origin = origin.rstrip('/')
        self.authorization = authorization
        self.opener = urllib.request.build_opener(NoRedirects())

    def __call__(self, path):
        headers = {'Authorization': self.authorization} if self.authorization else {}
        request = urllib.request.Request(self.origin + urllib.parse.quote(path, safe='/._-'), headers=headers)
        try:
            with self.opener.open(request, timeout=8) as response:
                return Response(response.status, dict(response.headers.items()), response.read())
        except urllib.error.HTTPError as error:
            return Response(error.code, dict(error.headers.items()), error.read())


def header(response, key):
    return next((value for name, value in response.headers.items() if name.lower() == key.lower()), '')


class PublicDocument(HTMLParser):
    def __init__(self):
        super().__init__()
        self.legacy = False
        self.react = False

    def handle_starttag(self, tag, attributes):
        values = dict(attributes)
        if tag == 'main' and 'main-content' in values.get('class', '').split():
            self.legacy = True
        if values.get('id') == 'root' or (tag == 'script' and values.get('src', '').startswith('/web/')):
            self.react = True


def check(manifest, fetch, managed=True, timeout=120):
    end = time.monotonic() + timeout
    while True:
        try:
            ready = fetch('/actuator/health/readiness')
            if ready.status == 200:
                break
        except (OSError, urllib.error.URLError):
            pass
        if time.monotonic() >= end:
            raise RuntimeError('Readiness выпуска не подтверждён')
        time.sleep(.5)
    if managed and header(ready, 'X-Release-Revision') != manifest['revision']:
        raise ValueError('Backend не соответствует ревизии выпуска')
    for path, checksum in manifest['files'].items():
        if not path.startswith('/web/') and path != '/app/index.html':
            continue
        response = fetch(path)
        if response.status != 200 or hashlib.sha256(response.body).hexdigest() != checksum:
            raise ValueError('Ресурс выпуска недоступен или изменён')
        if path.endswith(('.js', '.css')) and 'text/html' in header(response, 'Content-Type'):
            raise ValueError('Вместо ресурса возвращён HTML')
        caching = header(response, 'Cache-Control')
        if path.startswith('/web/assets/') and 'immutable' not in caching:
            raise ValueError('У hashed-ресурса нет immutable')
        if path == '/web/index.html' and not any(value in caching for value in ('no-cache', 'no-store')):
            raise ValueError('Index не требует перепроверки')
    root = fetch('/')
    if root.status != 200:
        raise ValueError('Публичная главная недоступна: HTTP ' + str(root.status))
    document = PublicDocument()
    document.feed(root.body.decode())
    if not document.legacy or document.react:
        raise ValueError('Публичная главная должна использовать серверный legacy интерфейс')
    missing = fetch('/web/assets/release-does-not-exist.js')
    if missing.status != 404:
        raise ValueError('Отсутствующий chunk не возвращает 404')
