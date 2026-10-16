# Контракт: HTTP API и WebSocket

> Что фронтенд получает от бэкенда и как. Раздел 1 сгенерирован из кода (коммит `eeee06c`), разделы 0, 2–5 — ручной анализ. Экраны, на которые ссылаются ID (`P-05`, `A-12`…), — в [screens.md](screens.md). Решения, на которые ссылаются `ADR-00xx`, — в [../adr/](../adr/README.md).

## 0. Главное одним экраном

- **Для React не хватает API почти для каждого экрана.** 108 JSON-эндпоинтов уже есть, но они покрывают в основном действия (сохранить результат, зарегистрироваться), а **данные для первой отрисовки** почти везде приходят из модели Thymeleaf. Список недостающих — в §2: 33 позиции (часть из них — группы из 2–5 эндпоинтов).
- **Публичные страницы нельзя собрать из текущего API анонимно:** `/api/tournaments/**`, `/api/tournaments/americano/**`, `/api/clubs/**` требуют входа (§3). Для публичных экранов нужен отдельный публичный read-only слой.
- **37 эндпоинтов нетипизированы** (`ResponseEntity<?>` или `Map<String, Object>`). Из них не сгенерировать TypeScript-типы (ADR-0004) — перед переводом экрана его эндпоинты переводятся на DTO.
- **Три JSON-зоны живут не под `/api/`:** `/tournaments/team-americano/api/**`, `/tournaments/team-playoff/api/**`, JSON-методы под `/admin/**`, плюс `/players/tournaments/**` и `/recuperar-password/*`. Для них не работает правило «`/api/**` отвечает JSON-ом, а не редиректом» (§4) — переносятся под `/api/` вместе со своим экраном.
- **Формат ошибок не единый** (§4.2). Новые эндпоинты — сразу в целевом формате, старые — при переводе их экрана.

---

## 1. Существующие JSON-эндпоинты

Сгенерировано из кода (коммит `eeee06c`): **108** JSON-эндпоинтов вне `/test/tournaments/**` (у тестового инструмента ещё 23 — он не переносится, ADR-0007).

«Доступ» — **итоговый**: URL-правило `SecurityConfig` + `@PreAuthorize`. «Используется» — какие экраны (ID из [screens.md](screens.md)) вызывают эндпоинт сейчас; «—» = текущий UI его не вызывает (кандидат в неиспользуемые или API «на вырост»).

### Auth

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/api/auth/login` | публичный | body: `AuthRequest` | `?` (нетипизированный) | — |
| GET | `/api/auth/me` | публичный | — | `?` (нетипизированный) | — |
| POST | `/api/auth/refresh` | публичный | body: `RefreshTokenRequest` | `?` (нетипизированный) | — |

### Турниры (общие)

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| GET | `/api/tournaments` | вход обязателен | — | `List<TournamentDto>` | — |
| POST | `/api/tournaments` | вход обязателен + SUPER_ADMIN/ORGANIZER | body: `TournamentDto` | `TournamentDto` | — |
| GET | `/api/tournaments/club/{clubId}` | вход обязателен | — | `List<TournamentDto>` | — |
| GET | `/api/tournaments/my` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `List<TournamentDto>` | — |
| GET | `/api/tournaments/search` | вход обязателен | query: `clubId?`, `genero?`, `nivel?`, `tipo?`, `estado?` | `List<TournamentDto>` | — |
| GET | `/api/tournaments/status/{status}` | вход обязателен | — | `List<TournamentDto>` | — |
| GET | `/api/tournaments/upcoming` | вход обязателен | — | `List<TournamentDto>` | — |
| GET | `/api/tournaments/{id}` | вход обязателен | — | `TournamentDto` | — |
| PUT | `/api/tournaments/{id}` | вход обязателен + SUPER_ADMIN/ORGANIZER | body: `TournamentDto` | `TournamentDto` | — |
| DELETE | `/api/tournaments/{id}` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `Void` | — |
| PATCH | `/api/tournaments/{id}/status` | вход обязателен + SUPER_ADMIN/ORGANIZER | query: `status` | `TournamentDto` | — |

### Парная регистрация

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/api/tournaments/double/accept-pair` | публичный | query: `token` | `?` (нетипизированный) | P-11 |
| POST | `/api/tournaments/double/confirm` | вход обязателен | query: `token` | `?` (нетипизированный) | — |
| POST | `/api/tournaments/double/{tournamentId}/add-partner` | вход обязателен | body: `PartnerRegistrationDto` | `?` (нетипизированный) | P-05 |
| GET | `/api/tournaments/double/{tournamentId}/looking-for-partner` | публичный | — | `List<LookingForPartnerDto>` | P-05 |
| POST | `/api/tournaments/double/{tournamentId}/propose-pair/{targetRegistrationId}` | вход обязателен | — | `?` (нетипизированный) | P-05 |
| POST | `/api/tournaments/double/{tournamentId}/register` | вход обязателен | body: `PartnerRegistrationDto` | `?` (нетипизированный) | P-05, U-01 |
| POST | `/api/tournaments/double/{tournamentId}/register-solo` | вход обязателен | body: `SoloDoubleRegistrationDto` | `?` (нетипизированный) | P-05, U-01 |

