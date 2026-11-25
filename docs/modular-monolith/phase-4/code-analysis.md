# Анализ следующего application-среза

Срез анализа: master `20bd696df97f78cefde6f34a2e8dbf25c2a706f2`, 25.11.2026, после PR #1372; baseline #1375 выполняется на `0dc4069a`, отличающемся только архивированием спеки. Задачи [фазы 4](README.md) созданы в открытом Milestone «Многомодульный монолит — архитектура и пилот». Исходный статический анализ дополнен [конкретным application-контрактом](application-contract.md), wire-эталонами и реальными transaction/browser-проверками #1375.

## Почему переходим к application

В reactor четыре дочерних модуля: api-contract, tournament-engines, application, bootstrap. В engines уже девять вычислительных классов, включая места SETS/POINTS. Application содержит PublicClub/PublicClubQuery/ClubReadPort; основная композиция турнирного ответа остаётся в корневом legacy src. Следующий полезный результат — отделение прикладного read-сценария, а не механическое перемещение целого JPA-сервиса.

RoundRobinRules.finalOrder всё ещё соединяет вычисление итоговых мест с entity/DTO. Это конкретный оставшийся вычислительный барьер; #1376 снимает его перед application, сохраняя прежний API. Валидация и применение счёта связаны с командной стороной и в read-фазу не включаются.

## Фактический путь чтения

| Узел | Текущая ответственность |
|---|---|
| controller/view/americano/TeamAmericanoViewController | Public/admin MVC, GET /tournaments/team-americano/api/{id}/ranking; вызывает getRanking/getTeams |
| controller/admin/AdminController | Включает teamAmericanoRanking в карточку мероприятия |
| service/americano/TeamAmericanoService.getRanking | Получает Tournament, ranking teams и rounds из repository, вызывает buildRanking |
| TeamAmericanoService.buildRanking | Фильтрует круг, определяет режим/fallback, загружает все матчи, считает места/сводку, строит DTO; вызывает additionalMatches.list и finalOrder |
| RoundRobinStandings | Адресные MapStruct-адаптеры к SETS/POINTS engines, возвращает исходные entities |
| RoundRobinAdditionalMatches.list | Отдельно загружает команды и placement-матчи, строит RoundRobinAdditionalMatchDto |
| TeamAmericanoService.toDto / TeamPlayingSideMapper | Игроки/гость, контакты, уровень/сторона, статистика, имена и позиции |

TeamAmericanoRankingDto содержит tournamentId/name, total/completed rounds, totalTeams, boolean isFinished, ranking, scoringMode, pointsPerMatch, courts, roundRobinComplete, additionalMatches и finalRanking. AmericanoTeamDto содержит существенно больше минимальных данных engine: маппинг нельзя заменить выдачей вычислительного record наружу. Фактический wire-key boolean-поля, null/enum/absence и все вложенные поля фиксирует #1375, а не выводит из одного имени Java-поля.

Repository: TournamentRepository, AmericanoTeamRepository, AmericanoRoundRepository и AmericanoMatchRepository. findRankingByTournamentId имеет собственный SQL-порядок; buildRanking затем применяет вычислительный порядок. AdditionalMatches.list делает дополнительные запросы и использует ID для имён пар. Порядок и кратность чтения — часть анализа baseline: перенос не разрешает незаметный новый фильтр/изоляцию/блокировку или изменение lazy/flush-семантики.

Прямые потребители — legacy MVC/API/AdminController. Фрагмент templates/fragments/round-robin.html рендерит DTO на сервере; static/js/round-robin.js отправляет изменения и перезагружает страницу, ranking GET не вызывает. React frontend/src/api/live.ts читает /api/public/events/{id}/live; PublicLiveService использует King/Americano/Team Playoff, а для текущего Format.TEAM_AMERICANO выдаёт LIVE_FORMAT_UNSUPPORTED по default-ветке. Expo mobile-app/src/schedule.ts читает /api/public/tournaments, auth.ts — auth/участие; данный ranking не используется. Общий React/Expo/public-live smoke сохраняется как соседняя регрессия, их домены этой задачей не перенесены.

Публичная MVC-страница и GET /{id}/ranking redirect разрешены без сессии. JSON /api/{id}/ranking под этим controller prefix не входит в permitAll: anonymous с Accept JSON получает 401, с Accept HTML — 302 на login. Авторизованный игрок или чужой Owner могут читать JSON; управление admin проверяет ownerId, даже для другого CLUB_ADMIN той же площадки. HTML-отказ admin — 302 на /error/403, JSON-отказ — 403. Эти различия закреплены тестами и конечным jar; менять SecurityConfig в переносе нельзя.

