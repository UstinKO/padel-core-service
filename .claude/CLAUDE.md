# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**padel-core-service** — Spring Boot 3.x монолит для управления падель-турнирами. UI рендерится на сервере через Thymeleaf. Целевой рынок — Аргентина (валюта ARS, валидация телефона +54).

**Stack:** Java 17 (source) / Java 21 (Docker/CI), Spring Boot 3.5.14, PostgreSQL 15, Liquibase, Spring Security (JWT + form login + Google OAuth2), Spring WebSocket (STOMP/SockJS), Micrometer + Prometheus + Grafana + Loki, Bucket4j, MapStruct, Lombok.

**Server security stack:** Nginx (rate limiting, DDoS protection, Cloudflare real_ip_module) + CrowdSec (behavioral IDS/IPS, iptables bouncer) + UFW + Cloudflare CDN (orange cloud для `1-padel.com`). Подробнее — [.claude/SECURITY.md](.claude/SECURITY.md). Каталог атак, за счёт чего каждая останавливается, и открытые риски — [.claude/THREATS.md](.claude/THREATS.md).

**Git-flow и код-ревью:** ветка от `master` вида `feature/LFPT-<номер issue>`, обязательная локальная проверка перед коммитом, коммит без push/PR (пушит и ревьюит пользователь сам) — [.claude/GIT_WORKFLOW.md](.claude/GIT_WORKFLOW.md). Стандарты качества кода (DRY/KISS/SOLID, соответствие ТЗ, известные архитектурные ловушки проекта) — [.claude/CODE_REVIEW.md](.claude/CODE_REVIEW.md).

**Spec-Driven Development:** проект разрабатывается по спекам, не по интуиции. [specs/MASTER.md](../specs/MASTER.md) — единый источник истины по бизнес-логике продукта (роли, форматы турниров, статусы регистрации, инварианты). Любая новая фича начинается со спеки в `specs/features/LFPT-XXX-*.md` (шаблон — `specs/features/_template.md`), согласованной с MASTER.md, и только потом переходит в код. Клиентские запросы на доработку оформляются в `specs/requests/` по шаблону `specs/requests/_template.md` и перерабатываются в полную фиче-спеку до начала реализации. Перед любой задачей по фиче — сначала прочитать соответствующую спеку и MASTER.md, не додумывать поведение системы. Есть автономный конвейер `/develop-feature` (Analyst → Developer → Tester → Architect, роли в `.claude/agents/`), который проводит фичу от клиентского запроса до PR — см. [.claude/GIT_WORKFLOW.md §8](GIT_WORKFLOW.md) про его права/ограничения.

**Переход на микросервисы:** монолит сейчас готовится к горизонтальному масштабированию (Docker Swarm, 2 реплики/сервис) и последующему выделению сервисов (Payments → Notifications → Bracket → Identity → King of Court → Americano → Tournament-core), с прицелом на мобильные приложения и новые продукты. Полный поэтапный план, известные архитектурные "мины" под несколько реплик и карта связей между доменами — [docs/microservices-migration-plan.md](../docs/microservices-migration-plan.md).

---

## Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests (requires PostgreSQL)
./mvnw clean verify

# Run locally (uses application.yml defaults: localhost:5432/player_padel_db)
./mvnw spring-boot:run

# Start full stack with observability (app + postgres + loki + prometheus + grafana)
docker compose up -d