### Действия игрока (не под /api)

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/players/tournaments/{tournamentId}/cancel` | вход обязателен | query: `reason?` | `?` (нетипизированный) | U-01 |
| POST | `/players/tournaments/{tournamentId}/cancel-double` | вход обязателен | query: `option`, `reason?`, `replaceData?` | `?` (нетипизированный) | U-01 |
| GET | `/players/tournaments/{tournamentId}/my-registration` | вход обязателен | — | `?` (нетипизированный) | U-01 |
| POST | `/players/tournaments/{tournamentId}/register` | вход обязателен | — | `?` (нетипизированный) | P-05, U-01 |

### Игроки

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| PATCH | `/api/players/me/contact` | вход обязателен | body: `UpdateContactRequest` | `Map<String, Object>` | P-05, U-01 |
| GET | `/api/players/me/contact-status` | вход обязателен | — | `Map<String, Object>` | P-05, U-01 |
| GET | `/api/players/search` | вход обязателен | query: `query`, `tournamentId` | `List<PlayerResponseDto>` | P-05, U-01 |
| GET | `/players/api` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `Page<PlayerResponseDto>` | — |
| GET | `/players/api/confirmar-email` | вход обязателен | query: `codigo` | `?` (нетипизированный) | — |
| POST | `/players/api/registro` | вход обязателен | body: `RegistroRequestDto` | `?` (нетипизированный) | — |
| GET | `/players/api/{id}` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `?` (нетипизированный) | — |

### Сброс пароля (не под /api)

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/recuperar-password/confirmar` | публичный | body: `PasswordResetConfirm` | `?` (нетипизированный) | P-01, P-02 |
| POST | `/recuperar-password/solicitar` | публичный | body: `PasswordResetRequest` | `?` (нетипизированный) | P-01, P-02 |

### Клубы

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| GET | `/api/clubs` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER | — | `List<ClubDto>` | — |
| POST | `/api/clubs` | вход обязателен + SUPER_ADMIN/ORGANIZER | body: `ClubDto` | `ClubDto` | — |
| GET | `/api/clubs/active` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER | — | `List<ClubDto>` | — |
| GET | `/api/clubs/nombre/{nombre}` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER | — | `ClubDto` | — |
| GET | `/api/clubs/search` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER | query: `q` | `List<ClubDto>` | — |
| GET | `/api/clubs/zona/{zona}` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER | — | `List<ClubDto>` | — |
| GET | `/api/clubs/{id}` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER | — | `ClubDto` | — |
| PUT | `/api/clubs/{id}` | вход обязателен + SUPER_ADMIN | body: `ClubDto` | `ClubDto` | — |
| DELETE | `/api/clubs/{id}` | вход обязателен + SUPER_ADMIN | — | `Void` | — |
| DELETE | `/api/clubs/{id}/hard` | вход обязателен + SUPER_ADMIN | — | `Void` | — |

### Владельцы

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| GET | `/api/owners/super-admin` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `OwnerDto` | — |
| GET | `/api/owners/{id}` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `OwnerDto` | — |
| PUT | `/api/owners/{id}` | вход обязателен + SUPER_ADMIN/ORGANIZER | body: `OwnerDto` | `OwnerDto` | — |

