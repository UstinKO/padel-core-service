#!/usr/bin/env python3
"""Проверяет единственного владельца application-классов в исполняемом jar."""
import argparse
import io
import re
import struct
import zipfile


PREFIX = 'com/padle/core/application/'
REQUIRED = {PREFIX + 'clubs/' + name + '.class'
            for name in ['PublicClub', 'PublicClubQuery', 'ClubReadPort', 'ClubDetails', 'InternalClubReadPort',
                         'ClubProfile', 'ClubProfile$Fields', 'ClubAdminReadPort', 'ClubAdminWritePort',
                         'ClubTournamentUsage']}
READ_PREFIX = PREFIX + 'roundrobin/'
REQUIRED.update(PREFIX + 'identity/' + name + '.class' for name in [
    'PasswordResetTokens', 'PasswordResetTokens$Token', 'OwnerAccount', 'OwnerAccount$Role',
    'OwnerAccounts', 'OwnerAccounts$Login', 'OwnerAccounts$Profile',
])
REQUIRED.update(READ_PREFIX + name + '.class' for name in [
    'RoundRobinReadPort', 'RoundRobinRankingQuery', 'RoundRobinRankingComposer', 'RoundRobinNotFound',
    'RoundRobinReadSet', 'RoundRobinReadSet$Event', 'RoundRobinReadSet$Team', 'RoundRobinReadSet$Player',
    'RoundRobinReadSet$Statistics', 'RoundRobinReadSet$Round', 'RoundRobinReadSet$Match',
    'RoundRobinReadSet$ScoringMode', 'RoundRobinReadSet$Phase', 'RoundRobinReadSet$Status',
    'RoundRobinReadSet$TeamStatus', 'RoundRobinReadSet$Side', 'RoundRobinRanking',
    'RoundRobinRanking$RankedTeam', 'RoundRobinRanking$AdditionalMatch', 'RoundRobinRanking$FinalStanding'])
REQUIRED.update(READ_PREFIX + name + '.class' for name in [
    'RoundRobinPosition', 'RoundRobinPositionInput', 'RoundRobinPositionInput$PositionTeam',
    'RoundRobinPositionInput$PositionMatch', 'RoundRobinPositionPort', 'RoundRobinPositionCommand',
    'RoundRobinStandingsCalculation'])
REQUIRED.update(READ_PREFIX + name + '.class' for name in [
    'RoundRobinFinishCommand', 'RoundRobinFinishPort', 'RoundRobinFinishEvent',
    'RoundRobinFinishCancelled', 'RoundRobinFinishIncomplete'])
ENGINE_PREFIX = 'com/padle/core/padelcoreservice/service/americano/'
ALLOWED_ENGINES = {ENGINE_PREFIX + name for name in [
    'RoundRobinSetStandingsEngine', 'RoundRobinSetStandingsEngine$Team',
    'RoundRobinSetStandingsEngine$Match', 'RoundRobinSetStandingsEngine$Ranked',
    'RoundRobinPointsStandingsEngine', 'RoundRobinPointsStandingsEngine$Team',
    'RoundRobinPointsStandingsEngine$Ranked', 'RoundRobinFinalOrderEngine',
    'RoundRobinFinalOrderEngine$Standing', 'RoundRobinFinalOrderEngine$Match', 'RoundRobinFinalOrderEngine$Ranked']}
FORBIDDEN = [b'com/padle/core/padelcoreservice/', b'org/springframework/data/',
             b'org/springframework/web/', b'org/springframework/security/', b'jakarta/persistence/',
             b'tools/jackson/', b'com/fasterxml/jackson/', b'java/sql/', b'javax/sql/']


def type_references(code):
    """Читает имена Class и типы descriptor/signature целиком, включая generics/arrays."""
    try:
        count = struct.unpack_from('>H', code, 8)[0]
        index, offset = 1, 10
        strings, classes = {}, []
        while index < count:
            tag = code[offset]
            offset += 1
            if tag == 1:
                length = struct.unpack_from('>H', code, offset)[0]
                offset += 2
                if offset + length > len(code):
                    raise ValueError('Обрезанный constant pool')
                strings[index] = code[offset:offset + length].decode('utf-8', errors='replace')
                offset += length
            elif tag == 7:
                classes.append(struct.unpack_from('>H', code, offset)[0])
                offset += 2
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                offset += 4
            elif tag in (5, 6):
                offset += 8
                index += 1
            elif tag in (8, 16, 19, 20):
                offset += 2
            elif tag == 15:
                offset += 3
            else:
                raise ValueError('Неизвестный constant pool tag')
            if offset > len(code):
                raise ValueError('Обрезанный constant pool')
            index += 1
        references = {strings[entry] for entry in classes}
        for value in strings.values():
            references.update(re.findall(r'L([A-Za-z_$][\w$]*(?:/[A-Za-z_$][\w$]*)+)', value))
        return references
    except (IndexError, KeyError, struct.error) as failure:
        raise ValueError('Некорректный constant pool') from failure


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
                    references = type_references(code)
                    if any(any(reference.startswith(prefix.decode()) for prefix in FORBIDDEN)
                           and reference not in ALLOWED_ENGINES for reference in references):
                        raise ValueError('Application зависит от запрещённого слоя')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', default='padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar')
    check_archive(parser.parse_args().jar)
    print('application-artifact: одна библиотека, Java 17, без копии в bootstrap и запрещённых слоёв')