# Required env vars for local run (if not using defaults):
# DB_URL, DB_USERNAME, DB_PASSWORD, MAIL_USERNAME, MAIL_PASSWORD,
# JWT_SECRET, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, SITE_KEY, SECRET_KEY
```

Тесты поднимают контекст Spring и требуют реального PostgreSQL. CI-pipeline в PR подключает сервис-контейнер postgres с кредами `epadel_user/test` к базе `player_padel_db`.

---

## Navigation Map (URL → Template → Controller)

### Public Pages

| URL | Template | Controller |
|-----|----------|------------|
| `GET /` | `index.html` | `HomeController` |
| `GET /login` | `login.html` | `HomeController` |
| `GET /torneos` | `torneos.html` | `TorneosController` |
| `GET /torneo/{id}` | `tournament-details.html` | `TournamentViewController` |
| `GET /tournaments/king-of-court/{kingId}` | `king-of-court-view.html` | `KingOfCourtViewController` (по ID `TournamentKingOfCourt`) |
| `GET /torneo/{tournamentId}/king-of-court` | `king-of-court-view.html` | `KingOfCourtViewController` (по ID родительского турнира) |
| `GET /tournaments/americano/{tournamentId}` | `tournaments/americano/view.html` | `AmericanoViewController` |
| `GET /tournaments/team-americano/{tournamentId}` | `tournaments/team-americano/view.html` | `TeamAmericanoViewController` |
| `GET /tournaments/team-playoff/{tournamentId}` | `tournaments/team-playoff/view.html` | `TeamPlayoffViewController` |
| `GET /clubs` | `clubs/list.html` | `ClubViewController` |
| `GET /clubs/{id}` | `clubs/view.html` | `ClubViewController` |
| `GET /ranking` | `ranking.html` | `RankingController` |
| `GET /players/registro` | `registro.html` | `PlayerPadelController` |
| `GET /players/confirmar-email` | `waitlist-confirmation.html` | `ConfirmacionEmailController` |
| `GET /cookies` | `legal/cookies.html` | `LegalController` (нет префикса `/legal` — класс без `@RequestMapping`) |
| `GET /privacidad` | `legal/privacidad.html` | `LegalController` |
| `GET /terminos` | `legal/terminos.html` | `LegalController` |
| `GET /waitlist/confirm` | `waitlist-confirmation.html` | `WaitlistController` |
| `GET /double-registration/complete` | — (redirect) | `PartnerRegistrationController` |
| `GET /double-registration/accept-pair` | — (redirect) | `PartnerRegistrationController` |

### Player Area (`ROLE_PLAYER`)

| URL | Template | Описание |
|-----|----------|----------|
| `GET /players/dashboard` | `players/dashboard.html` | Дашборд игрока |
| `GET /perfil` | `players/perfil.html` | Профиль |
| `POST /perfil/actualizar` | — (redirect) | Обновление профиля |
| `GET /recuperar-password` | — | Форма сброса пароля |
| `POST /recuperar-password/solicitar` | — | Запрос письма для сброса |
| `POST /recuperar-password/confirmar` | — | Подтверждение нового пароля |
| `POST /players/tournaments/{tournamentId}/register` | — (redirect) | Регистрация на турнир |
| `POST /players/tournaments/{tournamentId}/cancel` | — (redirect) | Отмена одиночной регистрации |
| `POST /players/tournaments/{tournamentId}/cancel-double` | — (redirect) | Отмена парной регистрации |

### Admin Panel (`ROLE_OWNER / SUPER_ADMIN / ORGANIZER`)

| URL | Template | Описание |
|-----|----------|----------|
| `GET /admin` | `admin/panel.html` | Главная панель |
| `GET /admin/tournaments` | `admin/tournaments/list.html` | Список турниров |
| `GET /admin/tournaments/new` | `admin/tournaments/form.html` | Форма создания турнира |
| `GET /admin/tournaments/{id}` | `admin/tournaments/details.html` | Детали турнира + список участников |
| `GET /admin/tournaments/{id}/edit` | `admin/tournaments/form.html` | Редактирование турнира |
| `GET /admin/tournaments/{id}/copy` | `admin/tournaments/form.html` | Копирование турнира |
| `GET /admin/clubs` | `admin/clubs/list.html` | Список клубов |
| `GET /admin/clubs/new` | `admin/clubs/form.html` | Форма создания клуба |
| `GET /admin/clubs/{id}` | `admin/clubs/details.html` | Детали клуба |
| `GET /admin/clubs/{id}/edit` | `admin/clubs/form.html` | Редактирование клуба |
| `GET /admin/players` | `admin/players/list.html` | Список игроков |
| `GET /admin/players/{id}` | `admin/players/details.html` | Детали игрока |
| `GET /admin/tournaments/king-of-court/{kingId}` | `admin/tournaments/king-of-court.html` | Управление King of Court |
| `GET /admin/tournaments/{tournamentId}/payments` | `admin/tournaments/payments.html` | Управление платежами турнира |
| `GET /admin/export` | — (Excel file) | Экспорт всех данных в Excel |

### Americano Admin Pages

| URL | Template | Описание |
|-----|----------|----------|
| `GET /tournaments/americano/admin/{tournamentId}` | `admin/americano/tournament.html` | Americano турнир (реальный путь — не `/admin/americano/...`) |
| `GET /tournaments/team-americano/admin/{tournamentId}` | `admin/americano/tournament-double.html` | Team Americano |
| `GET /tournaments/team-playoff/admin/{tournamentId}` | `admin/americano/tournament-playoff.html` | Плей-офф |
| `POST /tournaments/americano/{tournamentId}/preview-rounds` | `admin/americano/preview-rounds.html` | Предпросмотр раундов — только по сабмиту формы конфигурации, прямого GET-URL нет |
| `POST /tournaments/team-americano/{tournamentId}/preview-double-rounds` | `admin/americano/preview-double-rounds.html` | Предпросмотр командных раундов — та же логика, только по сабмиту формы |

**Мёртвый роут (не падает, LFPT-314):** `GET /tournaments/americano/admin/{tournamentId}/preview` не используется нигде в UI (нет ссылок ни в одном шаблоне/JS). Раньше для неинициализированного турнира падал (`TemplateInputException` на несуществующий `admin/americano/initialize`); теперь просто редиректит — на `/admin/tournaments/{tournamentId}`, если турнир не инициализирован, на `/tournaments/americano/admin/{tournamentId}`, если уже инициализирован. Рабочий предпросмотр — через `POST .../preview-rounds` (форма на странице деталей турнира).

**Не мёртвый роут, но раньше падал (LFPT-316):** `GET /tournaments/americano/{tournamentId}/initialize` технически публичный (не под `/admin/`), прямых ссылок в UI на него нет, но реально достижим — catch-блок `POST /tournaments/americano/{tournamentId}/preview-rounds` при любом исключении из `AmericanoService.previewRounds(...)` редиректит именно сюда. Раньше для неинициализированного турнира падал (`TemplateInputException` на несуществующий `tournaments/americano/initialize`); теперь редиректит на `/admin/tournaments/{tournamentId}` (та же рабочая страница с формой конфигурации/предпросмотра/инициализации, откуда и приходит форма `preview-rounds`). Две другие ветки метода (неверный тип турнира → `/tournaments/{tournamentId}`, уже инициализирован → `/tournaments/americano/{tournamentId}`) не изменились.

### Internal / Test Pages

| URL | Template | Описание |
|-----|----------|----------|
| `GET /test/tournaments` | `test/tournaments.html` | Управление тестовыми турнирами (регистрация игроков, создание пар) |

---

## REST API Endpoints

### Auth API (`/api/auth`)

| Method | URL | Описание |
|--------|-----|----------|
| POST | `/api/auth/login` | Логин, возвращает JWT access + refresh токены |
| POST | `/api/auth/refresh` | Обновление access-токена по refresh-токену |
| GET | `/api/auth/me` | Данные текущего авторизованного пользователя |

### Players API (`/players/api`, `/api/players`)

| Method | URL | Описание |
|--------|-----|----------|
| POST | `/players/registro` | Регистрация (form) |
| POST | `/players/api/registro` | Регистрация (JSON) |
| GET | `/players/api` | Список всех игроков |
| GET | `/players/api/{id}` | Игрок по ID |
| GET | `/players/api/confirmar-email` | Подтверждение email по токену |
| PATCH | `/api/players/me/contact` | Обновление контакта (телефон / Telegram) |
| GET | `/api/players/me/contact-status` | Проверка наличия контакта |

### Tournaments API (`/api/tournaments`)

| Method | URL | Описание |
|--------|-----|----------|
| GET | `/api/tournaments` | Все турниры |
| GET | `/api/tournaments/upcoming` | Предстоящие турниры |
| GET | `/api/tournaments/{id}` | Турнир по ID |
| GET | `/api/tournaments/club/{clubId}` | Турниры клуба |
| GET | `/api/tournaments/status/{status}` | Турниры по статусу |
| GET | `/api/tournaments/search` | Поиск турниров |
| GET | `/api/tournaments/my` | Турниры текущего пользователя |
| POST | `/api/tournaments` | Создать турнир |
| PUT | `/api/tournaments/{id}` | Обновить турнир |
| PATCH | `/api/tournaments/{id}/status` | Изменить статус |
| DELETE | `/api/tournaments/{id}` | Удалить турнир |

### Double Tournament Registration API (`/api/tournaments/double`)

| Method | URL | Описание |
|--------|-----|----------|
| POST | `/api/tournaments/double/{tournamentId}/register` | Регистрация пары — создаёт запись для первого игрока, отправляет приглашение второму |
| POST | `/api/tournaments/double/confirm` | Подтверждение второго игрока по токену |
| POST | `/api/tournaments/double/{tournamentId}/register-solo` | Одиночная регистрация с намерением найти пару на месте |
| POST | `/api/tournaments/double/{tournamentId}/add-partner` | Добавить партнёра к уже созданной одиночной регистрации |
| GET | `/api/tournaments/double/{tournamentId}/looking-for-partner` | Список игроков, ищущих пару на турнир |
| POST | `/api/tournaments/double/{tournamentId}/propose-pair/{targetRegistrationId}` | Предложить объединиться в пару другому игроку без пары |
| POST | `/api/tournaments/double/accept-pair` | Принять предложение объединиться в пару |

### King of Court API (`/api/king-of-court`)

| Method | URL | Описание |
|--------|-----|----------|
| POST | `/api/king-of-court/tournaments/{tournamentId}/initialize` | Инициализировать KoC турнир |
| GET | `/api/king-of-court/tournaments/{kingId}/state` | Текущее состояние (раунды, корты, счёт) |
| GET | `/api/king-of-court/tournaments/{kingId}/ranking` | Итоговый рейтинг |
| POST | `/api/king-of-court/matches/result` | Сохранить результат матча |
| PUT | `/api/king-of-court/matches/result/{resultId}` | Обновить результат матча |
| POST | `/api/king-of-court/tournaments/{kingId}/next-round` | Перейти к следующему раунду |
| POST | `/api/king-of-court/tournaments/{kingId}/rollback` | Откатить последний раунд |
| POST | `/api/king-of-court/tournaments/{kingId}/finish` | Завершить турнир |
| POST | `/api/king-of-court/tournaments/{kingId}/youtube` | Сохранить ссылку на YouTube |
| GET | `/api/king-of-court/tournaments/{kingId}/players/{playerId}/history` | История матчей игрока |
| GET | `/api/king-of-court/debug/tournaments/{kingId}/check-players` | Debug: проверка состава игроков |
| GET | `/api/king-of-court/ping` | Health check |

### Americano API (`/api/tournaments/americano`)

| Method | URL | Описание |
|--------|-----|----------|
| POST | `/{tournamentId}/register/{playerId}` | Регистрация игрока |
| DELETE | `/{tournamentId}/cancel/{playerId}` | Отмена регистрации |
| POST | `/{tournamentId}/initialize` | Инициализировать Americano |
| GET | `/{tournamentId}/initialized` | Проверить инициализацию |
| GET | `/{tournamentId}/rounds` | Список раундов |
| GET | `/rounds/{roundId}` | Раунд по ID |
| POST | `/rounds/{roundId}/start` | Начать раунд |
| POST | `/rounds/{roundId}/complete` | Завершить раунд |
| GET | `/{tournamentId}/matches` | Все матчи |
| GET | `/rounds/{roundId}/matches` | Матчи раунда |
| GET | `/matches/{matchId}` | Матч по ID |
| POST | `/matches/{matchId}/result` | Сохранить результат |
| GET | `/{tournamentId}/ranking` | Рейтинг |
| GET | `/{tournamentId}/ranking/simple` | Упрощённый рейтинг |
| GET | `/{tournamentId}/players/{playerId}/stats` | Статистика игрока |
| GET | `/{tournamentId}/players/count` | Количество игроков |
| POST | `/{tournamentId}/players/{playerId}/dropout` | Выбыл из турнира |
| POST | `/{tournamentId}/finish` | Завершить турнир |
| GET | `/{tournamentId}/preview-rounds` | Предпросмотр раундов |
| PUT | `/rounds/{roundId}/points-limit` | Обновить лимит очков |

### Clubs API (`/api/clubs`)

| Method | URL | Описание |
|--------|-----|----------|
| GET | `/api/clubs` | Все клубы |
| GET | `/api/clubs/active` | Активные клубы |
| GET | `/api/clubs/{id}` | Клуб по ID |
| GET | `/api/clubs/nombre/{nombre}` | По названию |
| GET | `/api/clubs/zona/{zona}` | По зоне |
| GET | `/api/clubs/search` | Поиск |
| POST | `/api/clubs` | Создать клуб |
| PUT | `/api/clubs/{id}` | Обновить клуб |
| DELETE | `/api/clubs/{id}` | Мягкое удаление |
| DELETE | `/api/clubs/{id}/hard` | Жёсткое удаление |

### Owners API (`/api/owners`)

| Method | URL | Описание |
|--------|-----|----------|
| GET | `/api/owners/super-admin` | Данные суперадмина |
| GET | `/api/owners/{id}` | Владелец по ID |
| PUT | `/api/owners/{id}` | Обновить владельца |

### Admin Action Endpoints (form POST)

| Method | URL | Описание |
|--------|-----|----------|
| POST | `/admin/tournaments` | Создать турнир |
| POST | `/admin/tournaments/{id}/edit` | Обновить турнир |
| POST | `/admin/tournaments/{id}/status` | Изменить статус |
| POST | `/admin/tournaments/{id}/delete` | Удалить турнир |
| POST | `/admin/tournaments/{id}/deactivate` | Деактивировать |
| POST | `/admin/tournaments/{tournamentId}/move-to-waitlist/{playerId}` | Перенести в лист ожидания |
| POST | `/admin/tournaments/{tournamentId}/move-to-main/{playerId}` | Перевести в основной состав |
| POST | `/admin/clubs` | Создать клуб |
| POST | `/admin/clubs/{id}/edit` | Обновить клуб |
| POST | `/admin/clubs/{id}/toggle-status` | Включить/отключить клуб |
| POST | `/admin/clubs/{id}/delete` | Удалить клуб |
| POST | `/admin/players/{id}/toggle-status` | Активировать/деактивировать игрока |
| POST | `/admin/tournaments/king-of-court/start` | Запустить KoC турнир |
| POST | `/admin/tournaments/king-of-court/{kingId}/finish` | Завершить KoC турнир |
| POST | `/admin/tournaments/king-of-court/{kingId}/next-round` | Следующий раунд KoC |
| POST | `/admin/tournaments/king-of-court/{kingId}/youtube` | Сохранить YouTube ссылку |
| POST | `/admin/tournaments/{tournamentId}/matches/generate` | Сгенерировать сетку матчей |
| POST | `/admin/tournaments/{tournamentId}/matches/{matchId}` | Обновить результат матча |

### Test / Internal Endpoints (`/test/tournaments`)

| Method | URL | Описание |
|--------|-----|----------|
| GET | `/test/tournaments` | Страница управления тестовыми турнирами |
| POST | `/{tournamentId}/register-test-players` | Зарегистрировать тестовых игроков (ID 1–50) |
| POST | `/{tournamentId}/clear-test-players` | Очистить тестовые регистрации |
| POST | `/{tournamentId}/register-real-players` | Зарегистрировать реальных игроков по ID |
| POST | `/{tournamentId}/register-pair` | Зарегистрировать пару из базы |
| POST | `/{tournamentId}/register-guest-pair` | Зарегистрировать пару (один/оба не в базе) |
| POST | `/{tournamentId}/remove-player/{playerId}` | Удалить игрока с турнира |
| GET | `/{tournamentId}/stats` | Статистика турнира |
| GET | `/{tournamentId}/stats-enhanced` | Расширенная статистика + список пар |
| GET | `/{tournamentId}/players` | Список зарегистрированных игроков |
| GET | `/available-players` | Доступные игроки для регистрации |
| GET | `/available-players-for-pairs` | Доступные игроки для создания пар |
| GET | `/available-pairs` | Список уже зарегистрированных пар |
| GET | `/search-players` | Поиск игроков по имени/телефону |
| GET | `/debug/registrations/{tournamentId}` | Debug: регистрации турнира |
| GET | `/debug/player-count` | Debug: количество игроков |
| GET | `/debug/first-players` | Debug: первые записи |
| POST | `/create-test-player` | Создать одного тестового игрока |
| POST | `/create-test-players-bulk` | Массовое создание тестовых игроков |
| POST | `/delete-test-players-bulk` | Массовое удаление тестовых игроков |
| GET | `/test-players-count` | Количество тестовых игроков |

### Other APIs

| Method | URL | Описание |
|--------|-----|----------|
| POST | `/api/cookies/accept` | Принять все cookie |
| POST | `/api/cookies/reject` | Отклонить cookie |
| POST | `/api/cookies/customize` | Настроить cookie |
| GET | `/api/admin/email-stats` | Статистика отправки email |
| GET | `/api/admin/email-daily-count` | Количество писем за день |
| POST | `/admin/tournaments/{tournamentId}/payments/save` | Зафиксировать платёж вручную (админом) |

### WebSocket

| Endpoint | Topic | Описание |
|----------|-------|----------|
| `/ws` (SockJS) | `/topic/king-of-court/{tournamentId}` | Real-time обновления King of Court |

---

## Architecture

### Two User Types

Both implement `UserDetails` and live in separate tables:

| Entity | Table | Roles |
|--------|-------|-------|
| `PlayerPadel` | `player_padel_db` | `ROLE_PLAYER` |
| `Owner` | `owners_db` | `ROLE_OWNER`, `ROLE_SUPER_ADMIN`, `ROLE_ORGANIZER` |

`CompositeUserDetailsService` пробует `OwnerUserService` первым, затем `PlayerUserDetailsService`. Важно для form login, JWT-фильтра и remember-me.

### Authentication Flow

- **Web UI**: form login → Spring session cookie. При успехе: admin → `/admin`, player → `/players/dashboard`.
- **API**: `JwtAuthenticationFilter` извлекает Bearer-токен и устанавливает `SecurityContext`. `JwtService` выпускает access (24h) и refresh (7d) токены с claims: `userId`, `fullName`, `role`, `tokenType`.
- **Google OAuth2**: `CustomOAuth2UserService` создаёт/обновляет `PlayerPadel` при первом входе; `OAuth2AuthenticationSuccessHandler` выпускает JWT и делает redirect.
- **Rate limiting**: `RateLimitFilter` (Bucket4j + Guava cache) блокирует IP, превышающие порог попыток регистрации.

### Tournament Formats

Enum `TournamentType` определяет четыре формата, у каждого свой сервис:

| Format | Service | Key models |
|--------|---------|------------|
| Одиночный / парный bracket | `BracketService` | `Match`, `MatchStatus` |
| King of Court | `KingOfCourtService` | `TournamentKingOfCourt`, `KingOfCourtRound`, `KingOfCourtCourt`, `KingOfCourtPlayerStats` |
| Americano | `AmericanoService` | `AmericanoRound`, `AmericanoMatch`, `AmericanoPlayer` |
| Team Americano | `TeamAmericanoService` | `AmericanoTeam`, `AmericanoRound` |

King of Court шлёт real-time состояние на `/topic/king-of-court/{tournamentId}` через `WebSocketService`.

### Registration Status Flow

`TournamentRegistration.status` (enum `RegistrationStatus`). Бизнес-правила флоу (когда что меняется, cron-логика листа ожидания, лимит приглашений) — см. [specs/MASTER.md § Статусы регистрации](../specs/MASTER.md#статусы-регистрации-на-турнир). Технически: `TournamentService.checkExpiredInvitations()` (cron `0 */10 * * * *`) переводит следующего WAITLIST-игрока в WAITLIST_INVITED.

### Double Tournament Registration Flow

Технические endpoint'ы: `POST /api/tournaments/double/{tournamentId}/register` → `GET /double-registration/complete?token=...` (`PartnerRegistrationController`) → `POST /api/tournaments/double/confirm`. Бизнес-описание флоу — см. [specs/MASTER.md § Флоу парной регистрации](../specs/MASTER.md#флоу-парной-регистрации).

### Package Layout

```
controller/
├── (root)        # AuthController, TournamentController, ClubController, OwnerController,
│                 # PlayerPadelController, WaitlistController, DoubleTournamentRegistrationController,
│                 # PartnerRegistrationController, AdminMetricsController, GlobalExceptionHandler
├── view/         # Публичные Thymeleaf-страницы
│   └── americano/
├── player/       # Дашборд игрока, профиль, сброс пароля
├── admin/        # Панель администратора
├── api/          # REST JSON endpoints
│   └── americano/
└── test/         # Test-only: DataExportController, TestTournamentController

