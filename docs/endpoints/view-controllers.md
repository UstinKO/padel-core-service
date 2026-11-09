# View-контроллеры — карта Thymeleaf SSR

Этот файл документирует все серверные контроллеры, которые рендерят HTML через Thymeleaf.  
**Назначение файла:** при выделении фронтенда в отдельный SPA (React/Vue/Next.js) этот список показывает, что именно нужно убрать из монолита, какие данные каждая страница получает от сервера, и что должно стать REST API.

---

## Принцип разделения при выносе фронта

При переходе на SPA:
1. Все `@Controller` (MVC) → удалить или заменить `@RestController` (JSON).
2. Все `@ModelAttribute` / `Model.addAttribute()` → перенести в REST API, которые фронт вызывает через `fetch`.
3. Все `RedirectAttributes` / flash-сообщения → заменить на JSON-ответы с кодами.
4. Все POST-формы (Thymeleaf form submit) → стать AJAX-вызовами к существующим или новым REST-эндпоинтам.
5. Папка `src/main/resources/templates/` → удалить полностью.
6. Зависимость `spring-boot-starter-thymeleaf` → убрать из `pom.xml`.

---

## Публичные страницы (без авторизации)

### `HomeController` → `GET /`
**Файл:** `controller/view/HomeController.java`  
**Шаблон:** `index.html`

**Данные для страницы:**
| Атрибут модели | Источник | Будущий REST-эндпоинт |
|----------------|----------|----------------------|
| `upcomingTournaments` | `TournamentService.getActiveTournamentsForHome()` | `GET /api/tournaments/upcoming` (уже есть) |
| `tournamentsJson` | JSON-строка тех же турниров для JS-фильтров | то же |
| `totalTournaments` | `allTournaments.size()` | в ответе `/api/tournaments/upcoming` |
| `totalPlayers` | `PlayerService.contarJugadoresActivos()` | `GET /api/stats` (нового создать) |
| `totalClubs` | захардкожено `50` | `GET /api/stats` |
| `isAuthenticated` | `SecurityContext` | токен на фронте |
| `userName` | `PlayerService.obtenerJugadorPorEmail()` | `GET /api/auth/me` (уже есть) |
| `isOwner` | роли из `Authentication` | `GET /api/auth/me` |

**Query params:** `registroExitoso`, `email` — flash-сообщения после регистрации. В SPA заменяются на redirect с state.

**Замечания:**
> `totalClubs = 50` — жёсткая заглушка. Нужна реальная статистика из `ClubService`.

---

### `HomeController` → `GET /login`
**Шаблон:** `login.html`

Рендерит форму Spring Security form login.  
В SPA: фронт рендерит форму, отправляет `POST /api/auth/login`, получает JWT.  
При переходе эту страницу и соответствующий Spring Security form login можно убрать, оставив только JWT-флоу.

**Редиректы при уже авторизованном:** `→ /admin` (Owner) или `→ /players/dashboard` (Player).

---

### `TorneosController` → `GET /torneos`
**Файл:** `controller/view/TorneosController.java`  
**Шаблон:** `torneos.html`

**Данные:**
| Атрибут | Источник | REST-аналог |
|---------|----------|-------------|
| `tournaments` | `TournamentService.getAllActiveTournaments()` | `GET /api/tournaments` с фильтром |
| `tournamentsJson` | JSON для JS-фильтров | то же |
| `totalTournaments` | `size()` | в ответе |
| `userName`, `player` | `PlayerService.obtenerJugadorPorEmail()` | `GET /api/auth/me` |

**Замечания:**
> Создаёт новый `ObjectMapper()` вместо инжектированного бина — нарушает конфигурацию сериализации.

---

### `TournamentViewController` → `GET /torneo/{id}`
**Файл:** `controller/view/TournamentViewController.java`  
**Шаблон:** `tournament-details.html`

Самый тяжёлый view-контроллер — агрегирует данные из 5+ сервисов.

**Данные:**
| Атрибут | Источник |
|---------|----------|
| `tournament` | `TournamentService.getActiveTournamentById()` |
| `registrations` | `TournamentService.getRegistrationsByTournament()` |
| `isRegistered`, `userRegistration`, `isInWaitlist` | `TournamentService.getRegistration()` |
| `isInitialized`, `activePlayers`, `leaderName`, `totalRounds`, `completedRounds` | `AmericanoService.*` |
| `isTeamPlayoffFormat`, `isPlayoffQualStarted`, `isPlayoffStarted`, `tournamentTeams` | `TeamPlayoffService.*` |
| `kingOfCourtActive`, `kingTournament` | `TournamentKingOfCourtRepository` + `KingOfCourtService` |
| `availableSpots` | `tournament.getDisponibles()` |
| `tournamentJson` | JSON-строка турнира для JS |

