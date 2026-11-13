import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('playoff_pilot', Path(__file__).with_name('playoff-pilot.py'))
pilot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pilot)


class PilotBoundaryTest(unittest.TestCase):
    def test_wrong_running_jar_does_not_reset_fixtures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'target').mkdir()
            (root / 'target/padel-core-service-0.0.1-SNAPSHOT.jar').write_bytes(b'local-jar')
            client = Mock()
            client.request.return_value = json.dumps(dict(environment='preview', revision='a' * 40))
            with patch.object(pilot.preview, 'ROOT', root), \
                    patch.object(pilot.preview, 'load', return_value=dict(project='own-preview')), \
                    patch.object(pilot.preview, 'compose'), patch.object(pilot.preview, 'Client', return_value=client), \
                    patch.object(pilot.preview, 'compose_environment', return_value={}), \
                    patch.object(pilot.subprocess, 'check_output', return_value='wrong-digest app.jar'), \
                    patch('sys.argv', ['playoff-pilot.py', 'run']):
                with self.assertRaisesRegex(ValueError, 'jar отличается'):
                    pilot.main()
            client.request.assert_called_once_with('/api/internal/ui-preview')

    def test_browser_failure_removes_private_input_and_public_fixture_has_no_passwords(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / 'runtime'
            runtime.mkdir()
            (root / 'target').mkdir()
            (root / 'target/padel-core-service-0.0.1-SNAPSHOT.jar').write_bytes(b'local-jar')
            digest = hashlib.sha256(b'local-jar').hexdigest()
            client = Mock(origin='http://127.0.0.1:18114')
            client.request.side_effect = [json.dumps(dict(environment='preview', revision='a' * 40)),
                                          json.dumps(dict(accounts={}, scenarios=[]))]
            state = dict(project='own-preview', gateway_password='private-gateway', fixture_password='private-fixture')

            def rejected_browser(*args, **kwargs):
                path = runtime / 'pilot-browser-input.json'
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                self.assertEqual(json.loads(path.read_text())['password'], state['fixture_password'])
                raise subprocess.CalledProcessError(1, 'browser')

            with patch.object(pilot.preview, 'ROOT', root), patch.object(pilot.preview, 'RUNTIME', runtime), \
                    patch.object(pilot.preview, 'load', return_value=state), patch.object(pilot.preview, 'compose'), \
                    patch.object(pilot.preview, 'Client', return_value=client), \
                    patch.object(pilot.preview, 'compose_environment', return_value={}), \
                    patch.object(pilot.subprocess, 'check_output', return_value=digest + ' app.jar'), \
                    patch.object(pilot.subprocess, 'run', side_effect=rejected_browser), \
                    patch('sys.argv', ['playoff-pilot.py', 'run']):
                with self.assertRaises(subprocess.CalledProcessError):
                    pilot.main()
            self.assertFalse((runtime / 'pilot-browser-input.json').exists())
            public = (root / 'target/modular-pilot/fixture.json').read_text()
            self.assertNotIn(state['gateway_password'], public)
            self.assertNotIn(state['fixture_password'], public)


if __name__ == '__main__':
    unittest.main()
