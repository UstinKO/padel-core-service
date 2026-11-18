#!/usr/bin/env python3
"""Собирает проверенный пакет выпуска из готового jar; секреты не упаковываются."""
import argparse
import json
from pathlib import Path
import subprocess
import artifact
from docker_adapter import build
from production import package


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--jar', type=Path, default=Path('padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar'))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    actual_revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if args.revision != actual_revision:
        raise ValueError('Ревизия пакета не соответствует исходному checkout')
    manifest = artifact.inspect(args.jar, args.revision, root / 'src/main/resources/static/app')
    (root / 'target/release-manifest.json').write_text(json.dumps(manifest, indent=2))
    image = build(args.jar, manifest, root / 'automation/releases/Dockerfile', root / 'target/release-image')
    package(root, args.jar, args.revision, manifest, image)
    print('Пакет выпуска подготовлен: ' + args.revision)


if __name__ == '__main__':
    main()
