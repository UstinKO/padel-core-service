# Правила квалификации Team Playoff в engines

[Issue #1338](https://github.com/UstinKO/padel-core-service/issues/1338), родитель #1041, предшественник #1332 / PR #1335. [Спецификация](../../../specs/features/LFPT-1338-team-playoff-qualification-module.md).

## Реализация

QualificationPairing, PlayoffFormat и два исходных Test перемещены из корневого src в существующий `padel-tournament-engines`. Все четыре файла сохранены байтово. Таблица прохода 8–16, порядок выбора пары для обмена, fallback, package и package-private доступ остаются прежними. TeamPlayoffService, EarlyPlayoffSeeding и сервисные тесты остаются в bootstrap; новых bean или Maven-зависимостей нет.

Библиотека теперь содержит шесть вычислительных классов и десять обязательных вложенных типов. Девять прежних вложенных типов публичны, новый QualificationPairing.Pairing остаётся package-private. Artifact guard проверяет все типы, единственного владельца в библиотеке, отсутствие shadow-копий основных/любых вложенных классов в BOOT-INF/classes, bytecode Java 17 и отсутствие Spring/JPA. Семь существующих Python-тестов автоматически охватывают расширенный список. ArchUnit разрешает только JDK и production-классы своего модуля; правила не ослаблялись.

Сервисное поведение, транзакции, права, история, HTTP/JSON/WS, UI и Liquibase не меняются. Приложение по-прежнему собирается в один boot jar из четырёх дочерних модулей. Выделение хранения #1051 остаётся поздним этапом.

## Package-private и DevTools

Перенос использует уже существующий [временный мост общего package](../phase-1/engine-extraction.md#временный-мост-devtools). Для JVM одинаковый runtime package требует одинакового classloader. Обычный boot jar загружает приложение и engines одним loader; DevTools включает engines в restart loader через сохранённый `META-INF/spring-devtools.properties`. Видимость Java API не расширяется ради переноса.

Проверка использовала настоящий Spring Boot DevTools, скомпилированные bootstrap-классы, engines jar и отдельный probe в том же package. Probe проверяет совпадение своего loader с настоящим TeamPlayoffService и двумя перенесёнными классами, напрямую вызывает package-private подбор пар/таблицу и инициирует настоящий `Restarter.restart()`. Оба запуска прошли через RestartClassLoader. В отрицательном контроле только из частной копии скомпилированных ресурсов убран restart.include: получен ожидаемый IllegalAccessError. Исходники и build output приложения этим контролем не изменялись.

Это минимальный probe-контекст Spring Boot, без БД и сервиса как bean; полноценный пользовательский сценарий проверяется отдельно на boot jar и сервисными тестами. Проверка не выдаётся за ручной запуск IDE или физического устройства.

## Проверки и воспроизведение

`./mvnw -pl padel-tournament-engines -am test` исполняет **63 теста** без Spring/БД: 17 перенесённых (6 QualificationPairing, 11 PlayoffFormat), 42 прежних алгоритмических и 4 ArchUnit. Корневой `./mvnw clean verify` с frontend и отдельной PostgreSQL проверяет сервисные сценарии 8–16 команд и ранний плей-офф. `python3 .github/bin/check-engine-artifact.py` проверяет конечный boot jar; `python3 -m unittest discover -s .github/bin -p test_engine_artifact.py` — отрицательные примеры.

На собственном preview запускаются `python3 automation/ui-preview/playoff-pilot.py run --rehearse` и `python3 automation/ui-preview/playoff-pilot.py extended`. Это существующие браузерные сценарии: полный цикл 8/9/16, квалификация, stale/replay, роли/CSRF, WS/восстановление истории, ранние пары и ранний полуфинал, общий legacy/React/Expo/JWT smoke и репетиция выпуска. При переходе на отдельно собранный неизменённый master и возврате сравниваются все игровые snapshots без metadata.generatedAt, отпечаток колонок схемы и identities/checksums Liquibase.

## Остаток миграции

| Кандидат | Почему отдельный следующий срез |
|---|---|
| EarlyPlayoffSeeding | Чистый расчёт с зависимостью от engines/PlayoffFormat; перебор сценариев и лимит требуют собственного проверяемого объёма |
| RoundRobinRules / RoundRobinStandings | Используют legacy entities и DTO; сначала нужны нейтральные входы/результаты, прямой перенос в engines нарушает границу |
| Остальные сценарии application | Нужны доменные модели и порты по сценарию, согласованная транзакция и адаптеры; перенос JPA-сервисов целиком не подходит |
| Persistence / Liquibase | Поздняя #1051, отдельная приёмка схемы и прежнего jar; физическая PostgreSQL не переносится |

## Результат 24.11.2026

[Безопасная сводка](team-playoff-qualification-qa-2026-11-24.json) содержит точные результаты, исходную ревизию и SHA-256 исходников/проверенных jar. Локальный jar собран до коммита из рабочей копии master `dc5cb1cab053afe6ab8a47f468ff9851838d3809` с переносом классов; этот SHA обозначает основу, а не будущий коммит PR. CI опубликованного коммита проверяется отдельно.

Корневой `clean verify` с frontend: **1364 теста**, 0 failures/errors, 4 штатных browser-пропуска (RegistrationSourceBrowserTest, ParticipationBrowserTest и два PlayerTelegramBrowserTest). В engines — 63 теста без пропусков. Python: 27 GitHub (включая 7 engine guard), 48 relay, 5 worker (1 Linux-only пропуск), 12 preview и 22 release. Три artifact-guard, docs-drift и agent-migration прошли.

Итоговый jar SHA-256 `2399b0cfe186c7f6f1c50e43743aff41c58855f28315844f6f4beff925bbd859`; неизменённый master — `98f7cf39b7ca0625e8f33f106a1de0160f6b80bb467f4dad3f67166d39ca80db`. Полные браузерные сценарии 8/9/16 и расширенные ранние сценарии 9/16 прошли; исходные ожидания пар/слотов/результатов не перезаписывались. Общий smoke и репетиция двух выпусков проверили старый lazy chunk, HTTP-сбои chunk/readiness и восстановление журнала. JWT login/me/events — 200, anonymous me — 401. DevTools-проверка: два успешных запуска и ожидаемый отказ отрицательного контроля.

Отдельное переключение на неизменённый master и штатный возврат нового jar сохранили пять игровых snapshots, отпечаток схемы и журнал Liquibase. До публикации master дополнен только архивацией спеки #1332 (PR #1339); код приложения и уникальный объём этого переноса не пересекаются с архивацией.

Физические устройства, новый ручной PASS владельца и многорепличная конкурентность не проверялись. Однорепличный concurrentSameKey входит в существующий браузерный сценарий. Принятые ограничения предыдущих фаз сохраняются, включая известный #1067 и ограничение Expo Basic 401 в preview (QA-адаптер проверяет настоящий API 200 и отрисовку). Доступы, runtime, полные state/HTTP JSON и частные логи остаются локально.
