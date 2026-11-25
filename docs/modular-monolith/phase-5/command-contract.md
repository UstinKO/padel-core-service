# Контракт первого командного среза

Исходная реализация — master `63dc0611029ec950e1587151b3b8b0cf89e046b6` (production-код совпадает с принятым #1398). Characterization #1403 задаёт требования для #1404/#1405. [Позиции #1404 реализованы](position-command.md); finish-классы/порт ниже остаются контрактом будущей #1405.

## Сохранение позиций: #1404

В application реализован RoundRobinPositionCommand с write REQUIRED и методом update(Long tournamentId). Существующий TeamAmericanoService.updateTeamPositions сохраняется как совместимый делегат. Вызовы из tryAutoCompleteRound и finish остаются в прежних точках. Самостоятельный update открывает write-транзакцию без истории; вложенный присоединяется к RESULT/FINISH.

RoundRobinPositionPort задаёт две операции:

```java
RoundRobinPositionInput load(Long tournamentId);
void savePositions(List<RoundRobinPosition> positions);
```

Нейтральные records application:

- RoundRobinPositionInput: mode и упорядоченные списки teams/matches. Mode использует собственный существующий RoundRobinReadSet.ScoringMode.
- PositionTeam: id, number, totalScore, matchesWon, pointsScored, pointsConceded, matchesLost, gameDifference. Числовые nullable-значения не получают новых defaults. Контакты/профили для расчёта мест не нужны.
- PositionMatch: firstId, secondId, phase/status из собственных enum read-модели, firstScore, secondScore.
- RoundRobinPosition: исходный index, teamId и position. Index сохраняет соответствие входным строкам engine без нового поиска/сортировки.

Общая чистая RoundRobinStandingsCalculation в application готовит разрешённые входы SETS/POINTS engines и возвращает positions. Read-композиция использует ту же подготовку/вычисление, сопоставляя результат со **своим** rankingTeams; write — со **своим** findByTournamentId. Различие входного порядка не устраняется. Формулы принадлежат engines, расширять allowlist ради legacy helper не требуется.

Legacy-адаптер load читает в порядке: rounds ASC → первый круговой mode/fallback POINTS → teams без ORDER BY → все matches ASC. У standalone update отсутствует проверка существования Tournament/инициализации. Пустой вход даёт пустой saveAll. Для POINTS сохраняется запрос всех matches, но их неиспользуемые значения и gameDifference не вычисляются. Для пустого SETS matches также не материализуются. Для SETS учитываются только COMPLETED circle matches с обоими score; PLACEMENT игнорируется. Общее/личное полное равенство сохраняет разделённые места.

MapStruct маппит entity в минимальные значения; полная read-модель с lazy-профилями не используется как замена минимального position-input. Адаптер применяет только currentPosition к исходным managed-командам и вызывает saveAll в вычисленном порядке. Ссылки по уже загруженным id допустимы внутри того же EntityManager; повторный SELECT, ThreadLocal-кэш или передача entity/Supplier в application не являются способом скрыть потерю контекста.

При фактическом изменении позиции Hibernate обновляет updated_at — это штатный эффект. Проверки успешной записи сравнивают все остальные поля; проверки rollback сравнивают **полные строки вместе с timestamps**. Старый RoundRobinStandings/helper API и его characterization пока адресно учитываются как совместимый адаптер, новых production-callers после переноса у него быть не должно.

## Завершение: #1405

RoundRobinFinishCommand с write REQUIRED возвращает собственный RoundRobinRanking. Legacy facade остаётся единственным production-входом с @Transactional/@ConductChange(FINISH); MapStruct DTO и перевод ошибок выполняются внутри этой же внешней транзакции.

RoundRobinFinishPort содержит операции по сценарию, а не один вызов старого finish:

```java
Optional<RoundRobinFinishEvent> findEvent(Long tournamentId);
long maximumRoundNumber(Long tournamentId);
long completedRounds(Long tournamentId);
boolean hasCircleRound(Long tournamentId);
boolean hasTeams(Long tournamentId);
void markFinished(Long tournamentId);
```

RoundRobinFinishEvent содержит id и признак cancelled. В приложении не нужна копия всего Tournament/всех enum хранения. Отсутствие события и отказ завершения выражаются собственными типизированными ошибками; они не зависят от старого ResourceNotFoundException/InvalidStateException. Ошибка незавершённого состояния сохраняет числовой remaining, не обрезая его новым max(0,...).

Последовательность в application:

1. findEvent; отсутствие переводится на прежнюю legacy-ошибку. Cancelled отклоняется до чтения счётчиков.
2. maximumRoundNumber с fallback 0, затем completedRounds — оба по всем фазам, как раньше.
3. hasCircleRound && hasTeams с прежним short-circuit. Legacy hasTeams использует прежний findByTournamentId, а не незаметную оптимизацию на exists.
4. Если не инициализировано или completed < max — прежний отказ с remaining=max−completed. MAX не заменяется количеством, query.finished не заменяет допуск.
5. PositionCommand.update.
6. markFinished: применение FINALIZADO к уже загруженному managed-событию и прежний save.
7. Существующий RoundRobinRankingQuery.ranking присоединяется к write-транзакции и читает свои записи до commit. Новый read-сценарий не пишется.

На внешней границе cancelled сохраняет round_robin.error.finished, pending — прежнее «No se puede finalizar. Quedan N rondas sin completar.». Старый body getTournament имел сообщение Tournament not found: ID, а обычный ConductChange-вход может отклонить missing ещё раньше сообщением Tournament not found: оболочка/порядок ошибок не переносятся в новый mapper. Query-not-found также сохраняет прежний перевод. Новых пользовательских строк нет.

Отдельного запрета FINALIZADO нет. Повтор без ключа и без изменения snapshot не создаёт историю/уведомление; новый ключ владельца создаёт FINISH даже при прежнем состоянии. Известный ключ сначала проверяется оболочкой и даёт ResponseStatusException 409 с X-Conduct-Replayed; stale — прежний InvalidStateException и X-Conduct-Stale. MVC finish ловит эти исключения и сохраняет **302 с error-flash**, а не превращается в новый JSON 409. Его success-flash по-прежнему берёт имя из кругового ranking. Эти особенности — characterization, не новые продуктовые решения.

## Что остаётся в bootstrap

ConductChangeAspect/TransactionTemplate, SELECT FOR UPDATE и версия/ключ, snapshots/история/restore, права HTTP/CSRF, MapStruct entity↔значения и результат↔DTO, все repositories/JPA/JDBC и publisher. Application не принимает EntityManager, Repository, Principal, Servlet, ConductChange или callback с entity.

PublicLiveNotifications объединяет события в пределах одной транзакции; транспорт вызывается после commit. Новая команда не создаёт вторую историю/уведомление. Любое исключение в position save, event save, query/маппинге или history snapshot откатывает всю внешнюю операцию. Нельзя добавлять REQUIRES_NEW, новый transaction manager, locks/isolation или ранний flush/commit «для видимости».

Внедрение — отдельными реальными Spring beans через существующую явную конфигурацию application. Guard REQUIRED дополняется фактическими новыми типами, точный allowlist 11 публичных engine-типов и negative cases сохраняются. Один boot jar/одна application library; classloader include DevTools не расширяется на весь legacy.

## Проверяемые наблюдения

RoundRobinWriteOrderTest фиксирует порядок repository-вызовов и сохранения, short-circuit/fallback, исходные entities и SETS-meetings. RoundRobinWriteBaselineTest использует настоящий Spring proxy/отдельную PostgreSQL без @Transactional на тестовом классе. Контролируемые WriteProbe вводятся после saveAll/save/query-boundary с реальным flush либо на JdbcClient.sql перед history INSERT; это проверка rollback настоящих SQL-записей при Java-отказе на границе, не имитация отказа диска/сети PostgreSQL.

После операции полные строки читаются независимо. Наблюдатель транспорта читает estado через отдельное JDBC-соединение: FINALIZADO должен быть уже виден после commit. Проверка конкуренции удерживает первый вызов до query и подтверждает ожидание второго через pg_blocking_pids; после release второй получает stale. Это два контекста одного процесса, не приёмка двух production-реплик #1214.

До включения соответствующего backend PR будущий контракт не объявляется реализованным. #1404 повторяет старые unit/integration/JSON ожидания; #1405 — также fault/key/version/concurrency; #1406 независимо повторяет клиентов и возврат конечного артефакта. Start/score/additional/history и поздняя #1051 сохраняют свои границы.

В новых write-тестах при появлении команд допускается адаптация только тестовой сборки DI/подключения наблюдателя к новой границе, без смены ожидаемых мест, I/O, отказов или history/transport-эффектов. Старые 12 read-baseline и 11 JSON не меняются. Исполнитель не заменяет проверку реальных managed-объектов прохождением unit с новым fake-портом.
