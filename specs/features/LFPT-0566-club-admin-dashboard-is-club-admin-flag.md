# LFPT-0566: Feature главная админ-панель — флаг isClubAdmin в модели для клубной учётной записи (бэкенд)

## Статус
approved

## Источник
GitHub issue [#566](https://github.com/UstinKO/padel-core-service/issues/566) — техническая постановка от архитектора (прямой вход, `specs/requests/` не нужен). Telegram-сообщение (для reply): 5114.

## Контекст / зачем
Фронтенд-задача [#547](https://github.com/UstinKO/padel-core-service/issues/547) должна скрыть карточку статистики «Администраторы» на главной админ-панели (`GET /admin`, `admin/panel.html`) для клубной учётной записи (`ROLE_CLUB_ADMIN`). Шаблону для этого нужен флаг роли в модели. Сейчас в `AdminController.adminPanel()` переменная `isClubAdmin` вычисляется (добавлена в LFPT-0546) и используется только для выбора клубной/платформенной статистики (`totalTournaments`/`totalWaitlist`/`recentTournaments`) — в модель Thymeleaf-рендеринга она не передаётся. Существующие флаги модели `isSuperAdmin`/`isAdminRole` не позволяют шаблону отличить `CLUB_ADMIN` от `OWNER`/`ORGANIZER`.

## Связь с MASTER.md
Не меняет инвариантов. `ROLE_CLUB_ADMIN` и изоляция доступа по клубу уже описаны в `specs/MASTER.md` (раздел «Роли пользователей», LFPT-375/LFPT-376) — эта задача лишь передаёт в Thymeleaf-модель уже существующий на уровне контроллера признак роли, не вводит новой бизнес-логики.

## Требования

### Функциональные
1. В `AdminController.adminPanel()` (метод `GET /admin`) добавить `model.addAttribute("isClubAdmin", isClubAdmin);` — рядом с уже существующими `model.addAttribute("isSuperAdmin", owner.isSuperAdmin());` и `model.addAttribute("isAdminRole", owner.isAdminRole());`. Локальная переменная `isClubAdmin` (`owner.isClubAdmin()`) уже вычисляется в начале метода (LFPT-0546) — переиспользуется как есть, повторный вызов `owner.isClubAdmin()` не нужен.
2. Никакой другой логики метода не меняется: `totalOwners` продолжает вычисляться и передаваться в модель как сейчас (`ownerService.getTotalActiveOwners()`), значение не фильтруется по клубу — скрытие соответствующей карточки в шаблоне делается в [#547] на основе нового флага.

### Нефункциональные
- i18n: новых пользовательских строк нет — чисто технический флаг модели, не текст.
- Безопасность: новой точки входа данных от пользователя нет — `isClubAdmin` вычисляется из `@AuthenticationPrincipal Owner`, не из параметров запроса.
- Производительность: не добавляет новых запросов к БД — `owner.isClubAdmin()` не обращается к репозиторию (чистая проверка enum на уже загруженной сущности `Owner`).

## Вне скоупа
- Сокрытие карточки «Администраторы» в шаблоне `admin/panel.html` — [#547].
- Фильтрация `totalOwners` по клубу — не запрошено ни в этой задаче, ни в [#547] (карточка скрывается целиком, а не фильтруется).
- Любые другие атрибуты модели `adminPanel()` — не трогаются.

## Изменения в системе

### API
Нет REST-эндпоинтов. Меняется только содержимое модели Thymeleaf-рендеринга `GET /admin` (серверный маршрут) — добавляется один boolean-атрибут `isClubAdmin`.

### БД
Нет новой миграции.

### UI
Шаблон `admin/panel.html` в этой (бэкенд) задаче не меняется — он продолжает рендериться без использования нового атрибута до того, как [#547] добавит `th:unless="${isClubAdmin}"` на карточку «Администраторы». Появление неиспользуемого атрибута модели не влияет на текущий рендер.

## Критерии приёмки
- [ ] `GET /admin` для `CLUB_ADMIN` — в модели `isClubAdmin == true`.
- [ ] `GET /admin` для `OWNER`(`ADMIN`)/`SUPER_ADMIN`/`ORGANIZER` — в модели `isClubAdmin == false`.
- [ ] Остальные атрибуты модели `adminPanel()` (`totalPlayers`, `totalOwners`, `totalTournaments`, `totalWaitlist`, `recentTournaments`, `recentTournamentsWithFlags`, `recentPlayers`, `matchesInProgress`, `upcomingMatches`, `activeTournaments`, `isSuperAdmin`, `isAdminRole`) не изменились (regression).
- [ ] `./mvnw verify` зелёный.

## Edge cases
- `CLUB_ADMIN` с `clubId == null` — `isClubAdmin` всё равно `true` (флаг зависит только от `role`, не от `clubId`); существующая обработка `clubId == null` (нули/пустой список для статистики, LFPT-0546) не меняется.
- Два реплики в Docker Swarm — не создаёт риска: чтение `Model`-атрибута per-request, никакого shared state.

## Открытые вопросы
Нет.
