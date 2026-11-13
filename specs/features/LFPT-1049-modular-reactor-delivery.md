# LFPT-1049: Feature многомодульный монолит, фаза 1 — подготовить рабочую Maven-сборку и доставку

## Статус

approved

Постановка первой фазы основана на согласованных семи модулях и подтверждённом пилоте. Реализация и проверки этой задачи ещё не выполнялись.

## Источник

[Issue #1049](https://github.com/UstinKO/padel-core-service/issues/1049), родитель [LFPT-1041](LFPT-1041-modular-monolith-analysis.md), прямое поручение владельца сформировать первую фазу после анализа кода. Техническая постановка из issue, без промежуточного клиентского запроса.

## Контекст / зачем

Создать рабочий Maven reactor как подготовку к PlayoffMatchingEngine: корневой parent/aggregator, padel-bootstrap с единственным исполняемым jar и padel-tournament-engines как library-модуль. Остальные пять согласованных модулей вводятся позднее. Движок пока остаётся на прежнем месте; вся старая функциональность работает.

[Подробный анализ](../../docs/modular-monolith/phase-1/code-analysis.md), [план первой фазы](../../docs/modular-monolith/phase-1/README.md), [матрица TC01–TC25](../../docs/modular-monolith/phase-1/test-cases.md).

## Связь с MASTER.md

Бизнес-правила, роли и доступ по клубу, посев/результаты турниров, регистрация/резерв/участие, платежи и история сохраняются. Архитектурное направление уточнено владельцем; новых инвариантов эта задача не вводит.

## Требования

### Функциональные

#### Цель и граница
Создать рабочий Maven reactor как подготовку к PlayoffMatchingEngine: корневой parent/aggregator, padel-bootstrap с единственным исполняемым jar и padel-tournament-engines как library-модуль. Остальные пять согласованных модулей вводятся позднее. Движок пока остаётся на прежнем месте; вся старая функциональность работает.

#### Решение для безопасного промежуточного состояния
- Корневой src/ остаётся единственным legacy-деревом. Bootstrap явно использует корневые main/test sources и ресурсы; корневой packaging=pom их не компилирует. Никаких копий одного класса и двойного component scan.
- Сохраняются classpath db/changelog/**, строки подключения, схема и расположение PostgreSQL. Это не выделение persistence.
- Канонический runtime jar: padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar; библиотечный jar не repackage. Release archive/manifest/runner и QA evidence могут сохранять корневой target как отдельный стабильный output.
- Frontend остаётся в frontend/, Vite outDir и mobile static/app не переносим. Frontend plugin/toolchain/generated-resources задаются от корня и выполняются один раз в bootstrap; output включается в runtime jar.
- Surefire reports модульные, working directory forked tests — корень для существующих src/.../tests/browser-путей. Main class/package сохраняются, @SpringBootTest discovery проверяется.

#### Что адаптировать в том же PR
pom.xml и два дочерних POM; Dockerfile (reactor/context/artifact), .dockerignore для вложенных target; preview Dockerfile и Dockerfile.dockerignore; automation/releases/build.py, preview_release.py, rehearse.py (явный app jar); прогоны/отчёты/target-пути .github/workflows/pr-check.yml, deploy.yml при необходимости; ссылки/инструкции сборки и агентских команд. Не переписывать нетронутые правила релиза/rollback.
docs-drift продолжает читать root src; позже должен видеть moved sources по необходимости. Нельзя получить зелёный отчёт пропуском всех исходников.

#### Проверки
Корневой ./mvnw verify с отдельным PostgreSQL и production frontend; backend и frontend suites; Docker build; immutable release artifact inspect (React manifest и точное совпадение Expo); preview build/up/smoke, release rehearsal/rollback; исходная UI-матрица; docs-drift и все релевантные Python suites. Команды -Dtest и spring-boot:run документировать для reactor (bootstrap с зависимостями в reactor/установленными локально), без стандартного goal на parent.

#### Критерии приёмки
- [ ] Корневая verify проходит, единственный boot jar запускается; неподготовленный engines не ломает reactor тесты.
- [ ] Библиотеки не получают web/JPA/frontend/repackage через ошибочное наследование; версии/Enforcer общие, Java release 17 сохранён.
- [ ] Нет потери/двойной упаковки static/templates/email/React/Expo/Liquibase; frontend собирается один раз.
- [ ] Все потребители runtime jar и тестовых отчётов используют правильные явные пути; Docker не выбирает wildcard library jar.
- [ ] Полный smoke, исходная матрица и rehearsal/rollback пройдены, не отложены в следующую задачу.
- [ ] PlayoffMatchingEngine и алгоритмы ещё не перенесены, БД не менялась; исключение legacy src адресно описано.


### Нефункциональные




Каждый PR самостоятельно оставляет приложение работоспособным: корневая полная сборка/verify, отдельный PostgreSQL для integration, smoke итогового jar/образа, действующие MVC/API/React/Expo, rollback. Реальные пользовательские БД/уведомления не используются. Java release 17, бизнес-правила и JSON/WS не меняются. Один слой и отдельный PR; без pipeline:ready, автомержа и деплоя в рамках подготовки. Документация — docs/modular-monolith/, спека с настоящим LFPT-номером.

Пилот сохраняет пути db/changelog, схему/данные/подключение. Временный root legacy src имеет одного владельца и один путь компиляции.

## Вне скоупа

Выделение persistence, физический перенос БД/Swarm, новые бизнес-правила, внутренний HTTP/микросервисы, полный рефакторинг TeamPlayoffService (#509), остальные движки и API codegen.

## Изменения в системе

### API

Внешний HTTP/WS контракт без изменений.

### БД

Нет Liquibase и production-изменений данных. Синтетические fixtures только в своей тестовой среде.

### UI

Внешний UI без изменений; browser/API smoke необходим по границе задачи.

### Документация

docs/modular-monolith/phase-1/ и актуальные карта/команды/пути по затронутой области; docs/Home.md, docs/architecture.md и .agents/PROJECT.md при реализации сборочных изменений. Фактическое состояние не подменяется будущими семью модулями.

## Критерии приёмки

Чек-лист конкретного issue выше обязателен полностью. Дополнительно:
- [ ] Результаты привязаны к последнему SHA/артефакту; не выполненные проверки не обозначены успешными.
- [ ] После этого PR приложение работоспособно без будущего PR; неизменность поведения проверена подходящими тестами.
- [ ] docs-drift, релевантные Python suites, diff и локальное ревью пройдены.
- [ ] Документы обновлены в том же PR; для реализации один слой/отдельный PR с Closes #1049.

## Зависимости

Зависит от #1048.

## Edge cases

Равная стоимость/порядок перечисления, три приоритета, пустой/нечётный вход, play-in/TBD/backfill, no-show, stale pair/version, чужой клуб, rollback/history/after-commit. Для сборки: CWD тестов, один main/repackage/bean, frontend один раз, модульные отчёты, явный runtime jar, отсутствие shadow-класса. Применимость — по матрице и конкретному изменению; недостижимый из UI вход не получает выдуманную кнопку.

## Открытые вопросы

Неразрешённых бизнес-вопросов нет. Точные fixtures/ожидаемые результаты и дефекты фиксируются исходными #1047/#1048; технический выбор внутри согласованной границы выполняется по анализу, без изменения поведения.
