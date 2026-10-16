# API: King of Court — `/api/king-of-court`

**Контроллер:** `KingOfCourtApiController`  
**Назначение:** Управление форматом "Король корта" в реальном времени. После каждого изменения состояние рассылается через WebSocket `/topic/king-of-court/{tournamentId}`.

---

## Ключевые концепции

- **King (`kingId`)** — отдельная сущность `TournamentKingOfCourt`, связанная с `Tournament`. `kingId` ≠ `tournamentId`.
- **Раунды** — каждый раунд содержит N кортов с матчами.
- **Калибровочные раунды** — первые N раундов, где розыгрыш случаен, без учёта рейтинга.
- **Ротация** — после раунда победители перемещаются на "корт выше", проигравшие — на "корт ниже".

---

## POST `/api/king-of-court/tournaments/{tournamentId}/initialize`

**Кто вызывает:** Организатор через UI (кнопка "Инициализировать KoC" на странице турнира).  
**Аутентификация:** Не указана (нет `@PreAuthorize`).

### Алгоритм

1. Создаёт `TournamentKingOfCourt` для турнира с `tournamentId`.
2. Распределяет зарегистрированных игроков по кортам.
3. Генерирует первый раунд.

### Контракт

**Path param:** `tournamentId` — Long (ID основного Tournament)

**Request body** `application/json`:
```json
{
  "maxCourts":        2,
  "calibrationRounds": 1
}
```

| Поле | Тип | Правило |
|------|-----|---------|
| `maxCourts` | Integer | ≥1, обязательно |
| `calibrationRounds` | Integer | ≥0, обязательно |

**Response 200 OK**: объект `TournamentKingOfCourt` (entity напрямую)

**Response 400 Bad Request**: сообщение об ошибке (обёрнуто в `ResponseStatusException`)

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize` для ролей управления + `tournamentAccessService.assertCanManageKing`. Исходное замечание: Нет `@PreAuthorize` — любой авторизованный пользователь (включая обычного игрока) может инициализировать KoC-турнир.

> **[BUG/SECURITY]** Возвращается JPA-entity `TournamentKingOfCourt` напрямую. При наличии `@OneToMany`-связей возможны `LazyInitializationException` или N+1 при сериализации. Нужно использовать DTO.

---

## GET `/api/king-of-court/tournaments/{kingId}/state`

**Кто вызывает:** UI (polling или после WebSocket-события).  
**Аутентификация:** Не указана.

### Контракт

**Response 200 OK** — `KingOfCourtStateDTO`:
```json
{
  "kingId": 1,
  "tournamentId": 5,
  "status": "IN_PROGRESS",
  "currentRound": 3,
  "totalRounds": 0,
  "courts": [
    {
      "courtNumber": 1,
      "teamA": ["Игрок 1", "Игрок 2"],
      "teamB": ["Игрок 3", "Игрок 4"],
      "scoreA": 5,
      "scoreB": 3,
      "completed": false
    }
  ],
  "ranking": [...]
}
```

---

## GET `/api/king-of-court/tournaments/{kingId}/ranking`

**Аутентификация:** Не указана.

Возвращает текущий рейтинг как поле из `KingOfCourtStateDTO.ranking`.

**Response 200 OK**: `List<PlayerStatsDTO>`

### Замечания (ревью)

> **[PERFORMANCE]** Вызывает `getCurrentState()` полностью, хотя нужен только список рейтинга. Если state дорогой — лучше отдельный запрос к сервису только за ranking.

---

## POST `/api/king-of-court/matches/result`

**Кто вызывает:** Организатор (вводит счёт матча).  
**Аутентификация:** Не указана.

### Алгоритм

1. Принимает `MatchResultRequest` с данными матча.
2. Вызывает `KingOfCourtService.saveMatchResult()`.
3. Сервис обновляет очки обоих игроков и публикует WebSocket-событие.

### Контракт

**Request body** `application/json`:
```json
{
  "roundId":      1,
  "courtNumber":  1,
  "winners":      [playerId1, playerId2],
  "losers":       [playerId3, playerId4],
  "winnersScore": 6,
  "losersScore":  4
}
```

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize` для ролей управления + `tournamentAccessService.assertCanManageKing`. Исходное замечание: Нет `@PreAuthorize`. Любой пользователь может записать результат матча.

