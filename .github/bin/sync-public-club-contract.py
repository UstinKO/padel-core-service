#!/usr/bin/env python3
"""Сверяет или собирает клубный раздел совместимого discovery JSON из contract-модуля."""
import argparse
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CANONICAL = ROOT / 'padel-api-contract/src/main/openapi/public-clubs.openapi.json'
DISCOVERY = ROOT / 'docs/frontend-react/contract/public-discovery.openapi.json'


def project(canonical, discovery):
    result = copy.deepcopy(discovery)
    if canonical['openapi'] != discovery['openapi']:
        raise ValueError('Версии OpenAPI различаются')
    for section, values in [('paths', canonical['paths']),
                            ('schemas', canonical['components']['schemas'])]:
        target = result['paths'] if section == 'paths' else result['components']['schemas']
        target.update(copy.deepcopy(values))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    canonical = json.loads(CANONICAL.read_text())
    discovery = json.loads(DISCOVERY.read_text())
    expected = project(canonical, discovery)
    if args.write:
        DISCOVERY.write_text(json.dumps(expected, ensure_ascii=False, indent=2) + '\n')
    elif discovery != expected:
        raise SystemExit('Клубный раздел discovery устарел: выполните sync-public-club-contract.py --write')
    print('club-contract: клубные paths/Club/Problem совпадают с каноническим источником')


if __name__ == '__main__':
    main()