### King of Court

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| GET | `/api/king-of-court/debug/tournaments/{kingId}/check-players` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER | — | `DebugInfo` | A-07 |
| POST | `/api/king-of-court/matches/result` | вход обязателен + OWNER/SUPER_ADMIN/ORGANIZER/ADMIN/CLUB_ADMIN | body: `MatchResultRequest` | `?` (нетипизированный) | A-07 |
| PUT | `/api/king-of-court/matches/result/{resultId}` | вход обязателен + OWNER/SUPER_ADMIN/ORGANIZER/ADMIN/CLUB_ADMIN | body: `MatchResultRequest` | `?` (нетипизированный) | A-07 |
| GET | `/api/king-of-court/ping` | публичный | — | `String` | — |
| POST | `/api/king-of-court/tournaments/{kingId}/finish` | вход обязателен + OWNER/SUPER_ADMIN/ORGANIZER/ADMIN/CLUB_ADMIN | — | `?` (нетипизированный) | A-07 |
| POST | `/api/king-of-court/tournaments/{kingId}/next-round` | вход обязателен + OWNER/SUPER_ADMIN/ORGANIZER/ADMIN/CLUB_ADMIN | — | `?` (нетипизированный) | A-07 |
| GET | `/api/king-of-court/tournaments/{kingId}/players/{playerId}/history` | публичный | — | `List<MatchHistoryDTO>` | A-07, L-01 |
| GET | `/api/king-of-court/tournaments/{kingId}/ranking` | публичный | — | `List<PlayerStatsDTO>` | — |
| POST | `/api/king-of-court/tournaments/{kingId}/rollback` | вход обязателен + OWNER/SUPER_ADMIN/ORGANIZER/ADMIN/CLUB_ADMIN | — | `?` (нетипизированный) | A-07 |
| GET | `/api/king-of-court/tournaments/{kingId}/state` | публичный | — | `KingOfCourtStateDTO` | A-07, L-01 |
| POST | `/api/king-of-court/tournaments/{kingId}/youtube` | вход обязателен + OWNER/SUPER_ADMIN/ORGANIZER/ADMIN/CLUB_ADMIN | query: `youtubeLink` | `?` (нетипизированный) | A-07 |
| POST | `/api/king-of-court/tournaments/{tournamentId}/initialize` | вход обязателен + OWNER/SUPER_ADMIN/ORGANIZER/ADMIN/CLUB_ADMIN | body: `InitializeKingOfCourtRequest` | `TournamentKingOfCourt` | A-07 |

### Americano

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| GET | `/api/tournaments/americano/matches/{matchId}` | вход обязателен | — | `AmericanoMatchDto` | A-08 |
| POST | `/api/tournaments/americano/matches/{matchId}/result` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | body: `AmericanoMatchResultDto` | `AmericanoMatchDto` | A-08 |
| GET | `/api/tournaments/americano/rounds/{roundId}` | вход обязателен | — | `AmericanoRoundDto` | A-08, A-10 |
| POST | `/api/tournaments/americano/rounds/{roundId}/complete` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | — | `AmericanoRoundDto` | A-08, A-10 |
| GET | `/api/tournaments/americano/rounds/{roundId}/matches` | вход обязателен | — | `List<AmericanoMatchDto>` | A-08, A-10 |
| PUT | `/api/tournaments/americano/rounds/{roundId}/points-limit` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `?` (нетипизированный) | A-08, A-10 |
| POST | `/api/tournaments/americano/rounds/{roundId}/start` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | — | `AmericanoRoundDto` | A-08, A-10 |
| DELETE | `/api/tournaments/americano/{tournamentId}/cancel/{playerId}` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | query: `reason?` | `Void` | — |
| POST | `/api/tournaments/americano/{tournamentId}/finish` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | query: `sortBy?`, `ascending?` | `AmericanoRankingDto` | — |
| GET | `/api/tournaments/americano/{tournamentId}/initialized` | вход обязателен | — | `Boolean` | — |
| GET | `/api/tournaments/americano/{tournamentId}/matches` | вход обязателен | — | `List<AmericanoMatchDto>` | — |
| GET | `/api/tournaments/americano/{tournamentId}/players/count` | вход обязателен | — | `Integer` | — |
| POST | `/api/tournaments/americano/{tournamentId}/players/{playerId}/dropout` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | body: `AmericanoDropoutDto` | `Void` | — |
| GET | `/api/tournaments/americano/{tournamentId}/players/{playerId}/matches` | вход обязателен | — | `List<AmericanoMatchDto>` | — |
| GET | `/api/tournaments/americano/{tournamentId}/players/{playerId}/stats` | вход обязателен | — | `AmericanoPlayerDto` | — |
| GET | `/api/tournaments/americano/{tournamentId}/ranking` | вход обязателен | query: `sortBy?`, `ascending?` | `AmericanoRankingDto` | A-08 |
| GET | `/api/tournaments/americano/{tournamentId}/ranking/simple` | вход обязателен | query: `sortBy?`, `sortDirection?` | `List<AmericanoPlayerDto>` | — |
| POST | `/api/tournaments/americano/{tournamentId}/register/{playerId}` | вход обязателен + ADMIN/SUPER_ADMIN/ORGANIZER/CLUB_ADMIN | — | `TournamentRegistrationDto` | — |
| GET | `/api/tournaments/americano/{tournamentId}/rounds` | вход обязателен | — | `List<AmericanoRoundDto>` | — |

