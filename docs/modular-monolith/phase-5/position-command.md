# Сохранение круговых позиций в application

Реализация [LFPT-1404](https://github.com/UstinKO/padel-core-service/issues/1404) продолжает принятый [baseline #1403 / PR #1408](baseline.md). Исходный master — `bbe0d788fad76d48eb224a295c948d0ab3f5874e`. Бизнес-правила, endpoints, схема, страницы и POM не меняются. Завершение целиком остаётся задачей #1405; независимая приёмка фазы — #1406.

## Владение и данные

`RoundRobinPositionCommand.update(Long)` — отдельный Spring bean с write-транзакцией REQUIRED. Он загружает `RoundRobinPositionInput` через `RoundRobinPositionPort`, вызывает общий чистый `RoundRobinStandingsCalculation` и передаёт `List<RoundRobinPosition>` обратно в порт. `TeamAmericanoService.updateTeamPositions` делегирует команде. Самостоятельный вызов открывает write-транзакцию без новой истории; вызовы из RESULT/FINISH присоединяются к прежней внешней транзакции.

Модель содержит только mode, числовые nullable-поля команд и значения матчей. Результат сохраняет исходный index, teamId и position. Entity, Repository, DTO, JPA/JDBC, Web/Security/Jackson в application не добавляются. Mode/phase/status используют собственные enum существующей read-модели. [Конкретный контракт](command-contract.md) сохранён.

Общая подготовка входов POINTS/SETS вынесена из `RoundRobinRankingComposer` в `RoundRobinStandingsCalculation`: query и command используют один активный алгоритм подготовки, формулы остаются в engines. Разные входные порядки query и write не унифицируются. FinalOrder-композиция остаётся прежней.

## JPA и транзакция

`LegacyRoundRobinPositionAdapter.load` сохраняет порядок чтений: rounds ASC → первый circle mode/fallback POINTS → teams без ORDER BY → все matches ASC. MapStruct `RoundRobinPositionMapper` материализует минимальные значения без профилей. Для POINTS не вычисляется неиспользуемый gameDifference и не материализуются неиспользуемые matches; сами repository-чтения сохранены. Пустой SETS также не материализует matches. Это сохраняет прежние nullable/ленивые обращения.

`savePositions` получает ссылки на уже загруженные managed-команды через тот же EntityManager. MapStruct изменяет только currentPosition; saveAll получает исходные объекты в вычисленном порядке. Дополнительного SELECT или production ThreadLocal нет. Реальная JPA-проверка измеряет три SELECT (rounds/teams/matches), один SELECT команд и отсутствие чтения профилей. Успешная запись изменяет currentPosition и штатный updated_at, остальные поля остаются прежними. Исключение на второй команде после настоящего flush первой откатывает полные строки вместе с timestamps; самостоятельная команда не создаёт историю.

Проверки прежних RESULT/FINISH наблюдают фактический bean/порт и один EntityManager. Старые точки вызовов, locks, версия/ключ, ConductChange, snapshot, restore и уведомление после commit остаются в bootstrap. Новых REQUIRES_NEW, раннего commit, manager или isolation нет.

## Совместимые мосты

- `TeamAmericanoService.updateTeamPositions` — делегат для прежних callers finish/автозавершения. Их перенос выполняется отдельно; #1405 меняет только согласованную finish-границу.
- `RoundRobinStandings.rank` больше не имеет production-callers. Его Java API и старые characterization остаются совместимым мостом; удаление требует отдельного учёта оставшихся тестовых/внешних потребителей. Старые POINTS/SETS MapStruct-мапперы также используются совместимыми helper/comparator API.
- Entity-мапперы, repositories, новый position-адаптер, read/DTO-адаптеры остаются в bootstrap до подготовки позднего persistence-среза #1051. Эта задача не разблокирует #1051 и не выделяет физическую БД/Swarm.

## Проверки реализации

[Машинный отчёт](position-command-qa-2026-11-25.json) содержит SHA артефактов и измерения. Полный `./mvnw -B clean verify` с frontend на отдельной PostgreSQL: **1647 тестов, 0 ошибок/падений, 5 штатных пропусков**; Java runtime 21, source/release 17. Добавлено 19 проверок: 12 application, одно архитектурное правило и шесть adapter/JPA. Исходные 28 write-проверок сохраняют ожидания; адаптированы только DI и наблюдение новой границы. Старые 12 read-проверок и 11 полных JSON побайтно сохранены.

Artifact guards/отрицательные пробы проходят. REQUIRED application содержит 30 классов; allowlist engines сохраняет ровно 11 публичных типов. ArchUnit дополнительно разрешает preparation POINTS/SETS только общему calculation. Десять вычислительных классов engines и 24 обязательных вложенных типа прежние. Автоматика: 31 GitHub + 48 relay + 5 worker (один штатный пропуск) + 15 preview + 22 release; docs-drift/agent-migration без расхождений.

DevTools проходит два настоящих warm-start с общим RestartClassLoader для command/query/port/adapter/MapStruct/engines/service и реальными Spring-прокси. Это отдельный минимальный probe-контекст: repositories/EntityManager и transaction manager в нём тестовые. Он доказывает загрузчики/wiring, а SQL/ACID/managed identity доказывают отдельные интеграционные тесты с PostgreSQL. Include-настройки DevTools не расширены.

Конечный JAR собран до итогового коммита: SHA-256 `4ec17d7e4f325dfa512121a5af917e89913ecbfdf4b978d9c577e203cbc35f81`. Прежний принятый JAR #1403 имеет SHA-256 `94b346d14ce40a6717df0cfc5e91245006c8c285c868f0e7b790bb1d66c2a255`. Из 972 production-классов 957 совпадают побайтно, добавлено 11, изменено четыре, удалений нет. `TeamAmericanoService$PairData` изменился только из-за номеров строк; engines/api-contract совпадают с baseline. Файлы API, UI, схемы и POM не меняются.

Браузер/runtime и общий пилот прошли: 72 представления круговой матрицы и ещё 48 повторных представлений после restart, session/CSRF/JWT/права, два WS-зрителя и плей-офф 8/9/16. Репетиция выпуска проверила lazy chunk, rollback и восстановление после chunk/readiness-сбоев. Restart и возврат на принятый JAR #1403 с повторным запуском текущего сохранили ответы семи RR и пяти pilot-случаев; для семи RR дополнительно сравнивались полные связанные строки/история и схема/Liquibase. Stop/up сохранил RR-контрольные данные. GET не записывает позиции. Аудит последнего app-контейнера не обнаружил quota/template/parse ошибок; это не аудит всех сменявшихся контейнеров. Новые ручные сеансы, физические устройства, VoiceOver и две production-реплики не запускались. Известные #445/#453/#1067 и обычный Expo Basic 401 сохраняются; проверка concurrency двух контекстов одного процесса не заменяет #1214.