**Замечания:**
> Этот метод — главный кандидат для разбивки на несколько REST-эндпоинтов при переходе на SPA. Фронт сделает 2-3 параллельных `fetch()` вместо одной серверной сборки.

> Также содержит `POST /torneo/{id}/delete` с `@PreAuthorize("hasRole('OWNER')")` — смешение публичной view-страницы и admin-действия.

---

### `KingOfCourtViewController` → `GET /tournaments/king-of-court/{kingId}`
**Файл:** `controller/view/KingOfCourtViewController.java`  
**Шаблон:** `king-of-court-view.html`

Публичная страница просмотра турнира King of Court в реальном времени (через WebSocket).

**Данные:**
| Атрибут | Источник |
|---------|----------|
| `kingData` | `KingOfCourtService.getCurrentState()` |
| `tournamentId` | из `TournamentKingOfCourt.tournament.id` |
| `tournamentName` | из entity |
| `isViewer` | `true` (константа) |

**Также:** `GET /torneo/{tournamentId}/king-of-court` — альтернативный URL по `tournamentId` (находит `kingId` по tournament).

В SPA: начальное состояние загружается через `GET /api/king-of-court/tournaments/{kingId}/state`, обновления — через WebSocket `/topic/king-of-court/{tournamentId}`.

---

### `ClubViewController` → `GET /clubs`, `GET /clubs/{id}`
**Файл:** `controller/view/ClubViewController.java`  
**Шаблоны:** `clubs/list.html`, `clubs/view.html`

**Данные:**
| URL | Атрибут | Источник | REST-аналог |
|-----|---------|----------|-------------|
| `/clubs` | `clubs` | `ClubService.getActiveClubs()` | `GET /api/clubs/active` (уже есть) |
| `/clubs/{id}` | `club` | `ClubService.getClubById()` | `GET /api/clubs/{id}` (уже есть) |

Оба метода добавляют `isAuthenticated` из `SecurityContext`.

---

### `RankingController` → `GET /ranking`
**Файл:** `controller/view/RankingController.java`  
**Шаблон:** `ranking.html`

**Данные:**
| Атрибут | Источник |
|---------|----------|
| `ranking` | `RankingService.getRankingCompleto()` |
| `topRanking` | `RankingService.getTopRanking(10)` |
| `rankingJson`, `topRankingJson` | JSON-строки для JS |
| `totalJugadores` | `ranking.size()` |

В SPA: `GET /api/ranking` (нужно создать REST-эндпоинт для `RankingService`).

---

### `LegalController` → `/terminos`, `/privacidad`, `/cookies`
**Файл:** `controller/view/LegalController.java`  
**Шаблоны:** `legal/terminos.html`, `legal/privacidad.html`, `legal/cookies.html`

Статические страницы. Передают только `year = LocalDate.now().year`.  
В SPA: можно вынести как статические HTML/MDX страницы фронтенда без обращения к бэку.

**Замечания:**
> URL в PROJECT.md указаны с префиксом `/legal/...` (`/legal/cookies`, `/legal/privacidad`, `/legal/terminos`), но в коде маппинги без префикса (`/cookies`, `/privacidad`, `/terminos`). Возможно расхождение с реальными URL.

---

### Americano — `AmericanoViewController` → `/tournaments/americano/...`
**Файл:** `controller/view/americano/AmericanoViewController.java`  
**Шаблоны:** `tournaments/americano/view.html`, `tournaments/americano/rounds.html`, и др.

Этот контроллер — **гибрид**: содержит как публичные view-страницы, так и admin-операции, что нарушает SRP. При выносе фронта нужна реструктуризация.

