import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile


specification = importlib.util.spec_from_file_location(
    'application_artifact', Path(__file__).with_name('check-application-artifact.py'))
artifact = importlib.util.module_from_spec(specification)
specification.loader.exec_module(artifact)


class ApplicationArtifactTest(unittest.TestCase):
    def archive(self, directory, *, duplicate=False, version=61, dependency=b'', missing=False):
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, 'w') as library:
            for name in artifact.REQUIRED:
                library.writestr(name, b'\xca\xfe\xba\xbe\x00\x00' + version.to_bytes(2, 'big') + dependency)
        path = Path(directory) / 'app.jar'
        with zipfile.ZipFile(path, 'w') as app:
            if not missing:
                app.writestr('BOOT-INF/lib/padel-application-0.0.1-SNAPSHOT.jar', nested.getvalue())
            if duplicate:
                app.writestr('BOOT-INF/classes/' + sorted(artifact.REQUIRED)[0], b'duplicate')
        return path

    def test_library_has_a_single_owner(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact.check_archive(self.archive(directory))

    def test_shadow_copy_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaisesRegex(ValueError, 'Дубликат'):
            artifact.check_archive(self.archive(directory, duplicate=True))

    def test_newer_bytecode_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaisesRegex(ValueError, 'Java 17'):
            artifact.check_archive(self.archive(directory, version=65))

    def test_missing_library_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaisesRegex(ValueError, 'ровно одна'):
            artifact.check_archive(self.archive(directory, missing=True))

    def test_forbidden_layer_references_are_rejected(self):
        for dependency in artifact.FORBIDDEN:
            with self.subTest(dependency=dependency), tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(ValueError, 'запрещённого слоя'):
                    artifact.check_archive(self.archive(directory, dependency=dependency))
