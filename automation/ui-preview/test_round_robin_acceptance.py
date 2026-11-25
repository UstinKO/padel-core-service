import importlib.util
from pathlib import Path
import unittest


specification = importlib.util.spec_from_file_location('round_robin_acceptance',
    Path(__file__).with_name('round-robin-acceptance.py'))
acceptance = importlib.util.module_from_spec(specification)
specification.loader.exec_module(acceptance)


class RoundRobinScopeTest(unittest.TestCase):
    def setUp(self):
        self.state = dict(instance='owned-preview')
        self.status = dict(environment='preview', fixtures=dict(playerIds=[1], tournamentIds=[2]))

    def test_only_matching_preview_database_is_accepted(self):
        acceptance.validate_scope(self.state, self.status, 'padel_ui_v2_preview:owned-preview')

    def test_production_or_another_database_instance_is_rejected(self):
        for identity, environment in [('production:owned-preview', 'preview'),
                                      ('padel_ui_v2_preview:foreign', 'preview'),
                                      ('padel_ui_v2_preview:owned-preview', 'production')]:
            with self.subTest(identity=identity, environment=environment), self.assertRaises(ValueError):
                acceptance.validate_scope(self.state, dict(self.status, environment=environment), identity)

    def test_database_without_synthetic_fixtures_is_rejected(self):
        for fixtures in [{}, dict(playerIds=[1]), dict(playerIds=[], tournamentIds=[2])]:
            with self.subTest(fixtures=fixtures), self.assertRaises(ValueError):
                acceptance.validate_scope(self.state, dict(self.status, fixtures=fixtures), 'padel_ui_v2_preview:owned-preview')
