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

**Response 200 OK**: JSON-массив `PublicTournamentDto`. Пустая выборка — `200 []`.

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

**Response 200 OK**: `PublicTournamentDto`.

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

**Явно исключено** (не входит в DTO): `ownerId`, `createdBy`, `contactoOrganizador`, `faqUrl`, `mostrarNivel`, `isActive`, `visibilidad`, любые данные об участниках/регистрациях — `TournamentDto` их и не содержит, участники в отдельных сущностях. Разрешены только два GET выше; все остальные маршруты `/api/**` (включая мутации `/api/tournaments/**`) сохраняют авторизацию без изменений.

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-18]** Доступ к `SOLO_POR_ENLACE` по прямому id через этот анонимный API — не новая брешь: то же поведение уже было у `getActiveTournamentById()` для веб-страницы `/torneo/{id}` (LFPT-491, покрыто `TournamentVisibilidadTest`). Эта фича не меняет правило видимости, только добавляет JSON-представление тех же двух методов.
