# API: Контактные данные игрока — `/api/players/me/...`

**Контроллер:** `PlayerContactController`  
**Назначение:** Проверка и обновление контактных данных (телефон WhatsApp +54, Telegram username). Контактные данные обязательны для регистрации на турнир.

---

## PATCH `/api/players/me/contact`

**Кто вызывает:** JavaScript модального окна "Добавить контакт" — появляется когда игрок пытается зарегистрироваться на турнир без контакта; клиент 1Padel.App (LFPT-0660) — та же форма внутри экрана индивидуальной записи.  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

1. Проверяет аутентификацию.
2. Извлекает `PlayerPadel` из principal (поддерживает `PlayerPadel`, `CustomOAuth2User`, `UserDetails`).
3. Валидирует: хотя бы одно поле (`telefono` или `telegramUsername`) непустое.
4. Телефон: должен начинаться с `+54` (аргентинский формат).
5. Telegram: автоматически добавляет `@` если не указан.
6. Сохраняет через `PlayerService.save()`.

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

> **[закрыто, сверено с кодом 2026-10-16]** Сохранение и проверки уникальности — через `PlayerService`.

> **[закрыто, сверено с кодом 2026-10-16]** Дубля в `PlayerDashboardController` больше нет — единственный эндпоинт `/api/players/me/contact`.

---

## GET `/api/players/me/contact-status`

**Кто вызывает:** JavaScript перед показом кнопки "Регистрация" на карточке турнира — определяет, нужно ли показывать модалку добавления контакта; клиент 1Padel.App (LFPT-0660) — та же проверка перед индивидуальной записью внутри приложения.  
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

> **[закрыто, сверено с кодом 2026-10-16]** Дубль `contact-status` удалён.

---

## GET `/api/players/me/tournaments`

**Кто вызывает:** клиент 1Padel.App (LFPT-0637) — «Мои мероприятия», только просмотр.
**Аутентификация:** `ROLE_PLAYER` (принципал-`Owner` — `403`, не пустой список).

### Алгоритм

1. Извлекает `PlayerPadel` из principal (как и остальные `/api/players/me/**` в этом контроллере).
2. `TournamentService.getMyTournamentsOverview(playerId)` — то же разбиение по `TournamentStatus`, что и в дашборде игрока (`getHistoricalAndActiveTournamentsByPlayer`): `upcoming` — турниры в `PUBLICADO`/`REGISTRO_ABIERTO`, `historical` — `CERRADO`/`FINALIZADO`/`CANCELADO`. Источник — тот же `findActiveRegistrationsByPlayerId` (фильтр по `isActive`, не по `status`), поэтому отменённые регистрации не попадают ни в один из списков.
3. Запись/отмена регистрации через этот эндпоинт не делаются — используются существующие `POST /players/tournaments/{id}/register|cancel|cancel-double`.

### Контракт

**Response 200 OK**:
```json
{
  "upcoming": [
    {
      "tournament": { "...": "TournamentDto" },
      "registrationStatus": "CONFIRMED | WAITLIST | ...",
      "waitlistPosition": 3,
      "registrationDate": "2026-10-10T12:00:00"
    }
  ],
  "historical": [ { "...": "TournamentDto" } ]
}
```

**Response 403 Forbidden** (principal — `Owner`, не игрок):
```json
{ "success": false, "message": "Solo disponible para jugadores" }
```

**Response 401 Unauthorized** — нет аутентификации (как и остальной `/api/**`).

---

## GET `/api/players/search`

**Кто вызывает:** JS страницы турнира/дашборда (парная регистрация с сайта); клиент 1Padel.App (LFPT-0707) — поиск партнёра по имени/фамилии/WhatsApp/Telegram при парной записи Americano внутри приложения.  
**Auth:** любой аутентифицированный пользователь (`anyRequest().authenticated()`)  
**Параметры:** `query` (минимум 2 символа, иначе пустой список), `tournamentId`.

Поиск кандидатов в партнёры при парной регистрации на турнир (`PlayerRepository.searchPartnerCandidates`, до 15 результатов). Исключаются текущий игрок и игроки, уже активно зарегистрированные на этот турнир. Ответ — список `PlayerResponseDto`.

Ищет по имени, фамилии, email, телефону и Telegram username активных (`activo = true`) игроков (LFPT-0679):
- **Имя/фамилия** — без учёта диакритики (Postgres `unaccent`, LFPT-0720): запрос `Max` находит `Máximo`.
- **Телефон** — нормализация форматов записи (`+54 9 11 2233-4455`, `011 2233-4455` и т.п.): запрос и сохранённый телефон сравниваются по последним 10 цифрам (без кода страны/с учётом мобильного префикса `9`) — эвристика, как и в аудите дублей (`docs/endpoints/admin-player-audit.md`), не полноценный парсер номеров. Срабатывает только если в `query` не менее 4 цифр — короче считается незначимым фрагментом и не ищется по телефону вовсе (иначе любой короткий цифровой запрос совпал бы с произвольным номером).
- **Telegram** — без учёта регистра и лидирующего `@` с обеих сторон сравнения (т.е. `@Ivan`, `ivan`, `IVAN` находят одну и ту же запись).
- Лимит на перебор контактных данных через этот эндпоинт — не отдельный механизм, а общий лимит `RateLimitFilter` на `/api/**` (60 запросов/мин на IP).

**Серверная защита от дублей при создании партнёра (LFPT-0720).** Эти подсказки — не единственный рубеж: перед тем как `POST /api/tournaments/double/{id}/register`/`register-solo`→`add-partner` создаст нового партнёра без `existingUserId`, `DoubleTournamentRegistrationService.findPartnerByContact` повторно проверяет телефон (нормализованный, как выше) и Telegram (без `@`/регистра) и, при совпадении, **переиспользует** найденного игрока вместо создания дубля — без отдельного кода ошибки, ответ выглядит как обычная успешная регистрация с уже существующим партнёром. Защита от гонки параллельных запросов с одинаковым новым контактом — `pg_advisory_xact_lock` на время транзакции (жёсткого уникального индекса на телефон нет сознательно — в проде уже есть дубли, см. `admin-player-audit.md`).
