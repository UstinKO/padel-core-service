# Фаза 1: подробный анализ пилота и сборки

Дата: 13.11.2026. Пилот и семь целевых модулей подтверждены владельцем. Область анализа — перенос PlayoffMatchingEngine и все обнаруженные потребители его сборки/артефактов, не полный аудит всех бизнес-доменов.

## Срез и пределы доказательств

Начальный исторический срез — `5c71977`; детальный анализ первой фазы выполнен на `5980db35362b34e0851d1761657220af914b4678`. Актуальный инвентарь: 408 Java-файлов приложения и 148 Java-файлов тестов. Входящие изменения касаются участия/резерва и UI; их сценарии входят в общую регрессию перед каждым переносом.

Через GitHub connector сверены blob SHA движка, TeamPlayoffService, TeamPlayoffEndToEndTest, POM, Dockerfile, PR Check, release build, preview Dockerfile и Vite config: содержимое совпало с локальным на момент проверки. Blob движка: `db93a14db6b4946d0e67e8cd1c971aa55b29593a`. Перед реализацией снова проверить текущий master.

Выполнено `./mvnw -Dskip.frontend=true -Dtest=PlayoffMatchingEngineTest test`: **8 тестов, 0 ошибок, 0 пропусков, BUILD SUCCESS**. Компиляция Java release 17 успешна. Full verify, PostgreSQL integration, Docker, браузер и ручные нажатия в текущем анализе не выполнялись. Успех unit не выдаётся за исходную e2e-приёмку.

## Движок и неизменяемая граница

[PlayoffMatchingEngine.java](../../../src/main/java/com/padle/core/padelcoreservice/service/americano/PlayoffMatchingEngine.java) — 192 строки; из production-внешних зависимостей только `@Component`, остальные импорты JDK. Публичный API: Candidate(teamId, seed, winStreak), Pairing(team1Id, team2Id), MatchingResult(pairings, allConstraintsSatisfied, warning), три PriorityOrder и две перегрузки match.

Алгоритм перечисляет разбиения на пары и лексикографически сравнивает стоимость. Замена лучшего происходит только при строго меньшей стоимости; при равенстве сохраняется первый найденный вариант. Поэтому сортировка входа, порядок перечисления и выходных пар значимы. Не оптимизировать сложность и не добавлять сортировку в пилоте.

- SEED_FIRST: посев → разведение → реванш, default overload; прямого production-вызова этой перегрузки поиск не выявил.
- REMATCH_FIRST: реванш → разведение → посев.
- STRENGTH_FIRST: разведение → реванш → посев.
- Пустой вход: пустые пары, true, null warning. Нечётный: IllegalArgumentException. allConstraintsSatisfied относится к отсутствию нарушений разведения/реванша; не переопределять как нулевую стоимость всех критериев.
- Warning при невозможности полного разведения возвращается как текст; текущий service логирует его. Нового UI для warning пилот не создаёт.

## Фактические потребители

Java-пути ниже — относительно `src/main/java/com/padle/core/padelcoreservice/`.

| Вызов | Откуда приходят данные | Где используется | Риск переноса |
|---|---|---|---|
| TeamPlayoffService.seedPlayoffPairs | Отсортированный квалификационный рейтинг, matchesWon, история completed матчей | Play-in, первоначальные/ранние пары, backfill оставшихся TBD | Смена порядка входа или REMATCH_FIRST изменит пары |
| TeamPlayoffService.seedNextPlayoffStage | Победители, общий seed, matchesWon, история турнира | Переход завершённой стадии к следующей, включая финал | STRENGTH_FIRST и существующий порядок выходных пар должны сохраниться |
| PlayoffMatchingEngineTest | Фиксированные Candidate/истории | 8 unit-тестов | Перенести вместе с классом, не заменить тесты упрощёнными |

Других production-потребителей поиск имени/поля matchingEngine по Java не выявил. Это состояние среза, не запрет будущих вызовов.

