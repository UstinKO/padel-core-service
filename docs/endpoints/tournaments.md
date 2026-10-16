# API: Турниры — `/api/tournaments`

**Контроллер:** `TournamentController`  
**Назначение:** CRUD-операции над турнирами. Read-эндпоинты публичные, write — только для `SUPER_ADMIN` / `ORGANIZER`.

---

## Модель данных `TournamentDto`

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | Long | ID турнира |
| `clubId` | Long | ID клуба |
| `ownerId` | Long | ID владельца/организатора |
| `clubNombre` | String | Название клуба (denormalized) |
| `clubDireccion` | String | Адрес клуба (denormalized) |
| `nombre` | String | Название турнира |
| `fechaInicio` | LocalDate | Дата начала |
| `horaInicio` | LocalTime | Время начала |
| `duracion` | String | Продолжительность (строка) |
| `generoFormato` | GenderFormat | MASCULINO / FEMENINO / MIXTO |
| `categoriaNivel` | String | Категория уровня (free text) |
| `tipo` | TournamentType | BRACKET / KING_OF_COURT / AMERICANO / TEAM_AMERICANO |
| `modalidad` | Modalidad | SINGLES / DOUBLES |
| `cupoMax` | Integer | Максимум участников |
| `precio` | BigDecimal | Взнос |
| `moneda` | String | Валюта (ARS, etc.) |
| `estado` | TournamentStatus | BORRADOR / REGISTRO_ABIERTO / EN_CURSO / FINALIZADO / CANCELADO |
| `deadlineCancelacion` | LocalDateTime | Дедлайн отмены регистрации |
| `infoDetallada` | String | Подробное описание |
| `contactoOrganizador` | String | Контакт организатора |
| `faqUrl` | String | Ссылка на FAQ |
| `inscritosActuales` | Integer | Текущее кол-во подтверждённых участников |
| `disponibles` | Integer | Свободных мест |
| `waitlistCount` | Integer | Кол-во в листе ожидания |

---

## GET `/api/tournaments` — Все турниры

**Аутентификация:** Не требуется (публичный).

Возвращает все турниры без фильтрации.

**Response 200 OK**: `List<TournamentDto>`

### Замечания (ревью)

> **[PERFORMANCE]** Нет пагинации. При большом числе турниров вернёт всё.  
> **[MINOR]** Непонятно, включает ли этот список удалённые (`isActive=false`) турниры — нужно явно проверить в `TournamentService.getAllTournaments()`.

---

## GET `/api/tournaments/upcoming` — Предстоящие турниры

**Аутентификация:** Не требуется.

Возвращает турниры с открытой регистрацией / в будущем.

**Response 200 OK**: `List<TournamentDto>`

---

## GET `/api/tournaments/{id}` — Турнир по ID

**Аутентификация:** Не требуется.

**Path param:** `id` — Long  
**Response 200 OK**: `TournamentDto`  
**Response 404 Not Found**

---

## GET `/api/tournaments/club/{clubId}` — Турниры клуба

**Аутентификация:** Не требуется.

**Path param:** `clubId` — Long  
**Response 200 OK**: `List<TournamentDto>`

---

## GET `/api/tournaments/status/{status}` — Турниры по статусу

**Аутентификация:** Не требуется.

**Path param:** `status` — `TournamentStatus` enum:  
`BORRADOR` | `REGISTRO_ABIERTO` | `EN_CURSO` | `FINALIZADO` | `CANCELADO`

**Response 200 OK**: `List<TournamentDto>`

---

## GET `/api/tournaments/search` — Поиск турниров

**Аутентификация:** Не требуется.

### Параметры запроса

| Param | Тип | Описание |
|-------|-----|----------|
| `clubId` | Long | Фильтр по клубу |
| `genero` | GenderFormat | MASCULINO / FEMENINO / MIXTO |
| `nivel` | String | Уровень (текст) |
| `tipo` | TournamentType | BRACKET / KING_OF_COURT / AMERICANO / TEAM_AMERICANO |
| `estado` | TournamentStatus | Статус турнира |

Все параметры опциональны.

**Response 200 OK**: `List<TournamentDto>`

---

## POST `/api/tournaments` — Создать турнир

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

### Алгоритм

1. Извлекает `Owner` из `Authentication.getPrincipal()`.
2. Валидирует тело запроса (`@Valid`).
3. Делегирует в `TournamentService.createTournament(dto, owner.getId())`.

### Контракт

**Request body** `application/json`: `TournamentDto` (без `id`, `inscritosActuales`, `disponibles`, `waitlistCount`)

**Response 200 OK**: созданный `TournamentDto` с присвоенным `id`

### Замечания (ревью)

> **[MINOR]** Возвращает `200 OK` вместо `201 Created` для операции создания ресурса. По REST-стандарту должно быть `201` с заголовком `Location`.

---

## PUT `/api/tournaments/{id}` — Обновить турнир

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

### Алгоритм

1. Извлекает `Owner`.
2. `TournamentService.updateTournament()` — проверяет, является ли owner владельцем турнира (если не `SUPER_ADMIN`).

**Response 200 OK**: обновлённый `TournamentDto`  
**Response 404 Not Found**: турнир не найден

---

## PATCH `/api/tournaments/{id}/status` — Изменить статус

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

**Query param:** `status` — `TournamentStatus`

**Response 200 OK**: `TournamentDto` с новым статусом  
**Response 404 Not Found**

### Замечания (ревью)

> **[BUG]** В вызове `TournamentService.updateTournamentStatus()` передаётся `owner.getId()` дважды (2-й и 3-й параметр). Скорее всего второй должен быть `requesterId` или аналогичным — нужно проверить сигнатуру сервисного метода.

---

## DELETE `/api/tournaments/{id}` — Удалить турнир

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

### Алгоритм

1. Если не `SUPER_ADMIN` — проверяет, что `tournament.ownerId == owner.getId()`.
2. Если нет — выбрасывает `SecurityException` (без `@ExceptionHandler` → 500).
3. Вызывает `TournamentService.deleteTournament(id)`.

**Response 204 No Content**: удалён  
**Response 404 Not Found**: не найден

### Замечания (ревью)

> **[BUG]** При отказе в доступе выбрасывается `SecurityException` (unchecked), которая не обрабатывается глобальным `@ExceptionHandler`. Клиент получит `500 Internal Server Error` вместо `403 Forbidden`.

> **[MINOR]** Дублирование авторизационной логики в контроллере: проверка `owner.getId() == tournament.ownerId` должна быть в сервисе.

---

## GET `/api/tournaments/my` — Мои турниры

**Аутентификация:** `ROLE_SUPER_ADMIN` или `ROLE_ORGANIZER`.

Возвращает турниры, созданные текущим владельцем. `SUPER_ADMIN` видит все.

**Response 200 OK**: `List<TournamentDto>`
