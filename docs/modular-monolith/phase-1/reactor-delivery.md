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

## Проверка после обновления ветки 18.11.2026

Ветка объединена с `master` на `0da0d3c5`. В конфликте `tests/browser/README.md` сохранены и описание модульных Surefire reports, и инструкция проверки объединения гостевых профилей. Опубликованная история сохранена через merge-коммит.

На объединённом дереве выполнен корневой `clean verify` с production frontend и отдельной PostgreSQL: **1093 теста, 0 ошибок/падений, 2 штатных пропуска optional browser suites**, все три Maven-проекта SUCCESS. Пять Python-наборов — **91 тест: 90 успешных, один пропуск Linux-only проверки `SO_PEERCRED` на macOS**; docs-drift и agent-migration — 0 расхождений.

Отчёт сквозного прогона и hash jar от 13.11 выше относятся к исходному артефакту.

### Исправление browser-проверки PR Check

Прогон PR Check [44263736295](https://github.com/UstinKO/padel-core-service/actions/runs/44263736295) прошёл сборку и общий browser smoke, но остановился на проверке устаревшей пары: тест ожидал машинный ключ `conduct.history.stale`, а контроллер возвращает локализованное сообщение. Для русской сессии тест теперь проверяет точный русский текст, HTTP 200 и `success=false`, неизменность версии, сетки и результатов после отказа. Ключ исключения в сервисе и поведение приложения не меняются.

При полном локальном повторе обнаружен также HTTP 429 на чтении публичного snapshot в сценарии 16 пар. Действующий API bucket общий для IP: 60 запросов с пополнением за минуту. Между чтениями snapshot добавлен интервал 1100 мс; сохраняются обычная пауза 150 мс после сохранения и одновременная отправка двух запросов с одинаковым ключом идемпотентности. Production-лимиты не меняются, отказавшие мутации автоматически не повторяются. Принятый JSON-эталон сеток сохранён.

После прохождения сценариев 8/9/16 выявлено ещё одно устаревшее ожидание. Изменение #1152, уже находящееся в `master`, разрешает общий посев восьми пар только после полной квалификации (`TeamPlayoffEarlyStageTest.eightTeamsWaitForQualificationBeforeStartingQuarterfinals`). Прежний сценарий трёх готовых четвертьфиналов и одного TBD до завершения квалификации для текущего приложения недоступен. Вместо него проверяются отсутствие кнопки, отказ прямого POST без изменения версии/матчей/рейтинга, завершение последней квалификации, посев всех восьми пар ровно один раз и прохождение до чемпиона.

Финальное сравнение также выявило изменение эталонов для 16 пар и NO_SHOW. В уже включённом в `master` коммите [4c7d8492](https://github.com/UstinKO/padel-core-service/commit/4c7d8492448b84dcacf4ba6e34be2d6abf24e896) добавлены `t.id ASC` для равных команд и отдельный `seedRoundOf16` с предпочтением зеркального посева. Эти изменения предшествуют Maven-переносу. Production-код домена, ресурсы и frontend в #1049 совпадают с `master` `0da0d3c5`; из Java в исправлении проверок изменяется только подготовка синтетических данных под `@Profile("preview")`.

Повтор выявил отдельную нестабильность входных данных девяти пар: `PlayoffPilotData` назначал счета предложениям квалификации, чей порядок зависел от запроса команд без `ORDER BY`. При reset получались разные квалификационные результаты; принятие одного нового результата за эталон не устраняло причину. Подготовка preview теперь задаёт фиксированное расписание по номерам синтетических команд. Для девяти пар: первый круг 1–2, 3–4, 5–6, 7–8; затем 2–9, 1–3, 5–7, 4–6, 8–9, счёт 6:(индекс встречи modulo 6), индекс с нуля. Входные результаты восьми и шестнадцати пар сохранены. Существующий интеграционный тест сравнивает квалификационные пары и счета до и после четырёх reset. В browser-сценарии NO_SHOW также фиксированы семь встреч: 1–2, 3–4, 5–6, 2–7, 4–6, 1–5, 3–7, все 6:2. Подбор пар приложения не меняется.

Исторический `browser-expectations.json` сохранён. Текущий прогон строго сравнивает весь результат с отдельным [эталоном master от 18.11](browser-expectations-2026-11-18.json), без удаления полей из сравнения. После замены недоступного `early` на `qualificationBoundary` остальные отличия от исходного эталона — 51 скалярное значение номера команды в рейтинге, паре или списке стадий; структура сеток, счёт, статусы и next-связи прежние. Изменения проверены по указанному коду #1152 и следующей таблице, а не приняты автоматическим перезаписыванием исходного JSON.

| Сценарий | Текущее ожидание относительно отчёта 13.11 |
|---|---|
| 8 пар | Исходный сценарий совпадает целиком; финал 1–5, чемпион №1 |
| 9 пар | Благодаря фиксированным входным результатам исходный сценарий совпадает целиком: play-in 9–6, QF 5–9, 1–2, 3–7, 8–4; SF 5–3, 1–8; финал 5–1, чемпион №5 |
| 16 пар | 1/8: 1–4, 5–16, 13–8, 9–12, 15–14, 10–11, 3–2, 6–7. QF: 1–15, 5–10, 13–3, 9–6; прежние SF 1–13, 5–9 и финал 1–5, чемпион №1 |
| Квалификация roster-8 | До последнего результата сетка запрещена. После него QF: 1–8, 3–2, 5–4, 7–6; равные проигравшие — 2, 4, 6 по ID. Прежние SF 1–5, 3–7 и финал 1–3, чемпион №1 |
| NO_SHOW | Рейтинг начинается 1, 3, 2, 4, 5; QF 1–4, 3–2. Семь активных пар, №8 исключён из доступных и посева; прежнее правило четырёх участников сетки сохранено |

Бизнес-код и правила в #1049 не меняются. Новые ранние назначения 9–16 пар не заявляются частью этой browser-матрицы; исходный дефект статуса #1067 остаётся зафиксирован. Режим `--record` сохраняет только кандидата в `target`, обычный прогон читает версионированный эталон из Git.

Финальный локальный повтор 18.11.2026 выполнен после исправления fixtures на новом jar с SHA-256 `bd9f7f897de5a5614e1c26d6575079667d939e54523af0c8c17ffa1073152b47`. Корневой `clean verify` повторно успешен: 1093 теста, 0 ошибок/падений, 2 штатных пропуска, включая успешную проверку повторяемости квалификации после четырёх reset. Новый собственный preview использует отдельную PostgreSQL и Mailpit.

| Проверка нового артефакта | Результат |
|---|---|
| Docker build/up и полный `check.py` | PASS: ранний отказ неверного окружения, изоляция сети, Mailpit, общий browser smoke, безопасный reset |
| `playoff-pilot.py run --rehearse` без `--record` | PASS: все пять сценариев, точное сравнение всего JSON, роли/CSRF, устаревшие пара/версия, WS двух зрителей, конкурентность/повтор, восстановление истории |
| JWT без session cookie | login/me/events — 200, анонимный me — 401 |
| Выпуски A/B, старый lazy chunk, rollback, HTTP 404/503 и восстановление журнала | PASS; пять snapshots сохранены, исключён только `generatedAt` |
| docs-drift / agent-migration / diff | 0 расхождений/ошибок |

Безопасные отчёты — `target/modular-pilot/{report,jwt-smoke,rollback}.json` и `target/release-rehearsal/report.json`; runtime с доступами не публикуется. Локальный `sourceRevision` — `0adbdf2e` перед коммитом исправлений, проверенное дерево однозначно связано с приведённым hash jar. GitHub Actions пересобирает и проверяет уже опубликованный коммит отдельно. Новая ручная приёмка здесь не заявляется (`manual=NOT_RUN`); M01–M09 остаются в #1045.
