"""Проверяет фактическую проверку контракта Meta из workflow деплоя."""
import json
from pathlib import Path
import subprocess
import sys
import textwrap
import unittest


class DeployMetaContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        workflow = Path(__file__).resolve().parents[1] / "workflows" / "deploy.yml"
        lines = workflow.read_text().splitlines()
        start = next(i for i, line in enumerate(lines)
                     if "/api/marketing/config | python3 -c '" in line) + 1
        end = next(i for i in range(start, len(lines)) if lines[i].strip() == "'")
        cls.validation = textwrap.dedent("\n".join(lines[start:end]))

    def run_validation(self, config):
        return subprocess.run([sys.executable, "-c", self.validation], input=json.dumps(config),
                              capture_output=True, text=True)

    def test_additional_fields_preserve_backward_compatibility(self):
        original = {"enabled": True, "pixelId": "930260823500064", "testMode": False}
        for config in (original, {**original, "attributionEnabled": True},
                       {**original, "attributionEnabled": False, "futureField": "value"}):
            with self.subTest(config=config):
                self.assertEqual(self.run_validation(config).returncode, 0)

    def test_missing_invalid_or_incorrectly_typed_meta_settings_are_rejected(self):
        original = {"enabled": True, "pixelId": "930260823500064", "testMode": False}
        invalid = [None, [], {}, {**original, "enabled": False}, {**original, "enabled": 1},
                   {**original, "pixelId": "wrong"}, {**original, "testMode": True},
                   {**original, "testMode": 0}, {"enabled": True, "testMode": False}]
        for config in invalid:
            with self.subTest(config=config):
                self.assertNotEqual(self.run_validation(config).returncode, 0)


if __name__ == "__main__":
    unittest.main()