| URL | Тип | Шаблон / Редирект |
|-----|-----|--------------------|
| `GET /{tournamentId}` | публичная | `tournaments/americano/view` |
| `GET /{tournamentId}/ranking` | публичная | `tournaments/americano/ranking` |
| `GET /{tournamentId}/players/{playerId}` | публичная | `tournaments/americano/player-stats` |
| `GET /{tournamentId}/rounds` | публичная | `tournaments/americano/rounds` |
| `GET /rounds/{roundId}` | публичная | `tournaments/americano/round` |
| `GET /matches/{matchId}` | публичная | `tournaments/americano/match` |
| `GET /{tournamentId}/initialize` | admin view | `tournaments/americano/initialize` |
| `POST /{tournamentId}/initialize` | admin action | редирект |
| `POST /{tournamentId}/preview-rounds` | admin action | `admin/americano/preview-rounds` |
| `POST /rounds/{roundId}/start` | admin action | редирект |
| `POST /rounds/{roundId}/complete` | admin action | редирект |
| `POST /matches/{matchId}/result` | admin action | редирект |
| `POST /{tournamentId}/players/{playerId}/dropout` | admin action | редирект |
| `POST /{tournamentId}/finish` | admin action | редирект |
| `GET /admin/{tournamentId}` | admin view | `admin/americano/tournament` |
| `GET /admin/{tournamentId}/preview` | admin view | `admin/americano/initialize` |
| `POST /admin/{tournamentId}/initialize` | admin action | редирект |
| `POST /initialize` | admin action (из детальной карточки) | редирект |

**Замечания:**
> Контроллер имеет ~650 строк — нарушение SRP. При рефакторинге нужно разделить на `AmericanoPublicViewController` и `AmericanoAdminController`.

> Несколько инициализаций (`POST /{id}/initialize`, `POST /admin/{id}/initialize`, `POST /initialize`) делают похожее с небольшими отличиями — потенциальный DRY.

---

### `TeamAmericanoViewController` → `/tournaments/team-americano/...`
**Файл:** `controller/view/americano/TeamAmericanoViewController.java`  
**Шаблоны:** `tournaments/team-americano/view.html`, `admin/americano/tournament-double.html`

| URL | Тип | Шаблон |
|-----|-----|--------|
| `GET /{tournamentId}` | публичная | `tournaments/team-americano/view` |
| `GET /{tournamentId}/ranking` | публичная | `tournaments/team-americano/ranking` |
| `POST /initialize` | admin | редирект |
| `POST /preview` | admin | `admin/americano/preview-double-rounds` |
| `GET /admin/{tournamentId}` | admin | `admin/americano/tournament-double` |
| `POST /matches/{matchId}/result` | admin | редирект |
| `POST /admin/{tournamentId}/finish` | admin | редирект |
| `POST /api/matches/{matchId}/result` | REST (AJAX) | JSON |
| `GET /api/{tournamentId}/ranking` | REST (AJAX) | JSON |

**Замечания:**
> Контроллер содержит встроенные REST-методы (`@ResponseBody`) прямо рядом с MVC-методами — нарушение SRP. При выносе фронта REST-часть переносится в `TeamAmericanoApiController`.

> `toMatchDto()` и `buildRoundDtos()` дублируются с аналогичными методами в `TeamPlayoffViewController` — нарушение DRY. Нужен маппер MapStruct.

> Прямое обращение к `AmericanoRoundRepository` и `AmericanoMatchRepository` в view-контроллере — нарушение слоёв архитектуры.

---

### `TeamPlayoffViewController` → `/tournaments/team-playoff/...`
**Файл:** `controller/view/americano/TeamPlayoffViewController.java`  
**Шаблоны:** `tournaments/team-playoff/view.html`, `admin/americano/tournament-playoff.html`

| URL | Тип | Описание |
|-----|-----|---------|
| `GET /{tournamentId}` | публичная | Публичная страница (квал + playoff) |
| `GET /admin/{tournamentId}` | admin | Страница управления |
| `POST /admin/{tournamentId}/teams/add` | admin | Добавить команду |
| `POST /admin/teams/{teamId}/update` | admin | Обновить команду |
| `POST /admin/teams/{teamId}/delete` | admin | Удалить команду |
| `POST /admin/teams/{teamId}/no-show` | admin | Отметить команду «не пришла» (LFPT-0729) — блокируется, если у команды уже есть матч квалификации |
| `POST /admin/teams/{teamId}/no-show/cancel` | admin | Отменить отметку «не пришла», команда возвращается в `ACTIVE` (LFPT-0729) |
| `POST /admin/{tournamentId}/import-registrations` | admin | Импорт команд из регистраций |
| `POST /admin/{tournamentId}/init-qualification` | admin | Инициализировать квалификацию |
| `POST /admin/{tournamentId}/init-playoff` | admin | Инициализировать playoff |
| `POST /admin/qual-matches/{matchId}/result` | admin | Результат матча квалификации |
| `POST /admin/playoff-matches/{matchId}/result` | admin | Результат матча playoff |
| `POST /api/qual-matches/{matchId}/result` | REST (AJAX) | JSON |
| `POST /api/playoff-matches/{matchId}/result` | REST (AJAX) | JSON |
| `GET /api/{tournamentId}/ranking` | REST (AJAX) | JSON |

