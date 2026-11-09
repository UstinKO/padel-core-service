#!/usr/bin/env python3
"""Приватный мост только для чтения выключателя конвейера через Unix-сокет."""

import json
import os
from pathlib import Path
import socket
import socketserver
import struct
import urllib.request


SOCKET_PATH = '/run/padel-codex-switch/status.sock'
APP_URL = 'http://127.0.0.1:8080/api/internal/telegram-pipeline/status'
WORKER_UID = 10001


def read_status(url, secret):
    try:
        request = urllib.request.Request(url, headers={'X-Internal-Secret': secret}, method='GET')
        with urllib.request.urlopen(request, timeout=5) as response:
            body = json.loads(response.read(4097))
        return {'paused': body.get('paused') is not False}
    except (OSError, ValueError, AttributeError):
        return {'paused': True}


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(5)
        try:
            _, uid, _ = struct.unpack('3i', self.request.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
            if uid != WORKER_UID or self.rfile.readline(32) != b'status\n':
                return
            result = read_status(APP_URL, os.environ['PIPELINE_SHARED_SECRET'])
            self.wfile.write(json.dumps(result).encode() + b'\n')
        except (OSError, ValueError, KeyError):
            return


class Server(socketserver.UnixStreamServer):
    pass


if __name__ == '__main__':
    path = Path(SOCKET_PATH)
    if path.exists() or path.is_symlink():
        path.unlink()
    with Server(str(path), Handler) as server:
        os.chmod(path, 0o666)
        server.serve_forever()
