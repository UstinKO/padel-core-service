# LFPT-0492: Feature переключатель видимости турнира в админке и копирование ссылки (frontend)

## Статус
approved

## Источник
Прямой технический вход — GitHub issue [#492](https://github.com/UstinKO/padel-core-service/issues/492) (отправитель: architect). Зависит от бэкенд-задачи [#491](https://github.com/UstinKO/padel-core-service/issues/491), уже смерженной в `master` (PR [#493](https://github.com/UstinKO/padel-core-service/pull/493), спека [specs/features/LFPT-0491-closed-events-link-only-backend.md](LFPT-0491-closed-events-link-only-backend.md)). Клиентский запрос, из которого выросли обе задачи: [specs/requests/LFPT-0491-closed-events-link-only.md](../requests/LFPT-0491-closed-events-link-only.md).

## Контекст / зачем
Бэкенд (#491) уже добавил турниру поле видимости `visibilidad` (`TournamentVisibility`: `PUBLICO` / `SOLO_POR_ENLACE`) и исключил турниры «только по ссылке» из всех публичных списков. Но в админ-панели нет способа задать этот режим при создании/редактировании турнира, нет индикации текущего режима на странице деталей — без этой задачи бэкендовая фича физически недостижима через UI.

## Связь с MASTER.md
Не меняет инвариантов `specs/MASTER.md` — чисто UI-слой над уже существующим полем `visibilidad`. Регистрация, статусы, оплаты, форматы турнира не затрагиваются.

## Требования

### Функциональные

1. **Форма создания турнира** (`admin/tournaments/form.html`, `GET /admin/tournaments/new` → `POST /admin/tournaments`): добавить `<select th:field="*{visibilidad}">` с двумя опциями — `PUBLICO` (по умолчанию) и `SOLO_POR_ENLACE` — по аналогии с существующим `<select th:field="*{modalidad}">` (структура/стили формы, `admin/tournaments/form.html:198-210`). Без опции-плейсхолдера (`value=""`): при `tournamentDto.visibilidad == null` (новый турнир) ни одна `<option>` не помечена `th:field` как выбранная, и браузер по умолчанию показывает/отправляет первую опцию в разметке — значит `PUBLICO` должен идти первым `<option>` в разметке, это и даёт дефолт «Публичное» без доп. JS. Бэкенд (`TournamentService.createTournament`) и так трактует `null` как `PUBLICO` (см. спеку #491, п.4) — отправка первой опции явным значением `PUBLICO` не ломает эту защиту, только делает поведение явным на UI.
2. **Форма редактирования турнира** (тот же `admin/tournaments/form.html`, переиспользуется для `GET /admin/tournaments/{id}/edit` → `POST /admin/tournaments/{id}/edit`): тот же `<select>` — `tournamentDto.visibilidad` для существующего турнира уже приходит из `Tournament.visibilidad` через `TournamentMapper` (поле с одинаковым именем, маппится автоматически, без доп. работы), `th:field` сам выставит текущее значение как выбранное.
3. **Форма копирования турнира** (`GET /admin/tournaments/{id}/copy`, тот же шаблон `admin/tournaments/form.html` через `AdminTournamentCopyController`): тот же `<select>` рендерится автоматически (общий шаблон) — явно решить, каким должно быть значение у копии. Технический выбор (не бизнес-вопрос, аналогично п.3 `GIT_WORKFLOW.md`): **копия турнира «только по ссылке» остаётся «только по ссылке»** — `AdminTournamentCopyController` уже копирует значение `visibilidad` в `TournamentDto` для предзаполнения формы (как остальные поля — `categoriaNivel`, `modalidad`), пользователь может поменять вручную перед сохранением. Обоснование: копия турнира — это обычно повтор того же мероприятия (та же закрытая группа), менять видимость по умолчанию при копировании более неожиданно, чем сохранить её.
4. **Controller — модель для `<select>`**: добавить `model.addAttribute("visibilidades", TournamentVisibility.values())` во все четыре места `AdminController`/`AdminTournamentCopyController`, где уже передаются `modalidades`/`niveles` для этой формы (по аналогии, тот же паттерн): `AdminController.newTournamentForm` (GET `/admin/tournaments/new`), `AdminController.createTournament` — ветка `bindingResult.hasErrors()` (повторный рендер формы создания), `AdminController` — GET `/admin/tournaments/{id}/edit`, `AdminController` — ветка `bindingResult.hasErrors()` в `POST /admin/tournaments/{id}/edit`, и `AdminTournamentCopyController` — GET `/admin/tournaments/{id}/copy`. (Четыре места в `AdminController` + одно в `AdminTournamentCopyController`.) `<select th:each="v : ${visibilidades}">` — по аналогии с `th:each="modalidad : ${modalidades}"`.
5. **Страница деталей турнира** (`admin/tournaments/details.html`): добавить бейдж текущего режима видимости в существующий блок `.tournament-badges` (строки 338-346, рядом с `badge-type`/`badge-status`), после `badge-status`. Показывать **всегда** (для обоих режимов) — по аналогии с остальными бейджами в этом же блоке, которые не скрываются в зависимости от значения (жанр/уровень/тип/статус показываются всегда); так админ сразу видит текущий режим, не только для закрытых турниров. Текст — через тот же паттерн, что и остальные enum-бейджи: `#{__${'enum.visibilidad.' + tournament.visibilidad.name()}__}`.
6. **Копирование ссылки на странице деталей — переиспользовать существующий механизм, не писать новый.** На этой же странице уже есть кнопка «Compartir» (`onclick="shareAdminTournament()"`, `admin/tournaments/details.html:357-368`) и подключён `share-panel.js` (`admin/tournaments/details.html:1843`) — модальное окно с полем URL, кнопкой «скопировать» (`copyShareLink()`, работает через `navigator.clipboard`) и кнопками WhatsApp/Telegram. Она уже вычисляет корректный публичный URL турнира (специфичный для формата — King of Court/Americano/Team Americano/Team Playoff, либо дефолтный `/torneo/{id}`, если формат ещё не инициализирован) и работает независимо от `visibilidad` — то есть уже полностью закрывает критерий «дать возможность скопировать прямую ссылку» для турнира «только по ссылке», без новых изменений в JS/контроллере. Ничего нового здесь не разрабатывать — критерий приёмки проверяется как регрессия существующей кнопки на турнире с `visibilidad = SOLO_POR_ENLACE`.

### Нефункциональные
- i18n: новые строки — подпись поля `<select>` в форме (`admin.tournaments.form.field.visibility`) и значения enum для бейджа/опций (`enum.visibilidad.PUBLICO`, `enum.visibilidad.SOLO_POR_ENLACE`) — во все три файла (`messages_es/ru/en.properties`), по аналогии с существующими `enum.tipo.*`/`enum.estado.*` и `admin.tournaments.form.field.mostrar_nivel`.
- Безопасность: новой точки входа данных от пользователя, кроме уже существующей и защищённой формы создания/редактирования турнира, не появляется — `visibilidad` лишь ещё одно поле того же `TournamentDto`, которое уже проходит через `@Valid`/`tournamentAccessService`/`@PreAuthorize` этой формы.

## Вне скоупа
- Любая логика видимости на бэкенде (поле, фильтрация публичных списков) — уже реализована в #491.
- Третий режим «По приглашению» — не входит ни в #491, ни в эту задачу.
- Новая кнопка/механизм копирования ссылки — не нужен, см. п.6 выше (переиспользуется существующий `shareAdminTournament()`/`share-panel.js`).
- Токенизация ссылки — не запрошено (см. спеку #491, раздел «Безопасность»).

## Изменения в системе

### API
Нет новых REST-эндпоинтов. `POST /admin/tournaments` и `POST /admin/tournaments/{id}/edit` (form POST) начинают принимать поле формы `visibilidad` — оба эндпоинта уже принимают `TournamentDto` целиком, новое поле просто перестаёт быть всегда-`null` со стороны формы.

### БД
Нет (миграция уже выполнена в #491, `v1.53-add-visibilidad-to-tournament.yaml`).

### UI
- `src/main/resources/templates/admin/tournaments/form.html` — новый `<select th:field="*{visibilidad}">` (используется формами создания, редактирования и копирования — один шаблон на все три).
- `src/main/resources/templates/admin/tournaments/details.html` — новый бейдж режима видимости в `.tournament-badges`; кнопка «Compartir» и `share-panel.js` не меняются (переиспользуются как есть).
- `src/main/java/com/padle/core/padelcoreservice/controller/admin/AdminController.java` — добавить `model.addAttribute("visibilidades", TournamentVisibility.values())` в 4 места (см. Требования, п.4).
- `src/main/java/com/padle/core/padelcoreservice/controller/admin/AdminTournamentCopyController.java` — то же, в GET `/admin/tournaments/{id}/copy`.
- `src/main/resources/i18n/messages_es.properties`, `messages_ru.properties`, `messages_en.properties` — новые ключи `admin.tournaments.form.field.visibility`, `enum.visibilidad.PUBLICO`, `enum.visibilidad.SOLO_POR_ENLACE`.

## Критерии приёмки
- [ ] В форме создания турнира (`/admin/tournaments/new`) есть `<select>` режима доступа с опциями «Публичное» (первая в разметке, выбрана по умолчанию для нового турнира) и «Только по ссылке».
- [ ] Создание турнира с выбранной опцией «Только по ссылке» — созданный турнир имеет `visibilidad = SOLO_POR_ENLACE` (проверяется через `GET /api/tournaments/{id}` или прямой запрос к БД); не выбирая ничего (оставив дефолт) — `visibilidad = PUBLICO`.
- [ ] В форме редактирования существующего турнира (`/admin/tournaments/{id}/edit`) `<select>` показывает текущее значение `visibilidad` турнира; изменение значения и сохранение — новое значение читается при повторном открытии формы и на странице деталей.
- [ ] Форма копирования турнира (`/admin/tournaments/{id}/copy`) предзаполняет `<select>` значением `visibilidad` исходного турнира.
- [ ] На странице деталей турнира в админке (`/admin/tournaments/{id}`) виден бейдж текущего режима доступа — «Publico»/«Solo por enlace» (или локализованный эквивалент) — для турниров в обоих режимах.
- [ ] На странице деталей турнира с `visibilidad = SOLO_POR_ENLACE` кнопка «Compartir» открывает панель с прямой публичной ссылкой на турнир и кнопкой копирования, копирование в буфer обмена работает (регрессионная проверка существующего `shareAdminTournament()`/`share-panel.js`, без новых изменений).
- [ ] Новые строки (`admin.tournaments.form.field.visibility`, `enum.visibilidad.PUBLICO`, `enum.visibilidad.SOLO_POR_ENLACE`) присутствуют и не пустые в `messages_es.properties`, `messages_ru.properties`, `messages_en.properties`.
- [ ] `./mvnw verify` зелёный, регрессии на соседних сценариях формы (создание/редактирование турнира без изменения видимости, копирование турнира) нет.

## Edge cases
- Новый турнир, пользователь ничего не трогает в `<select>` — сохраняется `PUBLICO` (и на уровне формы — первая опция, и на уровне бэкенда — `null → PUBLICO`, двойная защита).
- Редактирование турнира, созданного до фичи (после миграции `visibilidad = PUBLICO` по дефолту колонки) — форма корректно показывает «Публичное» как текущее выбранное значение, не пустой/сломанный `<select>`.
- Копирование турнира с `visibilidad = SOLO_POR_ENLACE` — копия предзаполнена тем же значением, администратор может переключить на «Публичное» перед сохранением copy, если не хочет наследовать режим.
- Турнир «только по ссылке», формат ещё не инициализирован (нет King of Court/Americano/... состояния) — кнопка «Compartir» на деталях уже сейчас (до этой задачи) показывает дефолтный `/torneo/{id}`, это поведение не меняется и не ломается новым бейджем/селектом.
- Два реплики в Docker Swarm — чисто UI-поле поверх уже существующего `Tournament.visibilidad`/`TournamentDto.visibilidad`, обрабатывается в рамках тех же `@Transactional`-методов, что и остальные поля формы; дополнительного риска не создаёт (см. также спеку #491, Edge cases).

## Открытые вопросы
Нет. Одно техническое решение принято архитектором самостоятельно (аналогично п.3 `GIT_WORKFLOW.md`):
1. Поведение `<select>` при копировании турнира (наследовать `visibilidad` исходника, а не сбрасывать на `PUBLICO`) — см. Требования, п.3, обоснование там же. Это не входило явно ни в клиентский запрос, ни в issue #492 (там речь только о форме создания/редактирования), но форма копирования технически — тот же шаблон `form.html`, значит `<select>` неизбежно появится и в ней; выбор сделан по аналогии с тем, как уже ведут себя остальные поля при копировании (`AdminTournamentCopyController` копирует их все, не сбрасывает).
