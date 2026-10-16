# API: Сброс пароля — `/recuperar-password`

**Контроллер:** `PasswordResetController`  
**Назначение:** Двухэтапный сброс пароля по email: запрос ссылки → переход по ссылке → смена пароля.

---

## Флоу

```
Пользователь: POST /recuperar-password/solicitar (email)
               → письмо с токеном
Пользователь: GET /recuperar-password?token=...
               → редирект на /?token=...
               → JS показывает модалку смены пароля
Пользователь: POST /recuperar-password/confirmar (token + новый пароль)
               → пароль обновлён
```

---

## POST `/recuperar-password/solicitar`

**Аутентификация:** Не требуется.  
**Content-Type:** `application/json`

### Алгоритм

1. Получает email из тела запроса.
2. Вызывает `PasswordResetService.requestPasswordReset(email, baseUrl)`.
3. Если email существует — создаёт `PasswordResetToken` (UUID, TTL обычно 1 час) и отправляет письмо `recuperar-password.html`.
4. **Всегда возвращает успех** — независимо от того, найден email или нет (защита от email enumeration).

### Контракт

**Request body** `application/json`:
```json
{
  "email": "juan@example.com"
}
```

**Response 200 OK**:
```json
{
  "success": true,
  "message": "Si el email existe, recibirás instrucciones para recuperar tu contraseña"
}
```

**Response 200 OK** (неожиданная внутренняя ошибка):
```json
{
  "success": false,
  "message": "Error al procesar la solicitud"
}
```

### Замечания (ревью)

> **[SECURITY — актуально на 2026-10-16]** Хорошо реализованная защита от email enumeration — всегда возвращает одинаковое сообщение. Но `success: false` при реальной ошибке (например, `EmailService` недоступен) помогает злоумышленнику отличить ошибку от "email не найден". Стоит унифицировать — возвращать всегда `success: true` с одним сообщением.

> **[закрыто, сверено с кодом 2026-10-16]** `/recuperar-password/**` входит в группу REGISTER `RateLimitFilter`.

> **[закрыто, сверено с кодом 2026-10-16]** Ссылка строится из конфигурации `app.base-url` (`PasswordResetService`).

---

## GET `/recuperar-password?token=...`

**Аутентификация:** Не требуется.

### Алгоритм

1. Принимает `token` из query param.
2. Валидирует токен через `PasswordResetService.validateToken(token)`.
3. Если токен валиден — редирект на `/?token={token}`. JavaScript на главной странице читает query param и показывает модальное окно.
4. Если токен невалиден/истёк — рендерит `error.html` с сообщением.

**Response**: redirect `/?token=...` или view `error`

### Замечания (ревью)

> **[SECURITY — актуально на 2026-10-16]** Токен передаётся в URL redirect'а — сохраняется в браузерной истории, Referer-заголовках, Nginx access log. После использования токен должен быть инвалидирован немедленно (не при переходе на `/?token=...`, а при `POST /confirmar`). Нужно убедиться что токен одноразовый.

---

## POST `/recuperar-password/confirmar`

**Аутентификация:** Не требуется.  
**Content-Type:** `application/json`

### Алгоритм

1. Принимает `PasswordResetConfirm` с токеном и новым паролем.
2. Вызывает `PasswordResetService.resetPassword(dto)`:
   - Находит `PasswordResetToken` по значению.
   - Проверяет что не истёк.
   - Хеширует новый пароль через BCrypt.
   - Обновляет `PlayerPadel.password`.
   - Инвалидирует/удаляет токен.

### Контракт

**Request body** `application/json** (`PasswordResetConfirm`):
```json
{
  "token":          "uuid-токен-из-ссылки",
  "newPassword":    "новыйПароль (минимум 8 символов)",
  "confirmPassword": "подтверждение"
}
```

**Response 200 OK**:
```json
{
  "success": true,
  "message": "Contraseña actualizada correctamente"
}
```

**Response 200 OK** (ошибка):
```json
{
  "success": false,
  "message": "Error al actualizar la contraseña. El enlace puede haber expirado"
}
```

### Замечания (ревью)

> **[MINOR]** Ошибки возвращают `200 OK` с `success: false` вместо `400 Bad Request`. Некорректно с точки зрения HTTP-семантики.

> **[MINOR]** Нет явной валидации совпадения `newPassword == confirmPassword` в контроллере — нужно проверить, есть ли она в `PasswordResetService` или `PasswordResetConfirm`.
