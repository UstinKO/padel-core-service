# LFPT-376: Клубные аккаунты — изоляция доступа по клубу (backend + UI)

## Статус
approved

## Источник
[GitHub issue #376](https://github.com/UstinKO/padel-core-service/issues/376) — прямая техническая постановка от архитектора/пользователя, без промежуточного клиентского запроса. Вторая часть серии клубных аккаунтов: зависит от [#375](https://github.com/UstinKO/padel-core-service/issues/375) (модель данных `Owner.clubId` + роль `CLUB_ADMIN`, done, `specs/done/LFPT-375-club-account-data-model.md`); после этого issue — #377 (пилот Black Padel).

Telegram-сообщение (для reply): 4427

## Контекст / зачем
LFPT-375 завёл роль `OwnerRole.CLUB_ADMIN` и поле `Owner.clubId`, но на уровне поведения ничего не изменил — роль существовала только как данные. Сейчас `/admin/**` защищён только по роли целиком (`hasAnyRole(...)`, без `CLUB_ADMIN`), а единственная существующая проверка владения ("это мой турнир?") делается вручную по `Tournament.ownerId`, причём непоследовательно: часть потоков (Americano — `isOrganizerOnly`-паттерн, KingOfCourt.initializeTournament) её имеют, часть (Team Americano, Team Playoff, Bracket-матчи, оплаты, дашборд-списки) — не имеют вообще, для любой роли. Цель фичи — дать клубному администратору реально работающую, изолированную по клубу админку: видит и администрирует только турниры/платежи/матчи своего клуба, получает 403 при прямом обращении по ID к чужому турниру.

## Связь с MASTER.md
`specs/MASTER.md` уже содержит строку `ROLE_CLUB_ADMIN` (добавлена в LFPT-375) с пометкой "изоляция доступа... реализуется в LFPT-376". Эта спека обновляет ту же строку, убирая пометку "ещё не реализовано" и кратко описывая, что именно изолируется. Инварианты не меняются, только описание роли.

## Требования

### Функциональные

1. **Доступ к `/admin/**`**: `SecurityConfig` разрешает роль `CLUB_ADMIN` наравне с `OWNER/SUPER_ADMIN/ORGANIZER/ADMIN`. Логин `CLUB_ADMIN` редиректит на `/admin` (как остальные админские роли), не на `/players/dashboard`.

2. **Центральная проверка доступа** — новый `TournamentAccessService` (`service/`), единая точка принятия решения "может ли этот `Owner` управлять этим турниром":
   - `SUPER_ADMIN`/`ADMIN` (`Owner.canViewAllTournaments()`) — доступ ко всем турнирам (без изменений, существующее поведение).
   - `CLUB_ADMIN` — доступ, только если `Owner.clubId` совпадает с `Tournament.clubId` (оба не `null`).
   - Остальные (`OWNER`/`ORGANIZER`) — доступ, только если `Tournament.ownerId` совпадает с `Owner.id` (существующее поведение, без изменений).
   - Резолверы по производным ID (`matchId` bracket-матча, `roundId`/`matchId` Americano, `teamId`/`matchId` Team Playoff, `kingId`/`roundId`/`resultId` King of Court) — резолвят до `tournamentId`/`clubId` через уже существующие JPA-связи (`Match.tournamentId`, `AmericanoRound/AmericanoMatch/AmericanoTeam.tournament`, `TournamentKingOfCourt.tournament`, `KingOfCourtRound.tournamentKing.tournament`, `KingOfCourtMatchResult.court.round.tournamentKing.tournament`) и делегируют в основную проверку.
   - Отказ — `org.springframework.security.access.AccessDeniedException` (уже используемый в проекте паттерн, см. `ClubService`/`AdminClubController`) — единообразно долетает до `CustomAccessDeniedHandler`: JSON 403 для `/api/**`, спец-редирект на публичную страницу турнира для `GET /admin/tournaments/{id}`, иначе `/error/403`. Так вместо непоследовательного `SecurityException` (который для не-`/api/` резолвится в голый `redirect:/` без кода 403) все новые проверки получают уже существующий, протестированный 403-контракт.

3. **Права распространяются на все потоки, где проверка владения сейчас отсутствует** (issue, п.2) — добавляется на уровне контроллера (перед вызовом сервиса), без сохранения статeless-семантики отдельных сервисов, кроме двух точек, где проверка уже жила внутри сервиса (см. "Изменения в системе"):
   - `AdminController`: `viewTournament`, `showEditForm`, `updateTournament` (см. п.4), `deleteTournament`, `deactivateTournament`, `moveToWaitlist`, `moveToMain`.
   - `TournamentController` (REST): `deleteTournament` (роли эндпоинта не меняются — `CLUB_ADMIN` в `@PreAuthorize` этого контроллера не добавляется, см. "Вне скоупа").
   - `PaymentManagementController`: `paymentManagementPage`, `saveAndAddTeam`, `savePayments` — сейчас проверки владения нет вообще ни для одной роли.
   - `AdminMatchController` (bracket): `updateMatchResult`, `generateBracket` — сейчас проверки нет вообще.
   - `AdminKingOfCourtController` + `KingOfCourtApiController`: все мутирующие эндпоинты, кроме уже защищённого `initializeTournament` (см. п.4) — `saveMatchResult`, `updateMatchResult`, `nextRound`, `finishTournament`, `updateYoutubeLink`, `rollbackLastRound`, `resetTournament`.
   - `AmericanoViewController`/`AmericanoApiController`: эндпоинты, у которых `AmericanoService` уже проверяет владение (см. п.4), дополнительно получают `CLUB_ADMIN` в `@PreAuthorize`, чтобы вообще пройти ролевой гейт; `initializeAmericanoTournament` — новая проверка (см. п.4).
   - `TeamAmericanoViewController` + `TeamPlayoffViewController` (полностью, включая `/api/**` под-пути) — ни одной проверки владения нет сейчас ни для одной роли; добавляется на уровне контроллера для каждого мутирующего эндпоинта.

4. **Расширение уже существующих проверок** (там, где проверка владения уже жила внутри сервиса и учитывала только `ownerId`):
   - `TournamentService.updateTournament`/`updateTournamentStatus` — сигнатура меняется с `(id, dto, ownerId, isSuperAdmin)` на `(id, dto, Owner owner)` (два вызывающих места: `AdminController`, `TournamentController`); внутренняя проверка заменяется на `TournamentAccessService.assertCanManageTournament(owner, tournament)`.
   - `AmericanoService` — 10 повторяющихся блоков `isOrganizerOnly`-проверки (DRY-нарушение уже в текущем коде) заменяются на один вызов `tournamentAccessService.assertCanManageTournament(currentOwner, tournament)` каждый; плюс `initializeAmericanoTournament` (сейчас без проверки вообще) получает новый параметр `Owner currentOwner` и ту же проверку.
   - `KingOfCourtService.initializeTournament` — inline-проверка (`SUPER_ADMIN`/`ADMIN` bypass, иначе `ownerId`) заменяется на `TournamentAccessService.assertCanManageTournament`.

5. **Фильтрация списков на уровне запроса** (issue, п.3):
   - `AdminController.listTournaments()` — для `CLUB_ADMIN` использует уже существующий `TournamentService.getTournamentsByClub(owner.getClubId())` вместо `getAllTournaments()`.
   - `AdminPlayerController.listPlayers()` — для `CLUB_ADMIN` фильтрует список игроков до тех, у кого есть хотя бы одна регистрация на турнир своего клуба (новый метод `TournamentRegistrationRepository.findDistinctPlayerIdsByTournamentClubId(clubId)`, решение по неоднозначности "что значит игрок клуба" — см. "Edge cases").
   - `AdminPlayerController.viewPlayer()` — для `CLUB_ADMIN` доступ только к игрокам, прошедшим тот же фильтр (иначе `AccessDeniedException`); список регистраций на странице игрока не фильтруется дополнительно (см. "Вне скоупа").
   - `AdminClubController.listClubs()` — для `CLUB_ADMIN` возвращает список из одного (своего) клуба вместо всех; `viewClub()` — доступ только к своему клубу (`AccessDeniedException` для чужого); создание/редактирование/удаление/toggle-status клуба остаются `SUPER_ADMIN`-only (без изменений, `CLUB_ADMIN` и так был бы заблокирован существующей проверкой).

6. **Super Admin** — без изменений в правах; фильтр по клубу в UI списка турниров (issue, п.4) — новый `<select>` в `admin/tournaments/list.html`, GET-параметр `clubId`, обрабатывается в `AdminController.listTournaments(@RequestParam(required=false) Long clubId, ...)` через уже существующий `getTournamentsByClub`. Список клубов для дропдауна — `clubService.getActiveClubsForAdmin()`.

7. **Клубная роль в UI** (issue, п.5) — тот же `<select>` в `admin/tournaments/list.html` скрыт условием `isSuperAdmin` (уже передаётся в модель).

8. **Создание турниров клубным админом** — технический риск, не упомянутый явно в критериях приёмки issue, но прямое следствие изоляции: форма `admin/tournaments/form.html` сейчас предлагает выбрать клуб из дропдауна (`clubService.getActiveClubsForAdmin()`) без ограничений. Если оставить как есть, `CLUB_ADMIN` сможет создать турнир, привязанный к чужому клубу, что обесценивает всю изоляцию. Решение (техническое, не бизнес-вопрос — минимальное, не меняющее сам факт "может ли клубный админ создавать турниры", только то, какому клубу турнир может быть привязан): `AdminController.newTournamentForm`/`createTournament` для `CLUB_ADMIN` — дропдаун клуба в модели сужается до одного (своего) клуба, `TournamentDto.clubId` из формы игнорируется и принудительно выставляется в `owner.getClubId()` на сервере (не доверяем клиенту).

### Нефункциональные
- i18n: новых пользовательских строк с текстом немного (заголовок фильтра по клубу в списке турниров) — добавить ключ в `messages_{es,ru,en}.properties`, не хардкодить текст в шаблоне.
- Безопасность: это и есть суть фичи — новых точек входа данных от пользователя нет, только новые проверки авторизации существующих.
- Производительность: `findDistinctPlayerIdsByTournamentClubId` — JPQL `SELECT DISTINCT tr.player.id FROM TournamentRegistration tr WHERE tr.tournament.clubId = :clubId`, обычный индексированный джойн, не батч-проблема (аналогичные списочные запросы в проекте уже так делаются).

## Вне скоупа
- `/api/tournaments/**` (`TournamentController`, REST CRUD-зеркало `AdminController`) — роли в его `@PreAuthorize` не расширяются на `CLUB_ADMIN`. Ни один шаблон admin-панели не дергает эти REST-эндпоинты для мутаций (формы шлют на `/admin/tournaments/**`), а issue не просит открывать отдельный REST-контракт. `TournamentService.updateTournament/updateTournamentStatus` всё равно чинятся (см. Требования п.4), потому что их вызывает `AdminController`, но сам REST-контроллер остаётся `SUPER_ADMIN`/`ORGANIZER`-only, как был.
- Дашборд `/admin` (`AdminController.adminPanel`) — `recentTournaments`, счётчики (`totalPlayers`, `totalOwners`, `totalTournaments`, `totalWaitlist`, `matchesInProgress`, `upcomingMatches`, `activeTournaments`) остаются платформенными (не фильтруются по клубу). Issue явно тестирует `/admin/tournaments` (список), не сводную панель. Список 5 последних турниров с чужими названиями на дашборде клубного админа — известное ограничение этой спеки, не критерий приёмки issue; возможный follow-up отдельным issue.
- Список регистраций на странице `admin/players/{id}` — не фильтруется по клубу даже для `CLUB_ADMIN`, который прошёл проверку доступа к самой странице игрока (видит все регистрации игрока, включая чужие клубы). Полная фильтрация потребовала бы разделять "может открыть страницу" и "какие регистрации на ней видны" — за пределами того, что тестируют критерии приёмки issue (там речь о игроках, а не о списке регистраций конкретного игрока).
- Public-facing debug-эндпоинт `KingOfCourtApiController.debugCheckPlayers` — не получает `CLUB_ADMIN` в `@PreAuthorize` (нет практической потребности, дебаг-инструмент).
- Публичная часть сайта (`torneos.html`, `tournament-details.html`, King of Court зрительская страница и т.п.) — без изменений (issue, п.6).
- `test/tournaments/**` (JDBC-based внутренний тестовый инструмент) — остаётся `SUPER_ADMIN`-only, не затрагивается.

## Изменения в системе

### API
Изменённое поведение существующих эндпоинтов (403 вместо 200 при доступе к чужому клубу/турниру), новых URL нет. Роли в `@PreAuthorize` расширяются на `CLUB_ADMIN` (без изменения списка для остальных ролей) в: `KingOfCourtApiController` (все мутирующие), `AmericanoViewController`/`AmericanoApiController` (все мутирующие), `TeamAmericanoViewController`, `TeamPlayoffViewController`. Новый `@RequestParam(required=false) Long clubId` на `GET /admin/tournaments`.

### БД
Нет новой миграции — модель данных (`Owner.clubId`, `OwnerRole.CLUB_ADMIN`) уже есть с LFPT-375.

### UI
- `admin/tournaments/list.html` — новый `<select clubId>` фильтр, виден только `isSuperAdmin`.
- `admin/tournaments/form.html` — дропдаун клуба сужен до одного варианта для `CLUB_ADMIN` (см. Требования п.8).
- Остальные шаблоны не меняются (изменения — в контроллерах/сервисах, не в разметке).

## Критерии приёмки
- [ ] `CLUB_ADMIN` логинится и попадает на `/admin` (не `/players/dashboard`)
- [ ] `GET /admin/tournaments` для `CLUB_ADMIN` возвращает только турниры его клуба
- [ ] `GET /admin/tournaments/{id}` чужого клуба клубным аккаунтом → не 200 с чужими данными (редирект на публичную страницу турнира через `CustomAccessDeniedHandler`, существующий контракт для authenticated non-owner)
- [ ] `POST /admin/tournaments/{id}/edit`, `/status`, `/delete`, `/deactivate`, `/move-to-waitlist/{playerId}`, `/move-to-main/{playerId}` для чужого турнира клубным аккаунтом → 403/redirect, не выполняется
- [ ] `GET`/`POST /admin/tournaments/{id}/payments`, `/payments/save`, `/payments/add-team` для чужого турнира → отказ (сейчас нет проверки вообще ни для одной роли — регресс-тест: свой турнир по-прежнему работает для `ORGANIZER`)
- [ ] Матчи/раунды/результаты для чужого турнира клубным аккаунтом отклоняются как минимум по одному эндпоинту каждого формата: bracket (`AdminMatchController`), King of Court (`KingOfCourtApiController.saveMatchResult`/`nextRound`), Americano (`AmericanoApiController.submitMatchResult`), Team Americano (`TeamAmericanoViewController` `/api/matches/{matchId}/result`), Team Playoff (`TeamPlayoffViewController` `/api/qual-matches/{matchId}/result`)
- [ ] Своя роль (тот же клуб) — все вышеперечисленные действия по-прежнему проходят успешно (регресс)
- [ ] `SUPER_ADMIN`/`ADMIN`/`ORGANIZER`-владелец — поведение не изменилось (регресс: логин, `/admin/tournaments` полный список, редактирование своих турниров)
- [ ] `GET /admin/players` для `CLUB_ADMIN` — только игроки с регистрацией на турнир своего клуба
- [ ] `GET /admin/players/{id}` для игрока без регистраций в клубе `CLUB_ADMIN` → отказ
- [ ] `GET /admin/clubs` для `CLUB_ADMIN` — только свой клуб; `GET /admin/clubs/{id}` чужого клуба → отказ
- [ ] `SUPER_ADMIN` видит фильтр по клубу в `/admin/tournaments`, `CLUB_ADMIN` фильтр не видит
- [ ] `POST /admin/tournaments` (создание) клубным админом — созданный турнир получает `clubId` клубного админа независимо от того, что было в форме
- [ ] Тесты на изоляцию — минимум по одному интеграционному тесту на "прямой доступ по ID к чужому турниру запрещён" для: турнира (edit), регистрации (waitlist move), оплаты, матча каждого формата (bracket, KoC, Americano, Team Americano, Team Playoff)

## Edge cases
- `CLUB_ADMIN` с `clubId = NULL` (некорректно созданный аккаунт, допустимо моделью данных по LFPT-375) — не проходит ни одну проверку (сравнение с `null` всегда `false`), эффективно не видит и не может администрировать ни один турнир; не является отдельным крашем/500, просто пустой список + 403 на прямые ID.
- Турнир с `clubId = NULL` (создан до LFPT-375/до появления клубных аккаунтов, либо не привязан к клубу намеренно) — `CLUB_ADMIN` не получает к нему доступ (симметрично предыдущему пункту); `SUPER_ADMIN`/`ADMIN`/владелец-`ORGANIZER` по `ownerId` — без изменений.
- "Игрок клуба" для фильтрации `/admin/players` определён как "есть хотя бы одна регистрация (любого статуса) на турнир этого клуба" — если игрок отменил единственную регистрацию (`CANCELLED`), но запись `TournamentRegistration` осталась в БД, он всё ещё считается "игроком клуба" (не по факту участия, а по факту хоть раз попытки зарегистрироваться) — сознательный выбор в пользу более широкого доступа клубного админа к истории, а не более узкого; технический выбор, не бизнес-вопрос (issue не уточняет).
- Игрок с регистрациями в нескольких клубах — виден в списках `/admin/players` обоих клубных админов одновременно; на странице деталей видны ВСЕ его регистрации, включая чужого клуба (см. "Вне скоупа").
- `AdminController.createTournament`/`newTournamentForm` для `CLUB_ADMIN` без `clubId` (`NULL`) — форма получит пустой дропдаун клуба; создание турнира с `clubId = NULL` из-под `CLUB_ADMIN` технически возможно (не добавляем отдельную валидацию, не запрошено issue) — созданный турнир будет не виден самому же создателю в списке (симметрично первому edge case), что заметно сразу и укажет на некорректно настроенный аккаунт.

## Открытые вопросы
Нет открытых вопросов, требующих решения пользователя. Технические неоднозначности (семантика "игрок клуба" для фильтрации, объём проверки на дашборде и на странице деталей игрока, ограничение форм создания турнира/клуба, выбор `AccessDeniedException` вместо действующего в части мест `SecurityException`) решены самостоятельно и задокументированы выше (Требования/Вне скоупа/Edge cases) — все они технические (как реализовать изоляцию, которую issue явно требует), не бизнес-решения (не меняют то, кто и что видит по сути, только как это выражено в коде/API).
