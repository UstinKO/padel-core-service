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
