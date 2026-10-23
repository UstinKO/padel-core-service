# API: Парная регистрация — `/api/tournaments/double`

**Контроллер:** `DoubleTournamentRegistrationController`  
**Назначение:** Регистрация пары игроков на парный турнир.

**LFPT-0687:** игрок, регистрирующий пару, уже сам договорился с партнёром — регистрация
завершается немедленно (статус по занятости турнира, `CONFIRMED`/`WAITLIST`), без ожидания
email-подтверждения от партнёра и без действия координатора. Партнёр без аккаунта создаётся
сразу же (контакт — телефон и/или Telegram; email у партнёра теперь не обязателен и в форме
регистрации не запрашивается, см. `tournament-details.html`). Описанный ниже двухэтапный
email-flow (`POST /confirm`) остаётся только как легаси-путь для регистраций, созданных до
этого изменения.

---

## Флоу парной регистрации

```
Игрок 1: POST /register (партнёр — контакт по телефону/Telegram, без аккаунта)
           ↓ аккаунт партнёра создаётся сразу, обе регистрации — CONFIRMED или WAITLIST
           ↓ (по занятости турнира, как и для партнёра с уже существующим аккаунтом)
Готово — ожидания не требуется.

Легаси (регистрации, созданные до LFPT-0687): Игрок 1 → статус PARTNER_INVITED с токеном
           ↓ email партнёру со ссылкой
Игрок 2: клик по ссылке → GET /double-registration/complete?token=...  (PartnerRegistrationController, view)
           ↓ подтверждение
Игрок 2: POST /confirm?token=... → пара встаёт в конец очереди резерва (LFPT-0668)
```

---

## POST `/api/tournaments/double/{tournamentId}/register`

**Кто вызывает:** Авторизованный игрок (Игрок 1) с дашборда или страницы турнира; клиент 1Padel.App (LFPT-0707) — парная запись Americano/Team Americano внутри приложения, тот же контракт (партнёр — существующий игрок по ID либо новые данные без email, LFPT-0687).  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

1. Извлекает ID текущего пользователя из principal (только `PlayerPadel`, не `Owner`).
2. Загружает `TournamentDto` по `tournamentId`.
3. Вызывает `DoubleTournamentRegistrationService.registerForDoubleTournament(tournamentDto, currentUserId, partnerDto)`:
   - Проверяет, что игрок не регистрирует сам себя.
   - Если партнёр уже есть в системе (найден по `existingUserId`/email/телефону) — создаёт записи для обоих игроков немедленно, статус по занятости турнира (`CONFIRMED`/`WAITLIST`).
   - Если партнёра нет — **создаёт его аккаунт сразу** (`nombre`/`apellido`/`telefono`/`telegramUsername`/`email` из запроса, уровень `SIN_ESPECIFICAR`) и регистрирует пару тем же путём, что и для уже существующего партнёра (LFPT-0687) — без промежуточного статуса и без ожидания подтверждения.
4. Возвращает результат со статусом и позицией.

### Контракт

**Path param:** `tournamentId` — Long

**Request body** `application/json`:
```json
{
  "nombre":           "Ana (обязательно)",
  "apellido":         "López (обязательно)",
  "email":            "ana@example.com (опционально — партнёру email больше не обязателен, LFPT-0687)",
  "telefono":         "+54911111111 (опционально, если partnerDto.existingUserId указан)",
  "telegramUsername": "ana_padel (опционально, альтернатива/дополнение к телефону, LFPT-0687)"
}
```