### Team Americano (не под /api)

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/tournaments/team-americano/api/matches/{matchId}/result` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-10 |
| GET | `/tournaments/team-americano/api/{tournamentId}/ranking` | вход обязателен | — | `TeamAmericanoRankingDto` | A-10 |

### Team Playoff (не под /api)

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/tournaments/team-playoff/api/matches/{matchId}/assign-court` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| POST | `/tournaments/team-playoff/api/matches/{matchId}/court` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| POST | `/tournaments/team-playoff/api/matches/{matchId}/queue/remove` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | — | `Map<String, Object>` | A-12 |
| GET | `/tournaments/team-playoff/api/matches/{matchId}/swap-candidates` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | — | `List<TeamPlayoffService.PlayoffSwapCandidate>` | A-12 |
| POST | `/tournaments/team-playoff/api/matches/{matchId}/swap-team` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| POST | `/tournaments/team-playoff/api/matches/{matchId}/team` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| POST | `/tournaments/team-playoff/api/playoff-matches/{matchId}/result` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| POST | `/tournaments/team-playoff/api/qual-matches/{matchId}/result` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| GET | `/tournaments/team-playoff/api/{tournamentId}/court-board` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | — | `Map<String, Object>` | A-12 |
| POST | `/tournaments/team-playoff/api/{tournamentId}/qual-matches` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| POST | `/tournaments/team-playoff/api/{tournamentId}/qual-matches/queue` | вход обязателен + SUPER_ADMIN/ADMIN/ORGANIZER/CLUB_ADMIN | body: `Map` (нетипизированный) | `Map<String, Object>` | A-12 |
| GET | `/tournaments/team-playoff/api/{tournamentId}/ranking` | вход обязателен | — | `TeamAmericanoRankingDto` | A-12 |

