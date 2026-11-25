# Контракт application-среза после исходных проверок

Подготовлено в [#1375](https://github.com/UstinKO/padel-core-service/issues/1375) для [#1377](https://github.com/UstinKO/padel-core-service/issues/1377). Ниже — конкретная техническая постановка будущего переноса, не уже созданные production-классы. Исходная реализация остаётся на master `0dc4069a`; [wire-эталоны](../../../src/test/resources/contracts/round-robin-query/README.md) проверены на ней.

## Пакет и входной порт

Новые классы принадлежат `com.padle.core.application.roundrobin`. Порт принадлежит application:

```java
public interface RoundRobinReadPort {
    Optional<RoundRobinReadSet> findByTournamentId(Long tournamentId);
}
```

Порт возвращает материализованные значения, не JPA/proxy/DTO/repository. Отсутствие турнира — Optional.empty; новый query бросает собственный RoundRobinNotFound. Legacy-фасад переводит его в прежний ResourceNotFoundException с прежним текстом. HTTP-обработчик этой ошибкой не добавляется: исходный ranking для отсутствующего турнира отвечает 500, anonymous JSON — 401. Обработка этого класса ошибок уже поставлена в [#453](https://github.com/UstinKO/padel-core-service/issues/453), публичная страница с IllegalArgumentException — в [#445](https://github.com/UstinKO/padel-core-service/issues/445). Исправление не включается скрыто в перенос; если canonical fix будет влит раньше #1377, baseline нужно актуализировать явно.

Новый @Service RoundRobinRankingQuery с constructor DI и @Transactional(readOnly=true) получает порт и использует чистый RoundRobinRankingComposer. Propagation REQUIRED, isolation/default rollback прежние. Domain/read-модели, composer и порт остаются без Spring. Composer может быть статической чистой функцией, отдельный Spring bean ему не требуется.

## Read-модели

| Тип | Поля/назначение |
|---|---|
| RoundRobinReadSet | event; rankingTeams; rounds; allMatches; additionalTeams; additionalMatches — упорядоченные List соответствующих records |
| Event | Long id, String name |
| Team | Long id, Long tournamentId, Integer number, String displayName, TeamStatus status, Player player1/player2, String guestName/guestPhone, Statistics statistics |
| Player | Long id, String name/email/phone, String level, Side side; сам Player может быть null |
| Statistics | Integer totalScore/played/wins/losses/drawn/pointsScored/pointsConceded/setsWon/setsLost/gamesWon/gamesLost |
| Round | Phase phase (null означает круг), Status status, ScoringMode scoringMode (nullable), Integer pointsPerMatch/courts |
| Match | Long id/firstId/secondId, Phase phase, Status status, Integer firstScore/secondScore/court/placementStart, String label |

Внутренние records можно вложить в RoundRobinReadSet; список полей не превращается в Map. Собственные enum: ScoringMode POINTS/SETS; Phase QUALIFICATION/PLAYOFF/PLACEMENT; Status PENDING/QUEUED/IN_PROGRESS/COMPLETED/CANCELLED; TeamStatus ACTIVE/DROPPED_OUT/DISQUALIFIED/NO_SHOW; Side LEFT/RIGHT/EITHER. Значения не импортируются из legacy. Level остаётся нейтральным nullable-кодом отображения: этот read-сценарий не рассчитывает уровень. В текущей БД nivel_jugador имеет NOT NULL; null в DTO получают SIN_ESPECIFICAR и отсутствующий профиль гостя. Исходная функция также допускает Java-null; обходить ограничение БД ради такого fixture нельзя. Новая нормализация данных не вводится.

Вход не содержит сохранённую currentPosition: в исходном ответе она всегда заменяется вычисленной позицией. Проверка специально сохраняет 9 в БД и получает 1/2 в DTO без обновления строк. Регистрационные/плей-офф-поля DTO, которые исходный toDto не заполняет, также не копируются из entity в read-модель «на всякий случай».

Два набора команд/матчей сохраняют существующие источники: ranking и allMatches, затем дополнительные запросы RoundRobinAdditionalMatches.list. Одна и та же модель Team используется повторно, дублировать классы по таблицам не нужно. Порядок списков соответствует текущим repository-запросам; не вводится новый общий SELECT/фильтр ACTIVE или выравнивание отсутствующих данных.

ReadSet — снимок значений модели, не обещание repeatable-read snapshot БД. Исходные repository-чтения выполняются с текущей изоляцией и не гарантируют единую версию при конкурентной записи. Изоляцию и блокировки этой фазой не менять.

## Композиция и результат

RoundRobinRankingComposer.compose(RoundRobinReadSet) — единственный владелец композиции результата. Последовательность сохраняется:

1. Круговые rounds имеют phase=null; первый круг задаёт mode с fallback POINTS, pointsPerMatch с fallback 32, courts с fallback 1.
2. SETS/POINTS получают минимальные нейтральные inputs; сохраняются прежние критерии, null-mode, общие/последовательные места и порядок строк.
3. totalRounds/completedRounds считаются только по кругу. roundRobinComplete — непустой полностью завершённый круг. finished — непустой круг и завершённость всех rounds, включая дополнительные; статус Tournament здесь не участвует.
4. AdditionalMatch результата использует только id, placementStart, label/court, ссылки id/number/name на две команды, scores/status. Контакты и профили в этот результат не добавляются.
5. Итоговые места вычисляет выделенный в #1376 final-order engine по текущим назначениям; круговая статистика остаётся прежней.

RoundRobinRanking содержит id/name турнира, totalRounds/completedRounds/totalTeams, finished, scoringMode/pointsPerMatch/courts, roundRobinComplete, List<RankedTeam>, List<AdditionalMatch> и List<FinalStanding>. RankedTeam связывает исходную Team с вычисленной позицией; FinalStanding — team id/number/name и место. В результате нет JPA/DTO/legacy enum.

Legacy result-маппер MapStruct строит прежний TeamAmericanoRankingDto. В нём намеренно остаются null: setDifference, match1/2GamesWon/Lost, qualStatus, tournamentStatus, queuePosition/queueOpponentName, eliminatedStage, registrationSource, hasPaid, attended, adminComment. Игровые разницы вычисляются как прежде; setDifference не начинает вычисляться из setsWon/Lost. Side передаётся в скрытые Java-поля DTO и по-прежнему отсутствует в JSON. Unmapped-поля и намеренные ignore перечисляются явно, без ослабления ReportingPolicy.

## Адаптеры и callers

- LegacyRoundRobinReadAdapter в root src реализует порт через существующие repository и MapStruct entity→read-model. Порядок загрузок: tournament, ranking teams, rounds, all matches; затем additional teams и placement matches. Нужные lazy-поля материализуются внутри транзакции. Прежние N+1/порядок запросов не оптимизируются незаметно в переносе.
- Для initialize тот же адаптер/адресная фабрика принимает уже имеющиеся tournament/teams/rounds, дочитывает оставшиеся источники и создаёт ReadSet. Это bootstrap API с legacy-типами, не метод application-порта. Нельзя подменять его новым query по id с другой транзакцией.
- Legacy buildRanking заменяется адаптацией ReadSet → общий composer → result mapper, после переноса всех callers алгоритм старой композиции удаляется. getRanking/getTeams делегируют query через Spring proxy и сохраняют сигнатуры/DTO. Отдельная копия алгоритма для initialize не остаётся.
- finishTournament вызывает прежний фасад getRanking, а новый внешний query с REQUIRED присоединяется к write-транзакции. Нельзя вводить REQUIRES_NEW, новый isolation, ранний commit или перенос ConductChange с командного сервиса.
- RoundRobinApplicationConfiguration явно импортирует query по образцу клубного wiring; одна реализация порта и один query bean. Composer — чистый компонент. После #1051 entity-маппер/адаптер переходят в persistence, result-маппер — в будущий web после подготовки его границы.

Исходный ConductChangeAspect оборачивает команду TransactionTemplate, делает flush перед capture и после operation. AUTO-flush от чтения в write-контексте также остаётся возможным. Требование GET без записей относится к внешнему read-only вызову и проверено отдельно; оно не означает запрет исходного flush незакоммиченных изменений при joined write-read.

## Точный доступ к engines

Application получает Maven-зависимость на padel-tournament-engines. Разрешаются только следующие публичные типы, без разрешения всего legacy service-пакета:

| Владелец | Разрешённые типы |
|---|---|
| RoundRobinSetStandingsEngine | Сам класс, $Team, $Match, $Ranked; private $Meetings не нужен |
| RoundRobinPointsStandingsEngine | Сам класс, $Team, $Ranked |
| RoundRobinFinalOrderEngine (#1376) | Планируемые сам класс, $Standing, $Match, $Ranked; фактический API сверить после #1376 |

Для final-order предлагаются Standing(index, teamId, position), Match(firstId, secondId, firstScore, secondScore, completed, placementStart) и Ranked(index, position). Record Match сохраняет исходные фильтры completed/placement и счёт, а не привносит отдельную политику выбора победителя. #1376 проверяет этот контракт characterization-набором до включения в allowlist.

ArchUnit проверяет точные имена импортированных типов, включая массивы/вложенные типы. Artifact guard не должен просто вырезать разрешённые префиксы из bytecode: это разрешило бы класс RoundRobinPointsStandingsEngineBackdoor. Разрешение применяется только к полному извлечённому имени type reference; остальные legacy-префиксы остаются запрещены. Все существующие ограничения JPA/JDBC/Web/Security/Jackson сохраняются.

Отрицательные пробы #1377: запрещённый соседний legacy-класс в том же package; класс с именем разрешённого engine плюс суффикс; запрещённые entity/DTO через generic/array; private $Meetings; дубликат application в BOOT-INF/classes; отсутствие обязательного query/порта/read-модели. Положительные: фактические публичные типы трёх engines, прежний PublicClubQuery и один application jar, Java 17. Текущие guards в #1375 не изменены.
