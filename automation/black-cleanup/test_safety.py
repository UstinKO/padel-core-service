import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import apply as operation
import prepare


class SafetyTest(unittest.TestCase):
    def test_dependency_order_is_child_first(self):
        schema = {'foreign_keys': [{'child': 'child', 'parent': 'parent'},
                                   {'child': 'parent', 'parent': 'parent'}]}
        self.assertEqual(['child', 'parent'], prepare.deletion_order(schema, {'parent': '', 'child': ''}))

    def test_unknown_cycle_stops(self):
        schema = {'foreign_keys': [{'child': 'a', 'parent': 'b'}, {'child': 'b', 'parent': 'a'}]}
        with self.assertRaises(RuntimeError):
            prepare.deletion_order(schema, {'a': '', 'b': ''})

    def test_identifier_is_not_arbitrary_sql(self):
        with self.assertRaises(ValueError):
            prepare.ident('table; delete from player_padel_db')

    def check_rejected(self, mutation=None, requested='approved', applied=False, outside=False):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            directory = root / 'copy'
            directory.mkdir()
            archive = directory / 'database.dump'
            archive.write_bytes(b'verified-backup')
            data = {'backup': str(archive), 'backup_sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
                    'fingerprint': 'approved', 'restore_verified': True, 'rehearsal_verified': True,
                    'profiles_deleted': 0, 'rating': {}}
            if mutation:
                data.update(mutation)
            manifest = directory / 'prepared.json'
            manifest.write_text(json.dumps(data))
            if applied:
                (directory / 'applied.json').write_text('{}')
            allowed_root = root / 'different' if outside else root
            allowed_root.mkdir(exist_ok=True)
            with patch.object(operation, 'ROOT', allowed_root), patch.object(operation, 'clean') as clean:
                with self.assertRaises(AssertionError):
                    operation.main(str(manifest), requested)
                clean.assert_not_called()

    def test_wrong_approved_plan_stops_before_live_session(self):
        self.check_rejected(requested='different')

    def test_changed_backup_stops_before_live_session(self):
        self.check_rejected({'backup_sha256': 'corrupt'})

    def test_unverified_restore_stops_before_live_session(self):
        self.check_rejected({'restore_verified': False})

    def test_profile_deletion_cannot_be_added(self):
        self.check_rejected({'profiles_deleted': 1})

    def test_repeated_application_stops(self):
        self.check_rejected(applied=True)

    def test_other_manifest_directory_is_rejected(self):
        self.check_rejected(outside=True)


if __name__ == '__main__':
    unittest.main()
