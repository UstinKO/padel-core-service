# LFPT-1403: Feature многомодульный монолит, фаза 5 — зафиксировать командную границу круговых позиций и завершения

## Статус
done

## Источник

[Issue #1403](https://github.com/UstinKO/padel-core-service/issues/1403). Техническая постановка из поручения продолжить многомодульный переход после мержа #1398; промежуточного клиентского запроса нет. Родитель [#1402](https://github.com/UstinKO/padel-core-service/issues/1402).

## Контекст / зачем

Зафиксировать до переноса наблюдаемое поведение updateTeamPositions/finishTournament и их транзакционной оболочки на принятом master. Эта задача добавляет characterization/fixtures/анализ; production-реализация команд остаётся прежней.

## Связь с MASTER.md

[MASTER](../MASTER.md): круговой парный турнир, история проведения, роли и границы доменов. Сохраняются POINTS/SETS, личные встречи и разделённые места, независимость круговой статистики от PLACEMENT, права владельца/клубного организатора и история вместе с подтверждённой записью. Новых бизнес-инвариантов нет. Исходная read-фаза принята через PR #1398. Первая задача фазы, не ждёт закрытия родителя.

## Требования

### Функциональные

- Сверить TeamAmericanoService, callers всех POST счёта/MVC finish, RoundRobinStandings/RankingComposer, repositories и ConductChangeAspect/TournamentConductHistory/PublicLiveNotifications; закрепить конкретные будущие модели/порты и порядок чтений/записей.
- Добавить исходные ожидания POINTS/SETS: пустые teams, fallback/null mode, мини-таблица/разделённые места, несортированный вход, круговые позиции при отличающемся finalRanking, pending PLACEMENT, CANCELADO/uninitialized/missing, max номера против count.
- Проверить реальные write-транзакции и persistence context, историю/key/version/FOR UPDATE, standalone позиции без ConductChange и вложенный вызов из RESULT/FINISH. Проверять after-commit и rollback с контролируемыми отказами при записи позиций, статуса, query и history snapshot.
- Зафиксировать повтор finish с/без ключа и автозавершение из submitMatchResult, включая исправление уже завершённого раунда. Не добавлять новые вызовы записи позиций ради устранения старого stale-состояния.
- Подготовить безопасные fixtures и матрицу W01–W18; проверить сериализацию наблюдаемого сценария в двух независимых write-контекстах без заявления двухрепличной приёмки. Недоказанные свойства/расхождения описывать до переноса.
- Сохранить исходные 12 read-baseline/11 JSON и принятые браузерные ожидания. На конечном jar выполнить общий/затронутый smoke и возврат к прежнему артефакту.

### Нефункциональные

Java source/release 17 при CI/Docker 21. Один процесс/один boot jar; Spring DI/@Transactional в application разрешены, JPA/JDBC/Spring Data/Web/Security/Jackson/legacy DTO запрещены. MapStruct на границах; ровно 11 публичных engine-типов, прочий legacy запрещён. Прежние lock/flush/propagation/isolation/history/after-commit сохраняются, REQUIRES_NEW/ранний commit не вводятся. Новых пользовательских строк нет; существующие HTTP/redirect/flash/WS и ES/RU/EN сохраняются.

Каждый реализационный PR самостоятельно проходит полный verify с frontend на отдельной PostgreSQL, guards/negative cases, конечный jar/runtime и реальный затронутый браузер, общий API/React/Expo/session/CSRF/JWT/WS-пилот, репетицию выпуска и прежний jar/возврат. Реальные уведомления и пользовательские БД не используются. Docs-drift и агентская автоматика обязательны; физические устройства/ручные/многорепличные проверки отмечаются только по факту.

## Вне скоупа

Перенос initialize/preview/loadPairs/генерации, submitMatchResult/apply-revert/автозавершения целиком, additional.create и общей платформы ConductChange/history/restore. Вызываемое обновление позиций меняет владельца только в своей задаче, прочие эффекты caller сохраняются. Новые игровые/финансовые правила, generated ranking DTO, UI v2 switching, persistence/web/integrations, физическая БД/Swarm, #1051/#1214 и общий рефакторинг #511/#509 не включаются.

## Изменения в системе

### API

Новых endpoints нет. Сохраняются MVC POST /tournaments/team-americano/admin/{id}/finish и все три действующих POST счёта, ranking GET/public/admin и AdminController-карточка. Прежние права/CSRF и разные HTTP/flash-контракты сохраняются; finish-result маппится в прежний DTO на границе.

### БД

Схема/Liquibase не меняются. Только собственные синтетические PostgreSQL/fixtures. Проверяемые записи — currentPosition, FINALIZADO и прежняя история в исходной транзакции; весь остальной связанный контекст сохраняется. GET не приобретает сохранения.

### UI

Текущие шаблоны/JS/дизайн не переписываются. Реальный браузер public/admin/карточка, RU/ES/EN и 1440/390/refresh подтверждает прежние представления и действия. React/Expo/public live проверяются в пределах действующих соседних контрактов, прямыми потребителями ranking не объявляются.

### Документация

[План фазы](../../docs/modular-monolith/phase-5/README.md), [анализ кода](../../docs/modular-monolith/phase-5/code-analysis.md), [W01–W18](../../docs/modular-monolith/phase-5/test-cases.md), docs/modular-monolith/README.md и plan.md, docs/architecture.md, .agents/PROJECT.md. Первый baseline уточняет конкретные модели/порт, fixtures и измеренные ожидания; каждый следующий PR обновляет фактические мосты и результаты.

## Критерии приёмки

- [x] Новые characterization-тесты проверяют фактические старые результаты и I/O; бизнес-правила и production flow не менялись.
- [x] Документирован проверяемый контракт будущих write-портов без entity/repository/DTO/callback над entity и без делегирования всей операции старому сервису; production-команды/порты пока не вводятся.
- [x] Отказы на конкретных write/history-границах на отдельной PostgreSQL откатывают позиции/статус/историю; отсутствие after-commit отправки проверено после завершения реальной транзакции, не только в fake unit.
- [x] Повторы/key/stale/standalone/RESULT caller и max-versus-count поведение закреплены; исходные read-эталоны остались прежними.
- [x] Полные verify с frontend, guards, runtime/браузер и прежний jar/возврат прошли; точные SHA/jar и ограничения записаны.

## Edge cases

Пустые teams/отсутствующий Tournament для standalone positions, fallback/null mode, POINTS/SETS/полное равенство, guest/nullable-уровни/стороны; PLACEMENT и отличие circle/final. Неинициализированный/CANCELADO/pending турнир, max номера против числа раундов, legacy-дыры номеров. Повтор с/без/new request key, stale version, отказ после частичной записи/при query/history snapshot и полный rollback. Исправление завершённого раунда не получает новый вызов записи позиций; restore сохраняет текущие регистрации/оплаты и поздние версии. Наблюдаемый конкурентный сценарий проверяется в двух независимых write-контекстах; полная двухрепличная приёмка остаётся #1214.

## Открытые вопросы

Бизнес-вопросов нет: текущие результаты/ошибки/эффекты сохраняются. [Конкретный контракт](../../docs/modular-monolith/phase-5/command-contract.md) и [измеренный baseline](../../docs/modular-monolith/phase-5/baseline.md) закреплены 28 новыми проверками; production-команды/порты этой задачей не вводились. Если characterization обнаружит расхождение MASTER с текущим поведением, не исправлять его скрытно в переносе: зафиксировать и отдельно согласовать изменение инварианта. Подготовка не является PASS будущей реализации. Обычная сессия, без pipeline:ready/автомержа/production-деплоя.
