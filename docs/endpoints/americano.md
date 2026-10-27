# API: Americano — `/api/tournaments/americano`

**Контроллер:** `AmericanoApiController`  
**Назначение:** Управление турнирами формата Americano и Team Americano: регистрация, инициализация, раунды, матчи, рейтинг, завершение.

---

## Форматы

| Тип | Описание |
|-----|----------|
| **Americano** | Индивидуальный: все играют со всеми, партнёры меняются |
| **Team Americano** | Командный: пары фиксированы, раунды меняются соперники |
| **Full Americano** | Математически полный граф пар (все комбинации) |
| **Flex Americano** | Приближённый граф (когда Full невозможен из-за числа игроков/раундов) |

Алгоритм выбирается через `AmericanoConfigDto.generationMode` (AUTO / FULL / FLEX), результат проверяется постфактум (`AmericanoService.verifyFormat`), независимо от запрошенного режима.

**Генератор расписания** (LFPT-0749) — `AmericanoScheduleEngine` (чистый класс без Spring/JPA, `service/americano/`), не часть `AmericanoService`:
- Партнёрские пары — методом круга (round-robin 1-factorization), когда весь турнир играет каждый раунд без bye (`N` кратно 4 и `N <= courts*4`) — математическая гарантия нулевых повторов партнёров на полном цикле `N-1` раундов. Иначе (bye неизбежен) — минимальное по весу совершенное паросочетание по накопленной истории (без такой гарантии).
- Группировка партнёрских пар в матчи (кто кому соперник) и назначение кортов — отдельные шаги, оба решаются точным битмаск-ДП (минимальное паросочетание/назначение по истории соперников/кортов), а не жадным или случайным перебором. Строится несколько кандидатов расписания, выбирается лучший по разбросу встреч соперников и сериям на одном корте.
- Результат проверяет `AmericanoScheduleValidator` (тоже чистый класс): обязательные условия (раунд/матч корректен) — нарушение бросает `InvalidStateException`, расписание не сохраняется.

---

## РЕГИСТРАЦИЯ

### POST `/{tournamentId}/register/{playerId}`

**Кто вызывает:** Организатор через UI тест-управления или API.  
**Аутентификация:** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")` (сверено с кодом 2026-10-16).

Регистрирует игрока `playerId` на турнир `tournamentId`.

**Response 200 OK**: `TournamentRegistrationDto`

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")`. Исходное замечание: Нет авторизации — любой пользователь может зарегистрировать любого игрока на любой турнир. Нужен `@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORGANIZER')")` или проверка что `playerId == currentUser.id`.

---

### DELETE `/{tournamentId}/cancel/{playerId}`

**Аутентификация:** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")` + проверка доступа к клубу турнира.

**Query param:** `reason` (необязательный)

Отменяет регистрацию игрока.

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")`. Исходное замечание: Нет авторизации — любой может отменить регистрацию любого игрока.

---

## ИНИЦИАЛИЗАЦИЯ

Инициализация Americano — только form-POST в `AmericanoViewController` (`/tournaments/americano/initialize` и др., см. `domain-overview.md` § 8); REST-эндпоинта `POST /{tournamentId}/initialize` нет (сверено 2026-10-16).

### GET `/{tournamentId}/initialized`

Проверяет, был ли турнир инициализирован.

**Response 200 OK**: `true` / `false`

---

## РАУНДЫ

### GET `/{tournamentId}/rounds`

Список всех раундов турнира.

**Response 200 OK**: `List<AmericanoRoundDto>`

### AmericanoRoundDto

```json
{
  "id": 1,
  "tournamentId": 10,
  "roundNumber": 1,
  "status": "PENDING|IN_PROGRESS|COMPLETED",
  "pointsPerMatch": 32,
  "matches": [...]
}
```

---

### GET `/rounds/{roundId}`

Один раунд по ID.

**Response 200 OK**: `AmericanoRoundDto`

---

### POST `/rounds/{roundId}/start`

**Аутентификация:** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")` + проверка доступа к клубу турнира.

Запускает раунд, меняет статус на `IN_PROGRESS`.

**Response 200 OK**: `AmericanoRoundDto`

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")`; роль `ADMIN` существует (`OwnerRole.ADMIN`, v1.38).

---

### POST `/rounds/{roundId}/complete`

**Аутентификация:** Аналогично `/start`.

Завершает раунд, меняет статус на `COMPLETED`.

**Response 200 OK**: `AmericanoRoundDto`

---

## МАТЧИ

### GET `/{tournamentId}/matches`

Все матчи турнира.

**Response 200 OK**: `List<AmericanoMatchDto>`

### AmericanoMatchDto

