# LFPT-0484: Feature чекбокс «Показывать уровень участников» в админке + колонка «Уровень» в публичном списке

## Статус
done (PR #487)

## Источник
GitHub issue [#484](https://github.com/UstinKO/padel-core-service/issues/484), написанный напрямую архитектором (`[отправитель: architect]`, тема «AI Разработка»). Исходное сообщение без клиентского запроса — задача технически однозначна, `specs/requests/` не создавался.

Telegram-сообщение (для reply): 4829

## Контекст / зачем

Бэкенд-часть ([#483](https://github.com/UstinKO/padel-core-service/issues/483), PR [#485](https://github.com/UstinKO/padel-core-service/pull/485), смержен) уже добавила `Tournament.mostrarNivel` (флаг, по умолчанию `false`) и прокинула уровень игрока (`nivelJugador`, может быть `null`) во все DTO, которые публичные view-контроллеры передают в шаблоны всех форматов турнира. Эта задача — чисто UI: чекбокс в форме админки, управляющий флагом, и отображение уровня участников в публичных списках, когда флаг включён. Функция задумана как обратимый эксперимент (включили — посмотрели — выключили, без переделки кода).

## Связь с MASTER.md

Не меняет инвариантов `specs/MASTER.md`. Чисто аддитивное отображение существующего поля профиля игрока (`nivelJugador`) в существующих списках участников — раздел «Форматы турниров» не требует правки.

## Требования

### Функциональные

1. **Чекбокс в форме турнира** (`admin/tournaments/form.html`, `AdminController.createTournament`/`updateTournament`): `th:field="*{mostrarNivel}"` на `TournamentDto` — поле уже есть и уже сохраняется бэкендом (`TournamentService.createTournament`/`updateTournamentFields`, #483), контроллеры не меняются. Чекбокс снят по умолчанию (`TournamentDto.mostrarNivel` для нового турнира — `null`/`false`).
2. **Колонка «Уровень» в публичных списках участников**, видимая только при `tournament.mostrarNivel == true`, во всех пяти шаблонах из issue:
   - `tournament-details.html` — индивидуальная таблица (`confirmedRegistrations`): новая колонка с `reg.playerNivel`.
   - `tournament-details.html` — три таблицы DOBLES (Team Playoff / обычный DOBLES / `SOLO_ADD_LATER`): одна колонка «Уровень» вместо двух отдельных — значение в формате `playerNivel / partnerNivel` (симметрично уже существующей паре колонок «Jugador 1» / «Jugador 2» в одной строке).
   - `king-of-court-view.html` — обе таблицы рейтинга (финальная и live, включая JS-рендер `king-of-court-viewer.js` и инлайн-скрипт финальной таблицы) — колонка после «Jugador» с `playerNivel`.
   - `tournaments/americano/view.html` — таблица рейтинга — колонка после «Jugador» с `player.playerNivel`.
   - `tournaments/team-americano/view.html` — таблица рейтинга пар — колонка после «Pareja» в формате `player1Nivel / player2Nivel`.
   - `tournaments/team-playoff/view.html` — таблица рейтинга квалификации — колонка после «Pareja» в формате `player1Nivel / player2Nivel`.
3. **Отображение уровня**: `Nivel.getDisplay()` (= `name()`, например `C6`, `D7`). Если значение `null` (бэкенд уже приводит `SIN_ESPECIFICAR` к `null`, #483) — показывать `—` (буквальный дефис, без i18n-ключа: символ одинаков во всех локалях).
4. **King of Court — передача флага в шаблон/JS**: `KingOfCourtViewController` сейчас не передаёт `tournament`/`mostrarNivel` в модель вообще (ни по одному из двух GET-маршрутов) — добавить `model.addAttribute("mostrarNivel", king.getTournament().getMostrarNivel())` в оба метода и прокинуть в `window.tournamentData.mostrarNivel` (инлайн-скрипт в `king-of-court-view.html`), так как там же рендерится заголовок/колонки обеих таблиц рейтинга и JS читает тот же флаг для живой таблицы.

### Нефункциональные
- i18n: новые строки — подпись чекбокса в форме (`admin.tournaments.form.field.mostrar_nivel`) и заголовки колонок (`tournament.table.level`, `koc.table.level`, `americano.table.level`, `team_americano.table.level`, `team_playoff.table.level`) — во все три файла (`messages_es/ru/en.properties`). Заголовки таблиц King of Court (включая live-таблицу) рендерятся через Thymeleaf в `king-of-court-view.html`, а не в `king-of-court-viewer.js` (JS только дописывает значения ячеек `<td>`) — отдельный JS i18n-ключ не требуется. `—` не локализуется (см. п.3 выше).
- Безопасность: новых точек входа данных от пользователя нет (флаг уже принимается существующей формой/эндпоинтами, см. #483).
- Производительность: не требуется (данные уровня уже в существующих DTO, без доп. запросов).

## Вне скоупа
- Любые изменения на бэкенде (поле, миграция, DTO/мапперы) — сделаны в #483/PR #485.
- `test/tournaments.html` (внутренняя тестовая страница) — не упомянута в issue, не трогаем.
- Изменение самого значения `nivelJugador` в профиле игрока — вне скоупа.
- Отдельная колонка для каждого игрока пары (а не объединённая `playerNivel / partnerNivel`) — технический выбор реализации (см. "Технические решения" ниже), не переделывать без отдельного запроса.

## Изменения в системе

### API
Нет новых/изменённых REST endpoints. `KingOfCourtViewController` — новый `model.addAttribute("mostrarNivel", ...)` на двух существующих `GET`-маршрутах (не API, server-rendered view).

### БД
Нет (миграция уже в #483).

### UI
- `src/main/resources/templates/admin/tournaments/form.html` — чекбокс.
- `src/main/resources/templates/tournament-details.html` — колонка «Уровень» в 4 таблицах (1 individual + 3 dobles-варианта).
- `src/main/resources/templates/king-of-court-view.html` + `src/main/resources/static/js/king-of-court-viewer.js` — колонка в обеих таблицах рейтинга (статичной финальной и live).
- `src/main/resources/templates/tournaments/americano/view.html` — колонка в таблице рейтинга.
- `src/main/resources/templates/tournaments/team-americano/view.html` — колонка в таблице рейтинга пар.
- `src/main/resources/templates/tournaments/team-playoff/view.html` — колонка в таблице квалификации.
- `src/main/java/com/padle/core/padelcoreservice/controller/view/KingOfCourtViewController.java` — передача флага в модель.
- `src/main/resources/i18n/messages_{es,ru,en}.properties` — новые ключи.

## Технические решения (на усмотрение реализующего, см. `GIT_WORKFLOW.md` §3)

- **Объединённая колонка для пар** (`playerNivel / partnerNivel`) вместо двух отдельных колонок на каждого игрока пары: issue говорит об одной колонке «Уровень», а не о дублировании существующих колонок «Jugador 1»/«Jugador 2». Сохраняет текущую структуру таблиц (не нужно менять colspan/разметку имён), единообразно с уже принятым в проекте паттерном «одна строка — одна пара».
- **`—` без i18n-ключа** — это типографский символ, а не переводимый текст; обычная практика в проекте (см. `tournament.partner.none`-подобные случаи, где текст описательный и локализован, а не голый символ).

## Критерии приёмки
- [ ] На странице создания турнира чекбокс «Показывать уровень участников в списке» снят по умолчанию.
- [ ] На странице редактирования существующего турнира с `mostrarNivel = true` чекбокс отображается отмеченным; снятие и сохранение формы переводит флаг в `false` (сквозная проверка через уже работающий бэкенд #483).
- [ ] У турнира с `mostrarNivel = true` и подтверждённым участником с заполненным уровнем в профиле — на `tournament-details.html` (individual-модальность) видна колонка «Уровень»/«Nivel»/«Level» со значением уровня.
- [ ] То же для DOBLES-модальности `tournament-details.html` (во всех трёх вариантов блока: team-playoff-формат, обычный DOBLES, `SOLO_ADD_LATER`) — колонка показывает `playerNivel / partnerNivel`.
- [ ] King of Court (`king-of-court-view.html`, обе таблицы — финальная и live) — колонка «Уровень» видна при `mostrarNivel = true`, значение приходит из `PlayerStatsDTO.playerNivel`.
- [ ] Americano individual (`tournaments/americano/view.html`) — колонка «Уровень» видна при `mostrarNivel = true`.
- [ ] Team Americano (`tournaments/team-americano/view.html`) — колонка «Уровень» (`player1Nivel / player2Nivel`) видна при `mostrarNivel = true`.
- [ ] Team Playoff (`tournaments/team-playoff/view.html`, таблица квалификации) — колонка «Уровень» (`player1Nivel / player2Nivel`) видна при `mostrarNivel = true`.
- [ ] При `mostrarNivel = false` (в т.ч. у существующих турниров, созданных до фичи) — колонка «Уровень» не отображается ни в одном из шести мест выше — проверено на существующем турнире без явного включения флага.
- [ ] Игрок/партнёр без указанного уровня в профиле (`nivelJugador = SIN_ESPECIFICAR` → бэкенд отдаёт `null`) — в колонке `—`.
- [ ] Подписи чекбокса и всех заголовков колонок локализованы на `es`/`ru`/`en`.

## Edge cases
- Турнир создан до фичи (после миграции `mostrarNivel = false` по умолчанию) — колонка не показывается без явного включения, уже проверено критерием выше.
- King of Court: `mostrarNivel` должен быть виден и в финальной (статичной) таблице, и в live-таблице, обновляемой через WebSocket/JS, — обе читают один и тот же флаг (`window.tournamentData.mostrarNivel`), один источник на странице.
- Пара без подтверждённого партнёра (`PARTNER_INVITED`/одиночный `SOLO_ADD_LATER`) — `partnerNivel` естественно `null` → `—` в объединённой колонке, без NPE.
- Гостевой игрок (Team Americano/Playoff `player2` без профиля) — `player2Nivel` уже `null` на бэкенде (#483) → `—`.

## Открытые вопросы
Нет. Технические решения по форме отображения колонки для пар (см. раздел выше) — на усмотрение реализующего, не бизнес-вопрос.