service/
├── AmericanoService, TeamAmericanoService, TeamPlayoffService
├── BracketService, KingOfCourtService, MatchService
├── TournamentService, TournamentNotificationService
├── PlayerService, OwnerService, ClubService
├── EmailService, PasswordResetService, PaymentService
├── RankingService, RecaptchaService, WebSocketService
├── SoloRegistrationSchedulerService  # регистрация "ищу пару" + автосведение пар по расписанию
├── TelegramService, TelegramBotPoller, TelegramReminderScheduler
└── (security) JwtService, CompositeUserDetailsService, CustomOAuth2UserService

model/
├── PlayerPadel, Owner, Club, Tournament, TournamentRegistration
├── Match, Ranking, Payment, PasswordResetToken
├── TournamentKingOfCourt, KingOfCourtRound, KingOfCourtCourt,
│   KingOfCourtPlayerStats, KingOfCourtMatchResult, CourtTeam
├── americano/ → AmericanoRound, AmericanoMatch, AmericanoPlayer, AmericanoTeam
└── enums/ → TournamentType, RegistrationStatus, TournamentStatus,
             Modalidad, Nivel, MatchStatus, PaymentStatus, OwnerRole, …

repository/
├── TournamentRepository, TournamentRegistrationRepository
├── PlayerRepository, OwnerRepository, ClubRepository
├── MatchRepository, RankingRepository, PaymentRepository
├── TournamentKingOfCourtRepository, KingOfCourtRound/Court/PlayerStats/MatchResultRepository
└── americano/ → AmericanoRound/Match/Player/TeamRepository
```

### Database Tables

| Table | Entity | Описание |
|-------|--------|----------|
| `player_padel_db` | `PlayerPadel` | Игроки |
| `owners_db` | `Owner` | Владельцы/администраторы |
| `club_db` | `Club` | Клубы |
| `tournaments_db` | `Tournament` | Турниры |
| `tournament_registrations_db` | `TournamentRegistration` | Регистрации (со статусом) |
| `matches_db` | `Match` | Матчи bracket-турниров |
| `ranking_db` | `Ranking` | Рейтинг игроков |
| `payments_db` | `Payment` | Платежи (без реального платёжного шлюза — фиксируются вручную админом) |
| `password_reset_tokens_db` | `PasswordResetToken` | Токены сброса пароля |
| `tournament_king_of_court_db` | `TournamentKingOfCourt` | KoC конфигурация |
| `king_of_court_round_db` | `KingOfCourtRound` | Раунды KoC |
| `king_of_court_court_db` | `KingOfCourtCourt` | Корты KoC |
| `court_players_db` | — (M:N) | Игроки на корте KoC |
| `court_teams_db` | `CourtTeam` | Команды на корте KoC |
| `king_of_court_player_stats_db` | `KingOfCourtPlayerStats` | Статистика KoC |
| `king_of_court_match_result_db` | `KingOfCourtMatchResult` | Результаты KoC |
| `americano_rounds_db` | `AmericanoRound` | Раунды Americano |
| `americano_matches_db` | `AmericanoMatch` | Матчи Americano |
| `americano_players_db` | `AmericanoPlayer` | Игроки Americano |
| `americano_teams_db` | `AmericanoTeam` | Команды Team Americano |

### Database Migrations

Все изменения схемы — YAML-файлы в `src/main/resources/db/changelog/versions/`, подключены через `changelog-master.yaml`. Текущая версия: v1.41. `ddl-auto = none` — только Liquibase. Новые файлы добавлять с инкрементальным номером и регистрировать в `changelog-master.yaml`.

### Email Templates

Все письма — Thymeleaf-шаблоны в `src/main/resources/templates/email/`:

| Файл | Когда отправляется |
|------|--------------------|
| `bienvenida.html` | После регистрации нового игрока |
| `confirmacion.html` | Подтверждение email |
| `tournament-confirmation.html` | Подтверждение регистрации на турнир |
| `new-partner-invitation.html` | Приглашение второго игрока в пару |
| `vacancy-invitation.html` | Приглашение из листа ожидания (с дедлайном) |
| `waitlist-notification.html` | Уведомление о попадании в лист ожидания |
| `no-spots-left.html` | Уведомление что мест нет |
| `recuperar-password.html` | Ссылка для сброса пароля |

### Observability

Custom AOP-аннотации → Micrometer:
- `@Timed` → `TimedAspect` → `Timer`
- `@Counted` → `CountedAspect` → `Counter`
- `@TrackErrors` → `TrackErrorsAspect` → error counter

Prometheus scrapes `/actuator/prometheus`. Логи — структурированный JSON (logstash-logback-encoder), отправляются в Loki через loki4j appender. Grafana визуализирует всё.

Grafana доступна по адресу `https://epadel.org/grafana/`. Дашборды:

| Дашборд | Что показывает |
|---------|----------------|
| JVM Micrometer | Heap, GC, threads, CPU |
| SpringBoot APM | HTTP метрики, latency, error rate |
| Application Logs | Логи Spring Boot в реальном времени (Loki, фильтр по уровню и тексту) |
| Email Business Metrics | Письма по типам, дневной лимит (300), ошибки — `/grafana/d/email-business-metrics/` |
| CrowdSec Security | Активные блокировки IP, алерты по сценариям, SSH/HTTP атаки — `/grafana/d/crowdsec-security/` |

Email Prometheus-метрики: `email_daily`, `email_daily_limit`, `email_sent_total{type}`, `email_errors_total`, `email_rejected_total`. CrowdSec метрики: `cs_active_decisions`, `cs_alerts`, `cs_bucket_poured_total`.

### Registration Anti-Abuse

Слои защиты регистрации: reCAPTCHA v3 (`RecaptchaService`) → honeypot (`@NoBotPattern` / `BotPatternValidator`) → проверка disposable-email доменов → IP rate limit (Guava + Bucket4j). Prod настройки жёстче dev.

На уровне сети (до приложения): Cloudflare CDN (для `1-padel.com`) + Nginx rate limiting (5r/s на `/login` и `/api/auth/`) + CrowdSec автоблокировка по поведению (видит реальные IP благодаря `real_ip_module`).

