# Чтение круговой таблицы в application

[LFPT-1377](https://github.com/UstinKO/padel-core-service/issues/1377) продолжает baseline #1375 / PR #1379 и final-order #1376 / PR #1381. Исходный master — `da2be354e62e57f43568f83bc102a339e5acda1e`. Реализован второй application-сценарий после клубов; следующий шаг — независимая приёмка #1378.

## Владение и вызовы

RoundRobinRankingQuery с constructor DI и readOnly REQUIRED получает собственный RoundRobinReadPort. Порт возвращает Optional<RoundRobinReadSet> с материализованными нейтральными records/enum. Отсутствие турнира — RoundRobinNotFound; прежний фасад переводит его в ResourceNotFoundException с тем же сообщением. HTTP 500 отсутствующего турнира и правила доступа сохраняются; исправление — #453.

RoundRobinRankingComposer — единственный владелец композиции: конфигурация/fallback, totals/completed/finished, круговая таблица, дополнительные встречи и итоговые места. Он вызывает существующие чистые SETS/POINTS/final-order engines напрямую. Полные модели и точные типы — в [контракте](application-contract.md). Компараторы, личные встречи и назначение слотов не копируются.

LegacyRoundRobinReadAdapter читает прежние источники в прежнем порядке: tournament → ranking teams → rounds → all matches → additional teams → placement matches. Оба набора команд/матчей сохраняются; новый фильтр ACTIVE, общий SELECT или изменение изоляции не вводятся. RoundRobinReadModelMapper материализует имена/профили/уровни/стороны и статистику внутри транзакции. В модели не попадают JPA/proxy, сохранённая currentPosition и регистрационные поля, отсутствовавшие в исходном DTO.

RoundRobinRankingMapper возвращает прежние DTO/enum. Для гостя сохраняются имя/телефон без профиля; SIN_ESPECIFICAR даёт null-уровень. Скрытые playingSide остаются только в Java DTO. setDifference, qualification/playoff/registration поля остаются null по явным ignore. Списки additionalMatches/finalRanking сохраняют неизменяемость, ranking — прежний изменяемый список. Новых endpoints, JSON-полей, UI-строк и изменений схемы/Liquibase нет.

## Транзакции

- Внешние getRanking/getTeams и MVC/API/Admin callers проходят через прежний TeamAmericanoService и реальный Spring proxy query. ReadOnly REQUIRED сохраняет одну транзакцию/EntityManager; GET не записывает вычисленные позиции и историю.
- initialize передаёт уже имеющиеся tournament/teams/rounds в fromLoaded того же адаптера. Остальные источники дочитываются внутри исходной write-транзакции, затем query.compose вызывает общий composer. Повторного поиска tournament/ranking teams нет; раннего commit или нового read snapshot нет.
- finishTournament сохраняет прежнюю последовательность команды и self-call getRanking. Внешний query присоединяется к текущей write-транзакции через REQUIRED и видит её изменения. ConductChange/history/flush/after-commit остаются на прежних командах; запрет новых save/flush относится к внешнему GET, а не к исходному AUTO-flush при joined write-read.

RoundRobinApplicationConfiguration явно импортирует один query bean; у порта одна реализация. Цикла legacy↔application нет: адаптер зависит только от repository/mapper, application — от своего порта и точных публичных типов engines. Production-композиция buildRanking/toDto удалена: buildRanking теперь только мост для initialize.

## Оставшиеся мосты

| Мост | Потребитель и условие удаления |
|---|---|
| TeamAmericanoService.getRanking/getTeams | Прежние MVC/API/Admin callers; остаётся до будущей web-границы |
| buildRanking/fromLoaded | initialize по незакоммиченным объектам; остаётся до отдельного переноса write-сценария |
| LegacyRoundRobinReadAdapter/RoundRobinReadModelMapper | JPA-реализация application-порта; переходят в persistence после подготовки #1051 |
| RoundRobinRankingMapper | Response DTO на legacy-входе; переходит в будущий web |
| RoundRobinStandings и SETS/POINTS mapper | updateTeamPositions командного сервиса и прежний Java API; удаляются после переноса этих callers |
| RoundRobinRules.finalOrder/mapper | Сохранённый Java helper API и его characterization; production-query вызывает engine напрямую |
| RoundRobinAdditionalMatches.add/list | Командный сервис и прежний helper list; новая read-композиция не вызывает list. Add/валидация/история остаются до отдельного write-сценария |

## Защита границ

Application зависит на Maven-уровне от engines. ArchUnit и artifact guard разрешают ровно 11 публичных типов SETS/POINTS/final-order; private $Meetings, соседи legacy и имена с дополнительным суффиксом запрещены. Guard читает Class и descriptor/signature constant pool целиком, включая generic/array. Обязательны клубный срез и 20 классов нового read-среза; одна application-библиотека и отсутствие копии в BOOT-INF/classes. DevTools включает application jar вместе с engines в restart loader; иначе application из base loader не сможет корректно использовать engine из restart loader. Проба с отключённым application include фиксирует расхождение, два запуска с include проверяют query и прежние мосты. Java release 17, Enforcer и запреты Spring Data/Web/Security, JPA/JDBC/Jackson сохраняются.

## Проверки и ограничения

Unit application: 16 новых проверок с fake-портом охватывают 11 состояний baseline, guest/levels/sides, fallback/null mode, частичный круг, дополнительные встречи и отсутствие турнира. Новый интеграционный набор проверяет один реальный proxy/порт, readOnly-вызов, joined write-read initialize/finish, материализованные значения после clear и порядок repository-вызовов. Исходные 12 baseline-проверок и 11 полных JSON-эталонов сохраняются; в старом RoundRobinRulesTest адаптирован только конструктор тестовой заглушки DI.

Итоговый корневой `./mvnw -B clean verify` с frontend на Java 21 / release 17 и собственной PostgreSQL: **1593 теста**, 0 failures/errors, 4 штатных пропуска. Application — 21 тест (16 новых unit, 3 ArchUnit, 2 клубных), engines — 141. Все 12 baseline-проверок/11 JSON-эталонов и прежние characterization-ожидания сохранены. Предварительные замечания к тестовой DI-заглушке и позиции repository observer устранены до итогового прогона. Три artifact guard прошли; Python: 31 GitHub (9 application guard), 48 relay, 5 worker (1 штатный пропуск), 12 preview, 22 release. Docs-drift и agent-migration — 0 расхождений.

Реальный Chromium: запуск SETS/POINTS, круг, исправление результатов круга/финала с возвратом, завершение, страница истории, 24 представления RU/ES/EN × desktop/mobile × public/admin и reload. Readonly API сохранил точные поля JSON, доступ и неизменность всех строк команд/истории/схемы. Восстановление через историю покрывается также прежними интеграционными тестами.

Полный runtime smoke, JWT/session/CSRF/роли, stale/replay, два зрителя WS и восстановление, расширенный ранний плей-офф 9/16 прошли. Репетиция выпуска/отката, старого lazy chunk, сбоев chunk/readiness сохранила пять игровых snapshots. Отдельный запуск предыдущего принятого jar #1376 и возврат сохранили ranking/rounds обоих круговых турниров, все строки их команд, snapshots, схему/Liquibase. DevTools: отрицательный контроль без application include фиксирует разные загрузчики; с include оба запуска query и прежних мостов прошли в минимальном контексте.

Проверенный jar: `dc85e170f5e298b649170060787b541fbaaac1d07696e57f8c0aa40c3d2f3d5a`; предыдущий #1376: `c96d4369d9cb36614ef29d70ee5e63005496156153deb4b08f1e337cf35e2ece`. Сборка сделана до итогового коммита на исходной ревизии с рабочими изменениями; runtime-отчёты сверены с хешем jar. [Безопасная QA-сводка](application-query-qa-2026-11-25.json) сохраняет результаты и хеши baseline/браузерных сценариев.

Ручной сеанс владельца и физические устройства не повторялись; #1067/Expo сохраняются. Хранение, команды проведения, начисление/валидация, generated ranking DTO, production-switch, Swarm и физическая БД не переносятся этой задачей.
