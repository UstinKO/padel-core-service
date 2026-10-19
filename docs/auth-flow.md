# Auth Flow — padel-core-service

> Полное описание всех механизмов аутентификации и авторизации в системе.

---

## Содержание

1. [Обзор: три способа входа](#1-обзор-три-способа-входа)
2. [Цепочка фильтров Spring Security](#2-цепочка-фильтров-spring-security)
3. [CompositeUserDetailsService — поиск пользователя](#3-compositeuserdetailsservice)
4. [Form Login (сессия)](#4-form-login-сессия)
5. [JWT (API)](#5-jwt-api)
6. [Google OAuth2](#6-google-oauth2)
7. [Сброс пароля](#7-сброс-пароля)
8. [Защита от злоупотреблений при регистрации](#8-защита-при-регистрации)
9. [Rate Limiting](#9-rate-limiting)
10. [Авторизация: роли и правила доступа](#10-авторизация-роли-и-правила-доступа)
11. [Security Headers и CORS](#11-security-headers-и-cors)
12. [Матрица: кто как входит и что получает](#12-матрица-кто-как-входит)

---

## 1. Обзор: три способа входа

В системе два типа пользователей и три способа аутентификации:

| Тип пользователя | Таблица | Роль | Способ входа |
|-----------------|---------|------|--------------|
| **Игрок** (`PlayerPadel`) | `player_padel_db` | `ROLE_PLAYER` | Form Login, JWT API, Google OAuth2 |
| **Администратор** (`Owner`) | `owners_db` | `ROLE_OWNER` / `ROLE_ORGANIZER` / `ROLE_SUPER_ADMIN` | Form Login |

После успешного Form Login:
- Игрок → редирект на `/players/dashboard`
- Администратор → редирект на `/admin`

---

## 2. Цепочка фильтров Spring Security

Каждый входящий HTTP-запрос проходит через фильтры в строго определённом порядке:

```
HTTP Request
     │
     ▼
┌──────────────────────────────────────────────────────┐
│  1. RateLimitFilter                                  │
│     Проверяет IP по Bucket4j. Если превышен лимит   │
│     или IP заблокирован — возвращает 429, дальше     │
│     запрос не идёт.                                  │
│     Статические ресурсы (/css/, /js/, /images/) и   │
│     whitelisted IP — пропускаются без проверки.     │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│  2. JwtAuthenticationFilter                          │
│     Ищет заголовок Authorization: Bearer <token>    │
│     Если есть и валиден — устанавливает              │
│     SecurityContext. Без токена — просто пропускает. │
│     Actuator endpoints (/actuator/**) пропускаются. │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│  3. UsernamePasswordAuthenticationFilter             │
│     (только POST /login)                             │
│     Spring Security form login.                     │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│  4. OAuth2LoginAuthenticationFilter                  │
│     (только /login/oauth2/code/*)                   │
│     Обрабатывает callback от Google.                │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│  5. AuthorizationFilter                              │
│     Проверяет права на основе матрицы в             │
│     SecurityConfig.authorizeHttpRequests()          │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
                  Controller
```

> **Важно:** `RateLimitFilter` стоит **перед** `JwtAuthenticationFilter`. Это значит, что запрос с невалидным IP блокируется ещё до любой логики аутентификации.

---

## 3. CompositeUserDetailsService

Spring Security использует единственный `UserDetailsService` для загрузки пользователя по email. Поскольку у нас два типа пользователей в разных таблицах, реализован `CompositeUserDetailsService`:

```
loadUserByUsername(email)
        │
        ▼
  OwnerUserService.loadUserByUsername(email)
        │
   найден? ──YES──► вернуть Owner (роль из поля role)
        │
       NO
        │
        ▼
  PlayerUserDetailsService.loadUserByUsername(email)
        │
   найден? ──YES──► вернуть PlayerPadel (роль ROLE_PLAYER)
        │
       NO
        │
        ▼
  throw UsernameNotFoundException
```

**Следствие:** если у игрока и администратора одинаковый email — войдёт администратор, потому что `OwnerUserService` проверяется первым.

---

## 4. Form Login (сессия)

Используется для входа через браузер. После успеха создаётся сессия Spring (`JSESSIONID` cookie).

### Флоу

```
Браузер                         Spring Security
   │                                 │
   │─── POST /login ─────────────────►│
   │    username=email                │
   │    password=pass                 │
   │    (+ remember-me cookie опц.)   │
   │                                  │
   │                    UsernamePasswordAuthenticationFilter
   │                                  │
   │                    CompositeUserDetailsService.loadUserByUsername(email)
   │                    OwnerUserService → (не найден)
   │                    PlayerUserDetailsService → PlayerPadel
   │                                  │
   │                    DaoAuthenticationProvider.additionalAuthenticationChecks()
   │                    BCrypt.matches(password, passwordHash)
   │                                  │
   │                    isEnabled() проверка:
   │                    activo = true AND email_confirmado = true
   │                                  │
   │              ┌──── success ───────┤
   │              │                   │
   │              │   customAuthenticationSuccessHandler:
   │              │   если роль ADMIN → /admin
   │              │   иначе           → /players/dashboard
   │              │                   │
   │◄─── 302 redirect ────────────────┤
   │                                  │
   │─── GET /players/dashboard ───────►│
   │    Cookie: JSESSIONID=...         │
   │◄─── 200 OK ──────────────────────┤
```

### Что происходит при неуспехе

| Причина | Редирект |
|---------|---------|
| Неверный пароль | `/login?error=true` |
| Email не подтверждён (`DisabledException`) | `/login?error=not_confirmed` |
| Аккаунт заблокирован (`LockedException`) | `/login?error=not_confirmed` |

### Remember-me

- Включается через чекбокс на форме входа.
- Токен живёт **24 часа** (`tokenValiditySeconds = 86400`).
- Использует `CompositeUserDetailsService` для восстановления пользователя по токену.
- При логауте cookie `JSESSIONID` удаляется, сессия инвалидируется.

### Выход

```
POST /logout → сессия инвалидирована, JSESSIONID удалён → редирект на /
```

---

## 5. JWT (API)

Используется мобильными клиентами и SPA. Stateless — сессия не создаётся.

### Структура токена

```json
// Access Token (HS256, HMAC-SHA256)
{
  "sub": "player@email.com",   // email (subject)
  "userId": 42,                // ID из player_padel_db
  "fullName": "Juan Perez",    // имя и фамилия
  "role": "ROLE_PLAYER",       // роль
  "tokenType": "access",       // маркер типа
  "iat": 1700000000,           // issued at (Unix timestamp)
  "exp": 1700086400            // expires: iat + 24ч
}

// Refresh Token (HS256)
{
  "sub": "player@email.com",
  "userId": 42,
  "role": "ROLE_PLAYER",
  "tokenType": "refresh",      // маркер типа — важен для isRefreshToken()
  "iat": 1700000000,
  "exp": 1700604800            // expires: iat + 7 дней
}
```

**Ключ подписи:** HMAC-SHA256, секрет из env-переменной `JWT_SECRET`.  
**TTL:** access — `JWT_EXPIRATION` (дефолт 86 400 000 мс = **24 ч**), refresh — `JWT_REFRESH_EXPIRATION` (дефолт 604 800 000 мс = **7 дней**).

---

### Флоу: получение токенов

```
Client                          /api/auth/login
  │                                   │
  │─── POST /api/auth/login ──────────►│
  │    { "email": "...",               │
  │      "password": "..." }           │
  │                                    │
  │                    AuthenticationManager.authenticate()
  │                    CompositeUserDetailsService → PlayerPadel
  │                    BCrypt проверка пароля
  │                    проверка emailConfirmado и activo
  │                                    │
  │◄─── 200 OK ───────────────────────┤
  │    {                               │
  │      "accessToken":  "eyJ...",     │
  │      "refreshToken": "eyJ...",     │
  │      "email":        "...",        │
  │      "nombreCompleto": "...",      │
  │      "playerId": 42,               │
  │      "expiresIn": 86400000         │
  │    }                               │
```

---

### Флоу: использование токена

```
Client                          JwtAuthenticationFilter
  │                                   │
  │─── GET /api/tournaments ──────────►│
  │    Authorization: Bearer eyJ...    │
  │                                    │
  │                    extractUsername(token) → email
  │                    SecurityContext пуст?
  │                    compositeUserDetailsService.loadUserByUsername(email)
  │                    validateToken(token, userDetails)
  │                    ─── устанавливает Authentication в SecurityContext
  │                                    │
  │                              Controller
  │◄─── 200 OK ───────────────────────┤
```

---

### Флоу: обновление токена

```
Client                          /api/auth/refresh
  │                                   │
  │─── POST /api/auth/refresh ────────►│
  │    body: {"refreshToken": "eyJ..."}│
  │                                    │
  │                    isRefreshToken() → проверяет claim "tokenType" = "refresh"
  │                    validateToken() → проверяет подпись и срок
  │                    loadUserByUsername(email)
  │                    генерирует новую пару: accessToken + refreshToken
  │                                    │
  │◄─── 200 OK (новая пара токенов) ──┤
```

> **Rotation:** при каждом refresh выдаётся новый refresh токен. Старый не инвалидируется явно — он просто перестаёт использоваться. Если нужна строгая ротация с черным списком — требует доработки (например, хранить `jti` в Redis).

---

### Ошибки JWT

| Ситуация | Поведение |
|----------|-----------|
| Нет заголовка `Authorization` | Фильтр пропускает запрос, контроллер получает анонимный SecurityContext |
| Токен истёк | `JwtException` → `SecurityContext` не устанавливается → 403/401 от Spring Security |
| Токен с невалидной подписью | То же |
| Передан refresh вместо access | `JwtAuthenticationFilter` проверяет `tokenType` (`jwtService.isRefreshToken(jwt)`) — `SecurityContext` не устанавливается, запрос идёт дальше анонимным → 401/403 от Spring Security (закрыто LFPT-0608) |
| Пользователь деактивирован | `loadUserByUsername` вернёт пользователя, но `isEnabled() = false` → `validateToken = false` → 403 |

---

## 6. Google OAuth2

Только для **игроков**. Администраторы входят исключительно через форму.

### Флоу

```
Браузер                      Spring Security          Google
  │                               │                     │
  │─── GET /oauth2/authorization/google ──────────────►│
  │                               │ redirect_uri,       │
  │                               │ scope=openid email  │
  │◄────────── redirect ──────────┤                     │
  │                                                     │
  │─── пользователь логинится в Google ────────────────►│
  │◄────────── code=... ──────────────────────────────-─┤
  │                                                     │
  │─── GET /login/oauth2/code/google?code=... ─────────►│
  │                               │                     │
  │                    OAuth2LoginAuthenticationFilter   │
  │                    DefaultOAuth2UserService.loadUser()
  │                    ──── обменивает code на access_token у Google
  │                    ──── загружает профиль (email, given_name, family_name)
  │                               │
  │                    CustomOAuth2UserService.loadUser()
  │                    OAuth2UserManagementService.createOrUpdateUser()
  │                               │
  │               Пользователь существует?
  │               ┌── YES ──────────────────────────────────────┐
  │               │ player.emailConfirmado = true (если не был) │
  │               │ player.oauth2User = true                    │
  │               │ player.provider = "google"                  │
  │               └─────────────────────────────────────────────┘
  │               ┌── NO ───────────────────────────────────────┐
  │               │ создаём PlayerPadel:                        │
  │               │   nombre = given_name                       │
  │               │   apellido = family_name                    │
  │               │   passwordHash = BCrypt(random 32 bytes)    │
  │               │   emailConfirmado = true                    │
  │               │   oauth2User = true                         │
  │               │   provider = "google"                       │
  │               └─────────────────────────────────────────────┘
  │                               │
  │                    OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
  │                    JwtService.generateToken() → accessToken
  │                    SecurityContext.setAuthentication()
  │                    response.setHeader("Authorization", "Bearer " + token)
  │                               │
  │◄─── 302 redirect /players/dashboard ──────────────┤
```

### Особенности

- OAuth2-пользователь **не может** войти через форму паролем — у него `passwordHash` сгенерирован случайно, пользователь его не знает.
- Email считается подтверждённым автоматически (`emailConfirmado = true`).
- JWT выдаётся сразу в хедере ответа (`Authorization: Bearer ...`). Браузер его видит, но в cookie не сохраняется — в текущей реализации сессионный cookie тоже устанавливается Spring Security. Фактически после OAuth2 работает **и сессия, и токен**.
- При ошибке OAuth2 → редирект на `/login?error=oauth2`.

### Вход из клиента 1Padel.App (`?client=app`, LFPT-0637)

Клиент, размещённый на `/app/` (исходники — `mobile-app/`, сборка — `static/app/`, LFPT-0638), запускает тот же флоу с одним отличием: `GET /oauth2/authorization/google?client=app`.

- `OAuth2AppLoginOriginFilter` (до `OAuth2AuthorizationRequestRedirectFilter`) видит `client=app` на этом конкретном URL и сохраняет флаг в `HttpSession` — **не** принимает произвольный `redirect_uri` от клиента, чтобы не открыть open redirect.
- `OAuth2AuthenticationSuccessHandler` после успешного входа видит флаг, снимает его и редиректит не на `/players/dashboard`, а на `/app/#accessToken=...&refreshToken=...&expiresIn=...` — токены в **fragment** URL (после `#`), не в query: fragment не уходит на сервер и не попадает в access-логи/`Referer`.
- Сессия и remember-me cookie выставляются как обычно (общий код для обоих случаев) — веб-вход без `client=app` не меняется.
- Ошибка OAuth2 для входа из приложения обрабатывается так же, как и для веба (`/login?error=oauth2`) — отдельной обработки на стороне клиента пока нет.
- Клиент при загрузке `/app/` читает токены из fragment и сразу убирает их из адресной строки (`history.replaceState`). Остальные запросы клиента: `POST /api/auth/login`, `POST /api/auth/refresh`, `GET /api/auth/me`, `GET /api/players/me/tournaments` — по Bearer-токену, без cookie сайта (`credentials: omit`). Анонимный/просроченный `/api/**` отвечает редиректом на `/login`, клиент считает его истёкшей сессией.
- Refresh-токен переживает закрытие/повторное открытие вкладки (LFPT-0646): на native — `expo-secure-store`, на вебе — `localStorage` (только за гейтом `secureAuthContext`, т.е. HTTPS или `localhost`/`127.0.0.1`, как и сам вход). Access-токен не персистится, только refresh — на старте клиент меняет его на новый access через `POST /api/auth/refresh`. Логаут удаляет сохранённый refresh-токен из хранилища; сервер при этом остаётся stateless — ничего не ревокирует (см. ниже).

---

## 7. Сброс пароля

Только для игроков с обычным паролем. OAuth2-пользователи технически тоже могут сбросить — это создаст им пароль, которого раньше не было (возможность входа через форму появится).

### Флоу

```
Игрок                    PasswordResetController / Service       Email
  │                                  │                             │
  │─── POST /recuperar-password/solicitar ───────────────────────►│
  │    { "email": "..." }            │                             │
  │                                  │                             │
  │                    playerRepository.findByEmail()              │
  │                    ┌── не найден → return true (защита от перебора)
  │                    │                                           │
  │                    tokenRepository.deleteByPlayer(player)  ← удаляем старые
  │                    token = UUID.randomUUID()
  │                    expiryDate = now + 24ч
  │                    PasswordResetToken.save()
  │                                  │                             │
  │                    EmailService.sendPasswordResetEmail() ─────►│
  │                                  │  ссылка: /recuperar-password?token=UUID
  │◄─── 200 { success: true } ───────┤                             │
  │                                                                │
  │  [пользователь переходит по ссылке из письма]                  │
  │                                                                │
  │─── GET /recuperar-password?token=UUID ──────────────────────►  │
  │                                  │
  │                    validateToken() → !used AND !expired
  │                    ┌── невалиден → return "error" (страница ошибки)
  │                    │
  │◄─── 302 redirect /?token=UUID ──┤
  │     (JS на главной откроет модальное окно)
  │                                  │
  │─── POST /recuperar-password/confirmar ──────────────────────►  │
  │    { "token": "UUID",            │
  │      "newPassword": "...",       │
  │      "confirmPassword": "..." }  │
  │                                  │
  │                    пароли совпадают?
  │                    токен валиден и не использован?
  │                    player.passwordHash = BCrypt(newPassword)
  │                    resetToken.used = true
  │                    playerRepository.save()
  │                    tokenRepository.save()
  │                                  │
  │◄─── 200 { success: true } ───────┤
```

### Важные детали

- Токен — `UUID.randomUUID()` (строка, хранится в `password_reset_tokens_db`).
- Срок действия — **24 часа** (`TOKEN_EXPIRATION_HOURS = 24`).
- Старые токены удаляются при каждом новом запросе сброса.
- При запросе несуществующего email — всегда возвращается `success: true` (защита от user enumeration).
- После использования токен помечается `used = true`, повторное использование невозможно.

---

## 8. Защита при регистрации

Регистрация новых игроков защищена несколькими слоями, которые срабатывают **последовательно**:

```
POST /players/registro
        │
        ▼
┌───────────────────────────────────┐
│  1. RateLimitFilter               │
│  3 запроса / минуту с одного IP   │
│  429 → стоп                       │
└──────────────────┬────────────────┘
                   │
                   ▼
┌───────────────────────────────────┐
│  2. Honeypot                      │
│  Скрытое поле <input name=website>│
│  Боты заполняют → запрос отклонён │
│  + IP +2 к счётчику подозрений    │
└──────────────────┬────────────────┘
                   │
                   ▼
┌───────────────────────────────────┐
│  3. reCAPTCHA v3                  │
│  Проверяем g-recaptcha-response   │
│  score < порога → отклонён        │
│  + IP помечается как подозрит.    │
└──────────────────┬────────────────┘
                   │
                   ▼
┌───────────────────────────────────┐
│  4. @NoBotPattern / BotPattern    │
│  Валидация через аннотацию        │
│  на полях RegistroRequestDto      │
└──────────────────┬────────────────┘
                   │
                   ▼
┌───────────────────────────────────┐
│  5. @ValidDomain                  │
│  Проверка на disposable-email     │
│  домены (mailinator, tempmail...) │
└──────────────────┬────────────────┘
                   │
                   ▼
┌───────────────────────────────────┐
│  6. Bean Validation               │
│  @Valid на RegistroRequestDto:    │
│  формат email, длина полей,       │
│  совпадение паролей               │
└──────────────────┬────────────────┘
                   │
                   ▼
             PlayerService.registrarJugador()
```

**Защита от timing attacks:** при любой ошибке (несовпадение паролей, невалидные данные) контроллер делает `Thread.sleep(2–5 секунд random)` перед ответом.

**Счётчик подозрений:** каждый запрос на регистрацию +1, каждый провал +2. При счётчике ≥ 5 за 15 минут IP получает статус `BLOCKED` — одна попытка на каждые 15 минут.

---

## 9. Rate Limiting

Реализован через `RateLimitFilter` (Bucket4j + Guava Cache).

### Лимиты по категориям

| Категория | Пути | Лимит | Алгоритм |
|-----------|------|-------|----------|
| `AUTH` | `/login`, `/api/auth/**`, `/oauth2/**`, `/login/oauth2/**` | 10 req / мин | classic |
| `REGISTER` | `/players/registro`, `/api/players/registro`, `/recuperar-password/**`, `/double-registration/**` | 3 req / мин | classic |
| `BLOCKED` | IP с ≥5 провалами за 15 мин | 1 req / 15 мин | classic |
| `API` | `/api/**` (всё остальное) | 60 req / мин | greedy |
| `GENERAL` | Всё остальное | 200 req / мин | greedy |

**Статические ресурсы** (`/css/`, `/js/`, `/images/`, `/webjars/`, `favicon.ico`, `robots.txt`) — не учитываются.

**Whitelisted IP** (разработчики / владелец) — полностью обходят rate limiting. Список задаётся конфигурацией `app.security.whitelisted-ips` (переменная окружения на сервере) и не публикуется.

### Извлечение IP (приоритет)

```
1. CF-Connecting-IP  (Cloudflare)
2. X-Forwarded-For   (первый в списке)
3. X-Real-IP         (Nginx)
4. request.getRemoteAddr()
```

### Ответ при превышении

HTTP `429 Too Many Requests` + `Retry-After: 60`:

```json
{
  "success": false,
  "message": "Demasiados intentos. Intenta en 1 minuto.",
  "retryAfter": 60
}
```

---

## 10. Авторизация: роли и правила доступа

### Роли

| Роль | Тип пользователя | Права |
|------|-----------------|-------|
| `ROLE_PLAYER` | `PlayerPadel` | Дашборд, профиль, регистрация на турниры |
| `ROLE_ORGANIZER` | `Owner` | Управление своими турнирами, клубами, игроками |
| `ROLE_OWNER` | `Owner` | То же, что ORGANIZER (legacy, оба проверяются) |
| `ROLE_SUPER_ADMIN` | `Owner` | Полный доступ ко всему |

### Матрица доступа (SecurityConfig)

| Путь | Доступ |
|------|--------|
| `/`, `/torneos`, `/clubs/**`, `/ranking`, `/login` | Публичный |
| `/players/registro`, `/api/auth/**` | Публичный |
| `/recuperar-password/**`, `/double-registration/complete` | Публичный |
| `/waitlist/confirm`, `/oauth2/**` | Публичный |
| `/ws/**`, `/actuator/health/**`, `/actuator/prometheus`, `/swagger-ui/**` | Публичный (prometheus снаружи закрыт Nginx, LFPT-0604) |
| `/css/**`, `/js/**`, `/images/**` | Публичный |
| `GET /api/king-of-court/tournaments/*/state` | Публичный |
| `GET /api/king-of-court/tournaments/*/ranking` | Публичный |
| `GET /api/king-of-court/ping` | Публичный |
| `/players/dashboard`, `/perfil/**`, `/players/lista` | Аутентифицированный (`authenticated()`) |
| `/admin/**` | `ROLE_OWNER` **или** `ROLE_SUPER_ADMIN` **или** `ROLE_ORGANIZER` |
| `/api/**` (остальное) | Аутентифицированный |
| `anyRequest()` | Аутентифицированный |

### Method-level security (`@PreAuthorize`)

Используется в REST API контроллерах поверх URL-правил:

```java
// TournamentController
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
public ResponseEntity<TournamentDto> createTournament(...)

// AmericanoApiController
@PreAuthorize("hasRole('ADMIN') or @securityService.isRoundOrganizer(#roundId)")
public ResponseEntity<AmericanoRoundDto> startRound(...)
```

### Проверка владельца внутри контроллеров

Для ORGANIZER дополнительно проверяется, что турнир/клуб принадлежит этому Owner:

```java
if (!owner.isSuperAdmin()) {
    if (!tournament.getOwnerId().equals(owner.getId())) {
        throw new SecurityException("No tienes permiso para editar este torneo");
    }
}
```

---

## 11. Security Headers и CORS

### HTTP Security Headers

| Заголовок | Значение | Защищает от |
|-----------|----------|-------------|
| `X-Frame-Options` | `DENY` | Clickjacking |
| `X-Content-Type-Options` | `nosniff` | MIME sniffing |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Downgrade attacks |
| `Content-Security-Policy` | (см. ниже) | XSS, data injection |

**CSP политика:**
```
default-src 'self';
script-src 'self' 'unsafe-inline' cdnjs.cloudflare.com fonts.googleapis.com 
           www.google.com www.gstatic.com cdn.jsdelivr.net;
style-src 'self' 'unsafe-inline' cdnjs.cloudflare.com fonts.googleapis.com cdn.jsdelivr.net;
font-src 'self' fonts.gstatic.com cdnjs.cloudflare.com cdn.jsdelivr.net;
img-src 'self' data: https:;
connect-src 'self' www.google.com;
frame-src www.google.com;
frame-ancestors 'none';
```

### CORS

Разрешённые origins:

```
http://localhost:8080
http://localhost:8081
http://localhost:3000
https://epadel.org
https://www.epadel.org
```

Разрешённые методы: `GET, POST, PUT, DELETE, OPTIONS`  
Credentials: `allowCredentials = true`  
Preflight cache: `maxAge = 3600s`  
Exposed headers: `Authorization`

### CSRF

**Отключён** (`AbstractHttpConfigurer::disable`). Причина: API использует JWT Bearer токены, а не cookie-based auth. Для форм (form login) это допустимо, так как форма логина защищена rate limiting и CORS. При переходе на полноценный SPA — стоит рассмотреть включение CSRF через Double-Submit Pattern.

### Управление сессией

```java
sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED
```

Сессия создаётся только если нужна — при Form Login и OAuth2. JWT-запросы сессии не создают (stateless).

---

## 12. Матрица: кто как входит

| Пользователь | Способ | Что получает | Последующие запросы |
|-------------|--------|--------------|---------------------|
| Игрок (браузер) | Form Login | `JSESSIONID` cookie | Cookie в каждом запросе |
| Игрок (API-клиент) | `POST /api/auth/login` | `accessToken` + `refreshToken` | `Authorization: Bearer <token>` |
| Игрок (Google) | OAuth2 redirect | `JSESSIONID` + `Authorization` header | Cookie (браузер), header (API) |
| Администратор | Form Login | `JSESSIONID` cookie | Cookie в каждом запросе |
| Администратор | `POST /api/auth/login` | ⚠️ Не поддерживается — `AuthController` загружает только из `PlayerRepository` | — |

> **Критическая заметка:** `AuthController` (`/api/auth/login`) использует `PlayerRepository.findByEmail()` напрямую — администраторы **не могут** получить JWT через этот эндпоинт. При декомпозиции или добавлении мобильного приложения для администраторов это нужно учесть.

### Lifecycle токенов

```
Регистрация
     │
     ▼
Email не подтверждён → LOGIN BLOCK
     │
     ▼ (подтверждает email)
isEnabled() = true
     │
     ▼
POST /api/auth/login → access (24ч) + refresh (7д)
     │
     ├── через 24ч access истёк
     │        │
     │        ▼
     │   POST /api/auth/refresh → новый access + новый refresh
     │
     ├── через 7д refresh истёк
     │        │
     │        ▼
     │   Нужен повторный логин
     │
     └── Пользователь деактивирован (activo=false)
              │
              ▼
         isEnabled()=false → validateToken()=false → 403 на следующем запросе
```