```json
{
  "id": 1,
  "roundId": 1,
  "courtNumber": 1,
  "team1Players": [playerId1, playerId2],
  "team2Players": [playerId3, playerId4],
  "team1Score": 15,
  "team2Score": 10,
  "status": "PENDING|IN_PROGRESS|COMPLETED"
}
```

---

### GET `/rounds/{roundId}/matches`

Матчи конкретного раунда.

**Response 200 OK**: `List<AmericanoMatchDto>`

---

### GET `/matches/{matchId}`

Один матч по ID.

**Response 200 OK**: `AmericanoMatchDto`

---

### POST `/matches/{matchId}/result`

**Кто вызывает:** Организатор (ввод счёта).  
**Аутентификация:** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")` + проверка доступа к клубу турнира.

### Контракт

**Request body** `application/json`:
```json
{
  "team1Score": 15,
  "team2Score": 10
}
```
(поле `matchId` устанавливается из path param, переопределяет тело)

**Response 200 OK**: `AmericanoMatchDto` с обновлёнными очками

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")`. Исходное замечание: Нет авторизации — любой может ввести результат любого матча.

---

### GET `/{tournamentId}/players/{playerId}/matches`

Матчи конкретного игрока в турнире.

**Response 200 OK**: `List<AmericanoMatchDto>`

---

## РЕЙТИНГ И СТАТИСТИКА

### GET `/{tournamentId}/ranking`

**Query params:**
- `sortBy` (по умолчанию: `score`) — поле сортировки
- `ascending` (по умолчанию: `false`) — направление

**Response 200 OK**: `AmericanoRankingDto`

```json
{
  "tournamentId": 10,
  "players": [
    {
      "playerId": 1,
      "playerName": "Juan Pérez",
      "totalScore": 120,
      "gamesPlayed": 8,
      "wins": 5,
      "position": 1
    }
  ]
}
```

---

### GET `/{tournamentId}/ranking/simple`

**Query params:**
- `sortBy` (по умолчанию: `score`)
- `sortDirection` (по умолчанию: `DESC`)

Упрощённый список без агрегатов.

**Response 200 OK**: `List<AmericanoPlayerDto>`

---

### GET `/{tournamentId}/players/{playerId}/stats`

Статистика конкретного игрока.

**Response 200 OK**: `AmericanoPlayerDto`

---

### GET `/{tournamentId}/players/count`

Количество активных (не выбывших) игроков.

**Response 200 OK**: `Integer`

---

## УПРАВЛЕНИЕ ИГРОКАМИ

### POST `/{tournamentId}/players/{playerId}/dropout`

**Аутентификация:** `hasRole('ADMIN') or @securityService.isTournamentOrganizer(#tournamentId)` — та же проблема с `ADMIN`.

Помечает игрока как выбывшего (dropout). Игрок больше не участвует в розыгрыше, но его результаты сохраняются.

**Request body** `application/json` (`AmericanoDropoutDto`):
```json
{
  "reason": "Травма (опционально)"
}
```

**Response 200 OK**: пустое тело

---

## ЗАВЕРШЕНИЕ ТУРНИРА

### POST `/{tournamentId}/finish`

**Аутентификация:** `hasRole('ADMIN') or ...` — та же проблема.

**Query params:** `sortBy`, `ascending`

Завершает турнир, формирует финальный рейтинг.

**Response 200 OK**: `AmericanoRankingDto` — финальная таблица

---

## ДОПОЛНИТЕЛЬНЫЕ

### PUT `/rounds/{roundId}/points-limit`

**Аутентификация:** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")` + проверка доступа к клубу турнира.

Изменяет лимит очков в раунде (только до начала раунда).

**Request body** `application/json`:
```json
{
  "pointsPerMatch": 15 | 21 | 24 | 32 | 40
}
```

**Response 200 OK**: пустое тело  
**Response 400**: `"Invalid points limit"` (если значение не из разрешённого списка)

### Замечания (ревью)

> **[MINOR]** Допустимые значения (15, 21, 24, 32, 40) захардкожены в контроллере. Лучше вынести в `enum` или конфигурацию.

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")`. Исходное замечание: Нет авторизации.

---

## Общие замечания по Americano API

> **[BUG — КРИТИЧНО]** `hasRole('ADMIN')` в `@PreAuthorize` никогда не срабатывает — такой роли нет. Нужно заменить на `hasAnyRole('SUPER_ADMIN','ORGANIZER')` во всех эндпоинтах, где используется этот паттерн.

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")`. Исходное замечание: Большинство write-эндпоинтов не имеют авторизации вообще. Нужен систематический аудит всех методов.

> **[DESIGN]** Инициализация (`POST /{tournamentId}/initialize`) — смешение MVC и REST паттернов в одном контроллере. Нужно разделить.
