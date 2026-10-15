# LFPT-0573: Feature страница «Оплаты и посещаемость» — добавление пары вручную, подсказки по присутствию (frontend)

## Статус
approved

## Источник
Клиентский запрос [specs/requests/LFPT-0572-payments-page-manual-team-add.md](../requests/LFPT-0572-payments-page-manual-team-add.md) (Telegram, пересланное сообщение от Evgeny_754, `telegram_launch_message_id: 5097`). GitHub issue [#573](https://github.com/UstinKO/padel-core-service/issues/573). Зависит от бэкенд-задачи [#572](https://github.com/UstinKO/padel-core-service/issues/572) (параметр `returnTo` в `TeamPlayoffViewController.addTeam`) — спека [LFPT-0572-payments-page-manual-team-add-backend.md](LFPT-0572-payments-page-manual-team-add-backend.md).

## Контекст / зачем

Этап 1 клиентского запроса объединяет добавление пары в Team Playoff и фиксацию оплаты/явки в один рабочий процесс, начинающийся со страницы «Оплаты и посещаемость» (`/admin/tournaments/{tournamentId}/payments`). Сейчас форма ручного поиска/создания двух игроков (LFPT-0561/0562) существует только на странице Team Playoff (`/tournaments/team-playoff/admin/{tournamentId}`, блок `#addTeamForm`/`#addTeamFormEl`), а страница оплат умеет только добавлять в Team Playoff уже существующую сайтовую регистрацию целиком, одной кнопкой на строку (T18, `PaymentManagementController.saveAndAddTeam`, кнопка `admin.payments.btn.add_team`).

Нужно: (1) перенести точку входа формы поиска/создания на страницу оплат, не дублируя ~140 строк разметки/CSS/JS; (2) переименовать существующую кнопку T18, не меняя её поведение (оно уже идемпотентно); (3) убрать дублирующую точку входа на странице Team Playoff в пользу ссылки; (4) добавить понятную подсказку у отметки явки; (5) показать на странице Team Playoff, почему конкретная команда недоступна для матча.

## Связь с MASTER.md

Не вводит новую роль, статус или инвариант `specs/MASTER.md`. Доступ не меняется — обе страницы уже видны тем же ролям (`SUPER_ADMIN, ADMIN, ORGANIZER, CLUB_ADMIN`, `tournamentAccessService.assertCanManageTournament`).

## Требования

### Функциональные

1. **Вынос общей разметки/CSS/JS формы поиска-или-создания игрока в переиспользуемые файлы.** Сейчас весь код (CSS `.player-slot`, `.player-search-*`, `.player-selected-*`, `.player-new-fields`, `.player-toggle-mode-btn`, `.add-team-form`, `.form-row`, `.form-row-3` — строки 73-147 `tournament-playoff.html`; JS — IIFE `initPlayerSlots` внутри `tournament-playoff.html`, строки ~869-1005) находится только в `tournament-playoff.html`. Вынести:
   - CSS → новый файл `static/css/team-playoff-player-search.css`, все селекторы скопированы как есть **плюс обёрнуты под общий класс-контейнер** `.team-playoff-add-form` (например `.team-playoff-add-form .player-slot{...}`), чтобы не конфликтовать с одноимёнными общими классами на других страницах (в `admin.css` уже есть несвязанный `.form-row` в медиа-блоке — see `CODE_REVIEW.md §2`, DRY, но без коллизий имён).
   - JS → новый файл `static/js/team-playoff-player-search.js` (это и есть файл, изначально описанный в LFPT-0562, но на деле не вынесенный туда при реализации) — функция инициализации принимает URL поиска и опциональный колбэк уведомления (`function initTeamPlayoffAddForm(searchUrl, notify)`), вызывается с `TOURNAMENT_ID`-специфичным URL на каждой из двух страниц; внутренний `notify(message)` — `if (window.showToast) window.showToast(message); else alert(message);` (на `tournament-playoff.html` уже есть `showToast`/`#toastBox`, на `payments.html` его нет — простой `alert` как запасной вариант, не требует переносить toast-инфраструктуру ради одной формы).
   - И CSS, и JS подключаются `<link>`/`<script>` на обеих страницах (`tournament-playoff.html` и `payments.html`); инлайновые копии в `tournament-playoff.html` удаляются.
2. **Кнопка «+ Добавить пару вручную» на странице оплат.** Новая кнопка рядом с заголовком страницы (`payments.html`), по клику показывает/прячет (`display:none` ↔ `block`, аналогично `toggleAddForm()`) панель с той же формой (`player-slot` ×2, `registrationSource`, `hasPaid`, `attended`, `adminComment`), POST на тот же существующий `/tournaments/team-playoff/admin/{tournamentId}/teams/add`, со скрытым полем `<input type="hidden" name="returnTo" value="payments">`. Кнопка видна только когда `tournament.tipo.name() == 'AMERICANO_TEAMS'` (тот же критерий, что уже используется для T18-кнопки/бейджа — `markTeamPlayoffEligibility` в контроллере вызывается строго при этом условии).
3. **Переименование существующей T18-кнопки/бейджа (без изменения поведения).** В `messages_{es,ru,en}.properties` меняются **значения** (не ключи) у `admin.payments.btn.add_team` (было "Agregar equipo"/"Добавить команду"/"Add team" → "Incluir en Team Playoff"/"Включить в Team Playoff"/"Add to Team Playoff"), `admin.payments.btn.add_team_title` (уточнить текст подсказки под новую формулировку) и `admin.payments.badge.in_tournament` (было "En el torneo"/"В турнире"/"In tournament" → "En Team Playoff"/"В Team Playoff"/"In Team Playoff") + `_title`. Сам контроллер/эндпоинт (`PaymentManagementController.saveAndAddTeam`) не меняется — повторное нажатие и сейчас невозможно (кнопка скрывается, как только `alreadyInTeamPlayoff == true`).
4. **Верхняя кнопка «Agregar equipo» на странице Team Playoff → ссылка.** В `tournament-playoff.html` убрать `<button onclick="toggleAddForm()">` и весь блок `<div id="addTeamForm">...</div>` (включая форму — она теперь только на странице оплат); заменить кнопку на `<a th:href="@{'/admin/tournaments/' + ${tournament.id} + '/payments'}" class="btn-sm btn-success">` с новым текстом (новый ключ `admin.playoff.link.add_team_payments` = "Добавить пару на странице оплат"/"Agregar pareja en la página de pagos"/"Add pair on the payments page"). Функция `toggleAddForm()` и связанный с ней JS-код удаляются вместе с формой (мёртвый код).
5. **Подсказка у отметки явки на странице оплат.** Столбец/чекбокс "Asistió" (`payments.html`, строки ~132, 221-229) получает видимую подпись «Presencia del equipo»/«Присутствие команды»/«Team presence» (новый ключ `admin.payments.attended.label`) и подсказку (новый ключ `admin.payments.attended.tooltip`, точный текст клиента на ru, переведённый на es/en), которая показывается по наведению, фокусу клавиатурой и нажатию на мобильном — через доступный паттерн "иконка-подсказка": маленькая кнопка `<button type="button" class="info-tip" tabindex="0" aria-label="...">` с текстом подсказки в `::after`/`::before`, видимым по `:hover`/`:focus` (чисто CSS) **плюс** JS-тоггл класса `.open` по `click` (для мобильных браузеров, где `:focus` на кнопке ведёт себя не всегда предсказуемо) — закрывается кликом вне подсказки. Реализуется как переиспользуемый компонент (CSS в `payment-management.css`, маленький JS-сниппет в существующем инлайн-скрипте `payments.html`), не привязан к конкретной странице — можно переиспользовать для будущих подсказок (не вводит новую внешнюю зависимость/библиотеку).
6. **Индикация недоступности команды для матча на странице Team Playoff.** В таблице команд (`tournament-playoff.html`, строки ~353-363, колонка "Asistió") рядом с существующей иконкой присутствия добавить видимый бейдж для команд с `!team.attended` — новый ключ `admin.playoff.badge.not_attended` ("Недоступна для матча"/"No disponible para partido"/"Not available for match") с `title`/`_title`-ключом, поясняющим причину (не влияет на существующую логику выбора команд в `renderCourtBoard`/`availableTeams` — чисто визуальное пояснение уже существующего правила LFPT-310/388, бэкенд не меняется).

### Нефункциональные
- i18n: все новые пользовательские строки — новые ключи в `messages_{es,ru,en}.properties` (см. список выше); существующие ключи `admin.payments.btn.add_team`, `admin.payments.btn.add_team_title`, `admin.payments.badge.in_tournament`, `admin.payments.badge.in_tournament_title` — значения меняются во всех трёх файлах. Проверить страницы под `Accept-Header: ru` — не осталось испанских строк для нового UI (`CODE_STYLE.md §6.4`).
- Безопасность: новой точки входа данных пользователя, кроме уже предусмотренной бэкендом (#572) и существующей (LFPT-0561 поиск/создание игрока), нет — форма на странице оплат использует тот же эндпоинт, что и форма на странице Team Playoff.
- Производительность: не вводит новых запросов к серверу сверх уже существующих (`players/search` переиспользуется как есть).

## Вне скоупа
- Сам эндпоинт `teams/add` и параметр `returnTo` — реализован в [#572].
- Поиск/создание игрока, дедуп по телефону — реализовано в LFPT-0561, не меняется.
- Любые изменения бизнес-правила "присутствие — единственное условие доступности команды для матча" (LFPT-310/388) — не трогается, только визуальное пояснение уже существующего правила.
- Правила регистрации, лимит мест, лист ожидания, расчёт присутствия по двум игрокам, отмена заявки, `importFromRegistrations`/«Importar inscripciones» — не меняются (см. «Границы этапа» клиентского запроса).

## Изменения в системе

### API
Нет новых эндпоинтов — использует `GET .../players/search` и `POST .../teams/add` (с `returnTo=payments`, [#572]) и существующий `POST /admin/tournaments/{tournamentId}/payments/add-team` (T18, без изменений).

### БД
Нет.

### UI
- `src/main/resources/templates/admin/tournaments/payments.html` — новая кнопка «+ Добавить пару вручную» + встроенная панель с формой (общая разметка из нового shared-файла/партиала); переименование кнопки/бейджа T18 (i18n-значения); подпись и подсказка у отметки явки.
- `src/main/resources/templates/admin/americano/tournament-playoff.html` — удаление верхней кнопки/формы `#addTeamForm`, замена на ссылку на страницу оплат; индикатор "недоступна для матча" в таблице команд; удаление инлайн-CSS/JS формы (перенесены в shared-файлы).
- Новый `src/main/resources/static/css/team-playoff-player-search.css`.
- Новый `src/main/resources/static/js/team-playoff-player-search.js`.
- `src/main/resources/i18n/messages_{es,ru,en}.properties` — новые и изменённые ключи (см. "Требования").

## Критерии приёмки
- [ ] На `/admin/tournaments/{tournamentId}/payments` для турнира `AMERICANO_TEAMS` видна кнопка «+ Добавить пару вручную»; по клику открывается форма поиска/создания двух игроков (идентичная форме на странице Team Playoff).
- [ ] Сохранение формы с двух новых ("с улицы") игроков создаёт ровно одну пару `TournamentRegistration` + одну `AmericanoTeam` (переиспользуется существующий механизм #561/#572) и возвращает координатора обратно на страницу оплат с новой парой в таблице (не на страницу Team Playoff).
- [ ] Сохранение формы с комбинациями существующий+новый / новый+существующий / оба существующих — работает так же (регрессия на LFPT-0561/0562), независимо от того, с какой страницы открыта форма.
- [ ] Для пары, уже зарегистрированной через сайт — кнопка в её строке называется «Включить в Team Playoff»; после нажатия видно состояние «В Team Playoff»; повторный рендер страницы кнопку больше не показывает (регрессия на существующую идемпотентность T18).
- [ ] На `/tournaments/team-playoff/admin/{tournamentId}` верхней кнопки/формы «Agregar equipo» больше нет — вместо неё ссылка «Добавить пару на странице оплат», ведущая на `/admin/tournaments/{tournamentId}/payments`.
- [ ] На странице оплат отметка явки подписана «Присутствие команды»; подсказка с заданным текстом видна при наведении мыши, при фокусе клавиатурой (Tab) и по нажатию на экране телефона (эмуляция touch в Playwright/проверка `:focus`).
- [ ] На странице Team Playoff команда с неотмеченным присутствием имеет видимый бейдж/пояснение "недоступна для матча"; команда с отмеченным присутствием — бейджа не имеет.
- [ ] Сценарий работает и после запуска квалификации (`qualStarted == true`) — форма и ссылка на странице Team Playoff по-прежнему корректны, существующие матчи не меняются.
- [ ] Права клубного администратора (`CLUB_ADMIN`) и организатора (`ORGANIZER`) — форма и ссылка видны и работают так же, как для `SUPER_ADMIN`/`ADMIN` (регрессия на существующую авторизацию, не меняется этой задачей).
- [ ] Новые i18n-строки присутствуют и не пустые во всех трёх `messages_{es,ru,en}.properties`; обе страницы под `Accept-Language: ru` не показывают испанских строк для нового/изменённого UI.
- [ ] `./mvnw verify` зелёный; регрессии на остальной части обеих страниц (список команд, квалификация, плей-офф, остальные колонки платежей) нет.

## Edge cases
- Форма на странице оплат отправлена с ошибкой (например, оба слота ссылаются на одного и того же уже активного в турнире игрока — `InvalidStateException` из `addTeamFromRegistration`/`addTeam`) — координатор остаётся на странице оплат (не переброшен на Team Playoff), видит понятное сообщение об ошибке.
- Медленный/ошибочный ответ `players/search` на странице оплат — тот же fallback-паттерн, что и на Team Playoff (сообщение "ошибка поиска", возможность переключиться в режим "новый игрок") — ничего нового, просто тот же переиспользуемый JS.
- Узкий экран/телефон — две колонки поиска игроков (как и на Team Playoff, уже адаптировано media-запросом `@media (max-width: 768px)`) не должны разъезжаться на странице оплат — таблица платежей и так горизонтально скроллится (`overflow-x:auto`), форма добавления — вне таблицы, по ширине контейнера.
- Подсказка явки: тап на мобильном не должен залипать открытой навсегда — закрывается повторным тапом в любом месте экрана или следующим тапом на другую подсказку/элемент.

## Открытые вопросы
Нет. Технические решения (аналогично `GIT_WORKFLOW.md §3`):
1. **Вынос CSS/JS формы в shared-файлы, а не копирование блока целиком на страницу оплат.** DRY (`CODE_REVIEW.md §2`) — ~140 строк CSS+JS иначе дублировались бы дословно на двух страницах и расходились бы при следующей правке (как уже один раз разошлось: JS-файл был заявлен в LFPT-0562, но не вынесен при реализации).
2. **Подсказка явки — свой маленький компонент (CSS + мини-JS), а не переиспользование `.lp-no-contact-tooltip` из `tournament-details.css`.** Этот паттерн привязан к другой странице и другому визуальному контексту (всплывающая подсказка про контакт игрока); минимальный независимый компонент для одной подсказки проще и не тянет зависимость между двумя неродственными страницами.
3. **`alert()` как fallback-уведомление на странице оплат (там, где на Team Playoff используется `showToast`/`#toastBox`).** Переносить всю toast-инфраструктуру ради одной формы — не оправдано (YAGNI, `CODE_REVIEW.md §3`); `notify()`-обёртка в shared JS уже делает это прозрачным для вызывающего кода.