Сервис зависит от JPA, history/conduct, WebSocket; сам движок их не знает. Сохранить package на пилоте: `com.padle.core.padelcoreservice.service.americano`. Только физическое размещение класса/test меняется. Удалить @Component и зарегистрировать ровно один @Bean в bootstrap. TeamPlayoffService остаётся в legacy и получает прежний тип через конструктор.

## Реальный путь из браузера

Контроллер: [TeamPlayoffViewController](../../../src/main/java/com/padle/core/padelcoreservice/controller/view/americano/TeamPlayoffViewController.java). Шаблон: [tournament-playoff.html](../../../src/main/resources/templates/admin/americano/tournament-playoff.html).

| Действие | Маршрут / параметры |
|---|---|
| Административный экран | GET /tournaments/team-playoff/admin/{tournamentId} |
| Старт квалификации | POST /tournaments/team-playoff/admin/{tournamentId}/init-qualification |
| Формирование сетки | POST /tournaments/team-playoff/admin/{tournamentId}/init-playoff |
| Пересборка | POST /tournaments/team-playoff/admin/{tournamentId}/regenerate-playoff, expectedConductVersion |
| Счёт квалификации | POST /tournaments/team-playoff/api/qual-matches/{matchId}/result |
| Счёт плей-офф | POST /tournaments/team-playoff/api/playoff-matches/{matchId}/result, expectedTeam1/expectedTeam2 и действующий JSON/body |
| Публичное чтение | GET /tournaments/team-playoff/{tournamentId}; публичный snapshot по действующему read API |
| Realtime | /topic/team-playoff/{tournamentId}, существующие MATCH_*, TEAM_ADVANCED, TOURNAMENT_FINISHED |

Скрипт формы использует conductFetch, CSRF и защиту актуального состава; ожидаемые поля фиксировать по конкретному handler, не добавлять expectedConductVersion всем операциям одинаково. `assertCanManageTournament` и server-side роли сохраняются. Достижимость кнопки и HTTP/redirect/flash-результат проверяются отдельно: текущий controller может обработать ошибку через redirect, отсутствие HTTP 500 не означает успех операции.

## Транзакции, история и события

TeamPlayoffService: readOnly по классу, write-аннотации на сценариях; дополнительно @ConductChange. ConductChangeAspect использует TransactionTemplate и оборачивает history.record. Это существующая часть атомарности, а не только @Transactional. WebSocketService отправляет через AfterCommit; после переноса нельзя получить второй bean, двойную отправку, потерю истории или преждевременное событие.

Пилот не меняет эти классы кроме необходимого wiring. В #1047 фиксируются интеграционные ожидания через реальные proxy: состояние и history откатываются вместе, после отката нет подтверждающего события, stale pair/version обрабатываются прежним образом. Идемпотентность/конкурентные гарантии не придумываются; обнаруженный текущий дефект получает отдельный issue.

## Что уже покрыто

- PlayoffMatchingEngineTest: три приоритета и различия между ними, разведение, неизбежный реванш, пустой и нечётный вход — 8 пройденных unit-тестов.
- TeamPlayoffEndToEndTest: параметризованный полный турнир 8–16 команд, no-show, малое число команд, snapshot/итоговые места, история/восстановление и обмен пар. Это service integration, а не браузер.
- Дополнительные группы: EarlyStage, TeamNumbering, DuplicatePrevention, Queue, NoShow, ExtraTeamOpponent, PlayoffRematchSwap, conduct/history и controller-проверки. Прогоны на БД сейчас не выполнены; покрытие и применимость уточняет #1047.
- PreviewFixtureService создаёт шесть демо-мероприятий с небольшим набором регистраций; не обеспечивает готовые турниры 8/9/16 пар и отдельную роль чужого клуба. Нужна fixture-задача; обычный seed нельзя считать достаточным.

## Карта сборочных зависимостей

