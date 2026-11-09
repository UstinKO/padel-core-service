import importlib.util
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("configure-meta-env.py")
SPEC = importlib.util.spec_from_file_location("configure_meta", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
TOKEN = "synthetic_meta_token_0123456789"


class ConfigureMetaTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / ".env"

    def test_preserves_other_settings_replaces_duplicates_and_is_idempotent(self):
        self.path.write_text("# existing\nDB_PASSWORD='unchanged $value'\nMETA_ENABLED=false\n export META_ENABLED = false\nMETA_TEST_EVENT_CODE=old\nOTHER=value")
        MODULE.configure(self.path, TOKEN)
        result = self.path.read_text()
        self.assertIn("DB_PASSWORD='unchanged $value'\n", result)
        self.assertIn("OTHER=value\n", result)
        self.assertEqual(result.count("META_ENABLED="), 1)
        self.assertIn("META_ENABLED=true\n", result)
        self.assertIn("META_TEST_MODE=false\n", result)
        self.assertIn("META_TEST_EVENT_CODE=\n", result)
        self.assertIn("META_CAPI_ACCESS_TOKEN='" + TOKEN + "'\n", result)
        MODULE.configure(self.path, TOKEN)
        self.assertEqual(self.path.read_text(), result)
        self.assertEqual(stat.S_IMODE(self.path.stat().st_mode) & 0o077, 0)

    def test_invalid_tokens_do_not_change_file_or_disclose_input(self):
        for token in ("", "short", TOKEN + "\nEVIL=yes", TOKEN + "'", TOKEN + "${ENV}"):
            with self.subTest(token_length=len(token)):
                self.path.write_text("DB=value\n")
                result = subprocess.run([sys.executable, str(SCRIPT), str(self.path)], env={**os.environ, "META_CAPI_ACCESS_TOKEN": token}, capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.path.read_text(), "DB=value\n")
                self.assertNotIn(TOKEN, result.stdout + result.stderr)

    def test_success_does_not_print_secret(self):
        self.path.write_text("DB=value\n")
        result = subprocess.run([sys.executable, str(SCRIPT), str(self.path)], env={**os.environ, "META_CAPI_ACCESS_TOKEN": TOKEN}, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout + result.stderr, "")

    def test_missing_file_and_symlink_are_rejected(self):
        with self.assertRaises(ValueError):
            MODULE.configure(self.path, TOKEN)
        target = self.path.with_name("target")
        target.write_text("DB=value\n")
        self.path.symlink_to(target)
        with self.assertRaises(ValueError):
            MODULE.configure(self.path, TOKEN)
        self.assertEqual(target.read_text(), "DB=value\n")


if __name__ == "__main__":
    unittest.main()