**Замечания:**
> Аналогично `TeamAmericanoViewController` — смешение MVC и REST, прямые вызовы `AmericanoMatchRepository`.

---

## Страницы игрока (`ROLE_PLAYER`)

### `PlayerDashboardController` → `GET /players/dashboard`
**Файл:** `controller/player/PlayerDashboardController.java`  
**Шаблон:** `players/dashboard.html`

**Данные:**
| Атрибут | Источник | Замечание |
|---------|----------|-----------|
| `player` | из principal | |
| `torneosInscritos` | `getActiveRegistrationsByPlayer()` | реальные данные |
| `tournamentsJson` | `getVisibleTournamentsForPlayer()` | |
| `historicalJson` | прошедшие турниры игрока | |
| `myTournamentIdsJson` | `getActiveRegistrationsByPlayer()` | |

Также этот контроллер содержит REST-методы (`@ResponseBody`):
- `POST /players/tournaments/{id}/register`
- `POST /players/tournaments/{id}/cancel`
- `POST /players/tournaments/{id}/cancel-double`
- `GET /players/tournaments/{id}/my-registration`
- `PATCH /players/api/players/me/contact`
- `GET /players/api/players/me/contact-status`

При вынесении фронта: REST-методы остаются как есть, view-метод `GET /players/dashboard` удаляется.

---

### `PerfilController` → `GET /perfil`, `POST /perfil/actualizar`
**Файл:** `controller/player/PerfilController.java`  
**Шаблон:** `players/perfil.html`

**GET /perfil** — передаёт `player` (entity из principal) в шаблон.

**POST /perfil/actualizar** — обновляет поля профиля:
- `nombre`, `apellido` — если изменились
- `telefono` — валидация `+54`, `null` если пустой
- `telegramUsername` — автодобавление `@`
- `nivelJugador` — enum `Nivel`
- Смена пароля: `currentPassword`, `newPassword`, `confirmPassword`

Все ответы — redirect на `/perfil` с flash-атрибутами.

В SPA: `GET /api/auth/me` (данные) + `PATCH /api/players/me/profile` (обновление, нужно создать) + `POST /api/players/me/password` (смена пароля, нужно создать).

---

### `PasswordResetController` → `/recuperar-password`
**Файл:** `controller/player/PasswordResetController.java`  

`GET /recuperar-password?token=...` — единственный view-метод: валидирует токен, затем делает `redirect:/?token={token}`. JS на главной показывает модальное окно.

Остальные методы (`POST /solicitar`, `POST /confirmar`) возвращают `@ResponseBody` — уже REST-совместимы.

В SPA: `GET /recuperar-password?token=...` можно заменить на `GET /api/auth/validate-reset-token?token=...` → фронт сам показывает форму при валидном токене.

---

## Служебные страницы

### `ConfirmacionEmailController` → `GET /players/confirmar-email`
**Файл:** `controller/view/ConfirmacionEmailController.java`  
**Шаблон:** нет (redirect)

Подтверждает email по `codigo` из query param. Делает `redirect:/login?confirmed=true`.

**Замечания:**
> Дублирует логику `GET /players/api/confirmar-email` (`PlayerPadelController`), но со страницей-редиректом. В идеале один контроллер — либо JSON-ответ для API, либо view с редиректом. Сейчас оба существуют параллельно.

---

### `PartnerRegistrationController` → `GET /double-registration/complete`
**Файл:** `controller/PartnerRegistrationController.java`  
**Шаблон:** нет (redirect на `/`)

Игрок 2 переходит по ссылке из email → токен обрабатывается → `redirect:/` с flash.

В SPA: `GET /double-registration/complete?token=...` делает API-вызов `POST /api/tournaments/double/confirm?token=...`, получает JSON, фронт показывает результат.

---

### `WaitlistController` → `GET /waitlist/confirm`
**Файл:** `controller/WaitlistController.java`  
**Шаблон:** `waitlist-confirmation.html`

