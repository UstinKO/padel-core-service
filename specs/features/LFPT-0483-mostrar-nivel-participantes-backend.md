# LFPT-0483: Feature настройка «Показывать уровень участников» — флаг на турнире + данные для публичного списка

## Статус
approved

## Источник
GitHub issue [#483](https://github.com/UstinKO/padel-core-service/issues/483), написанный напрямую архитектором по задаче из Telegram (`[отправитель: architect]`, тема «AI Разработка»). Исходное сообщение без клиентского запроса — задача технически однозначна, `specs/requests/` не создавался.

Telegram-сообщение (для reply): 4821

## Контекст / зачем

Администратор хочет опционально показывать уровень игрока (`PlayerPadel.nivelJugador`, например C6/C7/D6/D7) рядом с каждым участником в публичном списке зарегистрированных игроков турнира — участники заранее видят уровень соперников/партнёров. Функция задумана как обратимый эксперимент (включить для одного турнира, посмотреть — выключить без удаления кода), поэтому реализуется через флаг конкретного турнира, а не глобальную настройку.

Эта задача — **бэкенд-часть**: поле-флаг на турнире, миграция, и данные (флаг + уровень каждого участника) в моделях, которые публичные контроллеры уже передают в Thymeleaf-шаблоны. UI (чекбокс в админке, колонка в публичных списках) — зависимая задача [#484](https://github.com/UstinKO/padel-core-service/issues/484).

## Связь с MASTER.md

Не меняет инвариантов `specs/MASTER.md`. Не вводит новую роль, новый статус регистрации и не затрагивает флоу листа ожидания/парной регистрации — чисто аддитивный флаг отображения, существующий раздел «Форматы турниров» не требует правки (флаг работает одинаково для всех четырёх форматов, уже перечисленных там).

## Требования

### Функциональные

1. **Новое поле `Tournament.mostrarNivel`** (`Boolean`, `nullable=false`, `default false`) — по аналогии с существующими булевыми флагами турнира (`isActive`). Контролирует, нужно ли передавать/показывать уровень участников для конкретного турнира.
2. **DTO и мапперы**: добавить `mostrarNivel` в `TournamentDto` — поле с тем же именем автоматически подхватывается MapStruct (`TournamentMapper`, `unmappedTargetPolicy = IGNORE`, явных `@Mapping` для одноимённых полей не требуется).
3. **Создание/редактирование турнира**:
   - `AdminController.createTournament` (`POST /admin/tournaments`) — `tournamentMapper.toEntity(tournamentDto)` в `TournamentService.createTournament` уже прокидывает одноимённые поля из DTO автоматически; явной правки `createTournament` не требуется, если `TournamentDto.mostrarNivel` заполнен из формы (это делает [#484] через `th:field`). Для этой (бэкенд) задачи — просто не уронить значение, если оно `null` (трактовать как `false`).
   - `AdminController.updateTournament` (`POST /admin/tournaments/{id}/edit`) → `TournamentService.updateTournamentFields(Tournament existing, TournamentDto dto)` — метод собирает поля вручную (не через маппер), добавить туда `existing.setMostrarNivel(dto.getMostrarNivel() != null ? dto.getMostrarNivel() : false)`.
   - Соответствующий REST `POST /api/tournaments` / `PUT /api/tournaments/{id}` (`TournamentController`, также через `TournamentService`) — флаг проходит тем же путём, отдельной правки контроллера не требуется, если он тоже использует `TournamentDto`/`updateTournamentFields` — проверить при реализации, что это так.
4. **Данные для публичных страниц**: уровень игрока (`PlayerPadel.nivelJugador`, тип `Nivel`, может быть `null`) должен быть доступен в моделях, которые публичные view-контроллеры передают в шаблон, для каждого формата:
   - **Bracket / Cancha Abierta** (`TournamentViewController` → `tournament-details.html`): `TournamentRegistrationDto` (используется и для `registrations`, и для `confirmedRegistrations`/`activeDoubleRegistrations`/`confirmedPairsRegistrations`) — добавить `playerNivel` (из `player.nivelJugador`) и `partnerNivel` (из `partner.nivelJugador`, по аналогии с уже существующими `partnerNombre`/`partnerApellido` — см. `TournamentService.getRegistrationsByTournament`, блок дозаполнения партнёра) в `TournamentRegistrationMapper`/вручную в сервисе.
   - **King of Court** (`KingOfCourtViewController` → `king-of-court-view.html`, через `KingOfCourtStateDTO.ranking` — `List<PlayerStatsDTO>`): добавить `playerNivel` в `PlayerStatsDTO` и его сборку в `KingOfCourtService`.
   - **Americano** (`AmericanoViewController` → `tournaments/americano/view.html`, через `AmericanoPlayerDto`): добавить `playerNivel` в `AmericanoPlayerDto` и его маппинг/сборку в `AmericanoService`.
   - **Team Americano / Team Playoff** (`TeamAmericanoViewController`, `TeamPlayoffViewController` → соответствующие `view.html`, через team/pair DTO на базе `AmericanoTeam`): добавить уровень(и) игрока(ов) команды в соответствующий DTO.
   - Если `nivelJugador` игрока не задан в профиле — поле в DTO остаётся `null` (никакого дефолтного текста на бэкенде, `—` рисует фронтенд, задача [#484]).
5. **Флаг турнира в шаблонах**: `tournament` (`TournamentDto`, с полем `mostrarNivel`) уже передаётся моделью во все соответствующие публичные шаблоны (bracket/Cancha Abierta, King of Court, Americano, Team Americano, Team Playoff — везде есть `model.addAttribute("tournament", ...)` в соответствующих view-контроллерах) — отдельно прокидывать флаг не нужно, фронтенд-задача читает его напрямую из `tournament.mostrarNivel`.

### Нефункциональные
- i18n: новых пользовательских строк в рамках этой (backend) задачи нет — подписи чекбокса/колонки будут в [#484].
- Безопасность: новых точек входа данных от пользователя, кроме уже существующей формы создания/редактирования турнира (защищена `tournamentAccessService`/`@PreAuthorize`, без изменений), нет.
- Производительность: данные уровня игрока уже загружаются вместе с `PlayerPadel` в существующих JPA-запросах каждого формата (поле на той же сущности, не отдельная таблица/join) — дополнительных запросов к БД не требуется.

## Вне скоупа
- Чекбокс в форме админки и колонка «Уровень»/«Nivel»/«Level» в публичных шаблонах — [#484] (frontend).
- Глобальная (не per-турнир) настройка отображения уровня — не запрошено, флаг всегда уровня турнира.
- Любое изменение самого поля `nivelJugador` в профиле игрока (редактирование, валидация) — вне скоупа, эта задача только читает существующее значение.

## Изменения в системе

### API
Существующие `POST /admin/tournaments`, `POST /admin/tournaments/{id}/edit`, `POST /api/tournaments`, `PUT /api/tournaments/{id}` — без изменения сигнатуры, дополнительно принимают/сохраняют поле `mostrarNivel` через существующий `TournamentDto`. Новых endpoint'ов нет.

### БД
- `v1.52-add-mostrar-nivel-to-tournament.yaml`: `tournaments_db.mostrar_nivel BOOLEAN NOT NULL DEFAULT FALSE`.
- Регистрируется в `changelog-master.yaml` следующим по номеру после `v1.51-add-late-arrival-to-registration.yaml`.

### UI
Нет (эта задача — backend-only; UI-часть — [#484]).

## Критерии приёмки
- [ ] У `Tournament` есть поле `mostrarNivel` (`Boolean`, `NOT NULL DEFAULT FALSE`); миграция `v1.52-add-mostrar-nivel-to-tournament.yaml` зарегистрирована в `changelog-master.yaml` и применяется на чистой БД.
- [ ] Создание турнира (`TournamentService.createTournament`) с `TournamentDto.mostrarNivel = true` — сохранённый `Tournament.mostrarNivel == true`; без явного значения в DTO (`null`) — сохраняется `false`, не `null` и не исключение.
- [ ] Редактирование существующего турнира (`TournamentService.updateTournament`) — изменение `mostrarNivel` с `false` на `true` и обратно сохраняется и читается при повторном `GET`.
- [ ] Флаг сохраняется и читается одинаково для любого `TournamentType` (bracket, `KING_OF_COURT`, `AMERICANO`, `AMERICANO_TEAMS`, `CANCHA_ABIERTA`) — не только Cancha Abierta.
- [ ] Для турнира с `mostrarNivel = true` и `CONFIRMED`-игроком с заполненным `nivelJugador` — модель, которую `TournamentViewController`/`KingOfCourtViewController`/`AmericanoViewController`/`TeamAmericanoViewController` передают в соответствующий шаблон, содержит значение уровня этого игрока (не `null`) в DTO списка участников своего формата.
- [ ] Для `CONFIRMED`-игрока без заполненного `nivelJugador` в профиле — соответствующее поле уровня в DTO — `null` (не пустая строка, не исключение).
- [ ] Для парной регистрации (bracket/Cancha Abierta, `TournamentRegistrationDto`) — уровень партнёра (`partnerNivel`) заполняется наравне с `playerNivel`, по той же логике дозаполнения, что и `partnerNombre`/`partnerApellido`.
- [ ] `./mvnw verify` зелёный.

## Edge cases
- Турнир создан до этой фичи (миграция применена к существующим строкам) — `mostrarNivel` для всех существующих турниров становится `false` (дефолт колонки), публичное поведение для них не меняется до явного включения в [#484].
- Игрок с `nivelJugador = null` (не указал уровень при регистрации/в профиле) — поле уровня в DTO участника — `null`, без NPE при сборке списка (там, где уровень читается напрямую с `PlayerPadel`, а не через необязательный маппинг).
- Парная регистрация, где у `TournamentRegistration.partner` ещё нет подтверждённого партнёра (`PAIR_REGISTERED`/`PARTNER_INVITED`, партнёр пока `null`) — `partnerNivel` остаётся `null`, как и остальные `partner*`-поля в этом состоянии (существующее поведение `partnerNombre` и т.п.).
- Два реплики в Docker Swarm: поле на уже существующей сущности `Tournament`, читается/пишется в рамках уже существующих `@Transactional`-методов `createTournament`/`updateTournament` — дополнительного риска конкурентного доступа не создаёт сверх уже принятого для остальных полей турнира.

## Открытые вопросы
Нет. Название поля (`mostrarNivel`) и формат миграции (`v1.52-add-mostrar-nivel-to-tournament.yaml`) — техническое решение архитектора по аналогии с существующими булевыми флагами турнира (`isActive`) и последней зарегистрированной миграцией (`v1.51`), не бизнес-вопрос.