### Админ: JSON под /admin

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/admin/players/{id}/guest-credentials` | админ-роли (URL) | — | `Map<String, Object>` | A-17 |
| POST | `/admin/tournaments/{tournamentId}/move-to-main/{playerId}` | админ-роли (URL) | — | `Map<String, Object>` | A-04 |
| POST | `/admin/tournaments/{tournamentId}/move-to-waitlist/{playerId}` | админ-роли (URL) | — | `Map<String, Object>` | A-04 |
| POST | `/admin/tournaments/{tournamentId}/pair-solo` | админ-роли (URL) | query: `regId1`, `regId2` | `Map<String, Object>` | A-04 |

### Админ-метрики

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| GET | `/api/admin/email-daily-count` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `Map<String, Object>` | — |
| GET | `/api/admin/email-stats` | вход обязателен + SUPER_ADMIN/ORGANIZER | — | `Map<String, Object>` | — |

### Cookies

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| POST | `/api/cookies/accept` | публичный | — | `?` (нетипизированный) | P-01 |
| POST | `/api/cookies/customize` | публичный | query: `analytics?`, `marketing?` | `?` (нетипизированный) | P-01 |
| POST | `/api/cookies/reject` | публичный | — | `?` (нетипизированный) | P-01 |

### Служебные

| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |
|---|---|---|---|---|---|
| GET | `/api/internal/telegram-pipeline/status` | публичный | — | `Map<String, Boolean>` | — |
| POST | `/test/telegram/attacks` | публичный | — | `String` | — |
| POST | `/test/telegram/error` | публичный | — | `String` | — |
| POST | `/test/telegram/ping` | публичный | — | `String` | — |
| POST | `/test/telegram/reminders` | публичный | — | `String` | — |
| POST | `/test/telegram/reminders/force/{tournamentId}` | публичный | — | `String` | — |
| GET | `/test/telegram/status` | публичный | — | `String` | — |

---

## 2. Недостающие эндпоинты (что построить)

Собрано по экранам из [screens.md](screens.md). Имена — предварительные, финальные фиксируются в фиче-спеке экрана. «Фаза» — когда эндпоинт нужен (см. [plan.md](../plan.md)).

### 2.1. Каркас и сессия (Фаза 0–1)

| Эндпоинт | Зачем | Экраны |
|---|---|---|
| `GET /api/auth/session` | Кто вошёл, роли, clubId, флаги — для роутинга и меню | все |
| `POST /login` с JSON-ответом (ADR-0005) | Вход из SPA без редиректов — нужен к Ф4 (до этого вход через текущую страницу) | P-02 |
| `GET /api/public/config` | recaptchaEnabled/siteKey, googleOAuthEnabled, поддерживаемые языки, версия приложения | P-02, P-03 |
| `GET /api/admin/reference-data` | Все enum-справочники (типы, модальности, уровни, форматы, статусы, методы/статусы оплаты) с подписями на языке запроса | A-03, A-06, A-16 |

### 2.2. Публичное чтение (Фаза 3–5)

| Эндпоинт | Замещает (модель Thymeleaf) | Экраны |
|---|---|---|
| `GET /api/public/home` | `totalClubs`, `totalPlayers`, `totalTournaments`, `upcomingTournaments`, `tournamentsJson` | P-01 |
| `GET /api/public/tournaments` (+ фильтры) | `tournamentsJson` | P-04 |
| `GET /api/public/tournaments/{id}` | `tournament`, `availableSpots`, `registrations`/`confirmed*`, `tournamentTeams`, `leaderName`, флаги формата (`isInitialized`, `kingOfCourtActive`, `isPlayoff*`, `totalRounds`, `completedRounds`) | P-05 |
| `GET /api/public/clubs`, `GET /api/public/clubs/{id}` | `clubs`, `club` | P-06, P-07 |
| `GET /api/public/ranking` | `ranking`, `topRanking`, `totalJugadores` | P-08 |
| `GET /api/public/king-of-court/by-tournament/{tournamentId}` | резолв kingId во view-контроллере | L-01 |
| `GET /api/public/tournaments/{id}/americano` | `rounds`, `ranking`, `activePlayers`, `isInitialized` (+ `sortBy`/`ascending`) | L-02, L-03 |
| `GET /api/public/tournaments/{id}/team-americano` | `rounds`, `ranking`, `isInitialized` | L-04 |
| `GET /api/public/tournaments/{id}/team-playoff` | `qualRounds` (разбитые на волны), `playoffRounds`, `qualStarted`, `playoffStarted`, `ranking` | L-06 |

### 2.3. Текущий игрок (Фаза 4)

| Эндпоинт | Замещает | Экраны |
|---|---|---|
| `GET /api/tournaments/{id}/my-status` | `isRegistered`, `isInWaitlist`, `userRegistration`, `currentUserIsSolo`, `currentUserSoloRegId`, `currentUserPlayerId` | P-05, L-0x (подсветка «это я») |
| `GET /api/players/me/dashboard` | `tournamentsJson`, `historicalJson`, `myTournamentIdsJson`, `torneosInscritos` | U-01 |
| `GET/PUT /api/players/me`, `POST /api/players/me/password` | `player` + `POST /perfil/actualizar` | U-02 |
| `POST /api/players/registro` (починить путь, F-07) | `POST /players/registro` (форма) | P-03 |
| `GET/POST /api/waitlist/confirm?token=` (если флоу жив, F-06) | `WaitlistController` | P-10 |
| `GET /api/password-reset/validate?token=` | `GET /recuperar-password?token=` (сейчас падает на просроченном токене, F-02) | F-d |

### 2.4. Админка (Фаза 1–3)

| Эндпоинт | Замещает | Экраны |
|---|---|---|
| `POST /api/clubs/{id}/toggle-status` + клубная изоляция в `ClubController` | form POST | A-13…A-15 |
| `GET /api/admin/dashboard` | 13 атрибутов `AdminController#adminPanel` | A-01 |
| `GET /api/admin/tournaments?clubId=` | `tournaments`, `clubs`, `selectedClubId` | A-02 |
| `POST /api/admin/tournaments/{id}/deactivate`, `GET /api/admin/tournaments/{id}/copy-draft` | form POST, `GET .../copy` | A-02, A-03 |
| `GET /api/admin/tournaments/{id}` | 13 атрибутов `AdminController#viewTournament` | A-04 |
| перенос `move-to-main`, `move-to-waitlist`, `pair-solo` под `/api/admin/tournaments/{id}/...` | JSON под `/admin` | A-04 |
| `GET /api/admin/tournaments/{id}/bracket`, `POST .../bracket/generate`, `PUT .../bracket/matches/{matchId}` | `AdminMatchController` | A-05 |
| `GET/PUT /api/admin/tournaments/{id}/payments` | `PaymentManagementController` (форма `payments[i].*`) | A-06 |
| `POST /api/king-of-court/tournaments/{kingId}/reset`, `POST /api/king-of-court/start` | form POST в `AdminKingOfCourtController` | A-07 |
| `GET /api/admin/tournaments/{id}/americano` (состояние пульта), `POST /api/tournaments/americano/{id}/preview` | `viewAdminTournament`, `preview-rounds` (ответ на POST) | A-08, A-09 |
| перенос `/tournaments/team-americano/api/**` → `/api/tournaments/team-americano/**` + `GET` состояния + `preview`/`initialize`/`finish` | view-контроллер | A-10, A-11 |
| перенос `/tournaments/team-playoff/api/**` → `/api/tournaments/team-playoff/**` + REST для команд (`add/update/delete/import`), `init-qualification`, `init-playoff`, `regenerate-playoff`, результатов (сейчас дублируются формой и API) | view-контроллер | A-12 |
| `GET /api/admin/players`, `GET /api/admin/players/{id}`, `PATCH .../nivel`, `DELETE .../{id}`, перенос `guest-credentials` | `AdminPlayerController` | A-16, A-17 |
| `GET/POST /api/admin/telegram-pipeline` | `AdminTelegramPipelineController` | A-18 |