Игрок принимает приглашение из листа ожидания по ссылке из email.

**Query param:** `registrationId`

**Данные:**
| Атрибут | Тип | Описание |
|---------|-----|----------|
| `message` | String | Текст результата |
| `type` | String | `success` / `warning` / `error` |

В SPA: `GET /api/waitlist/confirm?registrationId=...` → JSON-ответ, фронт показывает страницу результата.

---

## Admin-панель (`ROLE_OWNER` / `ROLE_SUPER_ADMIN` / `ROLE_ORGANIZER`)

### `AdminController` → `/admin/...`
**Файл:** `controller/admin/AdminController.java`  
**Шаблоны:** `admin/panel.html`, `admin/tournaments/list.html`, `admin/tournaments/form.html`, `admin/tournaments/details.html`

| URL | Метод | Шаблон |
|-----|-------|--------|
| `/admin` | GET | `admin/panel` |
| `/admin/tournaments` | GET | `admin/tournaments/list` |
| `/admin/tournaments/new` | GET | `admin/tournaments/form` |
| `/admin/tournaments` | POST | redirect |
| `/admin/tournaments/{id}` | GET | `admin/tournaments/details` |
| `/admin/tournaments/{id}/edit` | GET | `admin/tournaments/form` |
| `/admin/tournaments/{id}/edit` | POST | redirect |
| `/admin/tournaments/{id}/status` | POST | redirect |
| `/admin/tournaments/{id}/delete` | POST | redirect |
| `/admin/tournaments/{id}/deactivate` | POST | redirect |
| `/admin/tournaments/{tournamentId}/move-to-waitlist/{playerId}` | POST | JSON (`@ResponseBody`) |
| `/admin/tournaments/{tournamentId}/move-to-main/{playerId}` | POST | JSON (`@ResponseBody`) |

**Данные главной панели (`/admin`):**
- `totalPlayers`, `totalOwners`, `totalTournaments`, `totalWaitlist` — счётчики
- `recentTournaments` — последние 5 турниров
- `recentTournamentsWithFlags` — список с флагом `isOwner` для каждого турнира
- `recentPlayers` — последние 5 игроков
- `matchesInProgress`, `upcomingMatches`, `activeTournaments` — матчи

**Замечания:**
> Контроллер смешивает MVC (`@ModelAttribute`, `RedirectAttributes`) и REST (`@ResponseBody`) методы. При выносе фронта REST-методы переезжают в `TournamentController`.

> `AdminController` зависит от 8 сервисов — нарушение SRP. При рефакторинге разделить на несколько контроллеров по домену.

---

### `AdminKingOfCourtController` → `/admin/tournaments/king-of-court/...`
**Файл:** `controller/admin/AdminKingOfCourtController.java`  
**Шаблон:** `admin/tournaments/king-of-court.html`

| URL | Метод | Описание |
|-----|-------|---------|
| `/admin/tournaments/king-of-court/{kingId}` | GET | Страница управления KoC |
| `/admin/tournaments/king-of-court/start` | POST | Инициализировать KoC (вызывает `KingOfCourtService.initializeTournament`) |
| `/admin/tournaments/king-of-court/{kingId}/finish` | POST | Завершить KoC |
| `/admin/tournaments/king-of-court/{kingId}/youtube` | POST | Обновить YouTube ссылку |
| `/admin/tournaments/king-of-court/{kingId}/reset` | POST | Полный сброс турнира |
| `/admin/tournaments/king-of-court/{kingId}/next-round` | POST | Следующий раунд |

**Данные страницы управления:**
| Атрибут | Источник |
|---------|----------|
| `kingTournament` | entity `TournamentKingOfCourt` |
| `tournament` | `kingTournament.getTournament()` |
| `state` | `KingOfCourtService.getCurrentState()` |
| `admin` | `true` |

---

### `AdminClubController`, `AdminPlayerController`, `AdminMatchController`, `AdminTournamentCopyController`
**Файл:** `controller/admin/Admin*.java`

Эти контроллеры не были прочитаны полностью, но по паттерну аналогичны остальным admin-контроллерам:

| Контроллер | URL | Шаблоны |
|------------|-----|---------|
| `AdminClubController` | `/admin/clubs/...` | `admin/clubs/list.html`, `admin/clubs/form.html`, `admin/clubs/details.html` |
| `AdminPlayerController` | `/admin/players/...` | `admin/players/list.html`, `admin/players/details.html` |
| `AdminMatchController` | `/admin/tournaments/{id}/matches/...` | нет (JSON или redirect) |
| `AdminTournamentCopyController` | `/admin/tournaments/{id}/copy` | `admin/tournaments/form.html` |

