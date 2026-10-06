# LFPT-0491: Feature закрытые мероприятия — поле видимости турнира и исключение из публичных списков (backend)

## Статус
approved

## Источник
Клиентский запрос [specs/requests/LFPT-0491-closed-events-link-only.md](../requests/LFPT-0491-closed-events-link-only.md) (Telegram, пересланное сообщение от Evgeny_754, `telegram_launch_message_id: 4850`). GitHub issue [#491](https://github.com/UstinKO/padel-core-service/issues/491).

Разбито на бэкенд/фронтенд (`GIT_WORKFLOW.md` §1.4) — эта спека покрывает бэкенд. Зависимая фронтенд-задача — [#492](https://github.com/UstinKO/padel-core-service/issues/492) (переключатель в админке, копирование ссылки).

## Контекст / зачем
Организаторам нужны закрытые мероприятия — турниры, которые не анонсируются публично (не попадают в общий список/расписание/поиск), но доступны и полностью функциональны по прямой ссылке для тех, кому администратор её отправил. Эта задача добавляет турниру настройку видимости и исключает турниры «только по ссылке» из всех публичных списочных точек входа, не трогая прямой доступ по ID.

## Связь с MASTER.md
Не меняет инвариантов `specs/MASTER.md` — не вводит новую роль, новый статус регистрации, не затрагивает флоу листа ожидания/парной регистрации. Регистрация, оплата, форматы турнира работают одинаково для обоих режимов видимости. Раздел «Форматы турниров» не требует правки.

## Требования

### Функциональные

1. **Новый enum `TournamentVisibility`** (`model/enums/`) с двумя значениями: `PUBLICO` (по умолчанию, текущее поведение), `SOLO_POR_ENLACE` (не показывается в публичных списках, доступен только по прямой ссылке). По аналогии со стилем существующих enum'ов турнира (`TournamentStatus`, `Modalidad`) — испаноязычные константы. Третий режим «По приглашению» (из клиентского запроса) **не реализуется** в этой задаче — выбор enum (а не boolean) сделан именно для того, чтобы его можно было добавить позже без миграции данных.
2. **Новое поле `Tournament.visibilidad`** (`TournamentVisibility`, `@Enumerated(STRING)`, `nullable=false`, default `PUBLICO`) — по аналогии с `estado`/`modalidad`.
3. **DTO**: добавить `visibilidad` в `TournamentDto` — поле с тем же именем автоматически подхватывается MapStruct (`TournamentMapper`, `unmappedTargetPolicy = IGNORE`), явных `@Mapping` не требуется.
4. **Создание/редактирование турнира**:
   - `TournamentService.createTournament` — `tournament.setVisibilidad(tournamentDto.getVisibilidad() != null ? tournamentDto.getVisibilidad() : TournamentVisibility.PUBLICO)` (аналогично существующей обработке `mostrarNivel`, null → дефолт, а не исключение).
   - `TournamentService.updateTournamentFields(Tournament existing, TournamentDto dto)` — `existing.setVisibilidad(dto.getVisibilidad() != null ? dto.getVisibilidad() : TournamentVisibility.PUBLICO)`.
   - UI-переключатель, который заполняет `TournamentDto.visibilidad` из формы — задача [#492]; на этом (бэкенд) шаге поле просто не должно теряться/падать, если форма его ещё не присылает.
5. **Исключение из публичных списков.** В `TournamentService` есть ровно два метода, которые уже строят публично доступные (без авторизации владельца) списки турниров **и при этом переиспользуются админ-кодом** — им нельзя фильтровать по видимости напрямую, иначе админ/суперадмин потеряют видимость своих закрытых турниров в собственной панели:
   - `getAllTournaments()` — используется и публичным `GET /api/tournaments` (`TournamentController`, без `@PreAuthorize`), и админкой (`AdminController`, суперадмин-ветка списка турниров; `getTournamentsForOwner` для суперадмина), и `TestTournamentController` (внутренний тест-инструмент). **Не менять.** Добавить новый метод `getAllPublicTournaments()`, который берёт результат `getAllTournaments()` и отфильтровывает по `visibilidad == PUBLICO`; `TournamentController.getAllTournaments()` (`GET /api/tournaments`) переключить на вызов `getAllPublicTournaments()`.
   - `getTournamentsByClub(Long clubId)` — используется и публичным `GET /api/tournaments/club/{clubId}`, и `AdminController` (клубный список для `OWNER`/`CLUB_ADMIN`). **Не менять.** Добавить `getPublicTournamentsByClub(Long clubId)` по той же схеме (фильтр по `visibilidad == PUBLICO` над результатом `getTournamentsByClub`); `TournamentController.getTournamentsByClub()` переключить на новый метод.

   Остальные публичные списочные методы `TournamentService` используются **только** своим единственным публичным вызывающим кодом — их можно отфильтровать прямо на месте (без новых методов-обёрток):
   - `getUpcomingTournaments()` → только `GET /api/tournaments/upcoming`.
   - `getTournamentsByStatus(TournamentStatus)` → только `GET /api/tournaments/status/{status}`.
   - `searchTournaments(...)` → только `GET /api/tournaments/search`.
   - `getAllActiveTournaments()` → только `TorneosController` (`/torneos`).
   - `getActiveTournamentsForHome()` → только `HomeController` (`/`).
   - `getVisibleTournamentsForPlayer()` → только `PlayerDashboardController` (виджет «открытые для регистрации» на дашборде игрока).

   Фильтрацию во всех случаях выполнять одним переиспользуемым приватным хелпером в `TournamentService` (например `filterPublicOnly(List<TournamentDto>)` / предикат по `dto.getVisibilidad() == TournamentVisibility.PUBLICO`), не дублировать условие в каждом методе.
6. **Прямой доступ по ссылке — без изменений.** `getTournamentDtoById(Long)`, `getTournamentById(Long)`, `getActiveTournamentById(Long)` (используются `/torneo/{id}`, `/api/tournaments/{id}` и всеми view-контроллерами форматов — `AmericanoViewController`, `TeamAmericanoViewController`, `TeamPlayoffViewController`, `KingOfCourtViewController`) **не фильтруются по видимости** — турнир «только по ссылке» должен открываться и быть полностью функциональным по прямому URL ровно как публичный. Эти методы и так не делают такой фильтрации — явно убедиться при реализации, что фильтр не просочился туда по ошибке.
7. **Регистрация/отмена/прочий функционал турнира** (`registerPlayer`, `cancelRegistration`, все API форматов) работают по `tournamentId`, видимость не проверяют — без изменений.
8. **Админ-панель** (`AdminController`, `getTournamentsForOwner`, `getRecentTournaments`) — без изменений, видит оба режима видимости (администратору нужно видеть и управлять своими закрытыми турнирами).

### Нефункциональные
- i18n: новых пользовательских строк в рамках этой (backend) задачи нет — подписи переключателя/бейджа — задача [#492].
- Безопасность: новых точек входа данных от пользователя, кроме уже существующей формы создания/редактирования турнира (защищена `tournamentAccessService`/`@PreAuthorize`), нет. Видимость «только по ссылке» — это сокрытие из списков/навигации (unlisted), не контроль доступа токеном — сам турнир открывается по тому же предсказуемому числовому `id`, как и публичный; это соответствует буквальной формулировке клиентского запроса («не показывается в списках», «не находится через навигацию») и не требует новой токенизации ссылки. Если потребуется защита от подбора ID — это отдельная, более серьёзная задача (не входит в текущий скоуп, можно зафиксировать на будущее).
- Производительность: фильтрация — `stream().filter(...)` над уже построенным списком DTO (аналогично существующей фильтрации в `getVisibleTournamentsForPlayer`), без дополнительных запросов к БД.

## Вне скоупа
- UI-переключатель в форме создания/редактирования, бейдж текущего режима и кнопка копирования ссылки в админке — [#492].
- Третий режим видимости «По приглашению» (доступ только конкретным пользователям) — явно за скоупом клиентского запроса, только задел на будущее через enum.
- Токенизация/не-угадываемая ссылка вместо текущего числового `id` — не запрошено, см. примечание по безопасности выше.
- Любые изменения в логике регистрации, статусов, оплат — не затрагиваются.

## Изменения в системе

### API
- `GET /api/tournaments` (`TournamentController.getAllTournaments`) — тело ответа теперь исключает турниры `SOLO_POR_ENLACE` (вызывает новый `TournamentService.getAllPublicTournaments()` вместо `getAllTournaments()`). Сигнатура эндпоинта не меняется.
- `GET /api/tournaments/club/{clubId}` — аналогично, через новый `getPublicTournamentsByClub(clubId)`.
- `GET /api/tournaments/upcoming`, `GET /api/tournaments/status/{status}`, `GET /api/tournaments/search` — та же фильтрация, без изменения сигнатуры эндпоинтов.
- `GET /api/tournaments/{id}`, `GET /torneo/{id}` и все эндпоинты форматов по `tournamentId` — без изменений (видимость не влияет).
- Новых REST-эндпоинтов нет.

### БД
- `v1.53-add-visibilidad-to-tournament.yaml`: `tournaments_db.visibilidad VARCHAR(30) NOT NULL DEFAULT 'PUBLICO'`.
- Регистрируется в `changelog-master.yaml` следующим по номеру после `v1.52-add-mostrar-nivel-to-tournament.yaml`.

### UI
Нет (эта задача — backend-only; UI — [#492]).

## Критерии приёмки
- [ ] У `Tournament` есть поле `visibilidad` (`TournamentVisibility`, `NOT NULL DEFAULT PUBLICO`); миграция `v1.53-add-visibilidad-to-tournament.yaml` зарегистрирована в `changelog-master.yaml` и применяется на чистой БД.
- [ ] Создание турнира с `TournamentDto.visibilidad = SOLO_POR_ENLACE` — сохранённый `Tournament.visibilidad == SOLO_POR_ENLACE`; без явного значения в DTO (`null`) — сохраняется `PUBLICO`, не `null` и не исключение.
- [ ] Редактирование существующего турнира — изменение `visibilidad` в любую сторону сохраняется и читается при повторном `GET`.
- [ ] Турнир с `visibilidad = SOLO_POR_ENLACE` **не** возвращается: `GET /api/tournaments`, `GET /api/tournaments/upcoming`, `GET /api/tournaments/club/{clubId}`, `GET /api/tournaments/status/{status}` (для статуса этого турнира), `GET /api/tournaments/search` (без фильтров и с фильтрами, под которые турнир подходит), публичной страницей `/torneos`, главной страницей `/`, виджетом открытых турниров на дашборде игрока (`/players/dashboard`).
- [ ] Турнир с `visibilidad = SOLO_POR_ENLACE` **по-прежнему** доступен и полностью функционален по прямой ссылке: `GET /torneo/{id}` отдаёт 200 и страницу турнира (не 404), `GET /api/tournaments/{id}` отдаёт турнир, регистрация/отмена регистрации по `tournamentId` работают как для публичного турнира того же формата и модальности.
- [ ] Турниры с `visibilidad = SOLO_POR_ENLACE` **продолжают** отображаться в админ-панели: `GET /api/tournaments/my` (владелец/суперадмин), `/admin/tournaments` (список), клубный список `AdminController` для `OWNER`/`CLUB_ADMIN` своего клуба.
- [ ] Существующие турниры (созданные до миграции) после применения миграции имеют `visibilidad = PUBLICO` и продолжают отображаться во всех публичных списках без изменений (нет регрессии).
- [ ] `./mvnw verify` зелёный.

## Edge cases
- Турнир создан до этой фичи (миграция применена к существующим строкам) — `visibilidad` для всех существующих турниров становится `PUBLICO` (дефолт колонки), публичное поведение для них не меняется.
- `visibilidad = null` на уровне DTO при создании/обновлении (форма ещё не прислала поле, например пока не выкатили [#492]) — трактуется как `PUBLICO`, без NPE/400.
- Турнир `SOLO_POR_ENLACE`, уже завершённый/отменённый (`estado = FINALIZADO`/`CANCELADO`) — видимость не взаимодействует со статусом турнира, обычные правила `estado`/`isActive` для таких турниров продолжают действовать как раньше (например, `getActiveTournamentById` всё равно фильтрует по `isActive`, это не меняется).
- Поиск (`/api/tournaments/search`) с явным `estado`/`tipo`/`nivel`, под которые подходит `SOLO_POR_ENLACE`-турнир — всё равно не возвращается, фильтр по видимости применяется независимо от остальных параметров поиска.
- Два реплики в Docker Swarm: поле на уже существующей сущности `Tournament`, читается/пишется в рамках уже существующих `@Transactional`-методов `createTournament`/`updateTournament` — дополнительного риска конкурентного доступа не создаёт сверх уже принятого для остальных полей турнира.

## Открытые вопросы
Нет. Два технических решения приняты архитектором самостоятельно (аналогично п.3 `GIT_WORKFLOW.md` — разумный выбор с коротким обоснованием, не бизнес-вопрос):
1. Видимость «только по ссылке» реализована как сокрытие из списков по предсказуемому `id`, без отдельной токенизированной ссылки — соответствует буквальной формулировке клиентского запроса, см. примечание по безопасности в разделе «Нефункциональные».
2. Название поля (`visibilidad`), enum (`TournamentVisibility`) и формат миграции (`v1.53-add-visibilidad-to-tournament.yaml`) — по аналогии с существующими полями турнира (`estado`) и последней зарегистрированной миграцией (`v1.52`).
