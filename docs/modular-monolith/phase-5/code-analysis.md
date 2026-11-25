# Анализ первого командного среза

Анализ ниже сохраняет исторический граф до переноса. Актуальное изменение #1404 описано в [реализации position-команды](position-command.md): updateTeamPositions делегирует application, общий calculation используется query/write, у RoundRobinStandings.rank больше нет production-callers. Finish и прочие caller-эффекты пока прежние.

Срез кода: master `02200f09eeac5cb5eff15f7608889f3b9ee4e051`, после включения [PR #1398](https://github.com/UstinKO/padel-core-service/pull/1398). [Фаза 4](../phase-4/README.md) технически завершена: application владеет чтением круговой таблицы/итоговых мест, engines — десятью вычислениями. Новый командный срез ещё не реализован.

## Выбор границы

Первый шаг — сохранение **круговых** currentPosition и завершение Team Americano. Это ограниченная последовательность из существующих операций, использующая уже выделенные SETS/POINTS engines и ranking query. Перенос одного finish без общего обновления позиций оставил бы вычислительную orchestration в legacy; перенос всего TeamAmericanoService одновременно захватил бы регистрацию, генерацию расписания и обработку счёта.

Запуск/preview/loadPairs/генерация расписания, submitMatchResult и применение/отмена статистики, создание дополнительных матчей и платформа истории остаются следующими срезами. Общий рефакторинг #511/#509, Swarm #1214 и поздняя #1051 не включаются.

## Фактические вызовы

| Операция | Production-потребители | Текущая граница |
|---|---|---|
| TeamAmericanoService.updateTeamPositions | finishTournament; tryAutoCompleteRound из submitMatchResult | public write @Transactional, без ConductChange; внутренние вызовы идут через self-invocation |
| TeamAmericanoService.finishTournament | TeamAmericanoViewController POST /tournaments/team-americano/admin/{id}/finish | write @Transactional и ConductChange(FINISH) |
| submitMatchResult | MVC /matches/{id}/result; прежний JSON /api/matches/{id}/result; RoundRobinActionsController /api/round-robin/matches/{id}/result | write @Transactional и ConductChange(RESULT, AMERICANO_MATCH); переносится только вызываемое обновление позиций |
| getRanking / initialize composition | MVC, JSON, AdminController, joined write-read | уже существующий application query; сохраняется |

В репозитории единственный production-вызов RoundRobinStandings.rank — updateTeamPositions. Старые characterization/helper-тесты также используют этот Java API; отсутствие production-вызовов после переноса не разрешает молча менять их ожидания. Прежний helper адресно учитывается как совместимый мост, а не как новый владелец orchestration.

## Сохранение позиций

Порядок существующего тела updateTeamPositions:

1. Прочитать rounds через findByTournamentIdOrderByRoundNumberAsc; взять первый phase=null и его режим через RoundRobinRules.mode, fallback POINTS.
2. Прочитать teams через findByTournamentId, затем все matches через findByTournamentIdOrderByRoundIdAscMatchNumberAsc. У первого запроса нет ORDER BY; запрещено добавлять сортировку «для надёжности» вместо фиксации фактического входного порядка.
3. Вызвать RoundRobinStandings.rank. POINTS использует статистику команд; SETS — победы/разницу геймов и завершённые круговые матчи с обоими score для личных встреч. Полное равенство SETS даёт разделённые места.
4. Применить позиции к исходным managed-командам и вызвать saveAll в вычисленном порядке. Остальные поля не перезаписываются. Пустые teams допускают пустой saveAll; этот метод сам не проверяет существование Tournament или инициализацию.

Обновляются именно круговые позиции. PLACEMENT влияет на finalRanking, но не подменяет currentPosition. GET вычисляет позиции DTO, ничего не сохраняя; это не меняется.

В application уже есть закрытый метод standings в RoundRobinRankingComposer с подготовкой входов к тем же engines. Следующий шаг должен дать общую application-композицию для чтения и записи позиций, сохранив их **разные источники и порядок списков**. Копировать эту подготовку ещё в один командный класс или заново писать формулы не требуется. В engines остаётся единственный владелец самих правил.

## Завершение

Существующее finishTournament выполняет getTournament/findById, отклоняет CANCELADO через round_robin.error.finished, затем:

1. findMaxRoundNumber(...).orElse(0) — **максимальный номер**, не количество круговых раундов;
2. countCompletedRounds(...) — количество всех COMPLETED без фильтра phase;
3. isInitialized: existsByTournamentIdAndPhaseIsNull && непустой findByTournamentId, с прежним short-circuit;
4. если не инициализировано либо completed < total — прежний InvalidStateException с разницей total−completed;
5. updateTeamPositions;
6. estado=FINALIZADO и tournamentRepository.save;
7. getRanking в той же транзакции, MapStruct на внешней DTO-границе.

Нельзя заменить max(number) на count, исключить дополнительные раунды из допуска или «упростить» проверку до ranking.finished. Дыры номеров/нестандартные legacy-состояния фиксирует baseline; новые бизнес-правила из них не выводятся.

FINALIZADO не добавляет отдельного запрета повторного вызова. Семантика истории зависит от изменения snapshot и наличия request key. MVC-контроллер сохраняет redirect/flash и берёт имя для текущего success-flash из ranking, а не finalRanking. Исправление этого отображения не является частью модульного переноса.

## Транзакция, история, уведомления

ConductChangeAspect имеет Order(HIGHEST_PRECEDENCE + 30) и создаёт TransactionTemplate вокруг TournamentConductHistory.record и вызова метода. record определяет цель, выполняет SELECT ... FOR UPDATE через ConductSnapshotStore.lockTournament, flush, повторно проверяет цель, request key/expected version и сохраняет исходный snapshot. Затем вызывает сервис, делает flush и снимает новый snapshot. История и игровые изменения находятся в одной транзакции.

При изменившемся snapshot, request key или checkpoint создаётся запись действия; при первой версии возможна BASELINE. Повтор известного ключа владельца получает текущие X-Conduct-Replayed/409; stale — текущий X-Conduct-Stale и прежнее исключение. Эти правила принадлежат общей истории и этой фазой не переписываются.

TournamentConductHistory.save вызывает PublicLiveNotifications.changed. Сервис объединяет eventId в ресурсе транзакции и отправляет invalidation после commit; при rollback callback отправки не выполняется. AMERICANO_TEAMS попадает в этот механизм, хотя круговой формат не является прямым потребителем текущего public-live snapshot. Добавлять новый WS publish из application-команды нельзя.

Особенность текущего Java API: standalone updateTeamPositions пишет позиции без ConductChange/history, а вызов из RESULT/FINISH входит в их общую историю. Внутренний вызов аннотацию метода не перехватывает; write-контекст обеспечивается внешним методом/аспектом. Новый отдельный bean должен присоединиться к этому контексту, а не открыть независимую транзакцию. Прямой standalone-вызов также должен остаться write-вызовом.

restore платформы истории сохраняет поздние записи и текущие регистрации/оплаты; код restore в эту фазу не переносится. Проверяется совместимость восстановленного состояния с новым командным срезом.

## Целевая граница

Application владеет командами сохранения позиций и завершения, нейтральными состояниями/результатами, портами и общей подготовкой standings к engines. Legacy-адаптеры хранения и MapStruct остаются в bootstrap до #1051. Ни entities, ни Repository/EntityManager, ConductChange, Servlet/Principal, DTO или callback над entity в application не передаются.

Порты описывают необходимые операции сценария, не интерфейс на таблицу и не метод «вызвать старый finish». Проверка допуска, вызов команды позиций, установка завершённого состояния и композиция результата должны иметь одного владельца в application. Адаптер применяет готовые изменения, не дублирует решение о завершении или расчёт мест.

Контракт, который конкретизирует первый baseline:

| Граница | Необходимые значения/операции | Что остаётся снаружи |
|---|---|---|
| Позиции | Материализованные mode/team statistics/matches в прежнем порядке; применение списка teamId→position | JPA-чтение, ссылки на уже загруженные managed-объекты и saveAll |
| Завершение | Наличие/отмена события; max номера, completed count, circle-exists и teams в прежнем short-circuit; сохранение завершённого состояния | Repository/EntityManager и представление статуса хранения |
| Результат | Уже существующий ranking query после своих writes | Прежний DTO и перевод application-ошибок на legacy-границе |

Это перечень операций и данных, не замена анализа точных типов/методов в #1403. Порт не возвращает lazy stream, entity, Repository, Supplier над entity или транзакционный объект. Материализация только нужных значений не требует создания копии всей модели турнира.

Внешний TeamAmericanoService.finishTournament сохраняет @Transactional/@ConductChange, перевод прежних ошибок и DTO; это единственный production-вход завершения на данной фазе. TeamAmericanoService.updateTeamPositions становится совместимым делегатом. score/start/controllers не начинают обходить эти входы. Application-команды — отдельные Spring beans с write REQUIRED; ranking query присоединяется к той же write-транзакции. Новые REQUIRES_NEW, ранний commit, locks, isolation или отдельный transaction manager не вводятся.

Материализация не должна потерять managed identity и lazy/flush-семантику. Адаптер применяет позиции/состояние к уже загруженным объектам в том же persistence context; например, ссылка по уже загруженному id не требует нового SQL. ThreadLocal-кэш, транзакционный callback с entity или произвольный повторный SELECT вместо доказанной эквивалентности не являются границей порта. Точные методы/типы и последовательность доступа закрепляет первый baseline до переноса.

## Защита и доказательства

Сохраняются read baseline: 12 проверок, 11 полных JSON, браузерные ожидания #1375/#1378 и приложение из одного boot jar. Новые write-проверки добавляются отдельно. ArchUnit и artifact guard расширяют REQUIRED собственными новыми типами; allowlist ровно 11 публичных engine-типов и запреты остального legacy/JPA/JDBC/Web/Security/Jackson сохраняются. Отрицательные пробы не заменяются разрешением всего package.

Базовые тесты уже есть в RoundRobinIntegrationTest, RoundRobinRankingBaselineTest, RoundRobinApplicationIntegrationTest, RoundRobinEloIntegrationTest, TournamentConductHistoryIntegrationTest и MVC redirect/guest-наборах. Они не доказывают ещё не перенесённый write-port, общий persistence context всех новых proxy или отсутствие уведомления после отказа на записи. Эти проверки добавляются адресно.

После каждого реализационного PR обязательны полный verify с frontend/отдельной PostgreSQL, конечный jar/runtime/browser, общий пилот и репетиция возврата. Baseline фиксирует ожидания до изменений. Финальная frontend-приёмка повторяет тот же набор на принятом backend; не служит разрешением промежуточной поломки.

## Что исследовано и что не запускалось

Проверены код callers, SQL repository, aspect/history/after-commit, текущие read-модели/guards и существующие тесты. Прочитаны все открытые issues/PR и 813 закрытых PR всех авторов; действующей реализации этого write-среза не найдено. Есть соседние общие #511/#509 и отложенные #1051/#1214, их объём не присваивается новой фазе.

На момент подготовки этой постановки новые Maven/DB/browser/DevTools/rollback-прогоны не выполнялись. Последующие измеренные результаты #1403 перечислены отдельно в [baseline](baseline.md) и [контракте](command-contract.md). Результаты [приёмки фазы 4](../phase-4/acceptance.md) являются историческими доказательствами read-среза, не PASS будущих write-команд. Физические устройства, ручной сеанс владельца и две production-реплики не заявляются проверенными.
