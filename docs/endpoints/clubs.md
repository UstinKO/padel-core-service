# API: Клубы — `/api/clubs`

**Контроллер:** `ClubController`  
**Назначение:** CRUD для клубов. Read-эндпоинты публичные, write-операции — только для `ROLE_OWNER`.

---

## Модель данных `ClubDto`

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | Long | ID клуба |
| `nombre` | String | Название |
| `direccion` | String | Адрес улица |
| `zonaCiudad` | String | Район/город |
| `telefonoContacto` | String | Телефон |
| `emailContacto` | String | Email |
| `mapaUrl` | String | Ссылка на карту |
| `websiteUrl` | String | Сайт клуба |
| `descripcion` | String | Описание |
| `logoUrl` | String | URL логотипа |
| `createdAt` | LocalDateTime | Дата создания |
| `updatedAt` | LocalDateTime | Дата обновления |
| `createdBy` | Long | ID создателя |
| `isActive` | Boolean | Активен ли клуб |
| `direccionCompleta` | String | Computed: `direccion + ", " + zonaCiudad` |

---

## GET `/api/clubs` — Все клубы

**Аутентификация:** Не требуется.

**Response 200 OK**: `List<ClubDto>`

### Замечания (ревью)

> **[MINOR]** Включает ли список неактивные клубы (`isActive=false`)? Нужно проверить `ClubService.getAllClubs()`. Если да — публичный API возвращает удалённые клубы.

---

## GET `/api/clubs/active` — Активные клубы

**Аутентификация:** Не требуется.

**Response 200 OK**: `List<ClubDto>` — только `isActive=true`

---

## GET `/api/clubs/{id}` — Клуб по ID

**Аутентификация:** Не требуется.

**Path param:** `id` — Long  
**Response 200 OK**: `ClubDto`  
**Response 404 Not Found**

---

## GET `/api/clubs/nombre/{nombre}` — Клуб по названию

**Аутентификация:** Не требуется.

**Path param:** `nombre` — строка (точное совпадение названия)

**Response 200 OK**: `ClubDto`  
**Response 404 Not Found**

### Замечания (ревью)

> **[MINOR]** Название в path param может содержать пробелы и спецсимволы — нужна URL-кодировка на клиенте. Возможна коллизия с другими сегментами пути.

---

## GET `/api/clubs/zona/{zona}` — Клубы по зоне

**Аутентификация:** Не требуется.

**Path param:** `zona` — строка района/города

**Response 200 OK**: `List<ClubDto>`

---

## GET `/api/clubs/search` — Поиск клубов

**Аутентификация:** Не требуется.

**Query param:** `q` — строка поиска

**Response 200 OK**: `List<ClubDto>` — результаты поиска по имени/описанию

### Замечания (ревью)

> **[MINOR]** Нет минимальной длины строки запроса — запрос `q=` (пустой) может вернуть все клубы.

---

## POST `/api/clubs` — Создать клуб

**Аутентификация:** `ROLE_OWNER`.

### Алгоритм

1. Получает `ClubDto` из тела запроса.
2. Присваивает `createdBy = 1L` (ЗАХАРДКОЖЕНО — см. замечание).
3. Вызывает `ClubService.createClub(clubDto, createdBy)`.

### Контракт

**Request body** `application/json`: `ClubDto` (без `id`, `createdAt`, `updatedAt`)

**Response 200 OK**: созданный `ClubDto`  
**Response 400 Bad Request** (пустое тело): при ошибке дубликата или валидации

### Замечания (ревью)

> **[BUG — КРИТИЧНО]** `createdBy = 1L` захардкожено. Нужно получать ID текущего пользователя из `Authentication`. Коммент в коде гласит _"Временно, потом заменим"_ — это висит нераскрытым.

> **[MINOR]** `RuntimeException` перехватывается с возвратом `400 Bad Request` без тела — клиент не узнает причину ошибки.

> **[MINOR]** `200 OK` вместо `201 Created`.

---

## PUT `/api/clubs/{id}` — Обновить клуб

**Аутентификация:** `ROLE_OWNER`.

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** Изменение — только `SUPER_ADMIN` (`ClubService.updateClub`). Исходное замечание: Нет проверки, является ли текущий `OWNER` владельцем именно этого клуба (`createdBy`). Любой `OWNER` может изменить чужой клуб.

**Request body** `application/json`: `ClubDto`  
**Response 200 OK**: обновлённый `ClubDto`  
**Response 404 Not Found**

---

## DELETE `/api/clubs/{id}` — Мягкое удаление

**Аутентификация:** `ROLE_OWNER`.

Устанавливает `isActive = false` (soft delete). Клуб остаётся в БД.

**Response 204 No Content**  
**Response 404 Not Found**

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** Удаление — только `SUPER_ADMIN` (`@PreAuthorize` + `ClubService.deleteClub`). Исходное замечание: Нет проверки владельца клуба — любой `OWNER` может деактивировать чужой клуб.

---

## DELETE `/api/clubs/{id}/hard` — Жёсткое удаление

**Аутентификация:** `ROLE_OWNER`.

Физически удаляет запись из БД. Может нарушить ссылочную целостность с турнирами.

**Response 204 No Content**  
**Response 404 Not Found**

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasRole('SUPER_ADMIN')")` + проверка в `ClubService.hardDeleteClub`. Исходное замечание: Жёсткое удаление должно быть доступно только `SUPER_ADMIN`, не любому `OWNER`.

> **[BUG]** Если у клуба есть связанные турниры (`tournaments_db.club_id`), удаление нарушит FK-constraint и вернёт `500`. Нужна проверка перед удалением.
