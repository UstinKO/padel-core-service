# LFPT-0628: Feature уровень участников в админке турнира — колонка «Уровень» в таблице регистраций

## Статус
done

## Источник
GitHub issue [#628](https://github.com/UstinKO/padel-core-service/issues/628). Клиентский запрос — `specs/requests/LFPT-0628-nivel-participantes-admin.md`.

Telegram-сообщение (для reply): 5218

## Контекст / зачем

LFPT-0483/LFPT-0484 добавили флаг турнира `Tournament.mostrarNivel` и колонку «Уровень» (`PlayerPadel.nivelJugador`, например `C6`/`D7`) на **публичных** страницах списка участников всех форматов. Данные для этого (`TournamentRegistrationDto.playerNivel`/`partnerNivel`) заполняются бэкендом независимо от значения `mostrarNivel` — флаг управляет только отображением на фронтенде.

Администратор/координатор турнира хочет видеть тот же уровень участников **в админ-панели**, на странице деталей турнира (`/admin/tournaments/{id}`, `admin/tournaments/details.html`), где уже выводится список регистраций (`AdminController.viewTournament` → `model.addAttribute("registrations", tournamentService.getRegistrationsByTournament(id))`) — без перехода в профиль каждого игрока.

Данные уже доступны в модели, которую контроллер передаёт шаблону: `tournament.mostrarNivel` (флаг) и `reg.playerNivel`/`reg.partnerNivel` для каждой записи `registrations` (оба поля заполняет `TournamentRegistrationMapper.toDto` и дозаполнение партнёра в `TournamentService.getRegistrationsByTournament` — без изменений, LFPT-0483). Это чисто фронтенд-задача: добавить колонку в существующие таблицы `admin/tournaments/details.html`, читая уже переданные данные, по тому же паттерну, что уже работает в `tournament-details.html` (публичная страница).

## Связь с MASTER.md

Не меняет инвариантов `specs/MASTER.md`. Чисто аддитивное отображение существующего поля профиля игрока (`nivelJugador`) в существующей таблице регистраций админки — раздел «Форматы турниров» не требует правки, флаг и данные уже существуют и не меняются.

## Требования

### Функциональные

1. **Колонка «Уровень» во всех таблицах регистраций** на `admin/tournaments/details.html` (секция `#reg-tables`), видимая только при `tournament.mostrarNivel == true`:
   - **Inscripciones (confirmedRegs)** — главная таблица подтверждённых участников:
     - модальность `DOBLES`: колонка после «Pareja»/«Contacto pareja», значение `playerNivel / partnerNivel` (тот же объединённый формат, что уже на `tournament-details.html`, LFPT-0484).
     - модальность не `DOBLES` (individual): колонка после «Posición»/«Contacto», значение `playerNivel`.
   - **Reserva (overflow сверх `cupoMax`)** — колонка после «Posición», значение `playerNivel` (таблица общая для всех модальностей, партнёра не показывает — по аналогии с уже существующими колонками этой таблицы).
   - **Invitación pendiente (`invitedRegs`, `PARTNER_INVITED`)** — колонка после «Invitando a», значение `playerNivel / partnerNivel`.
   - **Inscripciones sin pareja (`soloRegs`)** — колонка после «Escenario», значение `playerNivel`.
   - **Lista de Espera (`WAITLIST`)** — колонка после «Posición en espera», значение `playerNivel`.
2. **Источник значения**: `reg.playerNivel`/`reg.partnerNivel` (`Nivel`, может быть `null`) — те же поля `TournamentRegistrationDto`, что уже используются на публичной странице. Никаких новых DTO-полей, мапперов или контроллерных изменений не требуется — модель (`tournament`, `registrations`) уже содержит всё необходимое (подтверждено чтением `AdminController.viewTournament`, `TournamentService.getRegistrationsByTournament`, `TournamentRegistrationMapper`).
3. **Формат отображения**: `reg.playerNivel != null ? reg.playerNivel.display : '—'` (аналогично `tournament-details.html`); для объединённых ячеек — `(playerNivel или '—') + ' / ' + (partnerNivel или '—')`.
4. **Видимость**: заголовок и ячейка колонки — `th:if="${tournament.mostrarNivel}"` на каждой из пяти таблиц. При `mostrarNivel == false` (в т.ч. у турниров, созданных до LFPT-0483) колонка не отображается ни в одной таблице — текущая вёрстка/количество колонок не меняется.

### Нефункциональные
- i18n: новый ключ заголовка колонки — `admin.tournaments.details.col.level` — во все три файла (`messages_es/ru/en.properties`), аналогично уже существующему `tournament.table.level` публичной страницы (но отдельный ключ — namespace админки `admin.tournaments.details.col.*`, не переиспользовать публичный `tournament.table.level` — другой раздел i18n-ключей по уже принятой в проекте конвенции разделения `admin.*`/без префикса).
- Безопасность: новых точек входа данных от пользователя нет — только отображение уже существующих, уже загруженных и уже защищённых (`tournamentAccessService.assertCanManageTournament`) данных.
- Производительность: не требуется — данные уровня уже в существующих DTO без дополнительных запросов к БД (как и в LFPT-0483).

## Вне скоупа
- Любые изменения `Tournament.mostrarNivel`, `TournamentRegistrationDto`, `TournamentRegistrationMapper`, `TournamentService.getRegistrationsByTournament` — не требуются, данные уже на месте (LFPT-0483).
- Публичные страницы (`tournament-details.html` и форматы King of Court/Americano/Team Americano/Team Playoff) — уже сделаны в LFPT-0484, не трогаем.
- Чекбокс в форме турнира (`admin/tournaments/form.html`) — уже существует (LFPT-0484), не трогаем.
- Специализированные admin-страницы форматов (`admin/americano/tournament*.html`, `admin/tournaments/king-of-court.html`) — не упомянуты в запросе («список зарегистрированных участников / регистраций» — это таблицы `admin/tournaments/details.html`), не трогаем.
- Колонка «Уровень» для каждого игрока пары отдельно (вместо объединённой `playerNivel / partnerNivel`) — сохраняем уже принятый в LFPT-0484 паттерн объединённой колонки, не вводим новый.

## Изменения в системе

### API
Нет новых/изменённых endpoints.

### БД
Нет (поле и данные уже существуют с LFPT-0483).

### UI
- `src/main/resources/templates/admin/tournaments/details.html` — колонка «Уровень» в пяти таблицах секции `#reg-tables` (Inscripciones, Reserva, Invitación pendiente, Inscripciones sin pareja, Lista de Espera).
- `src/main/resources/i18n/messages_{es,ru,en}.properties` — новый ключ `admin.tournaments.details.col.level`.

## Критерии приёмки
- [ ] Турнир с `mostrarNivel = true`, модальность individual, подтверждённый участник с заполненным уровнем в профиле — таблица «Inscripciones» на `/admin/tournaments/{id}` показывает колонку «Уровень» со значением уровня игрока.
- [ ] Турнир с `mostrarNivel = true`, модальность `DOBLES`, подтверждённая пара — таблица «Inscripciones» показывает колонку «Уровень» в формате `playerNivel / partnerNivel`.
- [ ] Таблица «Reserva» (участники сверх `cupoMax`) — колонка «Уровень» видна и заполнена при `mostrarNivel = true`.
- [ ] Таблица «Invitación pendiente» (`PARTNER_INVITED`) — колонка «Уровень» видна в формате `playerNivel / partnerNivel` при `mostrarNivel = true`.
- [ ] Таблица «Inscripciones sin pareja» (solo-регистрации) — колонка «Уровень» видна и заполнена при `mostrarNivel = true`.
- [ ] Таблица «Lista de Espera» — колонка «Уровень» видна и заполнена при `mostrarNivel = true`.
- [ ] Турнир с `mostrarNivel = false` (в т.ч. существующий, созданный до фичи) — колонка «Уровень» не отображается ни в одной из пяти таблиц, остальная вёрстка не меняется.
- [ ] Игрок/партнёр без заполненного уровня в профиле (`nivelJugador = SIN_ESPECIFICAR` → бэкенд отдаёт `null`) — в колонке показывается «—».
- [ ] Заголовок колонки локализован на `es`/`ru`/`en` (новый ключ `admin.tournaments.details.col.level`).
- [ ] `./mvnw verify` зелёный; `python3 .github/bin/docs-drift.py` — 0 расхождений.

## Edge cases
- Турнир создан до LFPT-0483 (`mostrarNivel = false` по умолчанию после миграции) — колонка не показывается ни в одной таблице без явного включения флага в форме редактирования.
- Пара без подтверждённого партнёра (`PARTNER_INVITED` с партнёром ещё `null`, либо соло-регистрация) — `partnerNivel` естественно `null` → `—` в объединённой колонке, без NPE (данные уже защищены существующей логикой `TournamentRegistrationMapper`/дозаполнения партнёра).
- Reserva/Lista de Espera пустые (нет записей сверх лимита / нет листа ожидания) — таблицы и так скрыты существующими `th:if` (`confirmedRegs.size() > effectiveCupoMax`, `tournament.waitlistCount > 0`), новая колонка не меняет это поведение.

## Открытые вопросы
Нет. Выбор добавить колонку во все пять таблиц регистраций (а не только в главную «Inscripciones») — техническое решение по аналогии с полнотой покрытия LFPT-0484 на публичной странице и буквальным текстом запроса («список зарегистрированных участников / регистраций», множественное число — вся секция регистраций, а не одна таблица), не бизнес-вопрос.
