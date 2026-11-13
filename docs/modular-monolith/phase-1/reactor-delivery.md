# Рабочая Maven-сборка и доставка первой фазы

Задача [LFPT-1049](https://github.com/UstinKO/padel-core-service/issues/1049). Это промежуточное работоспособное состояние: три Maven-проекта, один исполняемый jar. Семь модулей остаются целевой архитектурой.

## Владение исходниками и зависимостями

Корневой `pom.xml` — `com.padle.core:padel-parent`, packaging `pom`, parent/aggregator. Здесь версии, Java release 17, dependencyManagement и наследуемый Enforcer. Root не компилирует `src/`.

`padel-tournament-engines` — пустой library jar без web/JPA, frontend и Spring Boot repackage. Пустота разрешена на этом этапе; физический перенос PlayoffMatchingEngine и его unit-тестов выполняется в #1050.

`padel-bootstrap` зависит от библиотеки и единственный компилирует корневые `src/main/java`, `src/test/java`, `src/main/resources`, `src/test/resources`. Это адресное временное исключение legacy, а не общие исходники для нескольких модулей. Main class/package и component scan сохраняются. Все старые runtime/test-зависимости и processor paths остаются здесь. Maven Surefire запускает forked tests из корня, отчёты пишет в модульный target. Ошибочный `-Dtest` в bootstrap не превращается в успешный пустой прогон.

## Команды из корня

```sh
# Полная сборка с production React и отдельной тестовой PostgreSQL.
./mvnw clean verify

# Один набор без пересборки frontend; остальные тесты явно не объявляются проверенными.
./mvnw -Dskip.frontend=true -Dtest=PlayoffMatchingEngineTest test

# Сборка и запуск единственного приложения; окружение своей среды задаётся отдельно.
./mvnw package -DskipTests
java -jar padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar

# DevTools/Spring Boot Maven goal: сначала установить библиотеку и parent в local repository.
./mvnw install -DskipTests
./mvnw -pl padel-bootstrap spring-boot:run
```

`spring-boot:run` не вызывается на parent и не запускается с `-am`: goal иначе дойдёт до проекта без main. `padel.root=${maven.multiModuleProjectDirectory}` определяет корень для legacy sources/resources и frontend; команды выше выполняются из корня. Root `clean` очищает общий generated output, child `clean` — свой target. `-Dskip.frontend=true` предназначен для коротких backend-проверок, полный проверяемый выпуск собирается с frontend.

## Артефакты и ресурсы

| Назначение | Путь |
|---|---|
| Единственный runtime jar | `padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar` |
| Library jar | `padel-tournament-engines/target/padel-tournament-engines-0.0.1-SNAPSHOT.jar` |
| Surefire reports | `padel-bootstrap/target/surefire-reports/` |
| Legacy классы и тестовые классы | `padel-bootstrap/target/classes/`, `padel-bootstrap/target/test-classes/` |
| Node/pnpm, generated React | `target/frontend-toolchain/`, `target/generated-resources/frontend/static/web/` |
| Release manifest/archive/runner, QA evidence | прежний корневой `target/` |

Frontend plugin подключён только bootstrap, выполняет frozen install и Vite build один раз. Vite outDir, `frontend/` и Expo `src/main/resources/static/app/` сохраняются. Bootstrap включает весь legacy resource tree и generated frontend; templates/email/static и `db/changelog/**` сохраняют classpath-пути. Контракт БД, changeset, строки подключения и окружение не меняются.

Production Dockerfile получает три POM для dependency cache, затем оба дерева модулей и legacy src/frontend, собирает reactor и копирует только явный bootstrap jar. `.dockerignore` исключает вложенные target. Минимальный preview Docker context пропускает только этот runtime jar. Release build, preview release, rehearsal и пилот используют тот же явный путь. После общего smoke CI выдерживает 60 секунд по штатному AUTH-лимиту 10 входов в минуту и запускает `playoff-pilot.py run --rehearse` одним вызовом; повторные независимые логины не накладываются на заполненный bucket. Production-ограничения сохраняются. CI читает модульные Surefire reports, но сохраняет прежние пути evidence и release archive, поэтому deploy.yml не требует смены SCP-путей.

`docs-drift.py` продолжает читать корневой src: исходники ещё не перенесены, проверка не отключена. После #1050 поиск должен учитывать moved sources отдельным изменением.

## Проверки и ограничения

Проверено 13.11.2026 на отдельной PostgreSQL и новом собственном preview. SHA-256 runtime jar: `0e26c7870f32a08be1059cc60870226f335633cbb32a71b4e7da9b2968fa271a`. Ревизия каждого запуска фиксируется в `target/modular-pilot/report.json`, `target/release-rehearsal/report.json` и описании PR; файлы runtime с доступами не публикуются.

| Проверка | Фактический результат |
|---|---|
| Корневой `./mvnw clean verify`, production frontend включён | 987 тестов, 0 ошибок/падений, 2 штатных пропуска optional browser suites; все три проекта SUCCESS |
| Выбор `PlayoffMatchingEngineTest` из корня | 9 тестов успешны, пустой engines не прерывает прогон |
| Несуществующий `-Dtest=NoSuchReactorBoundaryTest` | Ожидаемый отказ bootstrap; пустой library SUCCESS не скрывает опечатку |
| Отдельный `ParticipationBrowserTest` с `-Dparticipation.browser=true` | 1 реальный browser-сценарий успешен; test CWD и SpringBootTest discovery работают |
| Frontend `check` / `test:e2e` | 44 / 52 теста успешны |
| Python suites .github/bin, relay, worker, preview, releases | 8 + 48 + 5 + 8 + 22 = 91 успешный тест |
| Effective POM engines | Нет runtime/test dependencies, frontend/boot plugins; Enforcer и Java 17 наследуются |
| Содержимое host jar | Побайтно совпали все исходные legacy resources, generated React и Expo; нет повторных ZIP entries; один PlayoffMatchingEngine с bytecode 61; библиотека без BOOT-INF |
| Production `docker build` и извлечённый jar | SUCCESS, правильный Start-Class/ресурсы, React manifest и точный Expo |
| Immutable release build, manifest/archive/runner | SUCCESS; корневые target-пути пакета сохраняются |
| Новый preview build/up, полный `check.py` | SUCCESS; отдельная БД/маркер/Mailpit, ограничения сети и ошибочного окружения, session/CSRF, legacy/React/Expo, seed/reset |
| `playoff-pilot.py run --rehearse` после общего smoke | PASS: 8/9/16, early/TBD/backfill, NO_SHOW, stale/version/repeated/concurrent, роли/CSRF, история/restore, настоящие WS обоих зрителей, JWT; логические ожидания сверены с прежним JSON-эталоном |
| Выпуск A/B, прежний lazy chunk, rollback, HTTP 404/503, pending recovery | PASS; все пять сохранённых snapshots прежние, кроме generatedAt |
| Старый монолит → reactor → явный rollback к старому → reactor | PASS на той же синтетической БД, пять snapshots, структура схемы и Liquibase checksums сохранены; прежний jar #1048 — `a99793a786a5c3f711ab367c1e30ec032e13872d`, SHA-256 `aeef762a9c1cd027d45cd1250048f3edceb3937cc404ae890aa46e072f39ca30` |
| docs-drift / agent-migration / diff / локальное ревью | 0 расхождений; Java/SQL/бизнес-контракты задачи не менялись |

Для отката к прежнему монолиту использованы существующие `preview_release.candidate` и `Releases.deploy/rollback`: сохранены текущий record и пять snapshots, подготовлен record из проверенного прежнего jar, выполнены переключения и в finally восстановлен текущий record. Старый jar прочитан из сохранённого исходного артефакта; среда #1048 и пользовательские БД не затрагивались. Повтор требует заранее сохранённого прежнего jar с указанным hash в собственном preview; `smoke --rehearse` остаётся обычной воспроизводимой командой для QA-вариантов.

Первый запуск двух независимых browser-команд подряд получил штатный `login?error=rate-limit`. Исправлена последовательность CI: после общего smoke — восстановление минутного AUTH bucket, затем один `run --rehearse`. Повтор этой последовательности успешен. Ни лимиты приложения, ни конкурентная проверка одного ключа не ослаблены; мутирующие операции не повторяются после отказа.

`RegistrationSourceBrowserTest` остаётся отдельным CI job с его выделенной PostgreSQL; локальный полный verify штатно его пропускает. Результат CI последнего коммита проверяется в PR отдельно от локального успеха. CI сохраняет безопасные отчёты pilot/JWT/rollback/rehearsal как `modular-pilot-qa`, без fixtures/runtime/доступов.

Полная матрица исходной #1048 повторена на новом артефакте; её принятый эталон не переписан под сборочные изменения. Отдельная полная ручная приёмка M01–M09 остаётся в #1045. Исходный дефект статуса после play-in — #1067, изменение Maven его не исправляет.
