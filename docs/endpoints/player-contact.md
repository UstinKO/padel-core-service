# API: Контактные данные игрока — `/api/players/me/...`

**Контроллер:** `PlayerContactController`  
**Назначение:** Проверка и обновление контактных данных (телефон WhatsApp +54, Telegram username). Контактные данные обязательны для регистрации на турнир.

---

## PATCH `/api/players/me/contact`

**Кто вызывает:** JavaScript модального окна "Добавить контакт" — появляется когда игрок пытается зарегистрироваться на турнир без контакта.  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

1. Проверяет аутентификацию.
2. Извлекает `PlayerPadel` из principal (поддерживает `PlayerPadel`, `CustomOAuth2User`, `UserDetails`).
3. Валидирует: хотя бы одно поле (`telefono` или `telegramUsername`) непустое.
4. Телефон: должен начинаться с `+54` (аргентинский формат).
5. Telegram: автоматически добавляет `@` если не указан.
6. Сохраняет через `PlayerRepository.save()`.

### Контракт

**Request body** `application/json`:
```json
{
  "telefono": "+54911234567 (опционально, должен начинаться с +54)",
  "telegramUsername": "miusuario (опционально, @ добавляется авт.)"
}
```

**Response 200 OK**:
```json
{
  "success": true,
  "message": "Datos de contacto guardados",
  "telefono": "+54911234567",
  "telegramUsername": "@miusuario"
}
```

**Response 400 Bad Request**:
```json
{
  "success": false,
  "message": "Debes ingresar al menos un dato de contacto"
}
```

```json
{
  "success": false,
  "message": "El número de WhatsApp debe comenzar con +54 (Argentina)"
}
```

**Response 401 Unauthorized**:
```json
{
  "success": false,
  "message": "No autenticado" | "Usuario no encontrado"
}
```

### Замечания (ревью)

> **[BUG]** `PlayerRepository.save()` вызывается напрямую в контроллере, минуя сервисный слой. Нарушает принцип разделения ответственности. Логику обновления нужно вынести в `PlayerService`.

> **[BUG]** Существует дублирующий эндпоинт в `PlayerDashboardController` (`PATCH /players/api/players/me/contact`) с другим путём и другой валидацией (нет проверки `+54` для телефона). Два эндпоинта делают похожее, но по-разному. Нужно оставить один.

---

## GET `/api/players/me/contact-status`

**Кто вызывает:** JavaScript перед показом кнопки "Регистрация" на карточке турнира — определяет, нужно ли показывать модалку добавления контакта.  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

1. Извлекает игрока из principal.
2. Вызывает `player.hasValidContact()` — телефон `+54` ИЛИ непустой Telegram.
3. Возвращает детализированный статус.

### Контракт

**Response 200 OK**:
```json
{
  "hasContact":       true | false,
  "hasPhone":         true | false,
  "hasTelegram":      true | false,
  "telefono":         "+54911234567" | "",
  "telegramUsername": "@miusuario" | ""
}
```

**Response 401**: `{"hasContact": false}`

### Замечания (ревью)

> **[MINOR]** В контроллере есть `log.info()` с форматом `ДОБАВЬ ЛОГИРОВАНИЕ` в теле комментария — это артефакт разработки. Уровень логирования для health-check статус-запроса должен быть `DEBUG`, не `INFO` (иначе загрязняет лог при активном polling'е с фронтенда).

> **[MINOR]** Аналогичный дубль в `PlayerDashboardController` на пути `/players/api/players/me/contact-status` без детализации (`hasPhone`, `hasTelegram`, `hasContact` только).
