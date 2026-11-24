# LFPT-1209 · Результаты локальной репетиции

Исторический протокол 23.11.2026 до объединения с новым master. Проверка выполнена в собственном вложенном Docker Engine 29.5.3
с лимитом 4 GiB / 2 CPU. PostgreSQL 15.17, plpgsql 1.0, unaccent 1.1;
образы закреплены в images.lock.json. Production не подключался.

## Данные и переходы

Финальная репетиция завершилась PASS. Проверены 49 несистемных таблиц
(включая preview/control и контрольную таблицу репетиции), 24 последовательности,
owners/constraints/indexes/columns/locale/extensions. SHA-256 считается по исходному
JSON PostgreSQL без округления numeric в float. Совпадение проверялось после restore,
старта Swarm, повторного deploy и возврата Compose.

| Этап | Измеренное время | Что входит |
|---|---|---|
| Custom backup финальной БД | 0.875 s | pg_dump и передача локальной копии |
| Restore PostgreSQL | 8.625 s | Новый PGDATA, single-transaction restore, сверка данных |
| Compose → Swarm | 97.688 s | Запрет записей, финальная копия, monitoring archives, restore, запуск и проверки |
| Swarm → Compose | 39.078 s | Остановка Swarm, ожидание томов, запуск Compose и проверки |

Custom dump — 198561 bytes; tar Grafana — 1013760 bytes, Prometheus — 133120 bytes,
Loki — 51200 bytes. Реальные filesystem uid:gid/mode исходного и восстановленного
корня совпали: PostgreSQL 999:999/700, Grafana 472:0/777, Prometheus 65534:65534/755,
Loki 10001:10001/755. Это измеренные настройки закреплённых тестовых образов,
а не рекомендуемый универсальный профиль прав production.

## Подтверждённые сценарии

- Source/target mounts совпали с явной картой физических томов; данные не подменены пустой БД.
- Проверка занятости отклонила активного писателя; pending Swarm writer также покрыт unit-тестом.
- Сверены содержимое и UID/GID/mode файлов monitoring archives до запуска.
- Через API после обоих переходов найдены dashboard Grafana, sample Prometheus
  с исходным timestamp и конкретная строка Loki с меткой своего run ID.
- Запись, сделанная после перехода в Swarm, сохранилась при повторном deploy
  и возврате в Compose на текущих восстановленных томах.
- Cleanup проверен для четырёх конкретных собственных run ID, включая незавершённую
  попытку: удалены только их незанятые volumes. Backup/отчёты оставлены в runtime;
  исходные данные #1208 и соседние проекты не удалялись.

## Регрессия восстановления

До исправления обычный pg_restore завершался ошибкой создания idx_player_claim_phone:
не найден unqualified helper player_claim_phone_argentina. В первоначальной ветке v1.84 квалифицировала
helper схемой public. Полный новый dump восстанавливается без пропуска post-data
или игнорирования ошибок. GuestProfileClaimIntegrationTest прошёл 16 сценариев,
включая новый тест индекса и AR/+7 при search_path pg_catalog.

## Автоматические проверки

- Maven reactor package с skip.frontend — SUCCESS; все модули собраны.
- Maven verify на отдельной PostgreSQL: bootstrap 1211 tests, 28 failures,
  0 errors, 2 skips. Все failures — прежний LokiLoggingConfigurationTest:
  Windows-дочерняя JVM не находит LoggingProbe через argfile с кириллицей в classpath.
  Полный Windows verify не объявляется зелёным; остальные тесты, включая новую
  миграцию и регрессионный сценарий, прошли.
- Отдельный повтор Loki на тех же скомпилированных классах через JUnit Launcher
  в Linux/Java 21 с ASCII classpath: 28/28 успешно, без пропусков. Контейнер имел
  собственную internal-сеть без Internet; это отдельный регрессионный прогон,
  а не переименование неуспешного Windows Maven verify в успешный.
