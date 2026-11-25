import tempfile
import unittest
from pathlib import Path

import pilot_manual


class PilotManualTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / 'session.json'

    def test_block_reset_until_explicit_end(self):
        pilot_manual.ensure_idle(self.path)
        pilot_manual.begin(self.path, 'own-preview', 'jar')
        pilot_manual.resume(self.path, 'own-preview', 'jar')
        with self.assertRaises(ValueError):
            pilot_manual.ensure_idle(self.path)
        with self.assertRaises(FileExistsError):
            pilot_manual.begin(self.path, 'own-preview', 'jar')
        pilot_manual.end(self.path, 'own-preview')
        pilot_manual.ensure_idle(self.path)

    def test_reject_other_project_and_artifact(self):
        pilot_manual.begin(self.path, 'own-preview', 'jar')
        for project, checksum in [('foreign', 'jar'), ('own-preview', 'other')]:
            with self.assertRaises(ValueError):
                pilot_manual.resume(self.path, project, checksum)
        with self.assertRaises(ValueError):
            pilot_manual.end(self.path, 'foreign')
        self.assertTrue(self.path.exists())
        self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)
