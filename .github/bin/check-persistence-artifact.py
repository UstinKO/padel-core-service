#!/usr/bin/env python3
"""Проверяет размещение JPA клубов и учётных записей и дерева Liquibase в исполняемом JAR."""
import argparse
import io
from pathlib import Path
import zipfile


CHANGELOG = 'db/changelog/'
LIB = 'BOOT-INF/lib/'
CLASSES = 'BOOT-INF/classes/'
PERSISTENCE = 'com/padle/core/persistence/'
CLUB_CLASSES = {
    PERSISTENCE + 'clubs/' + name + '.class'
    for name in ('Club', 'ClubRepository', 'JpaClubReadAdapter', 'JpaClubAdminAdapter',
                 'ClubMapper', 'ClubMapperImpl', 'ClubReadModelMapper', 'ClubReadModelMapperImpl')
}
IDENTITY_CLASSES = {
    PERSISTENCE + 'identity/' + name + '.class'
    for name in ('PasswordResetToken', 'PasswordResetTokenRepository', 'JpaPasswordResetTokens',
                 'PasswordResetTokenMapper', 'PasswordResetTokenMapperImpl',
                 'OwnerEntity', 'OwnerRepository', 'JpaOwnerAccounts', 'OwnerAccountMapper', 'OwnerAccountMapperImpl',
                 'PlayerPadel', 'PlayerRepository', 'JpaPlayerProfiles', 'PlayerProfileMapper', 'PlayerProfileMapperImpl',
                 'JdbcGuestProfileClaims', 'JdbcGuestProfileHistory')
}
REQUIRED = CLUB_CLASSES | IDENTITY_CLASSES | {PERSISTENCE + 'PersistenceConfiguration.class'}
LEGACY = 'com/padle/core/padelcoreservice/'
LEGACY_STORAGE = {
    LEGACY + name
    for name in ('model/Club', 'repository/ClubRepository', 'mapper/ClubMapper',
                 'mapper/ClubMapperImpl', 'mapper/ClubReadModelMapper', 'mapper/ClubReadModelMapperImpl',
                 'adapter/clubs/LegacyClubReadAdapter', 'adapter/clubs/LegacyClubAdminAdapter',
                 'model/PasswordResetToken', 'repository/PasswordResetTokenRepository', 'repository/OwnerRepository',
                 'repository/PlayerRepository', 'repository/GuestProfileClaimRepository',
                 'repository/GuestProfileHistoryRepository')
}
OWNER_PRINCIPAL = CLASSES + LEGACY + 'model/Owner.class'
PLAYER_SESSION_FORMAT = CLASSES + LEGACY + 'model/PlayerPadel.class'
FORBIDDEN = (
    LEGACY.encode(), b'com/padle/core/contract/', b'com/padle/core/web/',
    b'com/padle/core/integrations/', b'org/springframework/web/', b'org/springframework/security/'
)


def changelog_names(archive, prefix=''):
    return [name for name in archive.namelist()
            if name.startswith(prefix + CHANGELOG) and not name.endswith('/')]


def contains_storage(names, prefix=''):
    for name in names:
        if not name.startswith(prefix) or not name.endswith('.class'):
            continue
        path = name.removeprefix(prefix)
        if path.startswith(PERSISTENCE) or path.removesuffix('.class').split('$')[0] in LEGACY_STORAGE:
            return True
    return False


def check_library(library, source):
    names = library.namelist()
    if len(names) != len(set(names)) or any(name.startswith('BOOT-INF/') for name in names):
        raise ValueError('Persistence должна быть обычной библиотекой без повторов записей')
    if not REQUIRED.issubset(names):
        raise ValueError('В persistence отсутствуют перенесённые JPA-типы или конфигурация')
    if b'Ljakarta/persistence/Entity;' not in library.read(PERSISTENCE + 'identity/PlayerPadel.class'):
        raise ValueError('JPA-сущность PlayerPadel должна находиться в persistence')
    for name in names:
        if name.endswith('.class'):
            code = library.read(name)
            if not name.startswith(PERSISTENCE) or code[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(code[6:8], 'big') != 61:
                raise ValueError('Persistence-класс должен принадлежать модулю и иметь bytecode Java 17: ' + name)
            if any(marker in code for marker in FORBIDDEN):
                raise ValueError('Persistence ссылается на запрещённый слой: ' + name)

    packaged = {name[len(CHANGELOG):]: library.read(name) for name in changelog_names(library)}
    expected = {path.relative_to(source).as_posix(): path.read_bytes()
                for path in source.rglob('*') if path.is_file()}
    if 'changelog-master.yaml' not in expected or packaged != expected:
        raise ValueError('Дерево Liquibase в persistence должно совпадать с исходниками побайтно')
    return len(packaged)


def check_archive(path, source):
    with zipfile.ZipFile(path) as app:
        names = app.namelist()
        libraries = [name for name in names if name.startswith(LIB) and name.endswith('.jar')]
        persistence = [name for name in libraries if name.startswith(LIB + 'padel-persistence-')]
        liquibase = [name for name in libraries if name.startswith(LIB + 'liquibase-core-')]
        if len(persistence) != 1 or len(liquibase) != 1:
            raise ValueError('Требуется по одной библиотеке padel-persistence и liquibase-core')
        if changelog_names(app, CLASSES) or changelog_names(app):
            raise ValueError('Liquibase должен упаковываться только внутри persistence')
        if contains_storage(names, CLASSES):
            raise ValueError('В bootstrap осталась копия перенесённого JPA')
        if OWNER_PRINCIPAL not in names or b'Ljakarta/persistence/Entity;' in app.read(OWNER_PRINCIPAL):
            raise ValueError('Owner в bootstrap должен быть отделённым от JPA principal')
        if PLAYER_SESSION_FORMAT not in names or b'jakarta/persistence/' in app.read(PLAYER_SESSION_FORMAT):
            raise ValueError('PlayerPadel в bootstrap должен содержать только формат старой сессии')
        for nested in libraries:
            with zipfile.ZipFile(io.BytesIO(app.read(nested))) as archive:
                if nested == persistence[0]:
                    count = check_library(archive, source)
                elif changelog_names(archive) or contains_storage(archive.namelist()):
                    raise ValueError('Ресурс Liquibase или перенесённый JPA продублирован: ' + nested)
        return count


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=Path('padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar'))
    parser.add_argument('--changelog', type=Path, default=Path('padel-persistence/src/main/resources/db/changelog'))
    args = parser.parse_args()
    files = check_archive(args.jar, args.changelog)
    print(f'persistence-artifact: одна библиотека, один Liquibase, {files} ресурсов совпадают с исходниками')
