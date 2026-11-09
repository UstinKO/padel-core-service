import tempfile
from pathlib import Path
import unittest
from unittest.mock import Mock

from monitor import check, inspect_worker


class MonitorTest(unittest.TestCase):
    def test_bridge_failure_counts_as_worker_failure(self):
        running = Mock(returncode=0, stdout='true 0')
        down = Mock(returncode=3)
        with unittest.mock.patch('monitor.subprocess.run', side_effect=[running, down]):
            self.assertEqual(inspect_worker(), (False, 0))

    def test_down_alert_once_then_recovery_allows_new_alert(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'state.json'
            send = Mock()
            down = lambda: (False, 0)
            self.assertFalse(check(path, {}, now=lambda: 1, probe=down, send=send))
            self.assertTrue(check(path, {}, now=lambda: 2, probe=down, send=send))
            self.assertTrue(check(path, {}, now=lambda: 3, probe=down, send=send))
            self.assertEqual(send.call_count, 1)
            check(path, {}, now=lambda: 4, probe=lambda: (True, 0), send=send)
            check(path, {}, now=lambda: 5, probe=down, send=send)
            check(path, {}, now=lambda: 6, probe=down, send=send)
            self.assertEqual(send.call_count, 2)

    def test_five_restarts_trigger_alert_and_failed_delivery_is_retried(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'state.json'
            send = Mock(side_effect=[OSError(), None])
            check(path, {}, now=lambda: 1, probe=lambda: (True, 0), send=send)
            self.assertTrue(check(path, {}, now=lambda: 20, probe=lambda: (True, 5), send=send))
            self.assertTrue(check(path, {}, now=lambda: 40, probe=lambda: (True, 5), send=send))
            self.assertEqual(send.call_count, 2)


if __name__ == '__main__':
    unittest.main()