**Response 200 OK** (LFPT-0687 — пара сформирована сразу, `status` уже финальный):
```json
{
  "success":         true,
  "message":         "Registro completado",
  "status":          "CONFIRMED",
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

## POST `/api/tournaments/double/confirm` (легаси, LFPT-0687)

**С LFPT-0687 новые регистрации этим путём не создаются** — `registerForDoubleTournament`,
`addPartnerToSoloRegistration` и `replacePlayerInPair` регистрируют партнёра без аккаунта сразу
же (см. выше). Этот endpoint остаётся рабочим только для регистраций в статусе
`PARTNER_INVITED`, созданных до деплоя этого изменения — им всё ещё нужно пройти по ссылке
из уже отправленного письма, чтобы не остаться зависшими навсегда.

**Кто вызывает:** `PartnerRegistrationController` (view-контроллер) после того, как Игрок 2 перешёл по ссылке.  
**Аутентификация:** Не требуется (токен из email является credential'ом).

### Алгоритм

1. Принимает одноразовый `token` из query param.
2. `DoubleTournamentRegistrationService.confirmPartnerRegistration(token)`:
   - Валидирует токен (не истёк, существует).
   - **LFPT-0668**: обе регистрации (Игрок 1 и Игрок 2) всегда ставятся в конец очереди резерва (`WAITLIST`, позиция = максимум + 1), затем вызывается `TournamentService.processWaitlistForTournament` — он подтверждает пару (`CONFIRMED`) немедленно, только если место свободно и очередь до неё дошла. Раньше при наличии свободного места пара подтверждалась сразу, без учёта уже ожидающих в резерве полностью сформированных пар — исправлено, чтобы вновь подтверждённая пара не обходила их очередь.
3. Возвращает DTO регистрации (с её итоговым статусом — `CONFIRMED` или `WAITLIST`).

### Контракт

**Request**: `POST /api/tournaments/double/confirm?token=<uuid>`

**Response 200 OK**: `TournamentRegistrationDto` обоих игроков (либо только текущего — нужно уточнить в сервисе).

**Возможные ошибки**: выбрасываются исключения без обработки → 500 если токен не найден.

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-16]** `TournamentRegistrationException` → 400 с сообщением, прочие ошибки → 500 с `success: false`.

> **[SECURITY — актуально на 2026-10-16]** Токен передаётся в query param — попадёт в Nginx access log и браузерную историю. Для подобных одноразовых ссылок это приемлемо, но стоит убедиться, что токен достаточно длинный и энтропийный (UUID — окей).

---

## «Добавлю партнёра позже» — дедлайн, резерв и self-service действия (LFPT-0668)

**Контроллеры:** `DoubleTournamentRegistrationService` (бизнес-логика), `ReserveActionController` (public self-service действия), `SoloRegistrationSchedulerService` (фон).

### Флоу

```
Регистрация SOLO_ADD_LATER (DoubleTournamentRegistrationService.registerSoloForDoubleTournament)
  ↓ точный дедлайн = Tournament.calculateDefaultDeadline() (старт - 24ч) уже прошёл?
  да → сразу WAITLIST (резерв), письмо "перевели в резерв" синхронно
  нет → SOLO_ADD_LATER, слот занят наравне с подтверждённой парой
       ↓ каждые 5 минут: SoloRegistrationSchedulerService.moveExpiredAddLaterToReserve()
         дедлайн прошёл → TournamentRegistration.moveToWaitlist(...) + письмо "перевели в резерв"
       ↓ каждые 5 минут: SoloRegistrationSchedulerService.sendFinalReminders()
         за 3ч до старта, партнёр ещё не добавлен → письмо с 3 действиями (add/confirm/decline)

Партнёр добавлен (DoubleTournamentRegistrationService.addPartnerToSoloRegistration):
  Партнёр без аккаунта создаётся сразу же (LFPT-0687, как и в registerForDoubleTournament) —
  дедлайн/резерв определяют только дальнейший статус пары, а не ждут ли партнёра:
  - до дедлайна (SOLO_ADD_LATER) → сразу CONFIRMED (слот уже занят)
  - после перевода в резерв (WAITLIST без партнёра) → WAITLIST в конец очереди резерва,
    затем TournamentService.processWaitlistForTournament — подтверждает, если дошла очередь
    и есть свободное место, иначе оставляет в резерве (справедливая очерёдность — заявка без
    партнёра не обходит уже готовые пары).
```

### Self-service действия из email (без логина, по токену `partnerActionToken`)

| Method | URL | Описание |
|--------|-----|----------|
| GET | `/reserva/accion/confirmar?token=X` | No-op подтверждение, что заявка ещё актуальна — не меняет состояние, просто фиксирует отклик |
| GET | `/reserva/accion/cancelar?token=X` | Страница подтверждения отмены (превью турнира), без побочных эффектов |
| POST | `/reserva/accion/cancelar` (`token`) | Отменяет резервную регистрацию (`TournamentRegistration.cancel(...)`); идемпотентно для уже отменённой |
| GET | `/reserva/accion/resultado` | Redirect-target после `POST /reserva/accion/cancelar` (flash-сообщение success/error, тот же `waitlist-confirmation.html`) |

Токен действителен, пока заявка в статусе `WAITLIST` без партнёра и активна (`isActive=true`) — отдельного TTL нет, как и у `waitlistConfirmationToken`.

### `SOLO_EXPIRED` — больше не назначается

Новый код переводит просроченные `SOLO_ADD_LATER`-заявки в `WAITLIST`, а не в `SOLO_EXPIRED`. Статус остался в `RegistrationStatus` только ради уже существующих исторических записей.

---

## Клиент 1Padel.App (LFPT-0707) — парная запись внутри приложения

Экран парной записи `mobile-app/` (раздаётся на `/app/`) для Americano с `modality: DOBLES` и `AMERICANO_TEAMS` переиспользует существующие эндпоинты без изменений в их контракте:
- `POST .../register` — «есть партнёр» (сразу, существующий игрок или новые данные без email).
- `POST .../register-solo` (`soloType: SEARCH|ADD_LATER`) — «ищу партнёра на месте» / «добавлю партнёра позже».
- `POST .../add-partner` — добавление партнёра к уже существующей соло-регистрации (`SOLO_SEARCH`/`SOLO_ADD_LATER`).
- `GET .../looking-for-partner` и `POST .../propose-pair/{targetRegistrationId}` — список игроков без пары и предложение объединиться (email-приглашение, как и на сайте; принятие предложения — только по ссылке из письма, в приложении не реализовано).

Собственный статус регистрации на конкретное событие клиент узнаёт через `GET /players/tournaments/{id}/my-registration` (расширен в LFPT-0706), не через этот контроллер.
