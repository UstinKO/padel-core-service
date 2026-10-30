import io
import json
import os
from pathlib import Path
import socket
import tempfile
import threading
import unittest
from unittest.mock import patch

import switch_proxy


class SwitchProxyTest(unittest.TestCase):
    def test_upstream_must_return_explicit_false(self):
        for body, expected in [(b'{"paused":false}', False), (b'{"paused":true}', True),
                               (b'{"paused":"false"}', True), (b'{}', True), (b'bad', True)]:
            with self.subTest(body=body), patch('switch_proxy.urllib.request.urlopen', return_value=io.BytesIO(body)):
                self.assertEqual(switch_proxy.read_status('http://localhost/', 'secret')['paused'], expected)
        with patch('switch_proxy.urllib.request.urlopen', side_effect=OSError()):
            self.assertTrue(switch_proxy.read_status('http://localhost/', 'secret')['paused'])

    @unittest.skipUnless(hasattr(socket, 'SO_PEERCRED'), 'SO_PEERCRED is Linux-specific')
    def test_only_authorized_uid_and_fixed_command_receive_status(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp) / 'switch.sock')
            with switch_proxy.Server(path, switch_proxy.Handler) as server, \
                 patch.dict(os.environ, {'PIPELINE_SHARED_SECRET': 'secret'}), \
                 patch('switch_proxy.read_status', return_value={'paused': False}) as read:
                for uid, command, expected in [(os.getuid(), b'status\n', b'{"paused": false}\n'),
                                               (os.getuid() + 1, b'status\n', b''),
                                               (os.getuid(), b'other\n', b'')]:
                    with self.subTest(uid=uid, command=command), patch('switch_proxy.WORKER_UID', uid):
                        thread = threading.Thread(target=server.handle_request)
                        thread.start()
                        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
                            client.settimeout(3)
                            client.connect(path)
                            client.sendall(command)
                            try:
                                received = client.recv(128)
                            except ConnectionResetError:
                                received = b''
                            self.assertEqual(received, expected)
                        thread.join(timeout=3)
                        self.assertFalse(thread.is_alive())
                self.assertEqual(read.call_count, 1)


if __name__ == '__main__':
    unittest.main()