### Entity–DTO Mapping

Все преобразования entity → DTO через MapStruct-маперы в `mapper/`. Никогда не маппить вручную в контроллерах или сервисах. Маперы — Spring-компоненты (`componentModel = "spring"`). Lombok должен отрабатывать до MapStruct — обеспечивается `lombok-mapstruct-binding` в POM.

---

## Deployment

Push в `master` запускает `.github/workflows/deploy.yml`: сборка JAR → SCP на сервер в `/opt/padel-app/temp/` → пересборка Docker-образа (`docker compose build --no-cache padel-app`) → перезапуск контейнера → health-check `/actuator/health`. При неудаче — автоматический rollback из `app.jar.backup`. На сервере запущен полный `docker-compose.yml` стек.

### Серверный стек (production, `75.119.140.175`)

```
[UFW / iptables]
  └─ CrowdSec iptables bouncer (crowdsec-blacklists-0 ipset → DROP)
       └─ Nginx 1.18 (:443 SSL, rate limiting, geo whitelist)
            ├─ /grafana/ → Grafana :3000 (Docker)
            ├─ /ws       → Spring Boot :8080 (Docker, WebSocket)
            ├─ /api/     → Spring Boot :8080 (Docker)
            └─ /         → Spring Boot :8080 (Docker)
                              ├─ PostgreSQL :5432 (Docker)
                              ├─ Loki :3100 (Docker)
                              └─ Prometheus :9090 (Docker, + scrapes CrowdSec :6060)
```