- Linux Python 3.13: Swarm — 23/23, Telegram relay — 48/48, releases — 22/22.
  На Windows Swarm — 23/23; relay/releases требуют Unix fcntl, поэтому проверены в Linux.
- docs-drift, check-agent-migration и git diff --check — без расхождений.

## Границы результата

Числа относятся к небольшой синтетической БД и четырём хранилищам без пользовательского
трафика. Они не подтверждают production RTO/RPO, переключение полного proxy/app,
нагрузку двух JVM, offsite-расшифровку или REST #1211. Закрытую production карту,
роли/ACL, backup age/rclone и восстановление старых архивов с неквалифицированной функцией оператор
проверяет по [runbook](data-recovery.md) перед общим допуском #1217/#1218.
Runtime с полными картами, archive checksums и отчётами не включается в Git.


После ревью 24.11.2026 номер исправления перенесён на v1.88: v1.84–v1.87
из master сохраняются без изменений. Исторические замеры выше не подменяют
проверку объединённого кода и принудительного пересоздания task PostgreSQL.

## Проверка исправлений ревью 24.11.2026

Проверен код LFPT-1209 с актуальным master b95e9a09. После пользовательского
reset --soft ветка feature/LFPT-1209 указывает на этот же коммит master; реализация
задачи вместе с исправлениями ревью сохранена в индексе для одного коммита.
Незавершённых merge/rebase нет; commit/push остаются за владельцем.
Миграции v1.84-nivel-c3, v1.85-player-telegram, v1.86-organizer-organization и
v1.87-player-contact-preference совпадают с master. Исправление search_path
перенесено в v1.88; все пять миграций применились последовательно на отдельной
чистой тестовой PostgreSQL.

Новая реальная репетиция на объединённом коде: PASS, 54 таблицы и 24 sequences.
`service update --force` заменил task PostgreSQL
`plk4pha0nlngzepty5ferxw2y` → `r7ma32ccc1vnjdkz0y8cf9wma`.
После завершения rollout проверены прежний физический volume, контрольные суммы
и запись id=2, сделанная в Swarm. После возврата Compose сверки также прошли;
dashboard Grafana, исторический sample Prometheus и строка Loki сохранились.

| Этап | Время, с |
| --- | ---: |
| Restore | 8.969 |
| Compose → Swarm, повторный deploy и принудительное пересоздание PostgreSQL | 118.063 |
| Swarm → Compose | 40.718 |

Swarm unit-тесты: 33/33 на Windows и Linux, включая 10 новых регрессий ошибок
mkdir, независимых остановок и удержания lease при неизвестном состоянии writers,
а также отказа принимать старый task ID за успешное пересоздание.
Linux relay — 48/48, releases — 22/22. Maven reactor package — SUCCESS.
Полный Maven verify объединённого кода на Windows/Java 21 с отдельной PostgreSQL:
bootstrap — 1306 tests, 28 failures, 0 errors, 4 skips. Все failures относятся
к прежнему LokiLoggingConfigurationTest: дочерняя JVM не находит LoggingProbe
из-за кириллицы в classpath. GuestProfileClaimIntegrationTest — 16/16, включая
индекс при search_path pg_catalog. Первоначальный запуск остановлен из-за
зависания Mockito self-attach; итоговый прогон выполнен с заранее подключённым
ByteBuddy javaagent через параметр Maven, без изменения конфигурации проекта.
Полный Windows verify остаётся неуспешным и не объявляется зелёным.
LokiLoggingConfigurationTest отдельно на Linux/Java 21 с текущими скомпилированными
классами и ASCII classpath: 28/28, без пропусков (210.873 с). Временные volumes
репетиции удалены командой cleanup с точным run ID из report.json после проверки
отсутствия writers; собственный вложенный Engine остановлен. Внешний Docker Engine
по-прежнему не включён в Swarm.
Полные dumps, metadata и report.json остались в игнорируемом runtime;
это по-прежнему замеры небольшой синтетической БД, а не production RTO/RPO.
