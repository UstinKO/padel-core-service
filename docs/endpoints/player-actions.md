# API: Действия игрока — `/players/...`

**Контроллер:** `PlayerDashboardController`  
**Назначение:** Дашборд игрока, регистрация/отмена участия в турнирах, проверка регистрации. Все JSON-эндпоинты вызываются через AJAX с дашборда.

---

## GET `/players/dashboard`

**Тип:** Thymeleaf view.  
**Аутентификация:** `ROLE_PLAYER` (Spring Security redirect при отсутствии).

### Алгоритм

1. Извлекает `PlayerPadel` из principal (`CustomOAuth2User`, `PlayerPadel` или `UserDetails` — загрузка из БД).
2. Если principal — `Owner`, редиректит на `/admin`.
3. Загружает через `TournamentService.getVisibleTournamentsForPlayer()` открытые турниры.
4. Загружает ID турниров, на которые игрок уже зарегистрирован.
5. Сериализует данные в JSON-строки для передачи в Thymeleaf → JavaScript.

### Атрибуты модели

| Атрибут | Тип | Описание |
|---------|-----|----------|
| `player` | PlayerPadel | Текущий игрок |
| `torneosInscritos` | int | Кол-во активных регистраций |
| `tournamentsJson` | String | JSON-массив турниров для JS |
| `historicalJson` | String | JSON-массив прошедших турниров игрока для JS |
| `myTournamentIdsJson` | String | JSON-массив ID турниров игрока для JS |

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-16]** Заглушек `torneosGanados`/`rankingPosition`/`puntosRanking` в модели больше нет.

---

## POST `/players/tournaments/{tournamentId}/register`

**Кто вызывает:** JavaScript на дашборде и странице турнира; клиент 1Padel.App (LFPT-0660) — для индивидуальной записи на King of Court/Americano (INDIVIDUAL)/Padel Clinic/Cancha Abierta, без изменений контракта.  
**Аутентификация:** `ROLE_PLAYER`.  
**Возвращает:** JSON.

### Алгоритм

1. Извлекает игрока из principal.
2. Проверяет `player.hasValidContact()`: телефон `+54` ИЛИ Telegram username должны быть заполнены.
3. Если контакта нет — возвращает `400` с `"needContact": true` (фронт показывает модалку добавления контакта).
4. Вызывает `TournamentService.registerPlayer(tournamentId, playerId)`.
5. Если мест нет — ставит в лист ожидания (`WAITLIST`), иначе `CONFIRMED`.

### Контракт

**Path param:** `tournamentId` — Long

**Response 200 OK**:
```json
{
  "success": true,
  "message": "¡Registro confirmado!" | "Agregado a la lista de espera",
  "status": "CONFIRMED" | "WAITLIST",
  "position": 3,
  "waitlistPosition": null | 1
}
```

**Response 400** — нет контакта:
```json
{
  "success": false,
  "message": "Para inscribirte en un torneo...",
  "needContact": true
}
```

**Response 400** — другая ошибка:
```json
{
  "success": false,
  "message": "описание ошибки"
}
```

---

## POST `/players/tournaments/{tournamentId}/cancel`

**Кто вызывает:** JavaScript (кнопка отмены регистрации); клиент 1Padel.App (LFPT-0666) — для индивидуальной отмены записи на King of Court/Americano (INDIVIDUAL)/Padel Clinic/Cancha Abierta, без изменений контракта.  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

1. Вызывает `TournamentService.cancelRegistration()`.
2. Делает повторную проверку через `getRegistration()` чтобы убедиться в отмене.
3. При успехе — запускает логику листа ожидания (в сервисе): следующий WAITLIST-игрок переходит в WAITLIST_INVITED.

### Контракт

**Path param:** `tournamentId`  
**Query param:** `reason` (необязательный) — причина отмены

**Response 200 OK**:
```json
{
  "success": true,
  "message": "Inscripción cancelada exitosamente"
}
```

---

## POST `/players/tournaments/{tournamentId}/cancel-double`

**Кто вызывает:** JavaScript (модалка отмены парной регистрации).  
**Аутентификация:** `ROLE_PLAYER`.

### Алгоритм

Принимает `option` — режим отмены:

| option | Условие | Действие |
|--------|---------|----------|
| `self` | партнёр зарегистрирован в системе | Отменяет только себя, партнёр остаётся |
| `full` | всегда | Отменяет всю пару |
| `replace` | партнёр зарегистрирован в системе | Выходит сам, на его место приглашается новый игрок через `DoubleTournamentRegistrationService.replacePlayerInPair()` |

`replaceData` (для option=`replace`) — JSON-строка с полями нового игрока:
```json
{"firstName": "...", "lastName": "...", "phone": "...", "email": "..."}
```

### Контракт

**Path param:** `tournamentId`  
**Form params:** `option`, `reason` (опц.), `replaceData` (опц. JSON-строка)

**Response 200 OK**:
```json
{
  "success": true,
  "message": "..."
}
```

### Замечания (ревью)

> **[STYLE]** `replaceData` передаётся как строка-JSON в form param, а затем парсится вручную через `ObjectMapper`. Это антипаттерн — нужен отдельный `@RequestBody DTO` или хотя бы структурированный form-объект. Сейчас возможны неочевидные ошибки парсинга без валидации.

> **[закрыто, сверено с кодом 2026-10-16]** Используется инжектированный `ObjectMapper`.

---

## GET `/players/tournaments/{tournamentId}/my-registration`

**Кто вызывает:** JavaScript перед показом кнопки отмены парной регистрации.  
**Аутентификация:** `ROLE_PLAYER`.

### Контракт

**Response 200 OK** — не зарегистрирован:
```json
{ "registered": false }
```

**Response 200 OK** — зарегистрирован:
```json
{
  "registered": true,
  "partnerId": 15 | null,
  "partnerFirstName": "Ana" | null,
  "partnerLastName": "López" | null,
  "hasRegisteredPartner": true | false
}
```

---
