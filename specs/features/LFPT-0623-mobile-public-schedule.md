# LFPT-0623: Feature публичное API расписания турниров — анонимное чтение списка и деталей для мобильного приложения

## Статус
approved

## Источник
[specs/requests/LFPT-0623-mobile-public-schedule.md](../requests/LFPT-0623-mobile-public-schedule.md), issue [#623](https://github.com/UstinKO/padel-core-service/issues/623).

Клиентский запрос сопровождался приложенным заказчиком архивом (готовый `backend-schedule.patch`, "фиче-спека", описание задачи) — внешний непроверенный материал, пришедший вложением в Telegram-сообщении, пересланном от третьего лица. Использован только как референс по желаемому контракту (две анонимные GET-ручки, публичный DTO без контактов участников). Приложенная "фиче-спека" заявляла статус `testing`/"подтверждена пользователем" — это утверждение из непроверенного вложения, не из фактического согласования в этом репозитории, и не принимается на веру. Эта спека написана и проверена с нуля: содержимое приложенного диффа не копировалось, код написан самостоятельно по обычному процессу Analyst/Developer/Tester.

## Контекст / зачем
Мобильное приложение 1Padel.App — отдельный клиент сайта, которому нужно показывать расписание турниров без логина пользователя в приложении. Сегодня анонимного JSON-доступа к расписанию нет: `/api/tournaments/**` требует авторизацию (`SecurityConfig`: `.requestMatchers("/api/**").authenticated()`). Публично без авторизации те же данные уже доступны человеку через Thymeleaf-страницы `/` (`HomeController` → `TournamentService.getActiveTournamentsForHome()`) и `/torneo/{id}` (`TournamentViewController` → `getActiveTournamentById()`), обе под `permitAll()`. Нужен JSON-эквивалент той же пары методов для мобильного клиента.

## Связь с MASTER.md
В `specs/MASTER.md` нет отдельного раздела про видимость турниров — правило реализовано в коде и покрыто тестом `TournamentVisibilidadTest` (LFPT-491): `Tournament.visibilidad` = `PUBLICO` | `SOLO_POR_ENLACE`; `SOLO_POR_ENLACE` не попадает в публичные списки (`filterPublicOnly` в `TournamentService`), но полностью доступен по прямой ссылке/id (`getActiveTournamentById` не фильтрует по `visibilidad`) — это осознанное, уже существующее поведение ("видно тому, у кого есть ссылка"), а не брешь, которую вносит эта фича. Новые эндпоинты переиспользуют ровно эти два метода без изменений — инвариант не меняется, `MASTER.md` трогать не нужно.

## Требования

### Функциональные
- `GET /api/public/tournaments` — анонимный список, те же данные и правила, что на `/`: `TournamentService.getActiveTournamentsForHome()` (активные турниры со статусом `PUBLICADO`/`REGISTRO_ABIERTO`, `SOLO_POR_ENLACE` исключён). Пустой список — `200 []`.
- `GET /api/public/tournaments/{id}` — анонимные детали, те же правила, что на `/torneo/{id}`: `TournamentService.getActiveTournamentById(id)`, включая доступ к `SOLO_POR_ENLACE` по прямому id (существующее поведение `getActiveTournamentById`, не новая брешь). Турнир не найден или неактивен — `404 {"code":"TOURNAMENT_NOT_FOUND"}`. `id`, не являющийся числом (или переполняющий `Long`) — `400 {"code":"INVALID_TOURNAMENT_ID"}`.
- Публичный DTO `PublicTournamentDto` — отдельный от `TournamentDto`, явно перечисленный набор полей: `id`, `name`, `club{id,name,address}`, `type`, `modality`, `gender`, `level` (`categoriaNivel`, может быть `null` — уже показывается без ограничений на публичных страницах `index.html`/`tournament-details.html`, флаг `mostrarNivel` касается только уровня *отдельных участников* в списках регистраций, не уровня самого турнира), `startsAt`+`timeZone` (ISO-8601 с offset, `America/Argentina/Buenos_Aires` — турнир хранит местное аргентинское время), `duration`, `price`+`currency`, `status`, `registrationOpen`, `capacity`+`capacityUnit` (`PLAYERS`|`PAIRS` — пары для `Modalidad.DOBLES`, игроки иначе), `occupied`/`available`/`waitlistCount`, `cancellationDeadline` (ISO-8601 с offset либо `null`), `description` (`infoDetallada`), `websiteUrl` (`${app.base-url}/torneo/{id}`, конвенция уже используется в `TournamentService`/`EmailService`/`PasswordResetService` — `@Value("${app.base-url:http://localhost:8080}")`, не придумывать свой дефолт).
- DTO **не включает**: `ownerId`, `createdBy` (поля управления), `contactoOrganizador` (контакт организатора — не запрошен заказчиком, по умолчанию не публикуем), `faqUrl`, `mostrarNivel`, `isActive`, `visibilidad`, а также любые данные об участниках/регистрациях (`TournamentDto` их и не содержит — участники в отдельных сущностях/эндпоинтах, не трогаются этой фичей).
- Ошибка чтения БД (`DataAccessException`) — `503 {"code":"SCHEDULE_UNAVAILABLE"}`, без текста исключения в ответе.
- `SecurityConfig`: добавить `permitAll()` только для `GET /api/public/tournaments` и `GET /api/public/tournaments/*` — не трогать правило `"/api/**".authenticated()` для всего остального, не расширять права существующих мутаций.

### Нефункциональные
- i18n: новых пользовательских строк нет — ответ полностью машиночитаемый (enum-значения как в коде, `code` для ошибок), текста для перевода не создаётся.
- Безопасность: новая анонимная точка входа, но без новых данных — переиспользует уже публично доступные (без авторизации, через `/` и `/torneo/{id}`) данные и те же два метода сервиса. Не вводит деталей участников/организатора. `@Timed` на листинге (паттерн `TournamentService`/`EmailService`) — потенциально самый частый вызов мобильного клиента.
- Производительность: `getActiveTournamentsForHome()`/`getActiveTournamentById()` не меняются, батчинг (club/registration-count) уже реализован там же — новый код только маппит результат.

## Вне скоупа
- Данные/действия, требующие входа (регистрация, личный кабинет, платежи).
- Публикация в App Store/эндпоинт для самого мобильного приложения — только серверная часть.
- Исходники мобильного приложения, приложенные заказчиком, — не часть этой задачи.
- Расширение набора полей (контакт организатора, FAQ-ссылка) — можно добавить отдельной задачей, если понадобится.

## Изменения в системе

### API
- Новый контроллер `controller/api/PublicTournamentController` — `GET /api/public/tournaments`, `GET /api/public/tournaments/{id}`.
- Новый DTO `dto/PublicTournamentDto` (record).
- Новый маппер `mapper/PublicTournamentMapper` (MapStruct, `componentModel = "spring"`, как остальные мапперы проекта).
- `config/SecurityConfig` — два новых `permitAll()`-маршрута (GET только).

### БД
Нет изменений, новая Liquibase-миграция не нужна.

### UI
Нет изменений шаблонов — чисто JSON API.

### Документация (к обновлению в этом же PR)
- `.claude/CLAUDE.md` — таблица "Auth API"/рядом: добавить строку про `GET /api/public/tournaments` и `/api/public/tournaments/{id}`.
- `docs/endpoints/README.md` — строка на новый файл.
- `docs/endpoints/public-schedule.md` — новый файл: контракт, коды ошибок, какие поля исключены и почему.
- `docs/frontend-react/contract/api.md` — короткая заметка, что минимальный публичный контракт для мобильного клиента появился (без переноса веба на React).

## Критерии приёмки
- [ ] `GET /api/public/tournaments` без авторизации возвращает `200` и JSON-массив; турнир со статусом `PUBLICADO`/`REGISTRO_ABIERTO` присутствует, турнир `SOLO_POR_ENLACE` — отсутствует.
- [ ] Пустая выборка — `200 []`, не ошибка.
- [ ] `GET /api/public/tournaments/{id}` без авторизации для активного турнира (включая `SOLO_POR_ENLACE` по прямому id) — `200` с контрактом DTO.
- [ ] `GET /api/public/tournaments/{id}` для неизвестного или неактивного id — `404 {"code":"TOURNAMENT_NOT_FOUND"}`.
- [ ] `GET /api/public/tournaments/abc` (не число) — `400 {"code":"INVALID_TOURNAMENT_ID"}`.
- [ ] Ответ **не содержит** `ownerId`, `createdBy`, `contactoOrganizador`, `faqUrl`, `visibilidad`, `mostrarNivel`, `isActive` и любых полей об участниках/регистрациях — проверено тестом на реальном JSON, не только по коду DTO.
- [ ] `Modalidad.DOBLES` → `capacityUnit=PAIRS`; `INDIVIDUAL` → `PLAYERS`; `occupied`/`available`/`waitlistCount` совпадают с тем, что считает существующий сервис.
- [ ] `startsAt`/`cancellationDeadline` — ISO-8601 с offset `-03:00` (`America/Argentina/Buenos_Aires`); отсутствующий дедлайн — `null`, не придуманная дата.
- [ ] Существующие защищённые маршруты (`GET /api/tournaments`, `POST /api/tournaments`, `POST /api/public/tournaments`) по-прежнему требуют авторизацию/редиректят на логин — новые `permitAll()` не расширились шире двух указанных GET.
- [ ] `./mvnw verify` зелёный с тестовой PostgreSQL.
- [ ] `python3 .github/bin/docs-drift.py` — 0 расхождений; документация обновлена по карте выше.

## Edge cases
- Турнир существует, но `isActive=false` (снят с публикации) — как неизвестный, `404`.
- `id` отрицательный, `0`, или больше `Long.MAX_VALUE` (нечисловой из-за переполнения) — `400`, не `500`.
- Турнир без `deadlineCancelacion`/`infoDetallada`/`categoriaNivel` — соответствующие поля `null` в JSON, без исключений маппера.
- Два реплики (Docker Swarm) — эндпоинты без состояния, конкурентного доступа не затрагивают.

## Открытые вопросы
Нет открытых бизнес-вопросов в рамках заявленного объёма. Явное предположение первого этапа (зафиксировано в клиентском запросе): турниры проходят в Аргентине, часовой пояс турнира жёстко `America/Argentina/Buenos_Aires`; поддержка других часовых поясов — отдельная задача, если понадобится.
