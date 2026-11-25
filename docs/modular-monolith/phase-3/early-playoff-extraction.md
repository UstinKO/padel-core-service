# Расчёт раннего плей-офф в engines

[Issue #1343](https://github.com/UstinKO/padel-core-service/issues/1343), родитель #1041, предшественник #1338 / PR #1341. [Спецификация](../../../specs/features/LFPT-1343-early-playoff-seeding-module.md).

## Реализация

EarlyPlayoffSeeding перемещён из корневого src в существующий `padel-tournament-engines` без изменения содержимого. Package-private доступ, четыре record, private Search, порядок перебора 6:0–6:5 в обе стороны, кеш, лимит 4096 и семантика exhaustive/limited/manualBye сохранены. Зависимости — JDK, PlayoffMatchingEngine и PlayoffFormat; последние два класса уже принадлежат engines.

TeamPlayoffService, TeamPlayoffReadService, PlayoffPairExchange и сервисные тесты остаются в bootstrap. Сервисы, права, транзакции, история, HTTP/JSON/WS, UI и Liquibase не меняются. Отдельный bean не создаётся: сервис по-прежнему конструирует чистый расчёт с существующим matchingEngine. Reactor сохраняет четыре дочерних модуля и один boot jar.

В библиотеке теперь семь вычислительных классов и пятнадцать обязательных вложенных типов. Пять новых: Standing, Fixture, Pair, Result и private Search; Java API не становится публичнее. Artifact guard требует все типы в единственной библиотеке, запрещает shadow-копии основного/любого вложенного класса в BOOT-INF/classes, проверяет Java 17 и отсутствие Spring/JPA. Семь существующих Python-тестов включают отрицательные примеры для каждого нового типа. ArchUnit не ослаблялся.

## Проверки до и после переноса

Сначала добавлен EarlyPlayoffSeedingTest в корневой src/test и выполнен на исходном классе в bootstrap. Затем production и тот же файл теста перемещены в engines. Хеши подтверждают побайтовое совпадение production с master и теста с проверенной до переноса версией.

40 unit-проверок охватывают все 12 допустимых счетов, семь недопустимых, две служебные отметки раунда, составы 8–16, критерии прямого прохода, исключение получателей bye из 1/8, сохранение немиррорной fixed-пары и анти-рематч. Проверяются пересечение допустимых рекомендаций, пустой eligible, ранняя остановка без ручного выбора, возможный bye ручной пары, исчерпывающий ручной перебор и устойчивость play-in при незавершённых встречах среди прямых участников. Для 12³ исходов ниже лимита fixed-пара сохраняется; при 12⁴ выше лимита рекомендации очищаются, limited=true и exhaustive=false. Входные коллекции остаются прежними. Ожидания фиксируют исходное поведение, не вводят новое бизнес-правило.

| Команда | Назначение |
|---|---|
| `./mvnw -pl padel-bootstrap -am -Dskip.frontend=true -Dtest=EarlyPlayoffSeedingTest -Dsurefire.failIfNoSpecifiedTests=false test` | Выполнена до переноса: исходный класс в bootstrap, только новый unit-набор, без Spring/БД. Флаг отсутствующего выбранного теста относится только к этому запуску, конфигурация Surefire не меняется |
| `./mvnw -pl padel-tournament-engines -am test` | Выполнена после переноса: 103 unit/ArchUnit без Spring/БД |
| `./mvnw clean verify` | Всё приложение с frontend, отдельной PostgreSQL и сервисными сценариями ранних стадий/8–16 команд |
| `python3 .github/bin/check-engine-artifact.py` | Конечный boot jar и единственный владелец классов |

## Runtime и DevTools

На своём preview используются существующие `playoff-pilot.py run --rehearse` и `playoff-pilot.py extended`: полные циклы 8/9/16, ранние пары и ранний полуфинал, очередь, права/CSRF, stale/replay, настоящие WS и восстановление истории; общий legacy/React/Expo/session/JWT smoke и репетиция выпуска. Исходные browser-expectations не перезаписываются. Отдельное переключение на неизменённый master и возврат сравнивают пять игровых snapshots без metadata.generatedAt и отпечаток колонок схемы/identities/checksums Liquibase.

Сохраняется [временный мост общего package/classloader](../phase-1/engine-extraction.md#временный-мост-devtools). Настоящий Spring Boot DevTools probe загружается вместе со скомпилированным TeamPlayoffService, напрямую вызывает package-private EarlyPlayoffSeeding, roundOf16, projectedByes и evaluate с оставшейся встречей, то есть исполняет private Search. После Restarter.restart() вызовы повторяются. Отрицательный контроль убирает restart.include только из частной копии скомпилированных ресурсов; исходники и build output приложения не изменяются. Это минимальный probe-контекст без БД и сервиса как bean; полноценный пользовательский сценарий проверяется отдельно на boot jar.

## Остаток миграции

RoundRobinRules и RoundRobinStandings пока связаны с legacy entities/DTO. Для следующего среза нужны нейтральные входы/результаты и адресные преобразования; прямой перенос нарушит границу engines. Следующие application-сценарии требуют доменных моделей/портов и сохранения общей транзакции. Позднее выделение хранения #1051 и физическая PostgreSQL остаются отдельными задачами. Перенос чистых вычислений не объявляется завершением всей многомодульной миграции.

## Результат 24.11.2026

[Безопасная сводка](early-playoff-qa-2026-11-24.json) содержит результаты до/после, хеши production/теста, проверенных jar и ограничения. Локальный jar собран до коммита из рабочей копии master `70aea909c65d829ecf3cd0de4fd20a8efa912e52` с переносом класса и новым тестом; этот SHA обозначает основу, а не будущий коммит PR. CI опубликованного коммита проверяется отдельно.

До переноса 40 тестов прошли в bootstrap, после — тот же файл прошёл в engines. Полный `clean verify` с frontend: **1414 тестов**, 0 failures/errors, 4 штатных browser-пропуска (RegistrationSourceBrowserTest, ParticipationBrowserTest и два PlayerTelegramBrowserTest). В engines — 103 теста без пропусков: 40 новых, 59 прежних алгоритмических и 4 ArchUnit. Python: 27 GitHub (включая 7 engine guard), 48 relay, 5 worker (1 Linux-only пропуск), 12 preview, 22 release. Три artifact-guard, docs-drift и agent-migration прошли. DevTools: два успешных запуска и ожидаемый IllegalAccessError отрицательного контроля.

Итоговый jar SHA-256 `bec51e94b996b4f21606cdc8f9017427479dc61e23abea353da90907341d2153`; неизменённый master — `c2c7dcb4b6ac186702f7a3ef7fe777669751f359344b38b633b8fa03844e2aba`. Полный браузерный пилот 8/9/16 и расширенные ранние сценарии 9/16 прошли. Репетиция двух выпусков сохранила пять snapshots, проверила старый lazy chunk, HTTP-сбои chunk/readiness и восстановление журнала. JWT login/me/events — 200, anonymous me — 401.

Отдельное переключение на неизменённый master и штатный возврат нового jar сохранили пять игровых snapshots, отпечаток схемы и журнал Liquibase. До публикации master дополнен только архивацией спеки #1338 (PR #1344); код приложения и уникальный объём этого переноса с ней не пересекаются.

Физические устройства, новый ручной PASS владельца и многорепличная конкурентность не проверялись. Однорепличный concurrentSameKey входит в существующий браузерный сценарий. Сохраняются принятые ограничения, включая #1067 и Expo preview Basic 401 (QA-адаптер проверяет настоящий API 200 и отрисовку). Доступы, runtime, полные HTTP/state JSON и частные логи остаются локально.
