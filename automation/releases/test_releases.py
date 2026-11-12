import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import artifact
import smoke
from docker_adapter import DockerAdapter
from production import Production, package, release_image_tag
from transaction import Releases, RollbackFailure, atomic


class ArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.jar = self.root / 'app.jar'
        self.expo = self.root / 'expo'
        self.expo.mkdir()
        (self.expo / 'index.html').write_text('expo')
        self.files = {
            'web/index.html': '<script src="/web/assets/main-12345678.js"></script>',
            'web/assets/main-12345678.js': 'import("./lazy-12345678.js")',
            'web/assets/lazy-12345678.js': 'export const version=1',
            'app/index.html': 'expo',
            'web/.vite/manifest.json': json.dumps({'index.html': {'file': 'assets/main-12345678.js', 'isEntry': True,
                'dynamicImports': ['lazy']}, 'lazy': {'file': 'assets/lazy-12345678.js'}}),
        }

    def write(self):
        with zipfile.ZipFile(self.jar, 'w') as archive:
            for name, body in self.files.items():
                archive.writestr(artifact.PREFIX + '/' + name, body)

    def test_complete_graph_and_expo_are_verified(self):
        self.write()
        manifest = artifact.inspect(self.jar, 'a' * 40, self.expo)
        artifact.verify(self.jar, manifest)
        self.assertEqual(manifest['spaRoutes'], [])

    def test_missing_lazy_chunk_fails_before_deploy(self):
        del self.files['web/assets/lazy-12345678.js']
        self.write()
        with self.assertRaisesRegex(ValueError, 'графа'):
            artifact.inspect(self.jar, 'a' * 40)

    def test_expo_or_jar_change_cannot_match_existing_manifest(self):
        self.write()
        manifest = artifact.inspect(self.jar, 'a' * 40)
        self.files['app/index.html'] = 'changed'
        self.write()
        with self.assertRaises(ValueError):
            artifact.inspect(self.jar, 'a' * 40, self.expo)
        with self.assertRaises(ValueError):
            artifact.verify(self.jar, manifest)

    def test_unsafe_path_is_rejected(self):
        self.files['web/assets/../../private'] = 'private'
        self.write()
        with self.assertRaises(ValueError):
            artifact.inspect(self.jar, 'a' * 40)

    def test_immutable_name_collision_keeps_previous_bytes(self):
        self.write()
        directory = self.root / 'assets'
        artifact.retain(self.jar, directory)
        self.files['web/assets/lazy-12345678.js'] = 'different'
        self.write()
        with self.assertRaises(ValueError):
            artifact.retain(self.jar, directory)
        self.assertEqual((directory / 'lazy-12345678.js').read_text(), 'export const version=1')

    def test_rehearsal_probe_is_rejected_for_regular_release(self):
        self.files['web/assets/release-probe-test.js'] = 'QA'
        self.write()
        with self.assertRaises(ValueError):
            artifact.inspect(self.jar, 'a' * 40)
        artifact.verify(self.jar, artifact.inspect(self.jar, 'a' * 40, rehearsal=True))


class SmokeTest(unittest.TestCase):
    def test_legacy_home_can_change_content_without_becoming_a_react_route(self):
        for section in ('hero', 'home-intro'):
            document = smoke.PublicDocument()
            document.feed('<main class="main-content"><section class="' + section + '"></section></main>')
            self.assertTrue(document.legacy and not document.react)
        document.feed('<div id="root"></div><script src="/web/assets/index.js"></script>')
        self.assertTrue(document.react)

    def test_curl_connection_failure_is_retried_as_not_ready(self):
        import subprocess
        with tempfile.TemporaryDirectory() as root:
            adapter = DockerAdapter(root, Path(root) / 'releases', 'own-project', 'app')
            with patch.object(adapter, 'container', return_value={'Id': 'own-container'}), \
                    patch('docker_adapter.run', side_effect=subprocess.CalledProcessError(7, ['curl'])):
                self.assertEqual(adapter.fetch({})('/actuator/health/readiness').status, 503)

    def test_changed_revision_or_html_in_place_of_js_is_rejected(self):
        revision = 'a' * 40
        manifest = dict(revision=revision, files={'/web/assets/app.js': artifact.digest(b'HTML')})
        for actual_revision, content in [('b' * 40, 'application/javascript'), (revision, 'text/html')]:
            def request(path):
                if path == '/actuator/health/readiness':
                    return smoke.Response(200, {'X-Release-Revision': actual_revision}, b'{}')
                return smoke.Response(200, {'Content-Type': content, 'Cache-Control': 'immutable'}, b'HTML')
            with self.subTest(revision=actual_revision, content=content), self.assertRaises(ValueError):
                smoke.check(manifest, request)


