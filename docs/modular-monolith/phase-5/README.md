# Фаза 5: сохранение круговых позиций и завершение

Координация — [LFPT-1402](https://github.com/UstinKO/padel-core-service/issues/1402). Подготовлена после включения [приёмки #1378 / PR #1398](../phase-4/acceptance.md); исходный master анализа — `02200f09eeac5cb5eff15f7608889f3b9ee4e051`.

## Состояние

Предыдущая read-фаза принята. Application уже содержит PublicClubQuery и RoundRobinRankingQuery, engines — десять вычислительных классов. Write-команды этой фазы **пока не реализованы**. В #1403 зафиксирован [командный baseline](baseline.md) с [конкретным контрактом будущих портов](command-contract.md), анализом и матрицей проверок; #1404/#1405 остаются следующими реализациями. Координационная #1402 остаётся открытой до их завершения.

## Последовательность

| Порядок | Issue | Слой | Результат | Зависит от |
|---|---|---|---|---|
| 1 | [#1403](https://github.com/UstinKO/padel-core-service/issues/1403) | backend | Baseline позиций/завершения, фактические транзакции/история/ошибки и конкретный контракт портов | Влитый PR #1398 |
| 2 | [#1404](https://github.com/UstinKO/padel-core-service/issues/1404) | backend | Application-команда сохранения круговых позиций, общий расчёт с query и legacy-адаптер | #1403 |
| 3 | [#1405](https://github.com/UstinKO/padel-core-service/issues/1405) | backend | Application orchestration завершения с прежней транзакционной оболочкой | #1404 |
| 4 | [#1406](https://github.com/UstinKO/padel-core-service/issues/1406) | frontend | Независимая клиентская/runtime-приёмка конечного jar и возврата | #1405 |

Baseline #1403 реализован отдельным PR; #1404 берётся после его включения. Закрытия координационной #1402 рабочие задачи не ждут. Каждая реализация — отдельный PR, работоспособный без будущих задач. Depends-On в PR указывает на настоящий PR зависимости, не на номер issue; в исходных issues сохраняется последовательность задач. Автомерж/production-деплой в подготовку не входят.

Спецификации: [координация](../../../specs/features/LFPT-1402-round-robin-write-phase.md), [baseline](../../../specs/features/LFPT-1403-round-robin-write-baseline.md), [позиции](../../../specs/features/LFPT-1404-round-robin-position-command.md), [завершение](../../../specs/features/LFPT-1405-round-robin-finish-command.md), [приёмка](../../../specs/features/LFPT-1406-round-robin-write-acceptance.md).

## Почему такой срез

[Анализ кода](code-analysis.md) прослеживает updateTeamPositions от finish и tryAutoCompleteRound, все действующие HTTP-входы, реальные repository-чтения, ConductChange и после-commit invalidation. Завершение достаточно ограничено для следующей команды; сначала выделяется общее сохранение позиций, чтобы не оставить его orchestration в legacy.

Сохраняются следующие особенности:

- currentPosition — место **круга**, finalRanking — отдельный итог с PLACEMENT; GET не записывает ни позиции, ни историю.
- Допуск к завершению использует MAX(roundNumber), количество COMPLETED всех фаз и прежний short-circuit initialized. Это не заменяется числом круговых раундов или ranking.finished.
- Самостоятельное обновление позиций не имеет ConductChange; из RESULT/FINISH оно входит в существующую историю внешнего вызова.
- Повтор finish с/без ключа сохраняет текущую зависимость history-эффектов от snapshot/key; новые правила повторов не изобретаются.
- SELECT FOR UPDATE, версия/ключ, flush, снимки и уведомление после commit остаются в прежней общей оболочке. Исключение на любой записи/query/history откатывает весь сценарий.

## Граница реализации

Application владеет решениями и последовательностью действий, своими значениями/портами, командами и общей подготовкой SETS/POINTS standings к engines. Порт, который просто вызывает старый finish целиком, не считается выделением сценария. Legacy-адаптеры и MapStruct связывают managed-данные с новыми значениями и прежними DTO; JPA/Repository/ConductChange/Servlet/Principal/DTO в application запрещены.

TeamAmericanoService.finishTournament пока остаётся единственным production-входом с прежними @Transactional/@ConductChange. Новая finish-команда вызывает отдельную position-команду и ranking query в том же write-контексте. Legacy updateTeamPositions — адресный совместимый делегат. Нужны реальные proxy/DI и один EntityManager, а не надежда на аннотацию при self-invocation.

Начисление/отмена статистики счёта, автозавершение как caller, инициализация/preview/loadPairs/расписание, дополнительные встречи и платформа history/restore остаются в legacy. RoundRobinStandings и другие мосты учитываются по фактическим потребителям и условию удаления. Persistence/web/integrations, generated ranking DTO и физическая PostgreSQL/Swarm не выделяются; #1051 не становится готовой только от двух новых портов.

## Проверки

[W01–W18](test-cases.md) — проверяемая программа, не текущий PASS. Первый baseline добавил characterization до production-переноса, включая контролируемые отказы на настоящей отдельной PostgreSQL и независимое чтение после завершения транзакции. Последующие задачи повторяют исходные ожидания. Сохраняются 12 read-baseline/11 JSON и браузерная автоматизация фазы 4.

Каждый PR проходит [общие условия](../plan.md#условия-приёмки-каждой-реализационной-задачи): полный verify с frontend, guards/negative cases, конечный jar/runtime/браузер, общий пилот/репетицию выпуска и прежний jar/возврат. После новых writes сравниваются связанные строки/история/схема и ответы после restart/возврата. Новые ручные/физические/многорепличные результаты заявляются только по факту. Ограничения #445/#453/#1067 и Expo сохраняются.

## Итог подготовки

Подготовка не меняет приложение, POM, API, схему или пользовательские страницы. В GitHub созданы #1402–#1406 в открытом Milestone «Многомодульный монолит — архитектура и пилот», сохранён порядок backend → frontend. Комплект подготовки публикуется вместе с первой реализационной #1403; это не реализация остальных команд.

Исторические проверки аналитической подготовки 25.11.2026 до реализации #1403 прошли: 121 тест автоматики (31 GitHub, 48 relay, 5 worker с одним штатным пропуском, 15 preview, 22 release); docs-drift/agent-migration — 0 расхождений. Проверены 63 новые локальные ссылки, пять approved-спецификаций, соответствие заголовков/критериев GitHub, Milestone/слой и ацикличные зависимости. В том подготовительном шаге изменены только 14 Markdown-файлов; diff/приватность проверены. Maven, новая PostgreSQL/browser/rollback/DevTools здесь не запускались. Фаза 4 не переименовывается в доказательство будущих команд. Последующие проверки реализации #1403 приведены в [baseline](baseline.md).
