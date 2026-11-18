# Выделение PlayoffMatchingEngine

Задача [LFPT-1050](https://github.com/UstinKO/padel-core-service/issues/1050), база — `master` `a29823a9` после мержа reactor #1082. Дата: 18.11.2026. Фактически работают parent и два дочерних Maven-модуля; семь модулей остаются целевой архитектурой. Итоговая независимая ручная приёмка и решение о расширении переноса — #1045.

## Владение и подключение

| Объект | Текущий владелец |
|---|---|
| PlayoffMatchingEngine, его record/enum и package-private seedRoundOf16 | `padel-tournament-engines/src/main/java/com/padle/core/padelcoreservice/service/americano/` |
| Девять исходных unit-тестов и EngineArchitectureTest | `padel-tournament-engines/src/test/java/com/padle/core/padelcoreservice/service/americano/` |
| Единственный bean движка | `TournamentEngineConfig` в legacy source root bootstrap |
| TeamPlayoffService, EarlyPlayoffSeeding, I/O, транзакции и история | прежний корневой `src/`, компилирует только bootstrap |
| Схема и `db/changelog/**`, MVC/API/WS, React и Expo | bootstrap, прежние ресурсы и контракты |

Package и Java API сохраняются. Из движка удалены только импорт Spring и `@Component`; тело класса побайтно совпадает с исходным после удаления этих строк. Перенесённый unit-тест совпадает с исходным целиком. Новый `seedRoundOf16` из #1152 также переносится без правок. Потребители, порядок входа/выхода, tie-break, warning/null, приоритеты и сохранение результатов не переписываются.

В библиотеке нет compile/runtime/provided/system-зависимостей: Maven Enforcer запрещает их, в том числе транзитивные. JUnit/AssertJ и ArchUnit 1.5.1 подключены только как test. ArchUnit проверяет production-bytecode модуля и допускает JDK и собственный package; bootstrap не входит в его classpath. Legacy-код не объявляется уже соответствующим целевой архитектуре.

В boot jar движок находится только в `BOOT-INF/lib/padel-tournament-engines-0.0.1-SNAPSHOT.jar`; копии в `BOOT-INF/classes` нет. Сам library jar не перепаковывается Spring Boot. Compiler release остаётся 17.

## Временный мост DevTools

Сохранённый package объединяет классы из двух jar. `EarlyPlayoffSeeding` вызывает package-private `seedRoundOf16`; для доступа JVM требует одинаковый runtime package, включая classloader. При обычном запуске boot jar он общий. DevTools по умолчанию делит библиотеки и код приложения между base/restart loader.

`src/main/resources/META-INF/spring-devtools.properties` включает только jar `padel-tournament-engines` в restart loader. Это не Spring-зависимость движка и не расширение Java API. Мост действует до отдельного пересмотра package/API границы; массовый rename в пилот не входит.

Проверен реальный Spring Boot с DevTools и отдельной тестовой БД: без include сервисный вызов посева 1/8 завершился `IllegalAccessError`; с include тот же вызов вернул восемь пар, в контексте один bean. Проверка использовала скомпилированный bootstrap и library jar, не только отражение над конфигурацией.

## Команды

```sh
# Только модуль движков, без Spring и PostgreSQL: 9 unit + 1 architecture.
./mvnw -pl padel-tournament-engines -am test

# Перенесённый исходный набор выбирается по его новому владельцу.
./mvnw -pl padel-tournament-engines -am -Dtest=PlayoffMatchingEngineTest test

# Полная сборка приложения с frontend и своей PostgreSQL.
./mvnw clean verify

java -jar padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar

# Проверка единственного владельца engine в конечном boot jar, также выполняется CI.
python3 .github/bin/check-engine-artifact.py
```

Корневой выбор теста bootstrap по-прежнему проходит через engines. Для изолированного engine-теста нужно выбирать engines через `-pl`: проверку отсутствующего теста в bootstrap не ослабляем.

`docs-drift.py` обнаруживает и legacy source root, и `padel-*/src/main/java`. Проверены перенос класса между этими деревьями и отказ для отсутствующего класса, который остался только в `target`. Generated/build output не становится источником истины.

## Проверки

| Проверка | Фактический результат |
|---|---|
| Сравнение исходников | Тело engine после удаления Spring-регистрации и исходный unit-тест сохранены |
| Engines отдельно | 10 тестов, 0 ошибок/пропусков, без БД и Spring |
| Отрицательный контроль зависимости | Добавление compile-зависимости spring-context отклонено Enforcer; POM восстановлен |
| Отрицательный контроль bytecode | Временный production-класс с javax.sql.DataSource отклонён ArchUnit; удалён, чистый модуль повторно успешен |
| Корневой clean verify, production frontend | 10 engine + 1084 bootstrap = 1094 теста, 0 ошибок/падений, 2 штатных optional browser-пропуска |
| Реальный Spring-контекст | Один bean; fixtures и завершение турнира выполняются прежним TeamPlayoffService |
| DevTools с jar библиотеки | Отрицательный контроль без моста и успешный посев с мостом, один bean |
| Итоговый boot jar | Одна library-копия engine, без Spring в его bytecode, class version 61 |
| Production Docker build и извлечённый jar | PASS: один engine в library, исходные ресурсы/Expo побайтно сохранены, ZIP-дубликатов нет |
| Постоянный CI-check engine jar | PASS на host- и production-артефактах; отрицательные unit-контроли для дубликата, отсутствующей библиотеки, версии bytecode и Spring-ссылки |
| Пять Python-наборов | 98 тестов: 97 успешных, один Linux-only пропуск на macOS |
| Новый изолированный preview, полный check.py | PASS: ограничения окружения/сети, Mailpit, роли/session/CSRF, legacy/React/Expo, безопасный reset |
| playoff-pilot.py run --rehearse без --record | PASS: 8/9/16 до чемпиона, граница квалификации, NO_SHOW, stale/version, роли/CSRF, одновременный повтор, история/restore и настоящие WS двух зрителей; весь текущий JSON совпал |
| JWT без session cookie | login/me/events — 200; анонимный me — 401 |
| Выпуски A/B, прежний lazy chunk, HTTP 404/503 и journal recovery | PASS, все пять snapshots сохранены кроме generatedAt |
| Предыдущий reactor #1049 → модульный engine → native rollback → модульный engine | PASS на той же собственной БД: пять snapshots, структура схемы и Liquibase checksums сохранены; текущий record восстановлен |
| Остановка и повторный запуск preview после переключений | PASS, сохранён текущий выпуск |

SHA-256 проверенного нового jar: `7420e5b2e75d46fbc75a85488938257871d17ab4fa3171d33dfd51f1d786714e`. Предыдущий jar #1049 — `bd9f7f897de5a5614e1c26d6575079667d939e54523af0c8c17ffa1073152b47`, sourceRevision `0adbdf2e29dd904985be2c862f73886e599b76fd`; это сохранённый артефакт проверки #1082 до коммита исправлений. Он прочитан по проверенному hash, чужие preview/БД не затрагивались. Новый локальный артефакт собран из рабочего дерева на базе `a29823a9` до итогового коммита; hash связывает результаты с проверенным содержимым.

`jdeps` библиотеки возвращает только `java.base`; Maven dependency tree содержит только test-библиотеки. Отчёты приложения остаются в `padel-bootstrap/target/surefire-reports/`, движка — в `padel-tournament-engines/target/surefire-reports/`. CI читает оба модульных дерева и отдельно проверяет конечный jar скриптом `check-engine-artifact.py`. Служебные META-INF-ресурсы в boot jar проверяются с учётом штатного размещения в корне архива.

Текущий browser-эталон — [версия от 18.11](browser-expectations-2026-11-18.json), принятая при исправлении #1049. Он учитывает уже существующие изменения #1152 и фиксированное расписание синтетических fixtures; в #1050 его не меняем. [Исторический отчёт от 13.11](browser-baseline.md) и [исходный JSON](browser-expectations.json) сохраняются. Дефект статуса #1067 и отдельная ручная M01–M09 в #1045 не объявляются исправленными/выполненными этим переносом.

Для повторов используются только собственные отдельные PostgreSQL/preview/Mailpit. Пароли, runtime и исходные логи не публикуются; безопасные отчёты остаются в `target/modular-pilot/` и `target/release-rehearsal/`. Результат GitHub Actions последнего опубликованного коммита проверяется отдельно от локального успеха.
