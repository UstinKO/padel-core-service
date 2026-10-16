# API: Парная регистрация — `/api/tournaments/double`

**Контроллер:** `DoubleTournamentRegistrationController`  
**Назначение:** Регистрация пары игроков на парный турнир. Двухэтапный flow с приглашением второго игрока по email.

---

## Флоу парной регистрации

```
Игрок 1: POST /register → статус PAIR_REGISTERED
           ↓ email партнёру с токеном
Игрок 2: клик по ссылке → GET /double-registration/complete?token=...  (PartnerRegistrationController, view)
           ↓ подтверждение
Игрок 2: POST /confirm?token=... → оба переходят в CONFIRMED
```

---

## POST `/api/tournaments/double/{tournamentId}/register`

**Кто вызывает:** Авторизованный игрок (Игрок 1) с дашборда или страницы турнира.  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

1. Извлекает ID текущего пользователя из principal (только `PlayerPadel`, не `Owner`).
2. Загружает `TournamentDto` по `tournamentId`.
3. Вызывает `DoubleTournamentRegistrationService.registerForDoubleTournament(tournamentDto, currentUserId, partnerDto)`:
   - Проверяет, что игрок не регистрирует сам себя.
   - Создаёт запись для Игрока 1 со статусом `PAIR_REGISTERED`.
   - Если партнёр (`partnerDto.email`) уже есть в системе — создаёт запись для него со статусом `PARTNER_INVITED`.
   - Если партнёра нет — создаёт guest-запись.
   - Отправляет email партнёру с ссылкой-токеном.
4. Возвращает результат со статусом и позицией.

### Контракт

**Path param:** `tournamentId` — Long

**Request body** `application/json`:
```json
{
  "nombre":   "Ana (обязательно)",
  "apellido": "López (обязательно)",
  "email":    "ana@example.com (опционально, если партнёр не в системе)",
  "telefono": "+54911111111 (обязательно)"
}
```

**Response 200 OK**:
```json
{
  "success":         true,
  "message":         "Registro completado",
  "status":          "PAIR_REGISTERED",
  "position":        2,
  "waitlistPosition": null
}
```

**Response 400 Bad Request**:
```json
{
  "success": false,
  "message": "Ya estás registrado en este torneo o..." | "No puedes registrarte contigo mismo" | "..."
}
```

**Response 401 Unauthorized**:
```json
{
  "success": false,
  "message": "Usuario no autenticado"
}
```

**Response 500**:
```json
{
  "success": false,
  "message": "Error al procesar el registro: ..."
}
```

### Замечания (ревью)

> **[MINOR]** Ошибки `DataIntegrityViolationException` перехватываются и возвращают generic-сообщение. Полезно для безопасности, но при отладке не даст понять причину коллизии.

> **[MINOR]** Возвращается `200 OK` вместо `201 Created`.

---

## POST `/api/tournaments/double/confirm`

**Кто вызывает:** `PartnerRegistrationController` (view-контроллер) после того, как Игрок 2 перешёл по ссылке.  
**Аутентификация:** Не требуется (токен из email является credential'ом).

### Алгоритм

1. Принимает одноразовый `token` из query param.
2. `DoubleTournamentRegistrationService.confirmPartnerRegistration(token)`:
   - Валидирует токен (не истёк, существует).
   - Помечает регистрацию Игрока 2 как `CONFIRMED`.
   - Помечает регистрацию Игрока 1 как `CONFIRMED`.
3. Возвращает DTO регистрации.

### Контракт

**Request**: `POST /api/tournaments/double/confirm?token=<uuid>`

**Response 200 OK**: `TournamentRegistrationDto` обоих игроков (либо только текущего — нужно уточнить в сервисе).

**Возможные ошибки**: выбрасываются исключения без обработки → 500 если токен не найден.

### Замечания (ревью)

> **[BUG]** Нет явной обработки ошибок: если токен истёк или не найден, `DoubleTournamentRegistrationService` выбросит исключение, которое вернётся клиенту как `500`. Нужен `try/catch` с возвратом `400` и понятным сообщением.

> **[SECURITY — актуально на 2026-10-16]** Токен передаётся в query param — попадёт в Nginx access log и браузерную историю. Для подобных одноразовых ссылок это приемлемо, но стоит убедиться, что токен достаточно длинный и энтропийный (UUID — окей).

---

## POST `/api/tournaments/double/complete`

**Кто вызывает:** Используется в нестандартном сценарии — финализация если партнёр уже в системе.  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

1. Извлекает ID текущего пользователя как ID партнёра.
2. Вызывает `DoubleTournamentRegistrationService.completePartnerRegistration(partnerId, email)`.

### Контракт

**Query param:** `email` (необязательный)

**Response 200 OK**: `TournamentRegistrationDto`

### Замечания (ревью)

> **[MINOR]** Неясна бизнес-роль этого эндпоинта по сравнению с `/confirm`. Похоже на незакрытый альтернативный флоу. Нужно задокументировать или удалить, если не используется фронтендом.
