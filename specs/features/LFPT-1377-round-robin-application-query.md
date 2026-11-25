# LFPT-1377: Feature многомодульный монолит, фаза 4 — перенести чтение круговой таблицы в application

## Статус
approved

## Источник

[Issue #1377](https://github.com/UstinKO/padel-core-service/issues/1377), родитель [#1374](https://github.com/UstinKO/padel-core-service/issues/1374). Техническая постановка напрямую из поручения подготовить задачи следующей фазы после мержа PR #1372. Зависит от #1376.

## Контекст / зачем

Родитель #1374. В padel-application разместить один владелец композиции круговой таблицы/итоговых мест; инфраструктура реализует его порт, HTTP/MVC сохраняют текущие DTO.

## Связь с MASTER.md

[MASTER](../MASTER.md): круговой парный турнир, история проведения, роли и границы доменов. POINTS/SETS, личные встречи и общие места, дополнительные встречи без изменения круговой статистики, завершение/восстановление и права сохраняются. Изменения бизнес-инвариантов не требуются.

## Требования

### Функциональные

- Ввести собственные нейтральные record/enum read-моделей, результата и порта чтения application; ни JPA/proxy, ни старые DTO/enum, ни Repository/Sort/Pageable/Principal не пересекают границу. Domain/порт не зависят от Spring; сценарию разрешены согласованные @Service/DI/@Transactional.
- Подключить application→engines. Явно перечислить фактически используемые публичные типы/вложенные типы engines в ArchUnit и artifact guard; сохранить запрет остальных com/padle/core/padelcoreservice/** и добавить отрицательный пример соседнего legacy-класса в том же package. Запреты JPA/JDBC/Web/Security/DTO и остальные модульные проверки сохранить.
- Legacy JPA-адаптер и MapStruct entity↔read-model остаются в root src под bootstrap до #1051. Отдельный MapStruct result↔TeamAmericanoRankingDto сохраняет DTO и JSON на границе. Гостевые игроки/уровни/сторона и все прежние поля сохраняются без новых персональных полей.
- Сценарий использует существующие SETS/POINTS/final-order engines, вычисляет total/completed/finished/roundRobinComplete/fallback config и дополнительные встречи по baseline. Новых формул/копий старой композиции не оставлять.
- Переключить getRanking/getTeams и их MVC/API/Admin callers через прежнюю фасадную границу. Для initialize сохранить композицию по его незакоммиченным данным через тот же application-компонент; для finish чтение присоединяется к прежней write-транзакции (REQUIRED). Не вводить REQUIRES_NEW, новый isolation/lock, ранний flush/commit или изменение порядка ConductChange/history/after-commit.
- Сохранить порядок/семантику запросов и отсутствие записей от GET; материализовать нужные lazy-поля в адаптере внутри транзакции. Явный wiring по образцу PublicClubApplicationConfiguration; один bean/реализация порта, без циклов legacy↔application. Старую композицию удалить после переключения всех её callers; оставшиеся фасады/мапперы перечислить адресно с условием удаления.

### Нефункциональные

Java release 17, типизированные records/порты, MapStruct на границах. Собственные синтетические данные и PostgreSQL; внешние реальные уведомления не отправляются. Каждый реализационный PR самостоятельно проходит полный verify с frontend и runtime smoke, не требует последующей задачи для восстановления. Зависимости фаз — Java в одном процессе; один boot jar.

## Вне скоупа

Перенос команд запуска/счёта/завершения и начисления, смена формул/HTTP/JSON/WS/HTML, generated ranking DTO, UI v2 switching, новые дизайн/переводы, persistence/web/integrations и физическая БД/Swarm. Проверки команды не означают её перенос; #1051 остаётся поздней задачей.

## Изменения в системе

### API

Новых endpoints нет. Сохраняются GET /tournaments/team-americano/api/{id}/ranking, public/admin MVC и ответы initialize/finish. Реальные прямые/непрямые потребители уточняет #1375; API DTO остаются в legacy на этой фазе.

### БД

Схема/Liquibase и физическое размещение сохраняются. Только собственная тестовая БД; сохраняются propagation/isolation/readOnly/flush, история и after-commit. GET не приобретает записи.

### UI

Действующие страницы/контракты сохраняются. Проверка через браузер не включает новый UI; RU/ES/EN и desktop/mobile по исходной матрице.

### Документация

[План фазы](../../docs/modular-monolith/phase-4/README.md), [анализ](../../docs/modular-monolith/phase-4/code-analysis.md), docs/modular-monolith/plan.md и README, docs/architecture.md, .agents/PROJECT.md. Отчёты и ограничения обновляются по фактическим проверкам.

## Критерии приёмки

- [ ] Unit-тесты application с fake-портом и чистыми engines воспроизводят baseline всех состояний; application не импортирует legacy/JPA/DTO и не пишет при чтении.
- [ ] Integration-тесты доказывают реальный proxy/транзакцию, joined write-read при initialize/finish, прежний rollback/history и отсутствие нового save/flush от GET. Контракт ошибки отсутствующего турнира совпал.
- [ ] Все исходные wire/DTO/MVC эталоны после переноса совпали, включая null/enum/списки/имена/стороны/итоговые места; формулы имеют одного владельца.
- [ ] Архитектурные и отрицательные artifact-проверки различают разрешённый engine и запрещённый legacy того же package; одна application-библиотека и один исполняемый jar.
- [ ] Полный clean verify с frontend/отдельной PostgreSQL, общий runtime smoke, реальный браузер и release/rollback проходят; ни одна следующая задача не нужна для восстановления работоспособности этого PR.
- [ ] Документация содержит фактический граф/владение/адресные мосты и границы: write-сценарии и persistence ещё не перенесены.

## Edge cases

Пустой/неинициализированный/несуществующий турнир, null scoringMode и fallback config, гостевой партнёр/уровень/сторона, частичный круг, равенства/общие места, pending и дополнительные встречи без места, исправление/restore. Чтение из write-транзакции видит её изменения; не вводятся новый isolation/lock/REQUIRES_NEW и новый commit. Отсутствие повторной ручной/физической приёмки отмечается явно. Некорректные legacy-входы не превращаются в новые бизнес-правила.

## Открытые вопросы

Бизнес-вопросов нет. Конкретные модели/порт, wiring и joined write-read описаны по baseline #1375: [application-контракт](../../docs/modular-monolith/phase-4/application-contract.md). Зависимости задач сохраняются; фактический API final-order engine сверить после #1376 до включения в allowlist. Код application-среза в #1375 не переносится.
