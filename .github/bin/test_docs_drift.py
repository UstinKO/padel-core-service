import importlib.util
import os
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('docs-drift.py').resolve()


class ModularSourceDiscoveryTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        previous = Path.cwd()
        self.addCleanup(os.chdir, previous)
        os.chdir(temporary.name)
        self.legacy = Path('src/main/java/example')
        self.module = Path('padel-tournament-engines/src/main/java/example')
        self.legacy.mkdir(parents=True)
        self.module.mkdir(parents=True)
        Path('docs').mkdir()
        for name in ['database-architecture', 'architecture', 'auth-flow', 'domain-overview']:
            Path(f'docs/{name}.md').write_text('`SampleService`' if name == 'architecture' else '')
        specification = importlib.util.spec_from_file_location('docs_drift_under_test', SCRIPT)
        self.drift = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(self.drift)

    def test_move_from_legacy_to_module_preserves_class_reference(self):
        source = self.legacy / 'SampleService.java'
        source.write_text('package example; class SampleService {}')
        self.assertEqual(self.drift.check_class_references(), [])
        source.rename(self.module / source.name)
        self.assertEqual(self.drift.check_class_references(), [])

    def test_build_output_does_not_hide_missing_production_class(self):
        output = Path('padel-tournament-engines/target/classes')
        output.mkdir(parents=True)
        (output / 'SampleService.java').write_text('package example; class SampleService {}')
        self.assertEqual(self.drift.check_class_references(),
                         ['docs/architecture.md: класса SampleService нет в коде'])


if __name__ == '__main__':
    unittest.main()
