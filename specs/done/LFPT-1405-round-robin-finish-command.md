# LFPT-1405: Feature многомодульный монолит, фаза 5 — перенести завершение кругового турнира в application

## Статус
done

## Источник

[Issue #1405](https://github.com/UstinKO/padel-core-service/issues/1405). Техническая постановка из поручения продолжить многомодульный переход после мержа #1398; промежуточного клиентского запроса нет. Родитель [#1402](https://github.com/UstinKO/padel-core-service/issues/1402).

## Контекст / зачем

Передать application orchestration finishTournament после готовности команды позиций. Сохранить прежние проверки допуска, общую транзакцию истории и текущий ranking DTO на HTTP-границе.

## Связь с MASTER.md

[MASTER](../MASTER.md): круговой парный турнир, история проведения, роли и границы доменов. Сохраняются POINTS/SETS, личные встречи и разделённые места, независимость круговой статистики от PLACEMENT, права владельца/клубного организатора и история вместе с подтверждённой записью. Новых бизнес-инвариантов нет. Зависит от [#1404](https://github.com/UstinKO/padel-core-service/issues/1404).

## Требования

### Функциональные

- Добавить application finish-команду и порт состояния/сохранения с нейтральными типами/ошибками. Application владеет проверками, вызовом position-команды, переходом FINALIZADO и ranking query; порт не вызывает старый finish целиком.
- Сохранить проверку CANCELADO, MAX(roundNumber) всех фаз, count COMPLETED, short-circuit initialized, повторные вызовы и прежние ошибки/их внешнее представление. Не заменять допуск на query.finished и не исправлять текущий MVC success-flash.
- Legacy facade сохраняет @Transactional/@ConductChange(FINISH) и единственный production-вход; блокировка/flush/key/version/snapshot/history/after-commit остаются в исходной оболочке. Новая команда и query присоединяются к её транзакции, не создают REQUIRES_NEW/ранний commit.
- Legacy-адаптеры/MapStruct сохраняют managed identity и порядок I/O/сохранения состояния по baseline; DTO/entity/Security/Servlet/ConductChange в application не передаются.
- Реальными proxy/портом/новым независимым чтением проверить joined write-read и отказы при positions/save/query/history, idempotency/stale и сериализацию существующего lock. Новых уведомлений или истории из самой команды нет.
- Обновить DI, guards/negative cases, DevTools-пробу новых proxy, документацию/реестр мостов. Все предыдущие read/write ожидания и самостоятельная работоспособность этого PR обязательны.

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

[План фазы](../../docs/modular-monolith/phase-5/README.md), [анализ кода](../../docs/modular-monolith/phase-5/code-analysis.md), [W01–W18](../../docs/modular-monolith/phase-5/test-cases.md), docs/modular-monolith/README.md и plan.md, docs/architecture.md, .agents/PROJECT.md. [Реализация #1405 и результаты](../../docs/modular-monolith/phase-5/finish-command.md) фиксируют границу команды, адаптеры и переходные мосты.

## Критерии приёмки

- [x] В application находится проверка и последовательность завершения, а не фасад над старым legacy finish.
- [x] Готовый/неполный/неинициализированный/CANCELADO/missing, PLACEMENT, разрывы номеров и повторы совпали с baseline; currentPosition/finalRanking различаются как раньше.
- [x] Позиции, FINALIZADO и history атомарны; faults/query/history и stale/replay не оставляют частичных записей/уведомлений после rollback.
- [x] Один write EntityManager/transaction и реальный application bean подтверждены; query видит свои записи до commit и сохраняет прежний DTO.
- [x] Полный verify/guards и runtime/browser, restart/прежний jar/возврат прошли; ни start/score/history-платформа, ни схема/данные production не перенесены.

## Edge cases

Пустые teams/отсутствующий Tournament для standalone positions, fallback/null mode, POINTS/SETS/полное равенство, guest/nullable-уровни/стороны; PLACEMENT и отличие circle/final. Неинициализированный/CANCELADO/pending турнир, max номера против числа раундов, legacy-дыры номеров. Повтор с/без/new request key, stale version, отказ после частичной записи/при query/history snapshot и полный rollback. Исправление завершённого раунда не получает новый вызов записи позиций; restore сохраняет текущие регистрации/оплаты и поздние версии. Наблюдаемый конкурентный сценарий проверяется в двух независимых write-контекстах; полная двухрепличная приёмка остаётся #1214.

## Открытые вопросы

Бизнес-вопросов нет: текущие результаты/ошибки/эффекты сохраняются. Конкретный контракт портов и исходные ожидания закреплены в [baseline #1403](../../docs/modular-monolith/phase-5/baseline.md) и [command-contract](../../docs/modular-monolith/phase-5/command-contract.md). Реализация использует принятую команду позиций #1404 из PR #1409. Независимая клиентская приёмка проводится в #1406 после включения этого backend-среза.
