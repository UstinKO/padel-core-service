"""Регрессии замечаний PR #1286: lease при сбоях и настоящая замена task."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import lab
from rehearse_data import Rehearsal


class LeaseFailureTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.subject = Rehearsal({}, "a" * 32)
        self.subject.local = Path(self.temporary.name)
        (self.subject.local / "compose.json").write_text("{}", encoding="utf-8")

    def test_mkdir_failure_releases_lease_before_any_writer_can_start(self):
        # Каталог существует: mkdir(exist_ok=False) падает, как при реальной ошибке ФС.
        with patch.object(self.subject, "acquire_lease", return_value="lease"), \
                patch.object(self.subject, "release_lease") as release, patch.object(lab, "engine") as engine:
            with self.assertRaises(FileExistsError):
                self.subject.run()
            release.assert_called_once_with("lease")
            engine.assert_not_called()

    def test_stack_error_does_not_skip_compose_and_confirmed_cleanup_unlocks(self):
        events = []
        def stack():
            events.append("stack")
            raise RuntimeError("stack error")
        with patch.object(self.subject, "down_stack", side_effect=stack), \
                patch.object(self.subject, "down_compose", side_effect=lambda: events.append("compose")), \
                patch.object(self.subject, "stop_restore", side_effect=lambda: events.append("restore")), \
                patch.object(self.subject, "wait_quiescent", side_effect=lambda volumes: events.append("confirmed")), \
                patch.object(self.subject, "release_lease", side_effect=lambda lease: events.append("released")):
            with self.assertRaisesRegex(RuntimeError, "ошибки остановки"):
                self.subject.finalize_run("lease", True)
        self.assertEqual(events, ["stack", "compose", "restore", "confirmed", "released"])

    def test_unconfirmed_quiescence_keeps_lease_after_both_stop_attempts(self):
        with patch.object(self.subject, "down_stack", side_effect=RuntimeError("stack failed")) as stack, \
                patch.object(self.subject, "down_compose") as compose, patch.object(self.subject, "stop_restore") as restore, \
                patch.object(self.subject, "wait_quiescent", side_effect=TimeoutError("pending writer")), \
                patch.object(self.subject, "release_lease") as release:
            with self.assertRaisesRegex(RuntimeError, "lease сохранён"):
                self.subject.finalize_run("lease", True)
            stack.assert_called_once()
            compose.assert_called_once()
            restore.assert_called_once()
            release.assert_not_called()

    def test_compose_error_still_attempts_restore_cleanup_and_checks_both_groups(self):
        with patch.object(self.subject, "down_stack"), patch.object(self.subject, "down_compose", side_effect=OSError("compose failed")), \
                patch.object(self.subject, "stop_restore") as restore, patch.object(self.subject, "wait_quiescent") as check:
            errors = self.subject.stop_writers_and_confirm({**self.subject.source, **{
                "target-" + role: name for role, name in self.subject.target.items()}})
            self.assertEqual(len(errors), 1)
            restore.assert_called_once()
            self.assertEqual(len(check.call_args.args[0]), 8)

    def test_cleanup_command_keeps_lease_if_writer_state_is_unknown(self):
        with patch.object(self.subject, "acquire_lease", return_value="lease"), \
                patch.object(lab, "engine", side_effect=["", ""]), \
                patch.object(self.subject, "stop_writers_and_confirm", side_effect=RuntimeError("unknown")), \
                patch.object(self.subject, "release_lease") as release:
            with self.assertRaises(RuntimeError):
                self.subject.cleanup()
            release.assert_not_called()

    def test_cleanup_command_unlocks_after_volume_delete_failure_if_writers_are_confirmed_stopped(self):
        with patch.object(self.subject, "acquire_lease", return_value="lease"), \
                patch.object(lab, "engine", side_effect=["owned-volume", "owned-volume", subprocess.CalledProcessError(1, "remove")]), \
                patch.object(self.subject.store, "owned"), patch.object(self.subject.store, "quiescent"), \
                patch.object(self.subject, "stop_writers_and_confirm", return_value=[]), \
                patch.object(self.subject, "release_lease") as release:
            with self.assertRaises(subprocess.CalledProcessError):
                self.subject.cleanup()
            release.assert_called_once_with("lease")


class TaskReplacementTest(unittest.TestCase):
    def setUp(self):
        self.subject = Rehearsal({}, "b" * 32)

    def test_running_task_does_not_accept_preparing_or_shutdown_tasks(self):
        tasks = [{"ID": "preparing", "DesiredState": "running", "Status": {"State": "preparing"}},
                 {"ID": "old", "DesiredState": "shutdown", "Status": {"State": "running"}},
                 {"ID": "new", "DesiredState": "running", "Status": {"State": "running"}}]
        with patch.object(lab, "engine", side_effect=["preparing\nold\nnew", json.dumps(tasks)]):
            self.assertEqual(self.subject.running_postgres_task(), "new")

    def test_two_running_tasks_are_not_accepted(self):
        tasks = [{"ID": name, "DesiredState": "running", "Status": {"State": "running"}} for name in ["a", "b"]]
        with patch.object(lab, "engine", side_effect=["a\nb", json.dumps(tasks)]):
            self.assertIsNone(self.subject.running_postgres_task())

    def test_force_waits_for_new_id_and_completed_rollout(self):
        def wait(check, timeout):
            self.assertEqual(timeout, 180)
            self.assertFalse(check())  # 1/1 со старой task не считается заменой.
            self.assertFalse(check())  # Новая task есть, update ещё не завершён.
            self.assertTrue(check())
        with patch.object(self.subject, "running_postgres_task", side_effect=["old", "old", "new", "new", "new"]), \
                patch.object(self.subject, "stack_ready", return_value=True), \
                patch.object(self.subject, "pg_stack", return_value="new-container"), \
                patch.object(self.subject, "database_ready") as ready, patch.object(lab, "wait_for", side_effect=wait), \
                patch.object(lab, "engine", side_effect=["", json.dumps([{"UpdateStatus": {"State": "updating"}}]),
                                                       json.dumps([{"UpdateStatus": {"State": "completed"}}])]) as engine:
            self.assertEqual(self.subject.recreate_postgres(), {"before": "old", "after": "new"})
            ready.assert_called_once_with("new-container")
            self.assertEqual(engine.call_args_list[0].args, ("service", "update", "--force", "--detach=true", self.subject.stack + "_preview-db"))

    def test_old_task_cannot_pass_on_replica_count_alone(self):
        with patch.object(self.subject, "running_postgres_task", return_value="old"), \
                patch.object(self.subject, "database_ready") as ready, patch.object(lab, "engine"), \
                patch.object(lab, "wait_for", side_effect=TimeoutError("old task")):
            with self.assertRaises(TimeoutError):
                self.subject.recreate_postgres()
            ready.assert_not_called()


if __name__ == "__main__":
    unittest.main()
