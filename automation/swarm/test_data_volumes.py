import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import Mock, patch

import lab
from data_volumes import LABEL, ROLES, VolumeStore, digest, external_volumes, mount_inventory, tar_fingerprint, verify_archive
from rehearse_data import Rehearsal


class VolumeSafetyTest(unittest.TestCase):
    def setUp(self):
        self.mapping = {role: "existing-" + role for role in ROLES}
        self.template = json.loads((lab.DIRECTORY / "stack.json").read_text(encoding="utf-8"))
        self.owner = "a" * 32
        self.engine = Mock()
        self.store = VolumeStore(self.engine, {"postgres": "pinned-image"}, self.owner, "/lab/test")
        self.volume = json.dumps([{"Labels": {LABEL: self.owner}, "Driver": "local", "Options": None}])

    def test_external_names_are_explicit_and_template_is_unchanged(self):
        result = external_volumes(self.template, self.mapping)
        for role, name in self.mapping.items():
            self.assertEqual(result["volumes"][role], {"external": True, "name": name})
            self.assertEqual(self.template["volumes"][role], {})
        self.assertEqual(result["volumes"]["redis"], self.template["volumes"]["redis"])

    def test_native_map_cannot_be_silently_ignored_by_local_deploy(self):
        with patch("sys.argv", ["lab.py", "deploy", "--volume-map", "private.json"]), \
                patch.object(lab, "host") as host, patch("sys.stderr", new_callable=io.StringIO):
            with self.assertRaises(SystemExit):
                lab.main()
            host.assert_not_called()

    def test_invalid_or_ambiguous_mapping_is_rejected(self):
        for mapping in [None, list(ROLES), {}, dict(self.mapping, database=""), dict(self.mapping, database="${DB_VOLUME}"),
                        dict(self.mapping, database="../production"), dict(self.mapping, database=self.mapping["grafana"])]:
            with self.subTest(mapping=mapping), self.assertRaises(ValueError):
                external_volumes(self.template, mapping)

    def test_null_json_map_cannot_fall_back_to_new_empty_volumes(self):
        with tempfile.TemporaryDirectory() as temporary:
            mapping = Path(temporary) / "mapping.json"
            mapping.write_text("null", encoding="utf-8")
            with patch("sys.argv", ["lab.py", "render", "--app-image", "sha256:" + "a" * 64, "--volume-map", str(mapping)]), \
                    patch.object(lab, "prepare_rest_secret") as prepare, patch.object(lab, "run") as run:
                with self.assertRaises(ValueError):
                    lab.main()
                prepare.assert_not_called()
                run.assert_not_called()

    def test_foreign_and_remote_driver_volumes_are_rejected(self):
        for labels, driver, options in [(None, "local", None), ({LABEL: "someone"}, "local", None),
                                       ({LABEL: self.owner}, "nfs", None), ({LABEL: self.owner}, "local", {"type": "nfs"})]:
            self.engine.return_value = json.dumps([{"Labels": labels, "Driver": driver, "Options": options}])
            with self.subTest(driver=driver, labels=labels), self.assertRaises(ValueError):
                self.store.owned("volume")

    def test_active_or_restartable_writer_is_rejected(self):
        for running, policy in [(True, "no"), (False, "always"), (False, "unless-stopped")]:
            container = {"Mounts": [{"Name": "volume", "RW": True}], "State": {"Running": running},
                         "HostConfig": {"RestartPolicy": {"Name": policy}}}
            self.engine.side_effect = [self.volume, "container", json.dumps([container])]
            with self.subTest(running=running, policy=policy), self.assertRaises(ValueError):
                self.store.quiescent("volume")

    def test_pending_swarm_writer_is_rejected_even_without_container(self):
        service = {"Spec": {"Mode": {"Replicated": {"Replicas": 1}}, "TaskTemplate": {
            "ContainerSpec": {"Mounts": [{"Source": "volume"}]}}}}
        self.engine.side_effect = [self.volume, "", "service", json.dumps([service])]
        with self.assertRaises(ValueError):
            self.store.quiescent("volume")

    def test_stopped_nonrestartable_container_does_not_block_handoff(self):
        container = {"Mounts": [{"Name": "volume", "RW": True}], "State": {"Running": False},
                     "HostConfig": {"RestartPolicy": {"Name": "no"}}}
        self.engine.side_effect = [self.volume, "container", json.dumps([container]), ""]
        self.store.quiescent("volume")

    def test_existing_volume_is_never_silently_adopted(self):
        self.engine.return_value = "other\nvolume"
        with self.assertRaises(ValueError):
            self.store.create("volume")
        self.engine.assert_called_once_with("volume", "ls", "--format", "{{.Name}}")

    def test_corrupt_remote_archive_is_rejected_before_writable_mount(self):
        with patch.object(self.store, "helper", return_value="wrong  grafana.tar") as helper, patch.object(self.store, "empty") as empty:
            with self.assertRaises(ValueError):
                self.store.restore("target", "grafana.tar", "expected")
            empty.assert_not_called()
            helper.assert_called_once_with("target", "sha256sum /archive/grafana.tar")

    def test_database_checksum_failure_cannot_start_a_restore_container(self):
        with tempfile.TemporaryDirectory() as temporary:
            rehearsal = Rehearsal({"postgres": "pinned"}, self.owner)
            rehearsal.local = Path(temporary)
            path = rehearsal.local / "backup.dump"
            path.write_bytes(b"backup")
            rehearsal.report["backups"]["backup.dump"] = {"sha256": "invalid"}
            with patch.object(lab, "engine") as engine, self.assertRaises(ValueError):
                rehearsal.restore_database("volume", "backup.dump")
            engine.assert_not_called()

    def test_archive_names_cannot_escape_runtime(self):
        for filename in ["../bad.tar", "/tmp/bad.tar", "x.tar;rm"]:
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                self.store.archive("volume", filename)
        self.engine.assert_not_called()

    def test_inventory_does_not_publish_environment_values(self):
        self.engine.side_effect = [json.dumps([{"Name": "/db", "Image": "sha256:fixed",
            "Config": {"Env": ["PASSWORD=DO_NOT_PUBLISH"]}, "Mounts": [
                {"Type": "volume", "Name": "data", "Destination": "/data", "RW": True}]}]),
            json.dumps([{"RepoDigests": ["postgres@sha256:fixed"]}])]
        result = mount_inventory(self.engine, ["db"])
        self.assertNotIn("DO_NOT_PUBLISH", json.dumps(result))
        self.assertEqual(result[0]["mounts"][0]["Name"], "data")

    def test_lease_collision_stops_before_any_data_operation(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(lab, "RUNTIME", Path(temporary)), \
                patch.object(lab, "owned_engine"), patch.object(lab, "state", return_value={"instance": self.owner}), \
                patch.object(lab, "engine", side_effect=subprocess.CalledProcessError(1, "lease exists")):
            rehearsal = Rehearsal({"postgres": "pinned"}, self.owner)
            with self.assertRaises(subprocess.CalledProcessError):
                rehearsal.run()
            self.assertFalse(rehearsal.local.exists())

    def test_snapshot_keeps_exact_numeric_values_and_json_types(self):
        hashes = []
        for value in ['0.123456789012345678901', '0.123456789012345678902', '"0.123456789012345678901"']:
            rehearsal = Rehearsal({}, self.owner)
            row = '{"table_schema":"public","table_name":"probe","data":[{"value":' + value + '}]}'
            with patch.object(rehearsal, "query", side_effect=[
                    '[{"table_schema":"public","table_name":"probe"}]', row, '[]', *(['metadata'] * 7)]):
                snapshot = rehearsal.snapshot("container")
            hashes.append(snapshot["tables"][0]["sha256"])
            self.assertEqual(snapshot["tables"][0]["rows"], 1)
            self.assertNotIn("data", snapshot["tables"][0])
        self.assertEqual(len(set(hashes)), 3)

    def test_monitoring_checks_historical_samples_and_persisted_log(self):
        for metric, streams, success in [([{"value": [1, "1"]}], [{"values": [["1", "synthetic-volume-retention"]]}], True),
                                          ([], [], False), ([{"value": [1, "1"]}], [], False)]:
            rehearsal = Rehearsal({}, self.owner)
            rehearsal.report["monitoring_probe"] = {"timestamp_ns": 10**12, "dashboard_uid": "known-dashboard"}
            replies = [json.dumps({"dashboard": {"uid": "known-dashboard"}}),
                       json.dumps({"data": {"result": metric}}), json.dumps({"data": {"result": streams}})]
            with patch.object(rehearsal, "grafana_container", return_value="grafana"), \
                    patch.object(rehearsal, "monitoring_ready"), patch.object(rehearsal, "http", side_effect=replies) as http:
                if success:
                    rehearsal.verify_monitoring(True)
                    self.assertIn("time=1000.0", http.call_args_list[1].args[1])
                else:
                    with self.assertRaises(ValueError):
                        rehearsal.verify_monitoring(True)


class ArchiveIntegrityTest(unittest.TestCase):
    def archive(self, path, name="./file", mode=0o640, uid=472, link=None):
        with tarfile.open(path, "w") as archive:
            entry = tarfile.TarInfo(name)
            entry.mode, entry.uid, entry.gid = mode, uid, uid
            entry.size = 4
            if link is not None:
                entry.type, entry.linkname, entry.size = tarfile.SYMTYPE, link, 0
                archive.addfile(entry)
            else:
                archive.addfile(entry, io.BytesIO(b"data"))

    def test_checksum_and_metadata_are_both_checked(self):
        with tempfile.TemporaryDirectory() as temporary:
            a, b = Path(temporary) / "a.tar", Path(temporary) / "b.tar"
            self.archive(a)
            self.archive(b, mode=0o600)
            self.assertNotEqual(tar_fingerprint(a), tar_fingerprint(b))
            self.archive(b, uid=999)
            self.assertNotEqual(tar_fingerprint(a), tar_fingerprint(b))
            self.archive(b)
            self.assertEqual(tar_fingerprint(a), tar_fingerprint(b))
            verify_archive(a, digest(a))
            a.write_bytes(b"corrupted")
            with self.assertRaises(ValueError):
                verify_archive(a, digest(b))

    def test_paths_and_links_outside_volume_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "archive.tar"
            for name, link in [("../escape", None), ("/absolute", None), ("link", "../../escape")]:
                self.archive(path, name=name, link=link)
                with self.subTest(name=name, link=link), self.assertRaises(ValueError):
                    tar_fingerprint(path)


if __name__ == "__main__":
    unittest.main()
