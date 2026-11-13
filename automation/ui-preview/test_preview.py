import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import call, patch
import urllib.request
import uuid
import preview


class RuntimeBoundaryTest(unittest.TestCase):
    def test_managed_build_preserves_baseline_image_and_runtime_revision(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / 'release-state').mkdir()
            (path / 'release-state/current.json').write_text('{}')
            with patch.object(preview, 'RUNTIME', path), patch.object(preview, 'load', return_value={}), \
                    patch('sys.argv', ['preview.py', 'build']), patch.object(preview.subprocess, 'run') as build, \
                    patch.object(preview, 'compose') as compose, patch.object(preview, 'write_environment') as environment:
                preview.main()
                build.assert_called_once()
                compose.assert_not_called()
                environment.assert_not_called()

    def test_host_environment_cannot_replace_runtime(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / 'compose.env').write_text('PREVIEW_RUNTIME_DIR=' + str(path)
                + '\nPREVIEW_PORT=18114\nPREVIEW_REVISION=' + 'a' * 40 + '\n')
            with patch.object(preview, 'RUNTIME', path), patch.dict(preview.os.environ, {'PREVIEW_RUNTIME_DIR': '/other', 'PREVIEW_PORT': '5432'}):
                environment = preview.compose_environment({'port': 18114})
                self.assertEqual(environment['PREVIEW_RUNTIME_DIR'], str(path))
                self.assertEqual(environment['PREVIEW_PORT'], '18114')

    def test_existing_runtime_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(preview, 'RUNTIME', Path(directory)):
            path = Path(directory) / 'state.json'
            path.write_text('existing')
            with self.assertRaises(FileExistsError):
                preview.initialize(18114)
            self.assertEqual(path.read_text(), 'existing')

    def test_project_tampering_and_marker_mismatch_prevent_any_compose_call(self):
        for change in ['project', 'marker']:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                path = Path(directory)
                marker = str(uuid.uuid4())
                state = dict(instance=marker, project='padel-ui-v2-' + marker.replace('-', '')[:12], port=18114)
                (path / 'instance_id').write_text(marker if change != 'marker' else str(uuid.uuid4()))
                if change == 'project':
                    state['project'] = 'other-project'
                (path / 'state.json').write_text(json.dumps(state))
                with patch.object(preview, 'RUNTIME', path), patch.object(preview.subprocess, 'run') as run:
                    with self.assertRaises(ValueError):
                        preview.load()
                    run.assert_not_called()

    def test_readiness_retries_connection_reset_during_startup(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(preview, 'RUNTIME', Path(directory)),
            patch.object(preview, 'load', return_value={}),
            patch.object(preview, 'compose'),
            patch.object(preview, 'Client') as client,
            patch.object(preview.time, 'sleep') as sleep,
            patch('sys.argv', ['preview.py', 'up']),
            patch('builtins.print'),
        ):
            request = client.return_value.request
            request.side_effect = [ConnectionResetError(), b'{"status":"UP"}']

            preview.main()

            self.assertEqual(request.call_args_list, [call('/actuator/health/readiness')] * 2)
            sleep.assert_called_once_with(1)

    def test_redirect_cannot_leak_gateway_credentials_to_another_origin(self):
        handler = preview.LocalRedirects()
        request = urllib.request.Request('http://127.0.0.1:18114/login', headers={'Authorization': 'Basic private'})
        for url in ['https://example.test/login', 'http://127.0.0.1:18115/login']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                handler.redirect_request(request, None, 302, '', {}, url)
        redirected = handler.redirect_request(request, None, 302, '', {}, 'http://127.0.0.1:18114/admin')
        self.assertEqual(redirected.full_url, 'http://127.0.0.1:18114/admin')


if __name__ == '__main__':
    unittest.main()
