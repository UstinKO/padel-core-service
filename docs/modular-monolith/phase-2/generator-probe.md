# Проба Java DTO из OpenAPI — LFPT-1198

Дата: 20.11.2026. Основание — [issue #1198](https://github.com/UstinKO/padel-core-service/issues/1198), [спецификация](../../../specs/done/LFPT-1198-openapi-java-dto-probe.md) и [ADR-0002](../adr/0002-openapi-contract-first.md). Исходный master `647389a4` уже содержит оба исправления пилота. Результат относится к генерации моделей; production endpoints и reactor не переключены.

## Воспроизведение

Из корня checkout, с Python 3 и JDK 17 или новее:

```sh
python3 automation/openapi-probe/verify.py
```

Скрипт повторяет два ожидаемых отказа исходных шаблонов, затем дважды запускает чистый `verify` изолированного Maven-проекта, сравнивает SHA-256 всех generated Java, проверяет bytecode major 61 и получает дерево зависимостей. Логи, manifest и summary лежат в `target/openapi-probe-evidence/`. Maven clean удаляет только собственный target пробы. Классические модели, API-клиенты и серверные интерфейсы приложения не генерируются и не заменяются.

Для одной положительной сборки:

```sh
./mvnw -B -f automation/openapi-probe/pom.xml clean verify
```

Maven plugin привязан к `generate-sources`. Java находится только в `automation/openapi-probe/target/generated-sources/openapi/`, реализация тестового MapStruct — в `target/generated-test-sources/` этого проекта. Исходники генерации не коммитятся. В обычный root `modules` проба не добавлена; отдельный шаг PR Check запускает её после сборки приложения и сохраняет evidence.

## Зафиксированная конфигурация

| Настройка | Значение |
|---|---|
| Вход | OAS 3.1.0, исходные четыре JSON из `docs/frontend-react/contract/` |
| Maven plugin | `org.openapitools:openapi-generator-maven-plugin:7.26.0` |
| Генератор / библиотечный шаблон | `java` / `restclient`, только модели |
| JSON | `serializationLibrary=jackson`, `useJackson3=true`, `useSpringBoot4=true` |
| Java / Jakarta | `release=17`, `useJakartaEe=true`, `useBeanValidation=true` |
| Presence | `openApiNullable=false`; обёртка JsonNullable не добавляется |
| Составные модели | `useOneOfInterfaces=true`; отсутствие дискриминатора отдельно проверяется как ограничение |
| Детерминизм | `hideGenerationTimestamp=true`; timestamps/version comments MapStruct отключены |
| Исключённые выходы | APIs, supporting files, docs, generated tests; `supportUrlQuery=false` |
| Локальный шаблон | Только `templates/beanValidation.mustache`, описан ниже |

Название `restclient` выбирает шаблоны инструмента. Spring RestClient, MVC и HTTP-клиент не попадают в зависимости DTO: поддерживающие файлы и API вообще не создаются. [Документация генератора](https://openapi-generator.tech/docs/generators/java/) описывает параметры; положительный результат подтверждён реальной компиляцией и тестами этой пробы.

`prepare.py` читает действующие контракты discovery/account/player-events/public-live и помещает проверочный bundle в target. Локальные `$ref` получают префикс документа, чтобы разные `Problem` не слились в одну схему. SHA-256 исходных четырёх файлов сохраняются в `input-sha256.json`. Единственным редактируемым источником остаются оригинальные JSON. Дополнительные `ProbeFeatures` и `ProbeExtendedClub` явно синтетические: они проверяют отсутствующие в этих JSON readOnly/writeOnly/allOf, wire-enum с дефисом, date и внешний файловый `$ref`.

## Реальные отказы upstream

1. `java/native`, `useOneOfInterfaces=true`, без supporting files не компилирует live oneOf: классы `LiveKing`, `LiveAmericano`, `LivePending`, `LivePlayoff` реализуют `LiveSnapshot`, который шаблон создаёт классом. Ошибка — `interface expected here`; также требуются `AbstractOpenApiSchema` и клиентские helpers. Команда с `-Dprobe.library=native` и пустым `probe.templates` воспроизводится первой стадией скрипта. Подключение HTTP-клиента ради компиляции отклонено.
2. `java/restclient` компилирует модели, но исходный `beanValidation.mustache` добавляет `@NotNull` к `required` независимо от `isNullable`. В Club обязательны ключи nullable-полей: `name`, `address` и другие могут быть null. Проверка аннотации `getName()` воспроизводимо падает без адаптации. Сам по себе успешный JSON round-trip этот дефект не выявляет.

Малый шаблон сохраняет исходные `@Valid` и `beanValidationCore`, а `@NotNull` выдаёт только для required, не readOnly, не nullable. Это воспроизводимая правка входного шаблона, не generated Java. Настоящий Jakarta Validator подтверждает: Club с null в nullable-полях допустим, без id — нарушение `id`. Требование наличия ключа и допустимость null различны: Jakarta `@NotNull` не проверяет присутствие JSON-ключа. Для входных DTO это не решает presence/required, а клубный срез отдаёт только response.

## Проверки и границы результата

Две чистые положительные сборки дали одинаковые **52 Java-модели и одну реализацию MapStruct**. **69 class-файлов моделей** имеют bytecode Java 17. В каждой сборке **12 тестов, 0 failures/errors, 0 skips**. Предшествующие ожидаемые отказы проверяются отдельно и не объявляются успешной компиляцией upstream.

| Предмет | Фактический результат |
|---|---|
| Club и MapStruct | Все девять полей, URI canonicalUrl, кириллица и required-nullable ключи сохраняются при JSON → generated → record → generated → JSON |
| Деньги | `number` становится BigDecimal; большая дробная сумма и цена Tournament проходят без потери точности |
| Даты / зона | LocalDate, OffsetDateTime, сохранение момента и строки IANA-зоны; MapStruct переносит цену, дату и зону в record. Проверки валидности любой строки timeZone генератор не добавляет |
| Enum / коллекции | Wire-значение `in-progress`, неизвестное значение отвергается; nullable enum и действия/участие/отмены player Entry проходят round-trip |
| Account / Problem | Profile компилируется; Problem сохраняет status/code, fieldErrors, retryAfter и blockedUntil |
| `$ref` / allOf | Внешний файловый `$ref` на исходный Club в проверочном bundle и синтетическое расширение с marker компилируются и сохраняют данные |
| Отсутствие / null | Без JsonNullable оба состояния optional Profile.nombre переходят в одинаковый null. Проверено как ограничение; PATCH/request-модели так подключать нельзя |
| readOnly / writeOnly | Клиентский шаблон принимает readOnly `serverValue` из входа и сериализует writeOnly `password`. Отдельный тест фиксирует это поведение. Для входных моделей или секретных полей шаблон непригоден без отдельной доработки |
| Live oneOf | restclient создаёт компилируемый interface, но для существующего Snapshot без discriminator использует пустой type-property; чтение валидного по форме Pending заканчивается InvalidTypeIdException. Live DTO не переносить этим способом |
| Jakarta | Нет javax-зависимостей. `javax.annotation.processing.Generated` в MapStruct — аннотация JDK, не Java EE |

Проба не является полным валидатором OAS/JSON Schema. Она не подтверждает все варианты вложенного allOf/oneOf, точную проверку `const` и наличие каждого required-ключа при десериализации. Для будущих таких переносов нужны отдельные тесты и решение, без изменения публичной схемы ради инструмента.

## Фактическое дерево зависимостей

Compile classpath положительного проекта:

```text
tools.jackson.core:jackson-databind:3.2.4
  com.fasterxml.jackson.core:jackson-annotations:2.22
  tools.jackson.core:jackson-core:3.2.4
jakarta.validation:jakarta.validation-api:3.1.1
jakarta.annotation:jakarta.annotation-api:3.0.0
org.mapstruct:mapstruct:1.6.3
```

Версия `jackson-2-bom` в parent — 2.22.4; фактически управляемый общий artifact `jackson-annotations` имеет версию **2.22**. Это не Jackson 2 databind/core. Ручного изменения версий приложения не сделано.

Test-only: JUnit 6.0.3, AssertJ 3.27.7, Hibernate Validator 9.1.5.Final, его JBoss Logging 3.6.3.Final и ClassMate 1.7.3, Byte Buddy 1.18.15 и служебные JUnit API. Полное дерево создаётся командой `dependency:tree` и сохраняется в evidence. Enforcer проверяет проект транзитивно: Jackson 2 core/databind, javax, Spring, JPA и внешние HTTP-клиенты запрещены. Дополнительно проверяются импорты generated Java.

Сам Maven-генератор содержит собственный Jackson 2 в изолированном plugin classloader для чтения OAS. Это build tool, не dependency DTO или приложения; добавлением Jackson 2 в compile/runtime отказ не обходится. Plugin не подключён к reactor приложения.

## Проверка приложения и локальное ревью

Корневой `./mvnw -B verify` с frontend на отдельной PostgreSQL: **1105 тестов, 0 failures/errors, 2 штатных пропуска** (engines 10, bootstrap 1095). В исполняемом jar нет классов или зависимостей пробы. Runtime-код приложения совпадает с master `647389a4`; исходный jar SHA-256 `e0c640150f8b6f102fba9fd60dfe5c142ff9dfffa64197eb69507a051db336ed`.

На этом jar в новом изолированном preview выполнен `playoff-pilot.py run --rehearse`: Chromium проверил общий session/CSRF/roles/deep-link smoke, React/legacy, квалификацию без навигации, завершение 8/9/16 команд, NO_SHOW, stale/replay, двух зрителей и серверный restore. Репетиция двух синтетических выпусков, ошибки readiness/chunk, сохранение старого lazy chunk, rollback и восстановление журнала прошли; пять snapshots пилота сохранились. Это автоматический прогон агента, не новый ручной PASS владельца и не отдельное доказательство доставки restore обоим экранам после восстановления.

Дополнительно оба клубных GET вернули 200 (список из трёх клубов и карточка). У Expo обычный запрос API по-прежнему получил 401 с Basic challenge внешнего preview-шлюза. Контролируемый browser-адаптер добавил доступ только к same-origin API: фактический ответ 200, десять событий отрисованы. Ограничение обычного preview-клиента сохраняется; production/mobile-код и шлюз не ослаблены.

Локальные evidence: `target/modular-pilot/`, `target/release-rehearsal/` и `target/openapi-probe-evidence/runtime-extra-smoke.json`. Частные runtime, доступы и снимки не публикуются. `docs-drift`, `check-agent-migration`, 48 тестов relay, YAML PR Check, локальные ссылки и `git diff --check` прошли. Ревью подтвердило границы задачи, отсутствие generated Java в Git и новых runtime-зависимостей, связь с исходной issue и сохранение ручного гейта #1045. CI нового PR проверяется отдельно после публикации.

## Решение для следующего шага

Для #1200 допускается **только Club response двух существующих GET**, с этой версией/параметрами и малым шаблоном nullable-валидации. Nullable-ключи сериализуются явно, wire JSON остаётся прежним. В клубном response нет oneOf, readOnly/writeOnly, секретов и различения отсутствующего значения от null. В #1200 необходимо повторить endpoint/baseline-проверки, согласовать String/URI в MapStruct и сохранить один источник OpenAPI. Здесь такого runtime-переключения нет.

#1199 по-прежнему требует завершения приёмки #1045. Успех технической пробы, включение фиксов в master и автоматический smoke не заменяют отдельного решения по остаточным ограничениям ручного пилота.