Все Docker-порты привязаны к `127.0.0.1` (localhost binding) — прямой доступ из интернета закрыт. Подробнее — [.claude/SECURITY.md](.claude/SECURITY.md).

**Доступ для анализа:** при необходимости диагностики (проверить состояние контейнеров, логи, `current_version.txt`, health-check и т.п.) — SSH на сервер напрямую: `ssh root@75.119.140.175`. Для рутинной проверки статуса деплоя предпочтительнее `gh run view <run-id> --log` (шаг "Health check" в `deploy.yml` печатает то же самое, но без SSH) — см. `.claude/commands/develop-feature.md § Шаг 7`. Read-only диагностика по SSH не требует отдельного согласования; любые изменения на сервере — по-прежнему только с отдельным согласованием (см. `GIT_WORKFLOW.md §7`).

---

## Key Conventions

- `@Transactional(readOnly = true)` — дефолт на уровне класса в большинстве сервисов; write-методы переопределяют через `@Transactional`.
- Язык домена смешанный: поля entity на испанском (`nombre`, `fechaInicio`, `modalidad`), комментарии и логи на русском.
- `PlayerPadel.hasValidContact()` — регистрация на турнир требует телефон `+54` **или** Telegram username.
- `TestTournamentController` работает напрямую через JDBC (`DataSource`), **не через JPA**. Используется только для внутреннего тестирования организатором.
- При добавлении новой Liquibase-миграции: создать файл `v1.XX-<description>.yaml` в `versions/` и добавить `include` в конец `changelog-master.yaml`.
- Таблица регистраций называется `tournament_registrations_db` (не `tournament_registration`).
- **i18n**: публичный UI, admin-панель и email-шаблоны переведены на `es`/`ru`/`en` через Spring `AcceptHeaderLocaleResolver` (auto-detect по `Accept-Language`, без переключателя). Ключи — в `src/main/resources/i18n/messages_{es,ru,en}.properties`, JS-строки — в `static/js/i18n/messages-{es,ru,en}.js`. Подробности и статус — [docs/i18n/README.md](../docs/i18n/README.md).
