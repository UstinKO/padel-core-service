import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch

from codex_executor import CodexExecutor, worker_environment, write_status
from codex_worker import execute, run_process
import engine as E
from test_engine import FakeTelegram


class CodexExecutorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.spool = Path(self.temp.name)
        self.executor = CodexExecutor(self.spool, 'https://github.com/example/project.git')

    def test_launch_keeps_prompt_out_of_process_arguments_and_secrets_out_of_environment(self):
        with patch.dict(os.environ, {'TELEGRAM_BOT_TOKEN': 'private-bot-token',
                                     'PIPELINE_SHARED_SECRET': 'private-secret', 'GH_TOKEN': 'writer'}), \
             patch('codex_executor.shutil.which', return_value='/bin/codex'), \
             patch('codex_executor.subprocess.Popen') as spawn:
            spawn.return_value.pid = os.getpid()
            outcome, payload, _ = self.executor.launch('задача с временной приватной ссылкой')
        self.assertEqual(outcome, 'ok')
        job = self.spool / payload['execution_id']
        self.assertEqual((job / 'prompt.txt').read_text(), 'задача с временной приватной ссылкой')
        self.assertEqual(job.stat().st_mode & 0o777, 0o700)
        self.assertNotIn('задача', ' '.join(spawn.call_args.args[0]))
        env = spawn.call_args.kwargs['env']
        self.assertNotIn('TELEGRAM_BOT_TOKEN', env)
        self.assertNotIn('PIPELINE_SHARED_SECRET', env)
        self.assertEqual(env['GH_TOKEN'], 'writer')
        self.assertEqual(self.executor.status(payload['execution_id']), 'accepted')

    def test_missing_cli_and_oversized_prompt_fail_without_starting_a_process(self):
        with patch('codex_executor.shutil.which', return_value=None), patch('codex_executor.subprocess.Popen') as spawn:
            self.assertEqual(self.executor.launch('task')[0], 'error')
            self.assertEqual(self.executor.launch('x' * 65537)[0], 'error')
        spawn.assert_not_called()

    def test_deadline_and_path_traversal_are_failures(self):
        job = self.spool / ('a' * 32)
        job.mkdir()
        write_status(job, 'running', deadline=time.time() - 1)
        self.assertEqual(self.executor.status(job.name), 'failed')
        self.assertEqual(self.executor.status('../secret'), 'failed')

    def test_live_worker_keeps_slot_after_deadline(self):
        job = self.spool / ('b' * 32)
        job.mkdir()
        write_status(job, 'running', deadline=time.time() - 1, pid=os.getpid())
        self.assertEqual(self.executor.status(job.name), 'running')
        with patch('codex_executor.shutil.which', return_value='/bin/codex'), \
             patch('codex_executor.subprocess.Popen') as spawn:
            self.assertEqual(self.executor.launch('next task')[0], 'retry')
        spawn.assert_not_called()

    def test_unexpected_worker_exit_is_reaped_and_releases_slot(self):
        job = self.spool / ('d' * 32)
        job.mkdir()
        process = subprocess.Popen(['/bin/sh', '-c', 'exit 1'])
        write_status(job, 'running', deadline=time.time() + 60, pid=process.pid)
        deadline = time.time() + 2
        while time.time() < deadline and self.executor.status(job.name) != 'failed':
            time.sleep(0.01)
        self.assertEqual(self.executor.status(job.name), 'failed')
        process.poll()

    def test_same_execution_key_never_spawns_twice_even_after_success(self):
        key = 'c' * 32
        with patch('codex_executor.shutil.which', return_value='/bin/codex'), \
             patch('codex_executor.subprocess.Popen') as spawn:
            spawn.return_value.pid = os.getpid()
            self.assertEqual(self.executor.launch('task', key)[0], 'ok')
            self.assertEqual(self.executor.launch('task', key)[0], 'ok')
            write_status(self.spool / key, 'succeeded')
            self.assertEqual(self.executor.launch('task', key)[0], 'ok')
        self.assertEqual(spawn.call_count, 1)

    def test_worker_refuses_implicit_application_database(self):
        with patch.dict(os.environ, {'DB_URL': 'jdbc:postgresql://localhost/player_padel_db'}), \
             patch('codex_worker.run_process') as run:
            self.assertEqual(execute(self.spool, 'repository', 60, 'codex'), 1)
        run.assert_not_called()
        self.assertEqual(json.loads((self.spool / 'status.json').read_text())['state'], 'failed')

    def test_worker_uses_clean_checkout_and_real_codex_exit_code(self):
        (self.spool / 'prompt.txt').write_text('request')
        commands = []

        def run(command, cwd, output, timeout, prompt=None):
            commands.append((command, prompt))
            if command[0] == 'git':
                workspace = Path(command[-1])
                (workspace / '.agents/workflows').mkdir(parents=True)
                (workspace / 'AGENTS.md').write_text('rules')
                (workspace / '.agents/workflows/develop-feature.md').write_text('protocol')
            else:
                raise subprocess.CalledProcessError(1, command)

        with patch.dict(os.environ, {'DB_URL': 'jdbc:postgresql://localhost/worker_codex_test'}), \
             patch('codex_worker.run_process', side_effect=run):
            self.assertEqual(execute(self.spool, 'repository', 60, 'codex'), 1)
        self.assertEqual(commands[0][0][:3], ['git', 'clone', '--no-local'])
        self.assertIn('--sandbox', commands[1][0])
        self.assertIn('workspace-write', commands[1][0])
        self.assertIn(b'request', commands[1][1])
        self.assertEqual(json.loads((self.spool / 'status.json').read_text())['state'], 'failed')

    def test_process_timeout_stops_own_child(self):
        with (self.spool / 'process.log').open('wb') as output:
            with self.assertRaises(subprocess.TimeoutExpired):
                run_process(['/bin/sh', '-c', 'sleep 30'], self.spool, output, 0.05)

    def test_process_timeout_kills_descendant_ignoring_sigterm(self):
        pid_file = self.spool / 'descendant.pid'
        command = ['/bin/sh', '-c', 'sh -c \'trap "" TERM; sleep 30\' & echo $! > "$1"; wait',
                   'test', str(pid_file)]
        with (self.spool / 'tree.log').open('wb') as output:
            with self.assertRaises(subprocess.TimeoutExpired):
                run_process(command, self.spool, output, 0.15)
        pid = int(pid_file.read_text())
        result = subprocess.run(['ps', '-p', str(pid), '-o', 'stat='], capture_output=True, text=True)
        self.assertTrue(not result.stdout.strip() or result.stdout.strip().startswith('Z'), result.stdout)


class WorkerEngineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.store = E.Store(str(Path(self.temp.name) / 'engine.db'))
        self.addCleanup(self.store.db.close)
        self.tg = FakeTelegram()
        self.state = 'running'
        self.launches = []

        def fire(text):
            self.launches.append(text)
            return 'ok', {'execution_id': 'job', 'session_url': ''}, None

        self.engine = E.Engine(E.EngineConfig(repo='example/repo', max_parallel=1), self.store,
                               E.Telegram(self.tg.send, self.tg.edit), None, fire, lambda: False,
                               execution_status=lambda _: self.state)

    def test_worker_failure_reaches_card_and_releases_slot_without_github(self):
        first = self.engine.submit('architect', 'User', 'first')
        second = self.engine.submit('architect', 'User', 'second')
        self.assertEqual(self.store.get(second)['status'], E.QUEUED)
        self.state = 'failed'
        self.engine.tick()
        self.assertEqual(self.store.get(first)['status'], E.FAILED)
        self.assertEqual(self.store.get(second)['status'], E.RUNNING)
        self.assertIn('ошибкой', self.tg.edits[first][0])

    def test_successful_cli_exit_is_not_successful_deployment(self):
        task = self.engine.submit('architect', 'User', 'task')
        self.state = 'succeeded'
        self.engine.tick()
        self.assertEqual(self.store.get(task)['status'], E.RUNNING)

    def test_repair_waits_for_previous_execution_to_exit(self):
        task = self.engine.submit('architect', 'User', 'task')
        self.store.update(task, status=E.QUEUED)
        self.engine.tick()
        self.assertEqual(len(self.launches), 1)
        self.state = 'succeeded'
        self.engine.tick()
        self.assertEqual(len(self.launches), 2)

    def test_old_database_migrates_without_losing_task(self):
        path = str(Path(self.temp.name) / 'legacy.db')
        old = sqlite3.connect(path)
        old.executescript(E.SCHEMA.replace('    execution_id TEXT,\n', '').replace('    launch_key TEXT,\n', ''))
        old.execute("INSERT INTO tasks (id,role,orig_text,next_text,status,created_at,updated_at) VALUES (1,'architect','request','request','queued',1,1)")
        old.commit()
        old.close()
        migrated = E.Store(path)
        self.addCleanup(migrated.db.close)
        self.assertEqual(migrated.get(1)['orig_text'], 'request')
        self.assertIsNone(migrated.get(1)['execution_id'])
        self.assertIsNone(migrated.get(1)['launch_key'])

    def test_crash_after_spawn_resumes_same_execution_key(self):
        executor = CodexExecutor(Path(self.temp.name) / 'jobs', 'repository')
        self.engine.launch_execution = executor.launch
        original_update = self.store.update

        def update(task_id, **fields):
            if fields.get('status') == E.RUNNING:
                raise RuntimeError('simulated relay crash after spawn')
            original_update(task_id, **fields)

        with patch('codex_executor.shutil.which', return_value='/bin/codex'), \
             patch('codex_executor.subprocess.Popen') as spawn:
            spawn.return_value.pid = os.getpid()
            with patch.object(self.store, 'update', side_effect=update):
                with self.assertRaises(RuntimeError):
                    self.engine.submit('architect', 'User', 'task')
            task = self.store.active()[0]
            self.assertEqual(task['status'], E.QUEUED)
            self.assertIsNotNone(task['launch_key'])
            self.engine.tick()
        self.assertEqual(spawn.call_count, 1)
        self.assertEqual(self.store.get(task['id'])['status'], E.RUNNING)

    def test_answered_card_does_not_allow_overlapping_processes(self):
        executor = CodexExecutor(Path(self.temp.name) / 'jobs', 'repository')
        self.engine.launch_execution = executor.launch
        with patch('codex_executor.shutil.which', return_value='/bin/codex'), \
             patch('codex_executor.subprocess.Popen') as spawn:
            spawn.return_value.pid = os.getpid()
            first = self.engine.submit('architect', 'User', 'first')
            self.store.update(first, status=E.ANSWERED)
            second = self.engine.submit('architect', 'User', 'second')
        self.assertEqual(spawn.call_count, 1)
        self.assertEqual(self.store.get(second)['status'], E.QUEUED)


if __name__ == '__main__':
    unittest.main()
