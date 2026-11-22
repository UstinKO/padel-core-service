#!/usr/bin/env python3
"""Проверяет единственного владельца API-классов в исполняемом jar."""
import argparse
import io
import zipfile


PREFIX = 'com/padle/core/contract/'
REQUIRED = {PREFIX + 'clubs/Club.class'}
FORBIDDEN = [b'com/padle/core/padelcoreservice/', b'com/padle/core/application/',
             b'com/padle/core/engine/', b'com/padle/core/tournament/', b'org/springframework/', b'jakarta/persistence/',
             b'java/sql/', b'javax/sql/', b'java/net/http/', b'okhttp3/',
             b'com/fasterxml/jackson/databind/', b'com/fasterxml/jackson/core/']


def check_archive(path):
    with zipfile.ZipFile(path) as app:
        libraries = [name for name in app.namelist()
                     if name.startswith('BOOT-INF/lib/padel-api-contract-') and name.endswith('.jar')]
        if len(libraries) != 1:
            raise ValueError('Требуется ровно одна библиотека padel-api-contract')
        if any(name.startswith('BOOT-INF/classes/' + PREFIX) for name in app.namelist()):
            raise ValueError('Дубликат contract-классов в bootstrap')
        with zipfile.ZipFile(io.BytesIO(app.read(libraries[0]))) as library:
            if {name for name in library.namelist() if name.endswith('.class')} != REQUIRED:
                raise ValueError('В библиотеке должна быть только generated-модель Club')
            if any(name.startswith('BOOT-INF/') for name in library.namelist()):
                raise ValueError('Contract library не должна быть исполняемым jar')
            for name in library.namelist():
                if name.endswith('.class'):
                    code = library.read(name)
                    if code[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(code[6:8], 'big') != 61:
                        raise ValueError('Contract bytecode должен соответствовать Java 17')
                    if any(value in code for value in FORBIDDEN):
                        raise ValueError('Contract зависит от запрещённого слоя')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', default='padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar')
    check_archive(parser.parse_args().jar)
    print('contract-artifact: одна библиотека, Java 17, без копии в bootstrap и запрещённых слоёв')