| Файл/группа | Текущая связь | Требование первой фазы |
|---|---|---|
| pom.xml | Один jar, frontend-plugin, root src/resources, Surefire, Enforcer, MapStruct | Parent/aggregator + bootstrap/engines; не наследовать application deps и frontend/repackage в engines |
| frontend/vite.config.ts | outDir ../target/generated-resources/frontend/static/web | Оставить root output; bootstrap включает его как ресурс, frontend выполняется один раз |
| Dockerfile | COPY pom/src/frontend, root target/*.jar | Reactor descriptors/context и явный bootstrap jar; wildcard запретить |
| .dockerignore | target исключён только как текущий путь | Учесть вложенные target, не загружать локальные библиотечные artifacts в builder |
| automation/ui-preview/Dockerfile и Dockerfile.dockerignore | COPY/allowlist корневого jar | Оба указателя обновить вместе |
| automation/releases/build.py | default root jar; Expo root src | Jar явный bootstrap; Expo source root остаётся |
| automation/releases/preview_release.py, rehearse.py | Прямой root jar | Тот же канонический runtime artifact; корневые release archives сохраняются |
| automation/releases/artifact.py | Проверяет BOOT-INF/classes/static/web/app, Vite graph и Expo digest | Не ослаблять inspect/verify; engine окажется в BOOT-INF/lib, ресурсы остаются classpath-совместимыми |
| PR Check / deploy workflow | Root verify, surefire paths, browser -Dtest, toolchain, runner/archive paths | Модульные отчёты/выбор теста; root toolchain/evidence/archives сохранить явно; команда не должна успешно пропустить bootstrap |
| src/test File/Path и browser ProcessBuilder | Относительные src/main/resources, tests/browser, target/browser-qa | Surefire fork working directory — корень; reports — модульные; проверить resource reading и запуск Node |
| docs-drift | Root src/main/java и root migrations | В первой фазе legacy src остаётся; учесть moved engine в инвентаризации, не маскировать исчезновение исходников |
| Агентские команды / Maven run | Запуск на root jar-проекте | Документировать reactor-aware команды, main/repackage только bootstrap |

## Выбранное промежуточное размещение

Корень: parent POM, единственное legacy src/, frontend/, mobile-app/, automation/. padel-bootstrap — POM/конфигурация единственного boot jar, явно использует root legacy main/test/resources. padel-tournament-engines — собственные src/main/java и src/test/java, куда #1050 переносит только движок/unit.

Временная конфигурация sourceDirectory/testSourceDirectory/resource roots должна быть явной и покрытой проверками. Корневой POM с packaging=pom исходники не компилирует; копии запрещены. После появления application/web/integrations код переезжает по срезам, а остатки persistence — отдельной поздней #1051. Реестр root legacy очищается поздними задачами; это не финальная физическая раскладка семи модулей.

Канонический runtime jar первой фазы: `padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar`. Root `target` остаётся для frontend generated output/toolchain, evidence, release manifest/archive/runner. Потребители различают эти артефакты. Maven workingDirectory и тестовые отчёты не должны зависеть от случайного CWD.

## Итог анализа

Малый перенос алгоритма возможен без новой схемы/контракта и без выделения persistence. Уменьшать объём до «перенести .java» нельзя: рабочая сборка, упаковка и её потребители входят в отдельную целостную #1049. Исходная проверка и итоговая приёмка разнесены по закрываемым задачам без циклов; [план первой фазы](README.md).

## Проверка подготовленных постановок

После обновления документов: docs-drift и check-agent-migration — 0 расхождений; Markdown-ссылки/пробелы и git diff --check — успешно. Python suites: .github/bin — 8, telegram-relay — 48, codex-worker — 5, ui-preview — 6, releases — 22; всего **89 тестов успешно**. Relay/release сначала ограничивались sandbox (ps/локальные сокеты); повтор с разрешённым расширением выполнен успешно. Это unit-проверки автоматики, не запуск Docker/preview/e2e и не production-откат.
