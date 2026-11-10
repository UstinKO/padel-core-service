# API: Аутентификация — `/api/auth`

**Контроллер:** `AuthController`  
**Назначение:** JWT-аутентификация для мобильных клиентов и типизированное состояние same-origin
web-сессии для React UI. Веб-вход использует Spring Security form-login/Google OAuth2; React не
хранит JWT.

Вход через Google для клиента 1Padel.App (`GET /oauth2/authorization/google?client=app`, не часть `AuthController`) — см. [`docs/auth-flow.md` § «Вход из клиента 1Padel.App»](../auth-flow.md#вход-из-клиента-1padelapp-clientapp-lfpt-0637).

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
3. Маппит principal через `PlayerMapper` или `OwnerMapper` и возвращает DTO.

### Контракт

**Request**: `GET /api/auth/me`  
`Authorization: Bearer <accessToken>`

**Response 200 OK**: `PlayerResponseDto` или `OwnerDto` по фактическому типу principal.

**Response 401 Unauthorized**: `"No autenticado"`  
**Response 404 Not Found**: `"Usuario no encontrado"`

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-11-05]** JPA entity не сериализуется: Player и Owner
> преобразуются MapStruct-мапперами в `PlayerResponseDto`/`OwnerDto`.

> **[закрыто, сверено с кодом 2026-10-16]** `/me` обрабатывает и `Owner`, и `PlayerPadel` (по типу principal). Исходное замечание: эндпоинт искал только в `PlayerRepository`.

---

## GET `/api/auth/session`

**Кто вызывает:** same-origin React UI при загрузке shell.
**Аутентификация:** необязательна; учитываются обычная web-сессия и remember-me. JWT не выпускается.

**Response 200 OK**, `Cache-Control: no-store`:

```json
{
  "authenticated": true,
  "userType": "PLAYER",
  "id": 42,
  "fullName": "Juan Pérez",
  "email": "player@example.com",
  "roles": ["ROLE_PLAYER"],
  "clubId": null,
  "hasValidContact": true,
  "preferredLocale": "es"
}
```

- Anonymous получает `authenticated: false`, `roles: []`, остальные поля — `null`.
- Для Owner `userType: OWNER`, возвращается фактическая authority и `clubId`; искусственная
  `ROLE_OWNER` не добавляется.
- Для Player возвращаются `hasValidContact` и поддерживаемая locale `es|ru|en`.
- Пароль/хеш, телефон, Telegram, JWT, remember-me/JSESSIONID и CSRF token в JSON отсутствуют.
- Ответ инициирует выдачу cookie `XSRF-TOKEN`; React передаёт её в `X-XSRF-TOKEN` на мутациях.

Если authenticated principal не относится к `PlayerPadel` или `Owner`, ответ —
`500 application/problem+json` с полями `type`, `title`, `status`, `detail`, `instance` и
`code=SPA_AUTH_UNSUPPORTED_PRINCIPAL`, без имени Java-исключения или stack trace.

### CSRF-граница namespace

Из CSRF исключены только stateless JWT `POST /api/auth/login`, `POST /api/auth/refresh`,
Bearer-запросы и `/ws/**`. Остальные cookie-сессионные мутации в `/api/auth/**` защищены.
