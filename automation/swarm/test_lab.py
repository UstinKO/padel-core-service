import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("swarm_lab", Path(__file__).with_name("lab.py"))
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)


class OwnershipTest(unittest.TestCase):
    def test_stack_has_no_external_broker_and_rest_is_private(self):
        data = json.loads((lab.DIRECTORY / "stack.json").read_text(encoding="utf-8"))
        self.assertNotIn("rabbitmq", data["services"])
        self.assertNotIn("rabbitmq", data["volumes"])
        self.assertNotIn("rabbit_password", data["secrets"])
        self.assertTrue(data["networks"]["events"]["internal"])
        for name, service in data["services"].items():
            self.assertEqual(service["deploy"]["restart_policy"]["condition"], "any")
            if name != "gateway":
                self.assertFalse(service.get("ports"), name)
            if name != "padel-app":
                self.assertNotIn("events", service["networks"], name)
        secret = next(item for item in data["services"]["padel-app"]["secrets"]
                      if isinstance(item, dict) and item["source"] == "rest_peer_key")
        self.assertEqual(secret["mode"], 0o400)
        self.assertEqual(secret["uid"], "10001")

    def test_ready_rejects_old_stack_or_unfinished_rollout(self):
        data = json.loads((lab.DIRECTORY / "stack.json").read_text(encoding="utf-8"))
        rows = "\n".join(lab.STACK + "_" + name + " 1/1" for name in data["services"])
        for state in ["updating", "paused", "rollback_started", "rollback_paused"]:
            with self.subTest(state=state), patch.object(lab, "engine", side_effect=[
                    rows, "service-id", json.dumps([{"UpdateStatus": {"State": state}}])]):
                self.assertFalse(lab.ready())
        with patch.object(lab, "engine", return_value=rows + "\n" + lab.STACK + "_rabbitmq 1/1"):
            self.assertFalse(lab.ready())

    def test_legacy_runtime_adds_rest_secret_without_changing_existing_key(self):
        with tempfile.TemporaryDirectory() as temporary:
            with patch.object(lab, "RUNTIME", Path(temporary)), patch.object(lab, "state", return_value={}):
                lab.prepare_rest_secret()
                path = Path(temporary) / "rest_peer_key"
                first = path.read_bytes()
                self.assertGreaterEqual(len(first), 32)
                lab.prepare_rest_secret()
                self.assertEqual(first, path.read_bytes())

    def test_foreign_engine_is_rejected(self):
        with patch.object(lab, "state", return_value={"instance": "our-instance"}), patch.object(
                lab, "host", return_value=json.dumps([{"Config": {"Labels": {lab.LABEL: "someone-else"}}}])):
            with self.assertRaises(ValueError):
                lab.owned_engine()

    def test_rotated_secret_gets_new_version_without_changing_other_names(self):
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            template = json.loads((lab.DIRECTORY / "stack.json").read_text(encoding="utf-8"))
            for name in template["secrets"]:
                (runtime / name).write_text("first", encoding="utf-8")
            images = json.loads((lab.DIRECTORY / "images.lock.json").read_text(encoding="utf-8"))
            with patch.object(lab, "RUNTIME", runtime), patch.object(lab, "state", return_value={"instance": "fixed"}):
                before = lab.render(images, "sha256:app", "a" * 40)
                (runtime / "rest_peer_key").write_text("rotated", encoding="utf-8")
                after = lab.render(images, "sha256:app", "a" * 40)
                portable = lab.render(images, "sha256:app", "a" * 40, local_files=True)
            self.assertNotEqual(before["secrets"]["rest_peer_key"]["name"],
                                after["secrets"]["rest_peer_key"]["name"])
            self.assertEqual(before["secrets"]["jwt_secret"]["name"], after["secrets"]["jwt_secret"]["name"])
            self.assertEqual(before["services"]["padel-app"]["deploy"]["replicas"], 1)
            for kind in ["secrets", "configs"]:
                for name, item in portable[kind].items():
                    self.assertTrue(Path(item["file"]).is_absolute(), name)
                    self.assertTrue(Path(item["file"]).is_file(), name)
            self.assertEqual([port["published"] for port in portable["services"]["gateway"]["ports"]],
                             [18080, 18443])


if __name__ == "__main__":
    unittest.main()
