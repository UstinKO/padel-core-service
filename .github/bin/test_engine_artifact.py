import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile


specification = importlib.util.spec_from_file_location(
    'engine_artifact', Path(__file__).with_name('check-engine-artifact.py'))
artifact = importlib.util.module_from_spec(specification)
specification.loader.exec_module(artifact)


class EngineArtifactTest(unittest.TestCase):
    def archive(self, directory, *, duplicate=False, missing=False, version=61, dependency=b''):
        library = io.BytesIO()
        with zipfile.ZipFile(library, 'w') as jar:
            jar.writestr(artifact.ENGINE + '.class', b'\xca\xfe\xba\xbe\x00\x00'
                         + version.to_bytes(2, 'big') + dependency)
        path = Path(directory) / 'application.jar'
        with zipfile.ZipFile(path, 'w') as jar:
            if not missing:
                jar.writestr('BOOT-INF/lib/padel-tournament-engines-0.0.1-SNAPSHOT.jar', library.getvalue())
            if duplicate:
                jar.writestr('BOOT-INF/classes/' + artifact.ENGINE + '.class', b'duplicate')
        return path

    def test_library_is_the_only_owner(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact.check_archive(self.archive(directory))

    def test_shadow_copy_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'Дубликат'):
                artifact.check_archive(self.archive(directory, duplicate=True))

    def test_missing_library_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'ровно одна'):
                artifact.check_archive(self.archive(directory, missing=True))

    def test_newer_bytecode_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'Java 17'):
                artifact.check_archive(self.archive(directory, version=65))

    def test_framework_reference_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'Spring или JPA'):
                artifact.check_archive(self.archive(directory, dependency=b'org/springframework/stereotype/Component'))


if __name__ == '__main__':
    unittest.main()
