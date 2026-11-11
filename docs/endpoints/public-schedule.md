# API: Публичное расписание — `/api/public/tournaments`

**Контроллер:** `PublicTournamentController`  
**DTO:** `PublicTournamentDto`  
**Маппер:** `PublicTournamentMapper`  
**Назначение:** Анонимное чтение расписания турниров для мобильного приложения (LFPT-0623). Переиспользует те же методы `TournamentService` и правила видимости, что публичные Thymeleaf-страницы `/` и `/torneo/{id}` — новых данных не вводит.

---

## GET `/api/public/tournaments`

**Кто вызывает:** Мобильное приложение / любой анонимный клиент.  
**Аутентификация:** Не требуется.

### Алгоритм

1. Вызывает `TournamentService.getActiveTournamentsForHome()` — активные турниры со статусом `PUBLICADO`/`REGISTRO_ABIERTO`; `visibilidad = SOLO_POR_ENLACE` исключён (та же выборка, что на главной странице сайта).
2. Маппит каждый турнир в `PublicTournamentDto` (`PublicTournamentMapper`).
3. `Cache-Control: no-store`.

### Контракт

**Response 200 OK**: JSON-массив `PublicTournamentDto` без поля `participants`. Пустая выборка — `200 []`.

**Response 503 Service Unavailable** (сбой чтения БД, `DataAccessException`):
```json
{"code": "SCHEDULE_UNAVAILABLE"}
```
Текст исключения не возвращается клиенту.

---

## GET `/api/public/tournaments/{id}`

**Кто вызывает:** Мобильное приложение — экран деталей турнира.  
**Аутентификация:** Не требуется.

### Алгоритм

1. Вызывает `TournamentService.getActiveTournamentById(id)` — та же логика, что у `/torneo/{id}`: `visibilidad = SOLO_POR_ENLACE` доступен по прямому id (не фильтруется этим методом — осознанное существующее поведение, LFPT-491), но не попадает в список выше.
2. Турнир не найден/не активен → `404`. `id` не парсится в `Long` (нечисловой текст или переполнение) → `400`.

### Контракт

**Response 200 OK**: `PublicTournamentDto` с полем `participants`. Списки всегда присутствуют, даже если пусты. Ошибка чтения не превращается в пустые списки.

**Response 404 Not Found**:
```json
{"code": "TOURNAMENT_NOT_FOUND"}
```

**Response 400 Bad Request**:
```json
{"code": "INVALID_TOURNAMENT_ID"}
```

**Response 503 Service Unavailable**: как у списка.

---

## Контракт `PublicTournamentDto`

| Поле | Содержание |
|---|---|
| `id`, `name` | ID и название турнира |
| `club` | `{id, name, address}` |
| `type`, `modality`, `gender`, `level` | `TournamentType`, `Modalidad`, `GenderFormat`; `level` (`categoriaNivel`) может быть `null` |
| `startsAt`, `timeZone` | ISO-8601 со смещением; `timeZone` всегда `America/Argentina/Buenos_Aires` — модель хранит местное аргентинское время, турнир хранит `fechaInicio`/`horaInicio` без явной зоны |
| `duration` | Текст продолжительности (`duracion`), может быть `null` |
| `price`, `currency` | Число и код валюты (`precio`/`moneda`) |
| `status`, `registrationOpen` | `TournamentStatus` и флаг, разрешена ли запись (`isRegistrationOpen()`) |
| `capacity`, `capacityUnit` | Лимит (`cupoMax`) и единица: `PAIRS` для `Modalidad.DOBLES`, иначе `PLAYERS` |
| `occupied`, `available`, `waitlistCount` | Счётчики существующего сервиса, в той же единице |
| `cancellationDeadline` | ISO-8601 со смещением либо `null` |
| `description` | `infoDetallada`, может быть `null` |
| `websiteUrl` | `${app.base-url}/torneo/{id}` |
| `participants` | Только в ответе деталей: `{confirmed: [{id, names, seekingPartner}], waitlist: [...]}`. ID — `registration-{id}`; имена упорядочены и объединены в пары по отображению на сайте. `SOLO_SEARCH` помечен `seekingPartner: true`. Отменённые и неактивные заявки исключены. Имена из резерва возвращаются лишь для парного Americano, где они уже публичны на сайте; в других форматах `waitlist` пуст, даже если `waitlistCount` больше нуля. |

