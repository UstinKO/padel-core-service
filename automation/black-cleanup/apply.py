"""Применение только уже проверенного и подтверждённого плана разовой очистки."""
import hashlib
import json
import os
from pathlib import Path
import sys

from prepare import ROOT, Session, clean, rating_state


def main(manifest_path, approved_fingerprint):
    os.umask(0o077)
    manifest_path = Path(manifest_path).resolve(strict=True)
    assert manifest_path.parent.parent == ROOT.resolve(strict=True)
    assert manifest_path.name == 'prepared.json'
    prepared = json.loads(manifest_path.read_text())
    assert prepared['restore_verified'] and prepared['rehearsal_verified']
    assert approved_fingerprint == prepared['fingerprint']
    assert prepared['profiles_deleted'] == 0
    assert not (manifest_path.parent / 'applied.json').exists()
    archive = Path(prepared['backup']).resolve(strict=True)
    assert archive.parent == manifest_path.parent and archive.name == 'database.dump'
    checksum = hashlib.sha256()
    with archive.open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(part)
    assert checksum.hexdigest() == prepared['backup_sha256']
    assert rating_state() == prepared['rating']
    live = Session()
    try:
        applied = clean(live, expected=prepared, commit=True)
    finally:
        live.close()
    result = {'fingerprint': approved_fingerprint, 'backup': str(archive), 'profiles_deleted': 0,
              'deleted': {table: data['count'] for table, data in applied['selected'].items() if data['count']},
              'outside_rows_preserved': True, 'notifications_invoked': False, 'committed': True}
    (manifest_path.parent / 'applied.json').write_text(json.dumps(result, indent=2) + '\n')
    print('BLACK_CLEANUP_APPLIED=' + json.dumps(result, sort_keys=True), flush=True)


if __name__ == '__main__':
    try:
        assert len(sys.argv) == 3
        main(sys.argv[1], sys.argv[2])
    except BaseException as error:
        print('Применение остановлено: ' + type(error).__name__, flush=True)
        raise SystemExit(1)
