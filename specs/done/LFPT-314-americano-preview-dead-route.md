# LFPT-314: Устранение падения на мёртвом роуте предпросмотра инициализации Americano

## Статус
done — смержено в master, [PR #315](https://github.com/UstinKO/padel-core-service/pull/315)

## Источник
`specs/requests/LFPT-314-americano-preview-crash.md`. GitHub issue: https://github.com/UstinKO/padel-core-service/issues/314. Баг уже был задокументирован в `.claude/CLAUDE.md` (раздел "Americano Admin Pages", блок "Известный баг").

## Контекст / зачем
`GET /tournaments/americano/admin/{tournamentId}/preview` (метод `AmericanoViewController.showAdminPreviewForm`, строки 499-527) для неинициализированного Americano-турнира возвращает имя view `admin/americano/initialize`. Такого файла нет в `templates/admin/americano/` (в каталоге лежат только `preview-double-rounds.html`, `preview-rounds.html`, `tournament-double.html`, `tournament-playoff.html`, `tournament.html`) — Thymeleaf бросает `TemplateInputException`, пользователь видит 500/белый экран.

Анализ подтвердил: маршрут нигде не используется как реальная ссылка — ни в одном `.html`-шаблоне, ни в JS нет `href`/`action`, ведущих на `.../admin/{id}/preview` (проверено `grep` по `src/main/resources/templates` и `src/main/resources/static/js`). Единственная ссылка на соседний URL без `/preview` (`/tournaments/americano/admin/{id}`) ведёт на другой, рабочий метод — `viewAdminTournament` (строка 590). Рабочий флоу настройки и предпросмотра Americano-турнира полностью живёт на `admin/tournaments/details.html` (`GET /admin/tournaments/{id}`): инлайн-форма конфигурации, POST на `/tournaments/americano/admin/{id}/initialize` (рабочий обработчик `initializeAdminTournament`) и предпросмотр через `POST /tournaments/americano/{id}/preview-rounds`.

Поскольку роут не используется нигде в интерфейсе, создавать для него полноценный новый экран `admin/americano/initialize.html` избыточно — это дублировало бы уже существующий работающий UI на `admin/tournaments/details.html` и добавляло бы вторую точку поддержки той же логики инициализации. **Технический выбор:** чиним падение через редирект на уже существующую рабочую страницу настройки турнира, без создания нового шаблона.

Попутно при разборе того же метода найдена вторая, менее критичная проблема в соседней ветке того же метода (после исправления главного бага она перестаёт быть "спящей" и тоже требует починки в этом же PR, см. Функциональные требования, п.3).

## Связь с MASTER.md
Фича не меняет ни одного инварианта `specs/MASTER.md`. Затрагивает раздел "Форматы турниров" (Americano) только на уровне навигации админ-панели — бизнес-логика инициализации Americano-турнира (`AmericanoService.initializeAmericanoTournament`, `AmericanoService.isInitialized`) не меняется, переиспользуется существующая проверка `isInitialized`. Изменений в MASTER.md не требуется.

## Требования

### Функциональные

1. `GET /tournaments/americano/admin/{tournamentId}/preview` для существующего турнира типа `AMERICANO`, который ещё **не инициализирован**, должен вернуть HTTP 302 редирект на `/admin/tournaments/{tournamentId}` (уже рабочая страница деталей турнира с формой конфигурации/инициализации/предпросмотра) — без рендеринга какого-либо `admin/americano/*` шаблона и без нового flash-сообщения (чтобы не создавать новую пользовательскую строку, требующую i18n-перевода на `es/ru/en`, ради маршрута, на который никто не переходит из UI).
2. Ветка для турнира с типом, отличным от `AMERICANO`, не меняется: редирект на `/admin/tournaments/{tournamentId}` с flash `error` = `"Este torneo no es de tipo Americano"` (уже существующая строка, поведение регрессионно фиксируется тестом).
3. **Попутный фикс в той же ветке метода:** если турнир уже инициализирован, метод редиректит на `/admin/tournaments/americano/{tournamentId}` — такого маршрута не существует ни в одном контроллере (подтверждено `grep` по `src/main/java` и `templates`), это заведёт пользователя на 404. Исправить редирект на существующий рабочий маршрут `/tournaments/americano/admin/{tournamentId}` (`viewAdminTournament`, страница управления запущенным турниром). Существующее flash-сообщение `info` = `"El torneo ya está inicializado"` не меняется.
4. Поведение для несуществующего `tournamentId` (сейчас `IllegalArgumentException` → необработанная ошибка) не меняется в рамках этой фичи — это отдельная, более широкая проблема (общая для нескольких методов контроллера, не специфичная для этого бага), сознательно вне скоупа.

### Нефункциональные
- i18n: новых пользовательских строк нет (см. п.1 функциональных требований — решение осознанно спроектировано так, чтобы их избежать). Существующая строка `"El torneo ya está inicializado"` (п.3) не создаётся заново, просто исправляется целевой URL редиректа, содержимое сообщения не трогается.
- Производительность/нагрузка: не применимо, чистая навигационная правка без новых запросов к БД.
- Безопасность: не создаётся новая точка входа данных от пользователя. Метод как и раньше защищён только общим правилом `.anyRequest().authenticated()` из `SecurityConfig` (нет специфичного `@PreAuthorize`) — это уже так у соседнего рабочего метода `viewAdminTournament`, поэтому не меняется в рамках этой фичи (см. "Вне скоупа").

## Вне скоупа
- Создание нового Thymeleaf-шаблона `admin/americano/initialize.html` — осознанно отклонённый вариант (см. "Контекст").
- Добавление `@PreAuthorize` на `showAdminPreviewForm` — метод и так уже не имеет отдельной ролевой проверки, как и соседний `viewAdminTournament`; ужесточение авторизации не запрошено и не относится к причине падения.
- Обработка `IllegalArgumentException` для несуществующего `tournamentId` — существующее общее поведение контроллера, не связано с этим багом.
- **Отдельная находка вне скоупа этого тикета:** при анализе обнаружен второй, независимый мёртвый роут с идентичной причиной падения — `GET /tournaments/americano/{tournamentId}/initialize` (метод `showInitializeForm`, строки 166-194 того же файла) возвращает несуществующий шаблон `tournaments/americano/initialize` (файла `templates/tournaments/americano/initialize.html` не существует; подтверждено — ни один шаблон/JS на этот GET-роут не ссылается). Это тот же паттерн бага, но в другом методе, с другим URL-контекстом (публичный namespace `/tournaments/americano/...`, а не `/tournaments/americano/admin/...`). Рекомендуется завести отдельный тикет (например, LFPT-315) — не включаем в этот PR, чтобы не раздувать скоуп пилотного прогона.

## Изменения в системе

### API
- `GET /tournaments/americano/admin/{tournamentId}/preview` — поведение меняется (см. Функциональные требования). URL, метод, path-параметр не меняются.

### БД
Нет изменений. Новая Liquibase-миграция не требуется.

### UI
Новых шаблонов нет. Целевые страницы редиректов (`admin/tournaments/details.html` через `GET /admin/tournaments/{id}`, `admin/americano/tournament.html` через `GET /tournaments/americano/admin/{id}`) уже существуют и не изменяются.

## Критерии приёмки
- [ ] `GET /tournaments/americano/admin/{tournamentId}/preview` для существующего AMERICANO-турнира без инициализации возвращает HTTP 302 с заголовком `Location`, оканчивающимся на `/admin/tournaments/{tournamentId}` — не бросает `TemplateInputException`, не возвращает 500.
- [ ] `GET /tournaments/americano/admin/{tournamentId}/preview` для уже инициализированного AMERICANO-турнира возвращает HTTP 302 с `Location`, оканчивающимся на `/tournaments/americano/admin/{tournamentId}` (а не на несуществующий `/admin/tournaments/americano/{tournamentId}`).
- [ ] `GET /tournaments/americano/admin/{tournamentId}/preview` для турнира с типом, отличным от `AMERICANO`, по-прежнему возвращает HTTP 302 на `/admin/tournaments/{tournamentId}` с flash-атрибутом `error` = `"Este torneo no es de tipo Americano"` (регресс не сломан).
- [ ] MVC/юнит-тест на `AmericanoViewController.showAdminPreviewForm`, покрывающий все три ветки выше (сейчас тестов на этот метод нет — `grep` по `src/test` ничего не находит).
- [ ] Ручная/тестовая проверка: переход на `/tournaments/americano/admin/{существующий_id}/preview` для неинициализированного турнира в браузере открывает страницу деталей турнира с формой конфигурации/инициализации вместо страницы ошибки.
- [ ] `.claude/CLAUDE.md`, раздел "Americano Admin Pages" — запись "Известный баг" удаляется/актуализируется, так как маршрут больше не падает (обновляется в том же PR, где чинится код).

## Edge cases
- Несуществующий `tournamentId` — поведение не меняется (см. "Вне скоупа"), покрывать тестом в рамках этой фичи не требуется.
- Турнир существует, но неактивен/удалён (`getActiveTournamentById` не находит) — то же самое, не меняется.
- Конкурентный доступ — не применимо, метод не пишет в БД (только `GET`, только чтение `isInitialized`/`getActiveTournamentById`), 2 реплики за Docker Swarm ничего не меняют для этого чтения.
- Пользователь без прав ORGANIZER/SUPER_ADMIN, но аутентифицированный (например, ROLE_PLAYER) технически может открыть этот GET (нет `@PreAuthorize`, только `.anyRequest().authenticated()`) — после фикса его просто перекинет на `/admin/tournaments/{id}`, где сработает уже существующая (не меняемая этой фичей) проверка доступа/логика той страницы. Не хуже текущего поведения соседних методов данного контроллера.

## Открытые вопросы
Нет открытых вопросов, требующих бизнес-решения. Технический выбор (редирект вместо нового шаблона) сделан на основании подтверждённого факта, что маршрут не используется нигде в UI — см. "Контекст". Единственная находка, выходящая за рамки этого тикета (второй идентичный мёртвый роут `GET /tournaments/americano/{tournamentId}/initialize`), явно вынесена в раздел "Вне скоупа" с рекомендацией завести отдельный тикет — не блокирует статус `approved` этой спеки.