## Транзакционные callers

TeamAmericanoService имеет class-level @Transactional(readOnly=true). getRanking/getTeams используют его read-контекст. initialize — write @Transactional с @ConductChange(START): после сохранения команд/раундов/матчей вызывает buildRanking(tournament, teams, rounds) по своим объектам до commit. finishTournament — write @Transactional с @ConductChange(FINISH): обновляет позиции, сохраняет статус и вызывает getRanking внутри той же транзакции.

Новая внешняя application-граница не должна превращать внутреннее чтение в отдельную транзакцию или слепую повторную загрузку со сменой flush/order. Необходима одна композиция результата, которую можно использовать и для входных данных initialize, и для query через порт. Точная форма интерфейса/моста подтверждается #1375 до #1377. Разрешённый Spring proxy у нового query не отменяет требований проверить реальный вызов и присоединение к существующей write-транзакции.

GET не сохраняет currentPosition: updateTeamPositions — отдельный write-метод, а buildRanking подставляет вычисленные позиции в DTO. Это нужно сохранить. Старые propagation/isolation и наблюдаемость нескольких repository-чтений не повышаются до гарантии repeatable snapshot только из-за введения read-model с названием snapshot. History/ConductChange/after-commit остаются в прежнем командном сценарии.

## Подключение engines сейчас запрещено существующим guard

Target architecture разрешает application → engines, но текущий application ещё не использует эту зависимость:

- padel-application/pom.xml содержит Spring context/tx; зависимости на engines пока нет.
- ApplicationArchitectureTest разрешает только JDK, собственные application-типы, @Service и transaction annotations.
- .github/bin/check-application-artifact.py запрещает bytecode-префикс com/padle/core/padelcoreservice/. Выделенные движки сохраняют исторический package service/americano под тем же префиксом.

#1377 добавляет минимальную зависимость и точные разрешения фактических engine-типов/вложенных типов. Общий запрет legacy нельзя удалить или заменить разрешением service/americano целиком. Отрицательная проба должна отличать соседний legacy-класс того же package от разрешённого engine. Существующий PublicClub-срез, единственное владение application-классами, Java 17 и запреты JPA/JDBC/Spring Data/Web/Security/Jackson сохраняются.

## Направление границы

Приложение получает собственные read-модели/enum и порт чтения, материализованные без JPA/proxy/старого DTO. Legacy JPA-адаптер в bootstrap реализует этот порт; MapStruct переводит entity↔read-model. Query/композиция вызывает чистые engines и возвращает собственный результат; другой MapStruct строит прежний TeamAmericanoRankingDto на границе.

Новый порт не принимает Repository/Sort/Pageable/Principal, не создаётся интерфейс на каждую таблицу. Имена/контакты/уровни/стороны сохраняются в пределах прежнего ответа, новых персональных полей или изменения прав нет. Legacy ResourceNotFoundException не импортируется в application: отсутствие данных переводится на границе в прежний HTTP/ошибку по baseline.

Временные фасады getRanking/getTeams и мапперы адресно перечисляются с consumers и условием удаления. Старая композиция buildRanking удаляется после перевода всех callers на одного владельца; дублирование формулы не считается завершённым переносом. JPA-маппинг переносится в persistence только на поздней #1051 после подготовки остальных портов/графа.

## Проверяемые состояния и исключения

#1375 переиспользует RoundRobinIntegrationTest, RoundRobinRulesTest, RoundRobinStandingsTest и оба adapter-набора, а также round-robin-sets.mjs; расширяет лишь недостающее покрытие ответа и границ. Минимальная матрица: POINTS/SETS; пустое/частичное/полное/завершённое проведение; fallback null mode/config; guest/nullable уровень/сторона; общие места; финал/третье/другие места/встреча без места; pending/исправление/restore; отсутствие турнира и свои/чужие права.

Новые эталоны должны пройти на исходном коде до переноса. Некорректные внутренние ID/дубликаты/назначения в finalOrder не получают новых правил при выделении; если обнаружен продуктовый дефект, он оформляется отдельно. В #1377 проверяются GET без записей, write-read внутри initialize/finish и rollback без частичного состояния/history. #1378 повторяет реальные страницы и сравнивает API/позиции/данные на точном артефакте после restart и прежнего jar/возврата.

## Граница последующей работы

Фаза не переносит проведение/начисление/валидацию, права всех форматов, историю целиком и интеграции. После read-пилота требуется отдельная декомпозиция write-сценариев с портами и их ACID/locks/after-commit. Persistence/web/integrations и Swarm пока не выделяются; #1051 не объявляется разблокированной одной read-фазой.
