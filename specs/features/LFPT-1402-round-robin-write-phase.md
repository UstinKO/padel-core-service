# LFPT-1402: Feature многомодульный монолит, фаза 5 — выделить сохранение позиций и завершение кругового турнира

## Статус
approved

## Источник

[Issue #1402](https://github.com/UstinKO/padel-core-service/issues/1402). Техническая постановка из поручения продолжить многомодульный переход после мержа #1398; промежуточного клиентского запроса нет. Продолжение #1041 и принятой фазы #1374.

## Контекст / зачем

После принятой read-фазы #1374 / PR #1398 перенести первый ограниченный write-срез Team Americano в padel-application. Сначала общий сценарий сохранения круговых currentPosition, затем завершение; запуск, обработка счёта и платформа истории сохраняют прежних владельцев.

## Связь с MASTER.md

[MASTER](../MASTER.md): круговой парный турнир, история проведения, роли и границы доменов. Сохраняются POINTS/SETS, личные встречи и разделённые места, независимость круговой статистики от PLACEMENT, права владельца/клубного организатора и история вместе с подтверждённой записью. Новых бизнес-инвариантов нет. Исходная read-фаза принята через PR #1398. Реализация — дочерними задачами.

## Требования

### Функциональные

- Разобрать фактический граф write-вызовов и зафиксировать baseline до переноса: позиции, допускающее завершение состояние, ошибки, история, блокировка и after-commit.
- Перенести updateTeamPositions в application с собственным портом/значениями и общей с read-композицией подготовкой SETS/POINTS engines. Не подменять круговые позиции итоговыми и не писать из GET.
- Перенести orchestration finishTournament: прежние проверки, команда позиций, FINALIZADO, joined ranking query. Хранение/маппинг — legacy-адаптеры; facade сохраняет @Transactional/@ConductChange и перевод ошибок/DTO.
- Независимо повторить клиентскую/runtime-приёмку на принятом backend. Каждый промежуточный PR работоспособен самостоятельно.

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

- [ ] Baseline и обе реализации выполнены отдельными backend PR; независимая frontend-приёмка — отдельным следующим PR.
- [ ] В application один владелец сохранения позиций и завершения; нет вызова старого finish через порт вместо переноса orchestration, entity/repository/ConductChange в application не передаются.
- [ ] Сохранены текущие MAX(roundNumber), count COMPLETED всех фаз, short-circuit initialized и поведение повторов; currentPosition остаётся круговым.
- [ ] Реальные proxy/порт/EntityManager подтверждают одну write-транзакцию, общий rollback позиций/статуса/истории и отсутствие invalidation после rollback; прежний lock/version/key порядок сохранён.
- [ ] Исходные 12 read-baseline/11 JSON не ослаблены; конечный jar, браузер, restart/прежний jar/возврат прошли, ограничения перечислены.
- [ ] Остаток start/score/additional/history/persistence адресно учтён; все семь модулей готовыми и #1051 разблокированной не объявляются.

## Edge cases

Пустые teams/отсутствующий Tournament для standalone positions, fallback/null mode, POINTS/SETS/полное равенство, guest/nullable-уровни/стороны; PLACEMENT и отличие circle/final. Неинициализированный/CANCELADO/pending турнир, max номера против числа раундов, legacy-дыры номеров. Повтор с/без/new request key, stale version, отказ после частичной записи/при query/history snapshot и полный rollback. Исправление завершённого раунда не получает новый вызов записи позиций; restore сохраняет текущие регистрации/оплаты и поздние версии. Наблюдаемый конкурентный сценарий проверяется в двух независимых write-контекстах; полная двухрепличная приёмка остаётся #1214.

## Открытые вопросы

Бизнес-вопросов нет: текущие результаты/ошибки/эффекты сохраняются. [Baseline #1403](../../docs/modular-monolith/phase-5/baseline.md) и [контракт](../../docs/modular-monolith/phase-5/command-contract.md) закреплены отдельной первой задачей; оставшиеся команды и итоговая приёмка ещё не реализованы. Если characterization обнаружит расхождение MASTER с текущим поведением, не исправлять его скрытно в переносе: зафиксировать и отдельно согласовать изменение инварианта. Подготовка не является PASS будущей реализации. Обычная сессия, без pipeline:ready/автомержа/production-деплоя.
