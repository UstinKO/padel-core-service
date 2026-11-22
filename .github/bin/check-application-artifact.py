#!/usr/bin/env python3
"""Проверяет единственного владельца application-классов в исполняемом jar."""
import argparse
import io
import zipfile


PREFIX = 'com/padle/core/application/'
REQUIRED = {PREFIX + 'clubs/' + name + '.class'
            for name in ['PublicClub', 'PublicClubQuery', 'ClubReadPort']}
FORBIDDEN = [b'com/padle/core/padelcoreservice/', b'org/springframework/data/',
             b'org/springframework/web/', b'org/springframework/security/', b'jakarta/persistence/',
             b'tools/jackson/', b'com/fasterxml/jackson/', b'java/sql/', b'javax/sql/']


def check_archive(path):
    with zipfile.ZipFile(path) as app:
        libraries = [name for name in app.namelist()
                     if name.startswith('BOOT-INF/lib/padel-application-') and name.endswith('.jar')]
        if len(libraries) != 1:
            raise ValueError('Требуется ровно одна библиотека padel-application')
        if any(name.startswith('BOOT-INF/classes/' + PREFIX) for name in app.namelist()):
            raise ValueError('Дубликат application-классов в bootstrap')
        with zipfile.ZipFile(io.BytesIO(app.read(libraries[0]))) as library:
            if not REQUIRED.issubset(library.namelist()):
                raise ValueError('В библиотеке отсутствуют сценарий, порт или read-модель')
            if any(name.startswith('BOOT-INF/') for name in library.namelist()):
                raise ValueError('Application library не должна быть исполняемым jar')
            for name in library.namelist():
                if name.endswith('.class'):
                    code = library.read(name)
                    if code[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(code[6:8], 'big') != 61:
                        raise ValueError('Application bytecode должен соответствовать Java 17')
                    if any(value in code for value in FORBIDDEN):
                        raise ValueError('Application зависит от запрещённого слоя')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', default='padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar')
    check_archive(parser.parse_args().jar)
    print('application-artifact: одна библиотека, Java 17, без копии в bootstrap и запрещённых слоёв')