---

## 3. Расхождения в правах доступа (проверить до перевода экрана)

Сейчас права местами описаны дважды — URL-правилом (`/admin/**` → админ-роли) и `@PreAuthorize` — и эти описания не совпадают между view-контроллером и REST-контроллером одного и того же действия. При переводе экрана берём **права view-контроллера как эталон** (это то, что реально работает у пользователей), REST приводим к ним, и фиксируем в спеке экрана.

| Что | Сейчас в UI (view) | Сейчас в REST | Риск |
|---|---|---|---|
| Роль `CLUB_ADMIN` | работает на `/admin/**` с клубной изоляцией (LFPT-376) | в `@PreAuthorize` у `ClubController`, `TournamentController`, `OwnerController`, `AdminMetricsController` **её нет** | CLUB_ADMIN получит 403 на своих же экранах после перевода |
| Роль `OWNER` | фигурирует в URL-правиле `/admin/**`, в `customAuthenticationSuccessHandler`, в `hasRole('OWNER')` (`POST /torneo/{id}/delete`, `/admin/export`) и в `@PreAuthorize` King of Court | — | **Такой роли не существует**: `OwnerRole` = SUPER_ADMIN, ORGANIZER, ADMIN, CLUB_ADMIN, `Owner.getAuthorities()` выдаёт только их. Проверки на `OWNER` — мёртвые, `POST /torneo/{id}/delete` недоступен никому (F-09). В SPA роль `OWNER` не использовать |
| Правка клуба | `POST /admin/clubs/{id}/edit` — админ-роли | `PUT /api/clubs/{id}` — только SUPER_ADMIN | ORGANIZER/CLUB_ADMIN потеряют правку |
| Admin-страницы Americano/Team-*/Team Playoff | `anyRequest().authenticated()` — **видны любому игроку** (issue #391) | мутации закрыты `@PreAuthorize` | в SPA закрыть и страницы (роут-гард), и `GET`-эндпоинты состояния |
| Публичные live-страницы | `/tournaments/americano/*` и др. — `permitAll` | `GET /api/tournaments/americano/**`, `/tournaments/team-*/api/**` — **требуют входа** | аноним не увидит live-табло в SPA → нужен §2.2 |
| Турниры | `/torneos`, `/torneo/**` — `permitAll` | `GET /api/tournaments/**` — требует входа | то же |
| JSON-регистрация | — | `POST /players/api/registro` — требует входа (в `permitAll` опечатка пути, F-07) | регистрация из SPA невозможна без фикса |

---

## 4. Соглашения (целевые) и что сейчас

### 4.1. Что уже работает и сохраняется

| Что | Как сейчас | Для SPA |
|---|---|---|
| Сессия | `JSESSIONID` (Tomcat, in-memory) + remember-me cookie (`TokenBasedRememberMeServices`, общий для формы и Google) | Без изменений, same-origin (ADR-0005) |
| CSRF | `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler` (без маскирования) + фильтр, форсирующий выдачу cookie `XSRF-TOKEN` на каждом ответе | Клиент читает `XSRF-TOKEN` и шлёт `X-XSRF-TOKEN` в каждом мутирующем запросе — ровно то, что сейчас делает `csrf.js` |
| CSRF-исключения | запросы с `Authorization`, `/api/auth/**`, `/ws/**`, `/test/telegram/**` | `/test/telegram/**` — см. F-00 в [findings.md](findings.md) |
| JWT | `/api/auth/login` → access 24ч + refresh 7д | Не используется SPA. Остаётся для мобильных клиентов |
| CORS | allowlist: `localhost:8080/8081/3000`, `epadel.org`, `1-padel.com` (+www), credentials | Не нужен (same-origin). Для локальной разработки — прокси Vite, а не CORS |
| Rate limit | `RateLimitFilter`: для `/api/**` → `429` JSON `{success,message,retryAfter}` + `Retry-After`; для браузерных путей — редиректы | SPA обрабатывает `429`. Ветка GENERAL сейчас редиректит **и `/api/**`** (F-10) — исправить |
| Локаль | `AcceptHeaderLocaleResolver` (es по умолчанию, es/ru/en). `preferredLocale` игрока — только для писем | SPA шлёт `Accept-Language` = выбранный язык интерфейса; серверные сообщения об ошибках локализуются по нему (ADR-0009) |

### 4.2. Что изменить на бэкенде (Фаза 0)

1. **`/api/**` без входа → `401` JSON, а не `302` на `/login`.** Сейчас `exceptionHandling` задаёт только `accessDeniedHandler`; для неаутентифицированных срабатывает дефолтный entry point формы логина. Нужен `defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(401), "/api/**")`.
2. **Логин для SPA.** `POST /login` сейчас отвечает `302` на `/admin` / `/players/dashboard` / `/login?error=...`. Для запросов с `Accept: application/json` success/failure-хендлеры отвечают `200 {user}` / `401 {code}` (ADR-0005). Rate limit на `POST /login` сейчас тоже редирект — для JSON-запроса нужен `429`.
3. **`GET /api/auth/session`** — один формат для обеих ролей: `{authenticated, userType: PLAYER|OWNER, id, fullName, email, roles[], clubId?, hasValidContact?, preferredLocale?}`. Текущий `/api/auth/me` отдаёт два разных DTO и `401` строкой `"No autenticado"` — оставить для мобильных, SPA его не использует.
4. **Единый формат ошибки.** Сейчас встречаются: `{success:false, message}` (133 `put("success"…)`), `{error: "..."}` (`GlobalExceptionHandler`), строка в теле, и **11 мест отвечают `200` с `success:false`**. Целевой формат — `ProblemDetail` (RFC 9457, встроен в Spring 6): `{type, title, status, detail, code, errors?}`, где `code` — стабильный машинный код для i18n на клиенте (`TOURNAMENT_FULL`, `CONTACT_REQUIRED`…). Решение и переходный период — ADR-0011.
5. **Статусы:** ошибка валидации — `400/422`, нет прав — `403`, не найдено — `404`, конфликт состояния турнира (раунд уже начат и т.п.) — `409`. Никогда `200` с `success:false`.
6. **OpenAPI.** `springdoc-openapi` **не подключён** (в `SecurityConfig` открыты `/swagger-ui/**` и `/v3/api-docs/**`, но зависимости нет). Подключить в Фазе 0; `/v3/api-docs` — источник генерации TS-типов (ADR-0004). В проде Swagger UI — только SUPER_ADMIN.
7. **Никаких JPA-сущностей в ответах.** Сейчас в модель страниц местами кладутся сущности (`PlayerPadel` — с хешем пароля, `TournamentKingOfCourt`, `AmericanoMatch`); `POST /api/king-of-court/tournaments/{id}/initialize` **уже сейчас** отдаёт сущность `TournamentKingOfCourt` в JSON. Только MapStruct-DTO (как требует CLAUDE.md).

### 4.3. Именование новых эндпоинтов

- Всё новое — под `/api/`. Разделение по потребителю: `/api/public/**` (анонимно, только чтение, кешируемо), `/api/players/me/**` (текущий игрок), `/api/admin/**` (админ-роли, с клубной изоляцией для CLUB_ADMIN), доменные `/api/tournaments/{id}/...` — для общих ресурсов.
- **Версия в URL не вводится** (`/api/v1` не нужен, пока клиент один и деплоится вместе с бэкендом). Вопрос возвращается, когда выйдет мобильное приложение со своим циклом релизов — тогда ломающие изменения публичного API делаются через новые пути, а не правкой старых.
- **Эндпоинты «на страницу»** (`/api/admin/tournaments/{id}` со всем, что нужно пульту) допустимы и предпочтительны для первой отрисовки тяжёлых экранов — один запрос вместо шести. Действия остаются гранулярными.

---

## 5. WebSocket

| | King of Court | Team Playoff |
|---|---|---|
| Эндпоинт | `/ws` (SockJS, `setAllowedOriginPatterns("*")`) | тот же |
| Топик | `/topic/tournament/{kingId}` *(в CLAUDE.md ошибочно `/topic/king-of-court/{tournamentId}`)* | `/topic/team-playoff/{tournamentId}` |
| Типы сообщений | `RESULT_SAVED`, `ROUND_COMPLETED`, `NEXT_ROUND_STARTED`, `TOURNAMENT_FINISHED`, `STATE_UPDATED` | `MATCH_CREATED`, `MATCH_COMPLETED`, `MATCH_UPDATED`, `MATCH_REMOVED`, `TEAM_ADVANCED`, `TOURNAMENT_FINISHED` |
| Кто слушает | L-01, A-07 | L-06, A-12 |
| Клиент сейчас | `sockjs-client@1` + `stompjs@2.3.3` с jsDelivr | то же |

- Канал **только сервер → клиент** (нет ни одного `@MessageMapping`), все мутации — через REST. Сохраняем.
- Брокер `enableSimpleBroker` in-memory — ок для одного инстанса (решение 2026-09-06: остаёмся на одном монолите).
- Americano / Team Americano — **без realtime**, только перезагрузка. В React — поллинг `state` раз в N секунд на публичных страницах (ADR-0010), WebSocket — позже, если понадобится.
- Тело сообщений — сейчас разные Java-объекты (`Object matchData`). Для SPA сообщение — только сигнал «что изменилось» + id; клиент перечитывает `state` через REST (TanStack Query `invalidate`). Так не нужно типизировать полезную нагрузку каждого события и нельзя «разъехаться» с REST-состоянием.

---

