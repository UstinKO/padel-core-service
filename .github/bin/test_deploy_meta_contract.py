"""Проверяет контракт Meta, используемый автономным runner штатного деплоя."""
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'automation/releases'))
from production import valid_meta


class DeployMetaContractTest(unittest.TestCase):
    def test_additional_fields_preserve_backward_compatibility(self):
        original = {"enabled": True, "pixelId": "930260823500064", "testMode": False}
        for config in (original, {**original, "attributionEnabled": True},
                       {**original, "attributionEnabled": False, "futureField": "value"}):
            with self.subTest(config=config):
                self.assertTrue(valid_meta(config, '930260823500064'))

    def test_missing_invalid_or_incorrectly_typed_meta_settings_are_rejected(self):
        original = {"enabled": True, "pixelId": "930260823500064", "testMode": False}
        invalid = [None, [], {}, {**original, "enabled": False}, {**original, "enabled": 1},
                   {**original, "pixelId": "wrong"}, {**original, "testMode": True},
                   {**original, "testMode": 0}, {"enabled": True, "testMode": False}]
        for config in invalid:
            with self.subTest(config=config):
                self.assertFalse(valid_meta(config, '930260823500064'))


if __name__ == "__main__":
    unittest.main()
