# Фаза 4: прикладной сценарий чтения круговой таблицы

Координация: [LFPT-1374](https://github.com/UstinKO/padel-core-service/issues/1374). Фаза подготовлена после мержа [POINTS #1371 / PR #1372](../phase-3/round-robin-points-extraction.md). Код анализа — master `20bd696df97f78cefde6f34a2e8dbf25c2a706f2`; рабочая копия документации основана на `0dc4069a` (последующая архивация спеки, без изменения приложения).

## Результат фазы

В padel-application выделен второй прикладной срез после PublicClubQuery: чтение круговой таблицы и итоговых мест постоянных пар. Собственные read-модели/порт и композиция результата отделены от JPA/DTO. Engines остаётся владельцем чистых вычислений, legacy-адаптеры связывают данные и действующие ответы.

В #1377 [перенесён read-срез](application-query.md): application владеет композицией круговой таблицы и итоговых мест наряду с клубным сценарием. [Независимая техническая приёмка #1378](acceptance.md) завершает read-фазу; её включение — финальный шаг. Все семь модулей не объявляются готовыми. В engines десять вычислительных классов: [finalOrder выделен в #1376](final-order-extraction.md), прежний Java API сохранён через MapStruct-адаптер.

## Рабочие задачи

| Порядок | Задача | Слой | Результат | Предшественник |
|---|---|---|---|---|
| 1 | [#1375](https://github.com/UstinKO/padel-core-service/issues/1375) | backend | Baseline API/DTO, граф callers и транзакций | PR #1372 влит |
| 2 | [#1376](https://github.com/UstinKO/padel-core-service/issues/1376) | backend | Нейтральный final-order engine и прежний адаптер | #1375 |
| 3 | [#1377](https://github.com/UstinKO/padel-core-service/issues/1377) | backend | Read-модели/порт/query application и wiring | #1376 и результаты #1375 |
| 4 | [#1378](https://github.com/UstinKO/padel-core-service/issues/1378) | frontend | Независимая клиентская/runtime-приёмка | #1377 |

Спецификации: [координация](../../../specs/features/LFPT-1374-round-robin-application-phase.md), [baseline](../../../specs/done/LFPT-1375-round-robin-query-baseline.md), [итоговые места](../../../specs/done/LFPT-1376-round-robin-final-order-engine.md), [application](../../../specs/done/LFPT-1377-round-robin-application-query.md), [приёмка](../../../specs/features/LFPT-1378-round-robin-application-acceptance.md).

Первый шаг #1375 включён через PR #1379: [application-контракт](application-contract.md), полные wire-эталоны и проверки транзакций уточняют #1377/#1378. Их постановки переведены в approved, зависимости сохранены. В #1376 / PR #1381 выделен final-order engine; в #1377 перенесена read-композиция. Независимая техническая приёмка #1378 оформлена отдельным завершающим PR; ручные/физические ограничения сохранены. Отдельные PR сохраняют порядок backend → frontend и работоспособность после каждого шага. Переноса application в #1375 нет.

[Результаты baseline #1375](baseline.md), [безопасная QA-сводка](baseline-qa-2026-11-25.json). [Итоговая приёмка #1378](acceptance.md) подтверждает wire-эталоны, реальный браузер и полный сохранённый контекст после restart/прежних jar/возврата.

## Основные границы

- Сохраняются TeamAmericanoRankingDto/вложенные DTO, GET ranking, public/admin MVC, getTeams и ответы initialize/finish. Новая генерация ranking DTO из OpenAPI в эту фазу не входит.
- Domain/read-модели и порт принадлежат application, без legacy/JPA/DTO. Сценарию разрешены согласованные Spring @Service/DI/@Transactional; данные и вычислительные типы чистые.
- Application → engines вводится с разрешениями конкретных фактических типов. Запрет всего legacy package нельзя просто снять: в этом пакете сейчас находятся и выделенные engines, и запрещённые сервисы/entities.
- JPA-адаптер/MapStruct entity↔read-model остаются под bootstrap до #1051; result↔DTO — на legacy-входе до будущего web. У каждого моста указан потребитель/условие удаления.
- Read-композиция имеет одного владельца. initialize сохраняет результат по незакоммиченным данным, finish — чтение в той же write-транзакции; REQUIRED, без нового isolation/locks/commit/REQUIRES_NEW. GET не приобретает записи позиций/истории.

[Анализ фактического кода и рисков](code-analysis.md) обосновывает этот объём. Схема семи модулей, владение портами и Spring/ACID уже согласованы в [целевой архитектуре](../target-architecture.md); новый бизнес-инвариант не предлагается.

## Приёмка и дальнейшая работа

Каждый реализационный PR проходит полный verify с frontend/отдельной PostgreSQL, актуальные guards, общий runtime smoke и затронутые браузерные сценарии, а также возврат на прежний jar. Финальная #1378 сверяет исходные ответы, отображение и данные на конкретном итоговом артефакте; не заменяет собственные проверки #1376/#1377.

Команды запуска/счёта/завершения, начисление/валидация и их history/after-commit пока остаются в legacy. Их будущие порты и write-сценарии нужно декомпозировать отдельно по фактическому графу. Фаза 4 не снимает все условия поздней #1051. Persistence/web/integrations, физическая PostgreSQL и Swarm этой фазой не выделяются.

Физические устройства и новый ручной PASS владельца подтверждаются только фактическим сеансом; принятые ограничения #1067/Expo сохраняются. Производственные Java/POM/контракты/схема в #1375 не меняются; фактические новые проверки и ограничения перечисляются в отчёте baseline.
