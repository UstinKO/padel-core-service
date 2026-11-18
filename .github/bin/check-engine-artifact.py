#!/usr/bin/env python3
"""Проверка размещения выделенного движка в исполняемом jar."""
import argparse
import io
import zipfile


ENGINE = 'com/padle/core/padelcoreservice/service/americano/PlayoffMatchingEngine'


def engine_class(name):
    return name == ENGINE + '.class' or (name.startswith(ENGINE + '$') and name.endswith('.class'))


def check_archive(path):
    with zipfile.ZipFile(path) as application:
        names = application.namelist()
        libraries = [name for name in names
                     if name.startswith('BOOT-INF/lib/padel-tournament-engines-') and name.endswith('.jar')]
        if len(libraries) != 1:
            raise ValueError('Требуется ровно одна библиотека padel-tournament-engines')
        if any(name.startswith('BOOT-INF/classes/') and engine_class(name[len('BOOT-INF/classes/'):])
               for name in names):
            raise ValueError('Дубликат движка в BOOT-INF/classes')
        with zipfile.ZipFile(io.BytesIO(application.read(libraries[0]))) as library:
            entries = library.namelist()
            if ENGINE + '.class' not in entries:
                raise ValueError('Движок отсутствует в библиотеке')
            if any(name.startswith('BOOT-INF/') for name in entries):
                raise ValueError('Library jar не должен быть перепакован Spring Boot')
            for name in entries:
                if name.endswith('.class'):
                    code = library.read(name)
                    if code[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(code[6:8], 'big') != 61:
                        raise ValueError('Bytecode библиотеки должен соответствовать Java 17')
                    if b'org/springframework/' in code or b'jakarta/persistence/' in code:
                        raise ValueError('Bytecode библиотеки зависит от Spring или JPA')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', default='padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar')
    arguments = parser.parse_args()
    check_archive(arguments.jar)
    print('engine-artifact: одна библиотека, без дубликата в bootstrap, Java 17, без Spring/JPA')


if __name__ == '__main__':
    main()
