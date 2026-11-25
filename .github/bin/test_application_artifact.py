import importlib.util
import io
from pathlib import Path
import tempfile
import struct
import unittest
import zipfile


specification = importlib.util.spec_from_file_location(
    'application_artifact', Path(__file__).with_name('check-application-artifact.py'))
artifact = importlib.util.module_from_spec(specification)
specification.loader.exec_module(artifact)


class ApplicationArtifactTest(unittest.TestCase):
    def archive(self, directory, *, duplicate=False, version=61, dependency=b'', missing=False, missing_class=None):
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, 'w') as library:
            for name in artifact.REQUIRED:
                if name == missing_class:
                    continue
                text = dependency or b'java/lang/Object'
                pool = b'\x01' + struct.pack('>H', len(text)) + text + b'\x07\x00\x01'
                library.writestr(name, b'\xca\xfe\xba\xbe\x00\x00' + version.to_bytes(2, 'big')
                                 + b'\x00\x03' + pool)
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
                    artifact.check_archive(self.archive(directory, dependency=dependency + b'Forbidden'))

    def test_each_public_engine_type_is_allowed_exactly(self):
        for name in artifact.ALLOWED_ENGINES:
            for reference in [name, '[L' + name + ';', 'Ljava/util/List<L' + name + ';>;']:
                with self.subTest(reference=reference), tempfile.TemporaryDirectory() as directory:
                    artifact.check_archive(self.archive(directory, dependency=reference.encode()))

    def test_neighbors_suffix_private_nested_and_generic_array_backdoors_are_rejected(self):
        names = [artifact.ENGINE_PREFIX + 'RoundRobinRules',
                 artifact.ENGINE_PREFIX + 'RoundRobinPointsStandingsEngineBackdoor',
                 artifact.ENGINE_PREFIX + 'RoundRobinSetStandingsEngine$Meetings',
                 'com/padle/core/padelcoreservice/model/americano/AmericanoTeam',
                 'com/padle/core/padelcoreservice/dto/americano/AmericanoTeamDto']
        for name in names:
            for reference in [name, '[L' + name + ';', 'Ljava/util/List<L' + name + ';>;']:
                with self.subTest(reference=reference), tempfile.TemporaryDirectory() as directory:
                    with self.assertRaisesRegex(ValueError, 'запрещённого слоя'):
                        artifact.check_archive(self.archive(directory, dependency=reference.encode()))

    def test_each_query_port_and_read_model_is_required(self):
        for name in artifact.REQUIRED:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(ValueError, 'отсутствуют'):
                    artifact.check_archive(self.archive(directory, missing_class=name))

    def test_broken_constant_pool_is_rejected(self):
        for code in [b'', b'\xca\xfe\xba\xbe\x00\x00\x00=',
                     b'\xca\xfe\xba\xbe\x00\x00\x00=\x00\x02\xff']:
            with self.subTest(code=code), self.assertRaises(ValueError):
                artifact.type_references(code)
