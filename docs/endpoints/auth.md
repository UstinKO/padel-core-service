# API: Аутентификация — `/api/auth`

**Контроллер:** `AuthController`  
**Назначение:** JWT-аутентификация для мобильных/SPA клиентов. Веб-форма использует отдельный Spring Security form-login.

---

## POST `/api/auth/login`

**Кто вызывает:** Мобильный клиент / внешние интеграции.  
**Аутентификация:** Не требуется.

### Алгоритм

1. Валидирует тело запроса (`@Valid`).
2. Вызывает `AuthenticationManager.authenticate()` — делегирует в `CompositeUserDetailsService`.
3. Если аутентификация прошла, загружает `PlayerPadel` из БД по email.
4. Проверяет: email подтверждён (`emailConfirmado = true`) и аккаунт активен (`activo = true`).
5. Генерирует JWT access-токен (24ч) и refresh-токен (7д) через `JwtService`.
6. Возвращает оба токена и данные пользователя.

### Контракт

**Request body** `application/json`:
```json
{
  "email": "string (обязательно)",
  "password": "string (обязательно)"
}
```

**Response 200 OK**:
```json
{
  "accessToken":    "eyJ...",
  "refreshToken":   "eyJ...",
  "tokenType":      "Bearer",
  "expiresIn":      86400000,
  "email":          "player@example.com",
  "nombreCompleto": "Juan Pérez",
  "playerId":       42
}
```

**Response 400 Bad Request** (строка):
- `"Email o contraseña incorrectos"` — неверные credentials или пользователь не найден
- `"Por favor, confirma tu email primero"` — email не подтверждён
- `"Tu cuenta está desactivada"` — аккаунт деактивирован

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-16]** Логин идёт через `AuthenticationManager` + `CompositeUserDetailsService` (issue #124) — работает и для `Owner`. Исходное замечание: контроллер искал пользователя только в `PlayerRepository`.

> **[закрыто, сверено с кодом 2026-10-16]** `expiresIn` берётся из `JwtService.getAccessTokenExpirationMs()`.

> **[STYLE]** Все бизнес-ошибки возвращают `400 Bad Request` со строкой. Лучше использовать структурированный JSON `{"error": "...", "code": "EMAIL_NOT_CONFIRMED"}` для программного разбора на клиенте.

---

## POST `/api/auth/refresh`

**Кто вызывает:** Клиент при истечении access-токена.  
**Аутентификация:** Не требуется (сам refresh-токен — credential).

### Алгоритм

1. Принимает `refreshToken` как `@RequestParam` в query string.
2. Проверяет, что токен является refresh-типом (`isRefreshToken()`).
3. Извлекает email из токена и загружает `UserDetails` через `PlayerUserService`.
4. Валидирует подпись и TTL токена.
5. Загружает `PlayerPadel` из БД.
6. Генерирует **новую пару** токенов (rotation).
7. Возвращает структуру аналогичную `/login`.

### Контракт

**Request**: `POST /api/auth/refresh?refreshToken=eyJ...`

**Response 200 OK**: аналогично `/login` — новые `accessToken` + `refreshToken`.

**Response 400 Bad Request**:
- `"Token de refresco inválido"` — не является refresh-токеном
- `"Token de refresco expirado o inválido"` — истёк или подпись неверна
- `"Usuario no encontrado"` — игрок удалён из БД

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** Refresh-токен принимается в теле запроса (`@RequestBody RefreshTokenRequest`). Исходное замечание: Refresh-токен передаётся в query string (`@RequestParam`), что приводит к его логированию в Nginx access log и браузерной истории. Стандарт OAuth 2.0 рекомендует передавать его в теле запроса (`@RequestBody`).

> **[SECURITY — актуально на 2026-10-16]** Нет blacklist/revocation для refresh-токенов. Если токен украден, атакующий может использовать его до истечения 7 дней.

---

## GET `/api/auth/me`

**Кто вызывает:** Авторизованный клиент, чтобы получить актуальные данные своего профиля.  
**Аутентификация:** JWT Bearer token (только `PlayerPadel`).

### Алгоритм

1. Читает email из `SecurityContext`.
2. Загружает `PlayerPadel` из БД.
3. Возвращает entity напрямую.

### Контракт

**Request**: `GET /api/auth/me`  
`Authorization: Bearer <accessToken>`

**Response 200 OK**: объект `PlayerPadel` (entity напрямую, без DTO-маппинга).

**Response 401 Unauthorized**: `"No autenticado"`  
**Response 404 Not Found**: `"Usuario no encontrado"`

### Замечания (ревью)

> **[BUG/SECURITY]** Возвращается напрямую JPA-entity `PlayerPadel` вместо DTO. Это потенциально раскрывает внутренние поля: хешированный пароль (`password`), внутренние флаги, lazy-связи Hibernate. Нужно маппить через `PlayerResponseDto`.

> **[закрыто, сверено с кодом 2026-10-16]** `/me` обрабатывает и `Owner`, и `PlayerPadel` (по типу principal). Исходное замечание: эндпоинт искал только в `PlayerRepository`.
