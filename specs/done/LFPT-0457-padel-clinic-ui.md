# LFPT-0457: Feature новый тип турнира «Тренировка + игра» в форме создания, на карточках и странице турнира — локализация ES/RU/EN

## Статус
done — PR #459, деплой подтверждён (run 37133181489)

## Источник
`specs/requests/LFPT-0456-padel-clinic-event-type.md` (клиентский запрос через Telegram, тема "AI Разработка"), issue [#457](https://github.com/UstinKO/padel-core-service/issues/457). Зависит от [#456](https://github.com/UstinKO/padel-core-service/issues/456) (`TournamentType.PADEL_CLINIC`, бэкенд).

Telegram-сообщение (для reply): 4724

## Контекст / зачем
Новый тип турнира `PADEL_CLINIC` (LFPT-0456) должен быть виден и выбираем во всех местах UI, где уже отображаются существующие 4 типа — иначе организатор не сможет создать мероприятие нового типа, а игроки не увидят его название на карточках/странице турнира. Название должно переключаться по языку сайта (es/ru/en), без перевода контентных полей (названия клубов и т.п.).

## Связь с MASTER.md
`specs/MASTER.md § Форматы турниров` — таблица форматов. Обновляется одним PR после того, как обе задачи (456+457) смержены и задеплоены (см. "Вне скоупа" в LFPT-0456) — не в этом PR.

## Требования

### Функциональные
Полный найденный на этапе анализа footprint отображения типа турнира в системе — новый тип добавляется во все те же места, что уже содержат `CANCHA_ABIERTA` (кроме `IndividualTournamentReminderScheduler`/`EmailService`, см. "Вне скоупа" LFPT-0456):

1. **Админка — форма создания/редактирования** (`admin/tournaments/form.html`):
   - `<option value="PADEL_CLINIC">` в `<select id="tipo">`
   - JS-хелпер `onTipoChange()` — новый тип не входит в число "особых" (`AMERICANO_TEAMS`/`AMERICANO`/`KING_OF_COURT`, которые скрывают/фиксируют модальность) и явно НЕ требует новой ветки — он корректно попадает в ветку `else` (модальность видима и обязательна), как сейчас `CANCHA_ABIERTA`. Добавляется отдельная ветка `else if (val === 'PADEL_CLINIC')` только ради информативной подсказки пользователю (`tipoHint`), по аналогии с остальными типами — не меняет функциональное поведение формы.
2. **Публичные фильтры по типу турнира** — `<option value="PADEL_CLINIC">` в селектах фильтра:
   - `torneos.html`
   - `index.html`
   - `players/dashboard.html`
3. **Карточки турниров (JS, `tipoDisplayMap`)** — новая запись `'PADEL_CLINIC': t('enum.tipo.padel_clinic')`:
   - `static/js/torneos.js`
   - `static/js/home.js`
   - `static/js/dashboard.js`
4. **Страница турнира** (`tournament-details.html`, строка с `<span class="badge badge-type" th:text="${tournament.tipo}">`) — сейчас бейдж выводит сырое имя Java-константы (`.toString()`) НЕ локализованно вообще, для всех 4 существующих типов. Чинится тем же проверенным паттерном, что уже используется в админских шаблонах (`admin/tournaments/details.html`, `admin/americano/*.html`): `th:text="#{__${'enum.tipo.' + tournament.tipo.name()}__}"` — динамический lookup по имени `enum.tipo.<ENUM_NAME>` (`enum.tipo.KING_OF_COURT`, `enum.tipo.AMERICANO`, ..., ключи уже существуют в properties-файлах). Это одновременно чинит локализацию для всех старых типов на этой странице и добавляет её для нового.
5. **i18n-ключи** — зеркалируется ровно footprint `CANCHA_ABIERTA` (все файлы и все повторяющиеся блоки, в которых встречается этот ключ, включая дублирующиеся секции — см. Edge cases):
   - `filter.tipo.padel_clinic` — `messages_es.properties`, `messages_ru.properties`, `messages_en.properties` (в `messages.properties` по умолчанию НЕ добавляется — у `filter.tipo.cancha_abierta`/`filter.tipo.americano_teams` там уже нет аналога, сохраняем существующую, пусть и неполную, картину, не расширяя scope)
   - `admin.tournaments.form.field.type.padel_clinic` — `messages_es/ru/en.properties` И `messages.properties` (здесь аналог `cancha_abierta` есть во всех 4 файлах и во всех повторяющихся блоках — зеркалируется один в один)
   - `enum.tipo.PADEL_CLINIC` (обратите внимание на регистр и полное имя константы, не аббревиатуру — используется `tournament.tipo.name()`) — `messages_es/ru/en.properties` и `messages.properties`
   - `enum.tipo.padel_clinic` (строчными, другой namespace — JS-i18n) — `static/js/i18n/messages-es.js`, `messages-ru.js`, `messages-en.js`

Тексты по языкам (как указал заказчик):
   - RU: Тренировка + игра
   - ES: Clínica de pádel
   - EN: Padel Clinic

### Нефункциональные
- i18n — см. п.5 выше, все 3 языка сайта.
- Безопасность — изменения не затрагивают точки входа данных от пользователя (только статические опции списка и текстовые ключи локализации).

## Вне скоупа
- `TournamentType.PADEL_CLINIC` сам по себе — задача [#456](https://github.com/UstinKO/padel-core-service/issues/456), отдельный PR (эта задача от него зависит).
- Любая специфичная для типа бизнес-логика (сетки/раунды/напоминания за 5 часов) — не запрошена, см. LFPT-0456 "Вне скоупа".
- CSS/цветовая дифференциация бейджа по типу — у `.badge-type` единый стиль для всех типов, новый тип не получает отдельного цвета (консистентно с остальными).
- Исправление нелокализованного бейджа на `test/tournaments.html` (внутренняя тестовая страница) — вне скоупа, не публичная страница, заказчик про неё не упоминал.
- Исправление отсутствия `filter.tipo.cancha_abierta`/`filter.tipo.americano_teams` в дефолтном `messages.properties` — существующий, не связанный с этой задачей пробел, не трогаем.
- Обновление `specs/MASTER.md` — см. "Связь с MASTER.md".

## Изменения в системе

### API
Без изменений.

### БД
Без изменений (см. LFPT-0456).

### UI
- `src/main/resources/templates/admin/tournaments/form.html` — новый `<option>` в `#tipo` + ветка в `onTipoChange()`
- `src/main/resources/templates/torneos.html` — новый `<option>` в `#tipoFilter`
- `src/main/resources/templates/index.html` — новый `<option>` в фильтре типа
- `src/main/resources/templates/players/dashboard.html` — новый `<option>` в фильтре типа
- `src/main/resources/templates/tournament-details.html` — локализация бейджа типа (фикс для всех типов)
- `src/main/resources/static/js/torneos.js`, `home.js`, `dashboard.js` — новая запись в `tipoDisplayMap`
- `src/main/resources/i18n/messages_es.properties`, `messages_ru.properties`, `messages_en.properties`, `messages.properties` — новые ключи (см. "Требования" п.5)
- `src/main/resources/static/js/i18n/messages-es.js`, `messages-ru.js`, `messages-en.js` — новая запись `enum.tipo.padel_clinic`

## Критерии приёмки
- [ ] В форме создания турнира в админке (`/admin/tournaments/new`) доступен вариант «Тренировка + игра» (ru) / Clínica de pádel (es) / Padel Clinic (en) — в зависимости от `Accept-Language`
- [ ] Создание турнира с этим типом через форму проходит без ошибок (при наличии бэкенд-части из #456) и ведёт на страницу деталей
- [ ] Карточка турнира этого типа на `/`, `/torneos`, дашборде игрока показывает локализованное название типа на выбранном языке
- [ ] Страница турнира (`/torneo/{id}`) показывает локализованное название типа на выбранном языке — для турнира нового типа И для уже существующих 4 типов (регрессионная проверка, что фикс бейджа не сломал старые типы)
- [ ] Фильтр по типу турнира на `/torneos`, `/`, дашборде игрока включает новый вариант и фильтрация по нему работает
- [ ] Названия клубов и другие контентные поля не переведены (регрессия не внесена)
- [ ] `./mvnw compile -DskipTests` и `./mvnw verify` — без регрессий (шаблоны/статика не влияют на Java-тесты напрямую, но полный прогон обязателен по `GIT_WORKFLOW.md` §4)

## Edge cases
- Properties-файлы `messages_*.properties` содержат исторически задвоенные блоки одних и тех же ключей `admin.tournaments.form.field.type.*` (последний блок в файле выигрывает при загрузке `java.util.Properties`, более ранние блоки для этих конкретных ключей — мёртвый код). Новый ключ добавляется во все блоки, где присутствует `admin.tournaments.form.field.type.cancha_abierta`, то есть ровно туда же, где и его аналог для `CANCHA_ABIERTA` — не исправляя и не трогая саму задвоенность (не в скоупе).
- `enum.tipo.KOC` — отдельный, не используемый нигде по факту ключ (реальный lookup идёт по `enum.tipo.KING_OF_COURT`, т.к. `.name()` турнира — `KING_OF_COURT`, не `KOC`) — не трогаем, не наш баг.
- Язык сайта не из {es, ru, en} — `AcceptHeaderLocaleResolver` в проекте поддерживает ровно эти три (см. `.claude/CLAUDE.md` i18n), остальные случаи вне скоупа всего проекта, не только этой задачи.

## Открытые вопросы
Нет.