---

## PUT `/api/king-of-court/matches/result/{resultId}`

**Кто вызывает:** Организатор (корректировка счёта).  
**Аутентификация:** Не указана.

Контракт аналогичен `POST /matches/result`, но обновляет существующую запись по `resultId`.

**Response 200 OK**: пустое тело

---

## POST `/api/king-of-court/tournaments/{kingId}/next-round`

**Кто вызывает:** Организатор (переход к следующему раунду).  
**Аутентификация:** Не указана.

### Алгоритм

1. Завершает текущий раунд.
2. Применяет ротацию: победители → корт выше, проигравшие → корт ниже.
3. Генерирует новый раунд.
4. Публикует WebSocket-событие.

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize` для ролей управления + `tournamentAccessService.assertCanManageKing`. Исходное замечание: Нет `@PreAuthorize`.

---

## POST `/api/king-of-court/tournaments/{kingId}/rollback`

**Кто вызывает:** Организатор (отмена последнего раунда).  
**Аутентификация:** Не указана.

### Алгоритм

1. Удаляет последний раунд.
2. Откатывает очки (пересчитывает статистику).
3. Возвращает предыдущий раунд в статус "не завершён".

**Response 200 OK**: пустое тело  
**Response 400**: строка ошибки при невозможности отката

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize` для ролей управления + `tournamentAccessService.assertCanManageKing`. Исходное замечание: Нет `@PreAuthorize` — опасная операция, доступная без авторизации.

---

## POST `/api/king-of-court/tournaments/{kingId}/finish`

**Кто вызывает:** Организатор (завершение турнира).  
**Аутентификация:** Не указана.

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize` для ролей управления + `tournamentAccessService.assertCanManageKing`. Исходное замечание: Нет `@PreAuthorize`.

---

## POST `/api/king-of-court/tournaments/{kingId}/youtube`

**Кто вызывает:** Организатор (добавляет ссылку на стрим).  
**Аутентификация:** Не указана.

**Query param:** `youtubeLink` — строка URL

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** Авторизация: `@PreAuthorize` + `assertCanManageKing`. **Открыто:** URL по-прежнему не валидируется. Исходное замечание: Нет `@PreAuthorize`. Нет валидации URL (любая строка принимается как ссылка на YouTube).

---

## GET `/api/king-of-court/tournaments/{kingId}/players/{playerId}/history`

**Аутентификация:** Не указана.

**Response 200 OK**: `List<MatchHistoryDTO>` — история матчей игрока в данном турнире.

---

## GET `/api/king-of-court/ping`

Health check. Возвращает `"pong"`.

---

## GET `/api/king-of-court/debug/tournaments/{kingId}/check-players`

**Назначение:** Отладочный эндпоинт — проверка игроков и статистики.  
**Аутентификация:** Не указана.

**Response 200 OK**: `DebugInfo` с полями `state`, `playerStats`, `rawPlayerStats`, `message`.

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER')")`. Исходное замечание: Debug-эндпоинт с реальными данными турнира доступен в продакшене без аутентификации. Нужно либо закрыть `@PreAuthorize`, либо удалить.

---

## WebSocket: `/topic/king-of-court/{tournamentId}`

Публикуется через `WebSocketService` при каждом:
- Сохранении результата матча
- Переходе к следующему раунду
- Откате раунда
- Завершении турнира

Клиент получает обновлённый `KingOfCourtStateDTO` без необходимости перезагрузки страницы.

---

## Общие замечания по KoC API

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** Все перечисленные write-эндпоинты защищены `@PreAuthorize` + `assertCanManageKing`. Исходное замечание: Все write-эндпоинты (`/initialize`, `/next-round`, `/rollback`, `/finish`, `/matches/result`, `/youtube`) не защищены `@PreAuthorize`. Несмотря на то что `JwtAuthenticationFilter` требует авторизацию, любой аутентифицированный игрок может управлять чужим турниром. Нужно добавить `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")` или кастомную проверку через SpEL-метод.

> **[DESIGN]** `kingId` и `tournamentId` используются вперемешку в разных эндпоинтах. Клиент должен знать оба ID. Стоит либо унифицировать (работать всегда с `tournamentId`, резолвя `kingId` на сервере), либо чётко документировать разницу в UI.
