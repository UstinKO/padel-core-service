# API: Регистрация и данные игроков — `/players/...`, `/players/api/...`

**Контроллер:** `PlayerPadelController`  
**Назначение:** Регистрация новых игроков (web-форма и JSON API), просмотр данных.

---

## GET `/players/registro`

**Кто вызывает:** Браузер.  
**Аутентификация:** Не требуется.  
**Тип:** Thymeleaf view.

### Алгоритм

Рендерит форму регистрации `registro.html`. Передаёт в модель:
- `registroRequest` — пустой DTO для привязки формы
- `recaptchaSiteKey` — публичный ключ reCAPTCHA для JS-виджета
- `recaptchaEnabled` — флаг (dev: false, prod: true)

---

## POST `/players/registro` (HTML Form)

**Кто вызывает:** Браузер (submit формы).  
**Аутентификация:** Не требуется.  
**Content-Type:** `application/x-www-form-urlencoded`

### Алгоритм

1. **Honeypot-проверка**: поле `website` скрыто от пользователя; если оно заполнено — бот, редирект на `/?welcome=true` (тихая нейтрализация) + штраф в rate limiter.
2. **reCAPTCHA v3**: если `recaptchaEnabled`, проверяет токен через `RecaptchaService`. Провал — возврат на форму с ошибкой + rate limit штраф.
3. **Trim** полей (имя, фамилия, email, телефон).
4. **Совпадение паролей**: если не совпадают — `sleepRandom(2..5 сек)` + редирект с flash ошибкой.
5. **Bean Validation** (`@Valid`): имя/фамилия (≥3 символа, только буквы), email (валидный формат + non-disposable домен), пароль (8–100 символов).
6. Вызывает `PlayerService.registrarJugador()` — создаёт запись, отправляет email подтверждения.
7. **Автовход**: создаёт `UsernamePasswordAuthenticationToken` и прописывает в сессию без проверки пароля ещё раз.
8. Редирект на `/?welcome=true`.

### Параметры формы

| Поле | Тип | Обязательно | Правила |
|------|-----|-------------|---------|
| `nombre` | string | да | 3–100 символов, только буквы/пробел/дефис |
| `apellido` | string | да | 3–100 символов, только буквы/пробел/дефис |
| `email` | string | да | валидный email, не disposable домен |
| `telefono` | string | нет | 8–20 символов, формат `+?[0-9\s-]{8,20}` |
| `password` | string | да | 8–100 символов |
| `confirmPassword` | string | да | должен совпадать с `password` |
| `nivelJugador` | enum | нет | значения из `Nivel` |
| `g-recaptcha-response` | string | prod | токен reCAPTCHA v3 |
| `website` | string | — | honeypot, должно быть пустым |

### Ответы

| Сценарий | Результат |
|----------|-----------|
| Успех | `redirect:/?welcome=true` |
| Honeypot | `redirect:/?welcome=true` (тихо) |
| reCAPTCHA провал | возврат на `/players/registro` с `errorMessage` |
| Пароли не совпадают | `redirect:/players/registro` + flash `errorMessage` |
| Валидация не прошла | `redirect:/players/registro` + flash `errorMessage` |
| Email занят / ошибка бизнес-логики | `redirect:/players/registro` + flash `errorMessage` |

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-16]** Блокирующего `sleepRandom`/`Thread.sleep` в коде больше нет.

> **[закрыто, сверено с кодом 2026-10-16]** IP берётся из `X-Real-IP`, который выставляет Nginx (`RateLimitFilter.extractIp`).

---

## POST `/players/api/registro` (JSON API)

**Кто вызывает:** Мобильный/SPA клиент.  
**Аутентификация:** Не требуется.  
**Content-Type:** `application/json`

### Алгоритм

Упрощённый вариант без honeypot и sleepRandom:
1. Проверяет reCAPTCHA (токен берётся из тела `recaptchaToken`).
2. Проверяет совпадение паролей.
3. Вызывает `PlayerService.registrarJugador()`.
4. Возвращает `PlayerResponseDto` с 201 Created.

### Контракт

**Request body** `application/json`:
```json
{
  "nombre":          "Juan (3-100, только буквы)",
  "apellido":        "Pérez (3-100, только буквы)",
  "email":           "juan@example.com",
  "telefono":        "+54911234567 (опционально)",
  "password":        "minLength8",
  "confirmPassword": "minLength8",
  "nivelJugador":    "PRINCIPIANTE|INTERMEDIO|AVANZADO (опционально)",
  "recaptchaToken":  "03AGdBq... (если включено)"
}
```

**Response 201 Created** — `PlayerResponseDto`:
```json
{
  "id": 123,
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan@example.com",
  "telefono": "+54911234567",
  "telegramUsername": null,
  "fechaRegistro": "15/06/2026 10:00:00",
  "emailConfirmado": false,
  "activo": true,
  "nombreCompleto": "Juan Pérez",
  "nivelJugador": null
}
```

**Response 400 Bad Request** (строка):
- reCAPTCHA провал
- Пароли не совпадают
- Email уже занят
- Другие бизнес-ошибки

### Замечания (ревью)

> **[MINOR]** Bean Validation (`@Valid`) применяется, но ошибки валидации вернут 400 от Spring без кастомного сообщения — клиент получит стандартный Spring error body, а не строку как в других случаях. Нужна единая обработка ошибок.

---

## GET `/players/api` — Список всех игроков

**Аутентификация:** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")` (сверено с кодом 2026-10-16).

### Алгоритм

Возвращает полный список всех игроков через `PlayerService.obtenerTodosJugadores()`.

**Response 200 OK**: `List<PlayerResponseDto>`

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")`. Исходное замечание: Эндпоинт публично доступен без аутентификации. Возвращает email, телефон и telegram всех игроков — это утечка персональных данных (GDPR / аргентинский Ley 25.326). Необходимо: либо закрыть аутентификацией (ROLE_OWNER), либо убрать эндпоинт, либо возвращать только публичные поля.

> **[PERFORMANCE]** Нет пагинации. При большой базе игроков вернёт всё в один запрос.

---

## GET `/players/api/{id}` — Игрок по ID

**Аутентификация:** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")` (сверено с кодом 2026-10-16).

### Контракт

**Path:** `id` — Long, ID игрока  
**Response 200 OK**: `PlayerResponseDto`  
**Response 404 Not Found**: строка с сообщением об ошибке

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")`. Исходное замечание: Аналогично `/players/api` — нет защиты. Email, телефон и Telegram конкретного игрока доступны любому без авторизации.

---

## GET `/players/api/confirmar-email`

**Аутентификация:** Не требуется (ссылка из письма).

### Алгоритм

1. Принимает `codigo` (email-confirmation token) из query param.
2. Вызывает `PlayerService.confirmarEmail(codigo)`.
3. Помечает `emailConfirmado = true` для соответствующего игрока.

### Контракт

**Request**: `GET /players/api/confirmar-email?codigo=<token>`

**Response 200 OK** (строка): `"Email confirmado exitosamente para: juan@example.com"`  
**Response 400 Bad Request** (строка): описание ошибки (токен не найден / истёк)

### Замечания (ревью)

> **[UX]** Эндпоинт возвращает plain text в браузере — это API endpoint. В реальности пользователь кликает ссылку из письма и видит сырой JSON/text в браузере. Лучше использовать отдельный View-контроллер с редиректом на красивую страницу подтверждения (уже есть `ConfirmacionEmailController`/`waitlist-confirmation.html` — разобраться, не дублируется ли логика).