**Явно исключено** (не входит в DTO): `ownerId`, `createdBy`, `contactoOrganizador`, `faqUrl`, `mostrarNivel`, `isActive`, `visibilidad`, контактные и служебные данные участников/регистраций (email, телефон, Telegram, платежи, внутренний профиль). Поле `participants` содержит только публичные имена, безопасный ID и флаг поиска партнёра. Разрешены только два GET выше; все остальные маршруты `/api/**` (включая мутации `/api/tournaments/**`) сохраняют авторизацию без изменений.

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-18]** Доступ к `SOLO_POR_ENLACE` по прямому id через этот анонимный API — не новая брешь: то же поведение уже было у `getActiveTournamentById()` для веб-страницы `/torneo/{id}` (LFPT-491, покрыто `TournamentVisibilidadTest`). Эта фича не меняет правило видимости, только добавляет JSON-представление тех же двух методов.


## Публичные данные UI v2

Публичный каталог отделён от мобильного массива: `GET /api/public/tournaments` и детали `/{id}` сохраняют прежний контракт и ошибки. Новые endpoint'ы описаны в [OpenAPI](../frontend-react/contract/public-discovery.openapi.json).

| Метод | URL | Ответ |
|---|---|---|
| GET | `/api/public/catalog` | Страница `{items,page,size,totalElements,totalPages}`; `page=0`, `size=12` (1–50); `page × size <= 2147483647` (предел JPA). Фильтры `clubId,type,modality,gender,level,status,from,to`; enum-значения домена, даты ISO. |
| GET | `/api/public/home` | Реальные `totalClubs,totalPlayers,totalTournaments` и до шести `upcomingTournaments`. |
| GET | `/api/public/clubs` | Активные клубы: id, название, адрес/город, описание, логотип, ссылки на карту и сайт. |
| GET | `/api/public/clubs/{id}` | Карточка активного клуба; неизвестный/неактивный id — 404. |

Все ответы — `Cache-Control: no-store`. Каталог исключает неактивные мероприятия, черновики и `SOLO_POR_ENLACE`; сортировка дата → время → id. Фильтрация и пагинация выполняются в БД, счётчики и клубы загружаются пакетно. Главная дополнительно ограничена предстоящими `PUBLICADO`/`REGISTRO_ABIERTO`; статистика мероприятий относится к этой подборке. Доступ по прямому id, правила регистрации и вместимости не меняются.

Новые ошибки — локализованный `application/problem+json`: 400 `INVALID_CATALOG_FILTER`, 404 `CLUB_NOT_FOUND`, 503 `PUBLIC_DATA_UNAVAILABLE`. Мобильный контракт ошибок не изменён. Контакты игроков и административные поля в новые DTO не входят.

Пагинация: страница за последней при допустимом смещении возвращает пустой `items` и фактические итоговые счётчики. При `page × size > 2147483647` возвращается 400 `INVALID_CATALOG_FILTER` до запроса в БД; например, `page=2147483647&size=12`. Такой запрос не означает недоступность хранилища и не возвращает 503.


## Веб-карточка мероприятия и серверный SEO-контракт

`GET /api/public/events/{id}` — отдельный типизированный контракт React: `event` (прежний безопасный DTO с публичными именами), `organizer` (только опубликованный контакт события), `format`, `phase`, `actions`, `canonicalUrl`, `calendarUrl`, `pngUrl`. Мобильные `/api/public/tournaments` и `/{id}` не меняются. Черновик/неактивное/неизвестное событие — 404; событие только по ссылке доступно по id.

`format`: KING_OF_COURT, AMERICANO, TEAM_PAIRS, TEAM_AMERICANO, TEAM_PLAYOFF, CANCHA_ABIERTA, PADEL_CLINIC. TEAM_PAIRS обозначает отсутствие сохранённого варианта проведения; null-phase раунды парного события принадлежат round-robin, QUALIFICATION/PLAYOFF — Team Playoff. Фаза определяется сохранённым проведением и финальным статусом, не датой: NOT_STARTED/LIVE/QUALIFICATION/PLAYOFF/FINISHED/CANCELLED.

`actions` содержит только ссылки чтения REGISTRATION_INFO (действующая карточка с условиями), LIVE (существующее проведение/результаты), CALENDAR (существующий .ics). Это навигация, не разрешение на регистрацию: все права и условия мутаций проверяются прежними операциями. PNG URL не предоставляется без публичного серверного контракта. Контакты игроков, внутренние Owner/createdBy/paymentInstructions и YouTube не сериализуются.

Для `/preview/torneo/{id}`, `/preview/clubs`, `/preview/clubs/{id}` сервер формирует title/description/canonical/OG в исходном shell, сохраняя Vite assets и исключая данные приложения из DOM. Canonical берётся из app.base-url, без Host от клиента и preview-prefix. Preview защищён SUPER_ADMIN и noindex; HTML экранируется. Неизвестный id даёт HTTP 404, ошибка хранилища — 503; index не кешируется. Production allowlist не изменяется.