class ProductionBundleTest(unittest.TestCase):
    def test_bundle_keeps_the_image_tag_used_by_docker_save(self):
        revision = 'a' * 40
        manifest = {'revision': revision, 'jarSha256': 'b' * 64}
        image = 'padel-release:' + revision + '-' + 'b' * 16 + '-' + 'c' * 32
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'target').mkdir()
            (root / '.github/bin').mkdir(parents=True)
            (root / '.github/bin/configure-meta-env.py').touch()

            def save(arguments, *, check):
                self.assertEqual(arguments[:3], ['docker', 'image', 'save'])
                self.assertEqual(arguments[-1], image)
                self.assertTrue(check)
                Path(arguments[4]).write_bytes(b'test image archive')

            with patch('production.subprocess.run', side_effect=save):
                bundle_path = package(root, root / 'app.jar', revision, manifest, image)

            with tarfile.open(bundle_path, 'r:gz') as bundle:
                with bundle.extractfile('manifest.json') as metadata:
                    transported_manifest = json.load(metadata)
            self.assertEqual(release_image_tag(transported_manifest, revision), image)
            for wrong in ('sha256:' + 'd' * 64, image.replace('b' * 16, 'd' * 16), 'unrelated:latest'):
                with self.subTest(wrong=wrong), self.assertRaisesRegex(ValueError, 'Тег образа'):
                    release_image_tag(dict(transported_manifest, image=wrong), revision)


