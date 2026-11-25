# LFPT-1404: Feature многомодульный монолит, фаза 5 — перенести сохранение круговых позиций в application

## Статус
approved

## Источник

[Issue #1404](https://github.com/UstinKO/padel-core-service/issues/1404). Техническая постановка из поручения продолжить многомодульный переход после мержа #1398; промежуточного клиентского запроса нет. Родитель [#1402](https://github.com/UstinKO/padel-core-service/issues/1402).

## Контекст / зачем

Выделить общий write-сценарий updateTeamPositions, используемый finish и автозавершением раунда, без переноса обработчика счёта или изменения мест.

## Связь с MASTER.md

[MASTER](../MASTER.md): круговой парный турнир, история проведения, роли и границы доменов. Сохраняются POINTS/SETS, личные встречи и разделённые места, независимость круговой статистики от PLACEMENT, права владельца/клубного организатора и история вместе с подтверждённой записью. Новых бизнес-инвариантов нет. Зависит от [#1403](https://github.com/UstinKO/padel-core-service/issues/1403).

## Требования

### Функциональные

- Добавить application-команду, собственные материализованные значения и порт нужных операций. Сохранить round-mode fallback, исходный порядок несортированных teams/all matches и условия SETS-фильтра.
- Использовать уже выделенные SETS/POINTS engines и общую application-подготовку standings с query; не копировать формулы/подготовку в третий вариант и не запускать полную ranking-композицию с лишними чтениями вместо существующего position-flow.
- Legacy-адаптер и MapStruct применяют только вычисленные currentPosition к тем же managed-объектам; не теряют identity/lazy/flush и не перезаписывают остальные поля/статистику. Пустые входы и фактические обращения фиксирует baseline.
- TeamAmericanoService.updateTeamPositions становится делегатом. Существующие вызовы из tryAutoCompleteRound/finish сохраняют места вызова и одну внешнюю write-транзакцию; самостоятельный вызов остаётся write без новой ConductChange/history.
- Расширить REQUIRED/ArchUnit/artifact и явное DI новыми собственными типами; ровно 11 разрешённых публичных engine-типов и запреты legacy/JPA/JDBC/Web/Security/Jackson сохраняются. Старый helper/его тесты адресно учесть как совместимый мост.
- Повторить неизменный write/read baseline, реальные port/proxy/rollback, конечный jar и затронутый браузер; собственный полный runtime/прежний jar/возврат до передачи PR.

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

- [ ] Расчёт/оркестрация сохранения круговых позиций принадлежат application; порты не возвращают entities или старый Repository.
- [ ] POINTS/SETS/равенства/пустые данные/guest и все исходные позиции совпали; PLACEMENT не подменяет сохранённые круговые места.
- [ ] Standalone и вложенный RESULT/FINISH реально вызывают новый bean/порт в нужном write-контексте; исключение откатывает все частичные изменения.
- [ ] GET и исходные 12/11 не приобрели запись; API/HTML/история/статистика счёта прежние.
- [ ] Полный verify с frontend/guards, browser/runtime/release и старый jar/возврат прошли; временные мосты и ограничения задокументированы.

## Edge cases

Пустые teams/отсутствующий Tournament для standalone positions, fallback/null mode, POINTS/SETS/полное равенство, guest/nullable-уровни/стороны; PLACEMENT и отличие circle/final. Неинициализированный/CANCELADO/pending турнир, max номера против числа раундов, legacy-дыры номеров. Повтор с/без/new request key, stale version, отказ после частичной записи/при query/history snapshot и полный rollback. Исправление завершённого раунда не получает новый вызов записи позиций; restore сохраняет текущие регистрации/оплаты и поздние версии. Наблюдаемый конкурентный сценарий проверяется в двух независимых write-контекстах; полная двухрепличная приёмка остаётся #1214.

## Открытые вопросы

Бизнес-вопросов нет: текущие результаты/ошибки/эффекты сохраняются. Конкретный контракт портов и исходные ожидания закреплены в [baseline #1403](../../docs/modular-monolith/phase-5/baseline.md) и [command-contract](../../docs/modular-monolith/phase-5/command-contract.md); до production-переноса требуется включение его PR. Если characterization обнаружит расхождение MASTER с текущим поведением, не исправлять его скрытно в переносе: зафиксировать и отдельно согласовать изменение инварианта. Подготовка не является PASS будущей реализации. Обычная сессия, без pipeline:ready/автомержа/production-деплоя.