---

## Тестовые контроллеры (только для внутреннего использования)

### `TestTournamentController` → `/test/tournaments`
**Файл:** `controller/test/TestTournamentController.java`  
**Шаблон:** `test/tournaments.html`

Работает напрямую через JDBC (не через JPA). Используется организатором для заполнения тестовыми данными.

**При выносе фронта:** этот контроллер остаётся в монолите — или переносится как внутренний инструмент.

### `DataExportController` → `/admin/export`
**Файл:** `controller/test/DataExportController.java`

Возвращает Excel-файл (`application/vnd.ms-excel`), не HTML. **Не view-контроллер**, оставить как есть.

---

## Шаблоны без view-контроллера

Следующие шаблоны используются как email-шаблоны — они рендерятся через `EmailService`, не через контроллеры. При выносе фронта **остаются в монолите**:

```
email/bienvenida.html
email/confirmacion.html
email/new-partner-invitation.html
email/no-spots-left.html
email/recuperar-password.html
email/tournament-confirmation.html
email/vacancy-invitation.html
email/waitlist-notification.html
```

Также фрагменты шаблонов (только для Thymeleaf `th:replace`):
```
fragments/admin-footer.html
fragments/admin-header.html
fragments/king-of-court-modals.html
```

---

## Сводная таблица: что удалить при выносе фронта

### Контроллеры — удалить целиком

| Класс | Пакет | Заменить на |
|-------|-------|-------------|
| `HomeController` | `controller/view` | REST: `/api/tournaments/upcoming`, `/api/stats` |
| `TorneosController` | `controller/view` | REST: `/api/tournaments` |
| `TournamentViewController` | `controller/view` | REST: агрегирующий эндпоинт или несколько существующих |
| `KingOfCourtViewController` | `controller/view` | REST: `/api/king-of-court/tournaments/{kingId}/state` |
| `ClubViewController` | `controller/view` | REST: `/api/clubs/*` (уже есть) |
| `RankingController` | `controller/view` | REST: `GET /api/ranking` (создать) |
| `LegalController` | `controller/view` | Статические страницы на фронте |
| `ConfirmacionEmailController` | `controller/view` | REST: `/api/players/confirm-email` |
| `AmericanoViewController` | `controller/view/americano` | Разделить: REST-часть + удалить view |
| `TeamAmericanoViewController` | `controller/view/americano` | Разделить: REST-часть + удалить view |
| `TeamPlayoffViewController` | `controller/view/americano` | Разделить: REST-часть + удалить view |
| `PlayerDashboardController` | `controller/player` | Только REST-методы оставить |
| `PerfilController` | `controller/player` | REST: `PATCH /api/players/me/profile` (создать) |
| `PasswordResetController` | `controller/player` | Только REST-методы оставить |
| `PartnerRegistrationController` | `controller` | Удалить: заменить `GET` ссылкой на SPA-страницу |
| `WaitlistController` | `controller` | REST: `GET /api/waitlist/confirm` (создать) |
| `AdminController` | `controller/admin` | REST: view-методы удалить, `@ResponseBody` перенести |
| `AdminKingOfCourtController` | `controller/admin` | Перенести POST в `KingOfCourtApiController` |
| `AdminClubController` | `controller/admin` | REST: `/api/clubs/*` (расширить) |
| `AdminPlayerController` | `controller/admin` | REST: `/api/players/*` (расширить) |
| `AdminMatchController` | `controller/admin` | Проверить, есть ли REST-аналог |
| `AdminTournamentCopyController` | `controller/admin` | REST: `POST /api/tournaments/{id}/copy` (создать) |
| `PaymentManagementController` | `controller/view` | REST: `GET/POST /api/tournaments/{id}/payments` |

### Папки — удалить

```
src/main/resources/templates/         (весь Thymeleaf)
  исключение: email/**               (оставить для серверной генерации писем)
```

### Зависимости из `pom.xml` — убрать

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

### Spring Security — скорректировать

- Убрать конфиг form login (`.formLogin(...)`) из `SecurityConfig`.
- Убрать `remember-me` если используется.
- Оставить только JWT-фильтр + CORS-настройки для SPA-домена.
- `CompositeUserDetailsService` оставить для JWT.