class ProductionSnapshotTest(unittest.TestCase):
    def test_apply_restores_saved_env_and_jar_before_container_switch(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            adapter = Production(root, root / 'releases', 'own-project', 'app')
            record = {'id': 'a' * 32, 'revision': 'b' * 40}
            directory = adapter.folder(record)
            directory.mkdir()
            (directory / 'environment').write_bytes(b'KEY=previous$literal\n')
            (directory / 'app.jar').write_bytes(b'previous-jar')
            (root / '.env').write_bytes(b'candidate-env')
            (root / 'app.jar').write_bytes(b'candidate-jar')
            with patch.object(adapter, 'validate'), patch.object(DockerAdapter, 'apply') as switch:
                adapter.apply(record)
            self.assertEqual((root / '.env').read_bytes(), b'KEY=previous$literal\n')
            self.assertEqual((root / 'app.jar').read_bytes(), b'previous-jar')
            self.assertEqual((root / '.env').stat().st_mode & 0o777, 0o600)
            switch.assert_called_once_with(record)
            adapter.publish(record)
            self.assertEqual((root / 'current_version.txt').read_text(), 'b' * 40 + '\n')
            self.assertEqual((root / 'version.txt').read_bytes(), (root / 'current_version.txt').read_bytes())


    def test_smoke_checks_whatsapp_in_the_running_container(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            adapter = Production(root, root / 'releases', 'own-project', 'app')
            record = {'id': 'a' * 32, 'managed': True}
            directory = adapter.folder(record)
            directory.mkdir()
            url = 'https://wa.me/12025550100'
            environment = {'META_CAPI_ACCESS_TOKEN': 'synthetic', 'META_PIXEL_ID': '1',
                           'GUEST_CLAIM_WHATSAPP_URL': url}
            (directory / 'compose.json').write_text(json.dumps({'services': {'app': {'environment': environment}}}))
            response = smoke.Response(200, {}, b'{"enabled":true,"pixelId":"1","testMode":false}')
            with (patch.object(DockerAdapter, 'smoke'), patch.object(adapter, 'fetch', return_value=lambda _: response),
                  patch.object(adapter, 'container') as container, patch('builtins.print')):
                container.return_value = {'Config': {'Env': []}}
                with self.assertRaisesRegex(ValueError, 'ссылка WhatsApp'):
                    adapter.smoke(record)
                container.return_value = {'Config': {'Env': ['GUEST_CLAIM_WHATSAPP_URL=' + url]}}
                adapter.smoke(record)


class Adapter:
    def __init__(self):
        self.active = None
        self.failed = set()
        self.restore_fails = False
        self.interrupt = None

    def validate(self, record):
        if record == 'corrupt':
            raise ValueError('Некорректный артефакт')

    def apply(self, record):
        self.active = record
        if record == self.interrupt:
            self.interrupt = None
            raise KeyboardInterrupt()
        if self.restore_fails and record == 'A':
            raise OSError('Ошибка восстановления')

    def smoke(self, record):
        if record in self.failed:
            raise ValueError('Smoke не прошёл')

    def publish(self, record):
        self.published = record

    def stop(self, record):
        self.active = None


class TransactionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.adapter = Adapter()
        self.releases = Releases(self.root, self.adapter)
        self.releases.deploy('A')

    def test_success_and_explicit_rollback_switch_whole_release(self):
        self.releases.deploy('B')
        self.assertEqual(self.releases.read('current'), 'B')
        self.releases.rollback()
        self.assertEqual(self.adapter.active, 'A')
        self.assertEqual(self.releases.read('current'), 'A')
        self.assertEqual(self.releases.read('previous'), 'B')

    def test_smoke_or_health_failure_restores_previous_without_committing_candidate(self):
        for failure in ['missing-chunk', 'readiness']:
            with self.subTest(failure=failure):
                self.adapter.failed.add(failure)
                with self.assertRaises(ValueError):
                    self.releases.deploy(failure)
                self.assertEqual(self.adapter.active, 'A')
                self.assertEqual(self.releases.read('current'), 'A')
                self.assertIsNone(self.releases.read('pending'))

    def test_corrupt_candidate_never_activates(self):
        with self.assertRaises(ValueError):
            self.releases.deploy('corrupt')
        self.assertEqual(self.adapter.active, 'A')

    def test_interruption_restores_previous(self):
        self.adapter.interrupt = 'B'
        with self.assertRaises(KeyboardInterrupt):
            self.releases.deploy('B')
        self.assertEqual(self.adapter.active, 'A')
        self.assertEqual(self.releases.read('current'), 'A')

    def test_unconfirmed_rollback_keeps_recovery_journal(self):
        self.adapter.failed.add('B')
        self.adapter.restore_fails = True
        with self.assertRaises(RollbackFailure):
            self.releases.deploy('B')
        self.assertIsNotNone(self.releases.read('pending'))
        self.assertEqual(self.releases.read('current'), 'A')

    def test_journal_after_crash_is_recovered_before_next_operation(self):
        atomic(self.root / 'pending.json', dict(previous='A', candidate='B', prior=None))
        self.adapter.active = 'B'
        self.releases.recover()
        self.assertEqual(self.adapter.active, 'A')
        self.assertIsNone(self.releases.read('pending'))

    def test_parallel_switch_is_rejected(self):
        with self.releases.locked(), self.assertRaises(RuntimeError):
            Releases(self.root, self.adapter).deploy('B')

    def test_late_rollback_cannot_replace_a_newer_release(self):
        with patch.object(self.adapter, 'smoke'):
            self.releases.deploy({'revision': 'newer'})
        with self.assertRaises(RuntimeError):
            self.releases.rollback('older')
        self.assertEqual(self.adapter.active, {'revision': 'newer'})

    def test_first_failure_stops_candidate_and_does_not_claim_rollback(self):
        with tempfile.TemporaryDirectory() as root:
            self.adapter.failed.add('first')
            releases = Releases(root, self.adapter)
            with self.assertRaisesRegex(RuntimeError, 'предыдущего выпуска нет'):
                releases.deploy('first')
            self.assertIsNone(self.adapter.active)
            self.assertIsNone(releases.read('current'))


if __name__ == '__main__':
    unittest.main()
