# Контракт: экраны

> Сгенерировано из кода 2026-09-28 (ветка `master`, коммит `eeee06c`). Факты (URL, контроллер, атрибуты модели, формы, fetch, скрипты) извлечены скриптом из `controller/**` и `templates/**`; колонки «Назначение», «Целевой API» и «Фаза» — ручной анализ. Легенда и правила — в [README контракта](README.md).

Всего: **40 экранов** + **8 редирект-флоу** без собственных страниц.

## Сводная таблица

| ID | Экран | Доступ | Шаблон | Фаза |
|---|---|---|---|---|
| [P-01](#p-01) | Лендинг | публичный | `index` | Ф5 (SEO, см. ADR-0008) |
| [P-02](#p-02) | Вход | публичный | `login` | Ф4 (до этого React-экраны используют текущую страницу входа — сессия общая) |
| [P-03](#p-03) | Регистрация игрока | публичный | `registro` | Ф4 |
| [P-04](#p-04) | Список турниров | публичный | `torneos` | Ф5 (SEO) |
| [P-05](#p-05) | Карточка турнира | публичный (+ действия для игрока) | `tournament-details` | Ф5 (SEO, самая важная для OG) |
| [P-06](#p-06) | Список клубов | публичный | `clubs/list` | Ф5 |
| [P-07](#p-07) | Клуб | публичный | `clubs/view` | Ф5 |
| [P-08](#p-08) | Рейтинг | публичный | `ranking` | Ф5 |
| [P-09](#p-09) | Юридические страницы | публичный | `legal/cookies, legal/privacidad, legal/terminos` | Ф5 |
| [P-10](#p-10) | Подтверждение из листа ожидания | публичный (по токену) | `waitlist-confirmation` | Ф4 (после выяснения) |
| [P-11](#p-11) | Принять предложение пары | публичный (по токену из письма) | `accept-pair` | Ф4 |
| [P-12](#p-12) | Ошибки | публичный | `error/403, error/rate-limit, error/tournament-not-found` | Ф0 (экраны ошибок внутри SPA; Thymeleaf-версии живут до Ф6) |
| [L-01](#l-01) | King of Court — зритель | публичный | `king-of-court-view` | Ф3 |
| [L-02](#l-02) | Americano — публичная | публичный | `tournaments/americano/view` | Ф3 |
| [L-03](#l-03) | Americano — рейтинг | публичный | `tournaments/americano/ranking` | Ф3 |
| [L-04](#l-04) | Team Americano — публичная | публичный | `tournaments/team-americano/view` | Ф3 |
| [L-05](#l-05) | Team Americano — рейтинг | публичный | `— (шаблона нет)` | — |
| [L-06](#l-06) | Team Playoff — публичная | публичный | `tournaments/team-playoff/view` | Ф3 |
| [U-01](#u-01) | Дашборд игрока | ROLE_PLAYER (админа редиректит в /admin) | `players/dashboard` | Ф4 |
| [U-02](#u-02) | Профиль | ROLE_PLAYER | `players/perfil` | Ф4 |
| [A-01](#a-01) | Админ-панель | админ-роли | `admin/panel` | Ф2 |
| [A-02](#a-02) | Турниры — список | админ-роли | `admin/tournaments/list` | Ф2 |
| [A-03](#a-03) | Турнир — форма (создание/правка/копия) | админ-роли | `admin/tournaments/form` | Ф2 |
| [A-04](#a-04) | Турнир — детали | админ-роли | `admin/tournaments/details` | Ф2 |
| [A-05](#a-05) | Турнир — сетка матчей (Bracket) | админ-роли | `admin/tournaments/matches` | Ф2 |
| [A-06](#a-06) | Платежи турнира | админ-роли | `admin/tournaments/payments` | Ф2 |
| [A-07](#a-07) | King of Court — пульт | админ-роли | `admin/tournaments/king-of-court` | Ф3 |
| [A-08](#a-08) | Americano — пульт | админ-роли* | `admin/americano/tournament` | Ф3 |
| [A-09](#a-09) | Americano — предпросмотр раундов | админ-роли | `admin/americano/preview-rounds` | Ф3 |
| [A-10](#a-10) | Team Americano — пульт | админ-роли* | `admin/americano/tournament-double` | Ф3 |
| [A-11](#a-11) | Team Americano — предпросмотр | админ-роли | `admin/americano/preview-double-rounds` | Ф3 |
| [A-12](#a-12) | Team Playoff — пульт | админ-роли* | `admin/americano/tournament-playoff` | Ф3 (последним в фазе) |
| [A-13](#a-13) | Клубы — список | админ-роли | `admin/clubs/list` | Ф1 (пилот) |
| [A-14](#a-14) | Клубы — форма | админ-роли | `admin/clubs/form` | Ф1 (пилот) |
| [A-15](#a-15) | Клубы — детали | админ-роли | `admin/clubs/details` | Ф1 (пилот) |
| [A-16](#a-16) | Игроки — список | админ-роли | `admin/players/list` | Ф2 |
| [A-17](#a-17) | Игрок — детали | админ-роли | `admin/players/details` | Ф2 |
| [A-18](#a-18) | Telegram-конвейер | SUPER_ADMIN (иначе редирект) | `admin/telegram-pipeline` | Ф2 |
| [A-19](#a-19) | Экспорт в Excel | SUPER_ADMIN, OWNER | `— (файл)` | Ф2 |
| [T-01](#t-01) | Тестовые турниры | SUPER_ADMIN | `test/tournaments` | не переносим |


---

## Публичные страницы

### P-01

**Лендинг** · доступ: публичный · фаза: Ф5 (SEO, см. ADR-0008)

| Маршрут | Контроллер |
|---|---|
| `GET /` | `HomeController#homePage` |

**Что делает:** Главная: статистика, ближайшие турниры (из `tournamentsJson`), модалки сброса пароля по `?token=`, баннер cookie. OG-теги есть — важен для шаринга.

**Шаблон:** `templates/index.html` — 617 строк, inline JS 18 строк, i18n-ключей 80

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `emailRegistrado`, `isAuthenticated`, `isOwner`, `registroExitoso`, `totalClubs`, `totalPlayers`, `totalTournaments`, `tournamentsJson`, `upcomingTournaments`, `userName`

**Параметры запроса:** `RequestParam:email:String?`, `RequestParam:registroExitoso:Boolean?`

**Формы (действия):**

- `GET (JS-обработчик)`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `home.js`: рендер ближайших турниров из tournamentsJson (без fetch)
  - `password-reset.js`: POST /recuperar-password/solicitar; POST /recuperar-password/confirmar; читает ?token= на / и /login → модалка нового пароля
  - `cookie-consent.js`: POST /api/cookies/accept; POST /api/cookies/reject; POST /api/cookies/customize?analytics=&marketing=; document.cookie

**SEO/шаринг:** `og:title`, `og:description`, `og:image`, `og:url`, `description`, `twitter:card`

**Целевой API:** NEW `GET /api/public/home` (счётчики + ближайшие турниры) или переиспользовать `GET /api/tournaments/upcoming` + NEW `GET /api/public/stats`. Сброс пароля — существующие `POST /recuperar-password/*`.

### P-02

**Вход** · доступ: публичный · фаза: Ф4 (до этого React-экраны используют текущую страницу входа — сессия общая)

| Маршрут | Контроллер |
|---|---|
| `GET /login` | `HomeController#loginPage` |

**Что делает:** Форма Spring Security (`POST /login`, `username`/`password`/`remember-me`), кнопка Google (`/oauth2/authorization/google`), алерты по `?error=`, `?error=not_confirmed`, `?error=oauth2`, `?confirmed=true`, `?logout`, `?registered`, `?reset=success`. Уже залогиненных редиректит в `/admin` или `/players/dashboard`.

**Шаблон:** `templates/login.html` — 231 строк, inline JS 28 строк, i18n-ключей 23

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `error`, `logout`, `registered`

**Параметры запроса:** `RequestParam:error:String?`, `RequestParam:logout:String?`, `RequestParam:registered:String?`

**Формы (действия):**

- `POST @{/login}` — поля: `username`, `password`, `remember-me`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `auth.js`: клиентская валидация login/registro
  - `password-reset.js`: POST /recuperar-password/solicitar; POST /recuperar-password/confirmar; читает ?token= на / и /login → модалка нового пароля

**SEO/шаринг:** `og:title`, `og:description`, `og:image`, `og:url`, `description`, `twitter:card`

**Целевой API:** Логин через тот же `POST /login`, но с JSON-ответом для XHR (ADR-0005). NEW `GET /api/auth/session` (кто я + роли + флаги) вместо двух форматов `/api/auth/me`.

### P-03

**Регистрация игрока** · доступ: публичный · фаза: Ф4

| Маршрут | Контроллер |
|---|---|
| `GET /players/registro` | `PlayerPadelController#mostrarFormularioRegistro` |
| `POST /players/registro` | `PlayerPadelController#registrarJugador` |

**Что делает:** reCAPTCHA v3 (`g-recaptcha-response`, скрипт google.com), honeypot `website`, disposable-email, rate limit. После успеха — `redirect:/?welcome=true`.

**Шаблон:** `templates/registro.html` — 364 строк, inline JS 58 строк, i18n-ключей 30

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `errorMessage`, `recaptchaEnabled`, `recaptchaSiteKey`, `registroRequest`

**Параметры запроса:** `ModelAttribute:registroRequest:RegistroRequestDto`, `RequestParam:g-recaptcha-response:String?`, `RequestParam:website:String?`

**Сервер вставляет в JS** (`th:inline`): `recaptchaEnabled=${recaptchaEnabled}`, `[[recaptchaEnabled]]`

**Формы (действия):**

- `POST @{/players/registro}` — поля: `nombre`, `apellido`, `email`, `telefono`, `telegramUsername`, `nivelJugador`, `password`, `confirmPassword`, `terminos`, `website`

**Подключённый JS и что он вызывает:**

  - `auth.js`: клиентская валидация login/registro

**Внешние скрипты:** `https://www.google.com/recaptcha/api.js?hl=es`

**SEO/шаринг:** `og:title`, `og:description`, `og:image`, `og:url`, `description`, `twitter:card`

**Целевой API:** Существующий JSON `POST /players/api/registro` **анонимно недоступен**: в `permitAll` прописан `/api/players/registro`, а реальный путь `/players/api/registro` (F-07). Починить путь + проверить паритет проверок с form-версией (honeypot, reCAPTCHA, rate limit — `RateLimitFilter` сейчас узнаёт только `POST /players/registro`). NEW `GET /api/public/config` (recaptchaEnabled, siteKey).

### P-04

**Список турниров** · доступ: публичный · фаза: Ф5 (SEO)

| Маршрут | Контроллер |
|---|---|
| `GET /torneos` | `TorneosController#verTorneos` |

**Что делает:** Все турниры отдаются одним `tournamentsJson`, фильтрация на клиенте (`torneos.js`). Показывает имя пользователя, если вошёл.

**Шаблон:** `templates/torneos.html` — 331 строк, inline JS 8 строк, i18n-ключей 45

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `isAuthenticated`, `player`, `totalTournaments`, `tournaments`, `tournamentsJson`, `userName`

**Формы (действия):**

- `GET (JS-обработчик)`

**Подключённый JS и что он вызывает:**

  - `torneos.js`: фильтры/рендер карточек из tournamentsJson (без fetch)

**SEO/шаринг:** `description`

**Целевой API:** `GET /api/tournaments` / `/search` уже есть, **но требуют входа** (`/api/**` → authenticated, публичных исключений для них нет). Нужен публичный доступ (NEW `GET /api/public/tournaments` или permitAll на GET) + сверить поля DTO с тем, что рисует `torneos.js` (статус мест, клуб, уровень).

### P-05

**Карточка турнира** · доступ: публичный (+ действия для игрока) · фаза: Ф5 (SEO, самая важная для OG)

| Маршрут | Контроллер |
|---|---|
| `GET /torneo/{id}` | `TournamentViewController#viewTournament` |

**Что делает:** Самая нагруженная публичная страница: 26 атрибутов модели (флаги регистрации текущего игрока, лист ожидания, пары, поиск пары, ссылки на live-страницы формата). Регистрация одиночная/парная/"ищу пару", предложения пары, проверка контакта перед регистрацией. OG-теги — ссылку на турнир шарят в WhatsApp/Telegram и шлют в письмах.

**Шаблон:** `templates/tournament-details.html` — 1121 строк, inline JS 106 строк, i18n-ключей 127

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `activeDoubleRegistrations`, `activePlayers`, `availableSpots`, `completedRounds`, `confirmedPairsRegistrations`, `confirmedRegistrations`, `currentUserIsSolo`, `currentUserPlayerId`, `currentUserSoloRegId`, `isAuthenticated`, `isInWaitlist`, `isInitialized`, `isPlayoffQualStarted`, `isPlayoffStarted`, `isRegistered`, `isTeamPlayoffFormat`, `kingOfCourtActive`, `kingTournament`, `leaderName`, `player`, `registrations`, `totalRounds`, `tournament`, `tournamentJson`, `tournamentTeams`, `userRegistration`

**Параметры запроса:** `PathVariable:id:Long`

**Сервер вставляет в JS** (`th:inline`): `_addPartnerTournamentId=${tournament.id}`, `[[tournament.id]]`, `[[tournament]]`, `[[isAuthenticated]]`

**Формы (действия):**

- `GET (JS-обработчик)`

**fetch из inline-скриптов:** ``/api/tournaments/double/${_addPartnerTournamentId}/add-partner``

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `contact-check.js`: GET /api/players/me/contact-status; PATCH /api/players/me/contact
  - `tournament-details.js`: POST /players/tournaments/{id}/register; GET /api/players/search; POST /api/tournaments/double/{id}/register; POST /api/tournaments/double/{id}/register-solo; POST /api/tournaments/double/{id}/add-partner; GET /api/tournaments/double/{id}/looking-for-partner; POST /api/tournaments/double/{id}/propose-pair/{targetRegId}; шаринг: WhatsApp/Facebook/Twitter/mailto

**SEO/шаринг:** `og:title`, `og:description`, `og:image`, `og:url`, `description`, `twitter:card`

**Целевой API:** NEW `GET /api/tournaments/{id}/details` (публичная часть) + NEW `GET /api/tournaments/{id}/my-status` (всё про текущего игрока: isRegistered, isInWaitlist, currentUserIsSolo, userRegistration…). Действия — существующие `/players/tournaments/*` и `/api/tournaments/double/*`.

### P-06

**Список клубов** · доступ: публичный · фаза: Ф5

| Маршрут | Контроллер |
|---|---|
| `GET /clubs` | `ClubViewController#listClubs` |

**Что делает:** Простой список.

**Шаблон:** `templates/clubs/list.html` — 66 строк, inline JS 0 строк, i18n-ключей 4, layout-фрагменты: `fragments/public-header::header`, `fragments/public-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `clubs`, `isAuthenticated`

**Целевой API:** `GET /api/clubs/active` сейчас **только для админов** — нужен NEW публичный `GET /api/public/clubs`.

### P-07

**Клуб** · доступ: публичный · фаза: Ф5

| Маршрут | Контроллер |
|---|---|
| `GET /clubs/{id}` | `ClubViewController#viewClub` |

**Что делает:** Карточка клуба.

**Шаблон:** `templates/clubs/view.html` — 78 строк, inline JS 0 строк, i18n-ключей 10, layout-фрагменты: `fragments/public-header::header`, `fragments/public-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `club`, `isAuthenticated`

**Параметры запроса:** `PathVariable:id:Long`

**Целевой API:** NEW публичный `GET /api/public/clubs/{id}` (+ турниры клуба: `GET /api/tournaments/club/{clubId}` существует, но тоже требует входа).

### P-08

**Рейтинг** · доступ: публичный · фаза: Ф5

| Маршрут | Контроллер |
|---|---|
| `GET /ranking` | `RankingController#verRanking` |

**Что делает:** Весь рейтинг — `rankingJson` + `topRankingJson`, поиск/сортировка на клиенте.

**Шаблон:** `templates/ranking.html` — 174 строк, inline JS 6 строк, i18n-ключей 20

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `ranking`, `rankingJson`, `topRanking`, `topRankingJson`, `totalJugadores`

**Подключённый JS и что он вызывает:**

  - `ranking.js`: поиск/сортировка по rankingJson (без fetch)

**SEO/шаринг:** `description`

**Целевой API:** NEW `GET /api/ranking` (отдельного REST-эндпоинта рейтинга нет).

### P-09

**Юридические страницы** · доступ: публичный · фаза: Ф5

| Маршрут | Контроллер |
|---|---|
| `GET /cookies` | `LegalController#cookies` |
| `GET /privacidad` | `LegalController#privacidad` |
| `GET /terminos` | `LegalController#terminos` |

**Что делает:** Статический текст на 3 языках, атрибут `year`. Ссылки на них из писем и футера.

**Шаблон:** `templates/legal/cookies.html` — 330 строк, inline JS 0 строк, i18n-ключей 5

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `year`

**Целевой API:** Не нужен API — статические страницы в React (текст в i18n или md).

### P-10

**Подтверждение из листа ожидания** · доступ: публичный (по токену) · фаза: Ф4 (после выяснения)

| Маршрут | Контроллер |
|---|---|
| `GET /waitlist/confirm` | `WaitlistController#showConfirmationPage` |
| `POST /waitlist/confirm` | `WaitlistController#confirmFromWaitlist` |

**Что делает:** GET показывает превью, POST подтверждает. 5 состояний (pending/success/warning/error/notfound). **Токен `waitlistConfirmationToken` нигде не генерируется** (F-06) — вероятно, флоу мёртв после авто-подтверждения (LFPT-403).

**Шаблон:** `templates/waitlist-confirmation.html` — 188 строк, inline JS 22 строк, i18n-ключей 17

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `message`, `preview`, `type`

**Параметры запроса:** `RequestParam:token:String`

**Формы (действия):**

- `POST @{/waitlist/confirm}` — поля: `token`

**SEO/шаринг:** `og:title`, `og:description`, `og:image`, `og:url`, `description`, `twitter:card`

**Целевой API:** Выяснить, жив ли флоу (F-06). Если жив — NEW `GET/POST /api/waitlist/confirm?token=`.

### P-11

**Принять предложение пары** · доступ: публичный (по токену из письма) · фаза: Ф4

| Маршрут | Контроллер |
|---|---|
| `GET /double-registration/accept-pair` | `PartnerRegistrationController#acceptPairPage` |

**Что делает:** Страница с кнопкой, JS шлёт `POST /api/tournaments/double/accept-pair?token=`. Ссылка приходит в письме `pair-proposal`.

**Шаблон:** `templates/accept-pair.html` — 118 строк, inline JS 40 строк, i18n-ключей 10

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `token`

**Параметры запроса:** `RequestParam:token:String`

**fetch из inline-скриптов:** `'/api/tournaments/double/accept-pair?token='`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN

**Целевой API:** API уже есть. Страница — тонкая React-обёртка.

### P-12

**Ошибки** · доступ: публичный · фаза: Ф0 (экраны ошибок внутри SPA; Thymeleaf-версии живут до Ф6)

| Маршрут | Контроллер |
|---|---|
| `GET /error/403` | `HomeController#accessDeniedPage` |
| `GET /error/rate-limit` | `HomeController#rateLimitPage` |
| `GET /torneo/{id}` | `TournamentViewController#viewTournament` |

**Что делает:** 403 — от `CustomAccessDeniedHandler`; rate-limit — редирект из `RateLimitFilter` с `?retry=&blocked=`; not-found — из `/torneo/{id}`.

**Шаблон:** `templates/error/403.html` — 74 строк, inline JS 0 строк, i18n-ключей 6

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `activeDoubleRegistrations`, `activePlayers`, `availableSpots`, `completedRounds`, `confirmedPairsRegistrations`, `confirmedRegistrations`, `currentUserIsSolo`, `currentUserPlayerId`, `currentUserSoloRegId`, `isAuthenticated`, `isBlocked`, `isInWaitlist`, `isInitialized`, `isPlayoffQualStarted`, `isPlayoffStarted`, `isRegistered`, `isTeamPlayoffFormat`, `kingOfCourtActive`, `kingTournament`, `leaderName`, `player`, `registrations`, `retryAfter`, `totalRounds`, `tournament`, `tournamentJson`, `tournamentTeams`, `userRegistration`

**Параметры запроса:** `PathVariable:id:Long`, `RequestParam:blocked:Boolean?`, `RequestParam:retry:int?`

**SEO/шаринг:** `robots`

**Целевой API:** В SPA — роуты-заглушки; API отдаёт 403/404/429 JSON. Для rate-limit — заголовок `Retry-After` вместо query-параметров.


---

## Публичные live-страницы форматов

### L-01

**King of Court — зритель** · доступ: публичный · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/king-of-court/{kingId}` | `KingOfCourtViewController#viewKingOfCourtByKingId` |
| `GET /torneo/{tournamentId}/king-of-court` | `KingOfCourtViewController#viewKingOfCourt` |

**Что делает:** Два URL: по `kingId` и по id турнира. Данные — `kingData` + REST `state`, обновления по WebSocket `/topic/tournament/{kingId}`, модалки истории/итогов (`fragments/king-of-court-modals`).

**Шаблон:** `templates/king-of-court-view.html` — 241 строк, inline JS 52 строк, i18n-ключей 23, layout-фрагменты: `fragments/king-of-court-modals::historyModal`, `fragments/king-of-court-modals::finalResultsModal`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `currentPlayerId`, `isViewer`, `kingData`, `tournamentId`, `tournamentName`

**Параметры запроса:** `PathVariable:kingId:Long`, `PathVariable:tournamentId:Long`

**Сервер вставляет в JS** (`th:inline`): `[[kingData.kingId]]`, `[[tournamentId]]`, `[[kingData.currentRound]]`, `[[kingData.calibrationRounds]]`, `[[kingData.maxCourts]]`, `[[kingData.isFinished]]`, `[[kingData.youtubeLink]]`, `[[currentPlayerId]]`

**fetch из inline-скриптов:** ``/api/king-of-court/tournaments/${kingId}/state?t=${Date.now()}``

**Подключённый JS и что он вызывает:**

  - `king-of-court-viewer.js`: GET /api/king-of-court/tournaments/{kingId}/state; GET /api/king-of-court/tournaments/{kingId}/players/{playerId}/history; WS /ws → /topic/tournament/{kingId}

**Внешние скрипты:** `https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js`, `https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js`

**Целевой API:** API и WS уже есть. Нужен NEW `GET /api/king-of-court/by-tournament/{tournamentId}` (резолв kingId) — сейчас это делает view-контроллер.

### L-02

**Americano — публичная** · доступ: публичный · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/americano/{tournamentId}` | `AmericanoViewController#viewTournament` |

**Что делает:** Раунды, матчи, рейтинг с сортировкой `?sortBy=&ascending=`. Без WebSocket — обновление перезагрузкой.

**Шаблон:** `templates/tournaments/americano/view.html` — 474 строк, inline JS 61 строк, i18n-ключей 29

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `activePlayers`, `ascending`, `currentPlayerId`, `isAdmin`, `isAuthenticated`, `isInitialized`, `player`, `ranking`, `rounds`, `sortBy`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`, `RequestParam:ascending:boolean?`, `RequestParam:sortBy:String?`

**Формы (действия):**


**Целевой API:** `GET /api/tournaments/americano/{id}/rounds`, `/matches`, `/ranking` уже есть. NEW сводный `GET /api/tournaments/{id}` с флагом `isInitialized` уже частично (`/initialized`).

### L-03

**Americano — рейтинг** · доступ: публичный · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/americano/{tournamentId}/ranking` | `AmericanoViewController#viewRanking` |

**Что делает:** Отдельная страница рейтинга.

**Шаблон:** `templates/tournaments/americano/ranking.html` — 212 строк, inline JS 13 строк, i18n-ключей 14

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `ascending`, `currentPlayerId`, `ranking`, `sortBy`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`, `RequestParam:ascending:boolean?`, `RequestParam:sortBy:String?`

**Целевой API:** `GET /api/tournaments/americano/{id}/ranking` уже есть.

### L-04

**Team Americano — публичная** · доступ: публичный · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/team-americano/{tournamentId}` | `TeamAmericanoViewController#viewTournament` |

**Что делает:** Раунды/матчи/рейтинг команд.

**Шаблон:** `templates/tournaments/team-americano/view.html` — 380 строк, inline JS 35 строк, i18n-ключей 24

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `currentPlayerId`, `isAuthenticated`, `isInitialized`, `player`, `ranking`, `rounds`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`

**Формы (действия):**


**Целевой API:** Есть `GET /tournaments/team-americano/api/{id}/ranking` (не под `/api/`!). NEW `GET /api/tournaments/team-americano/{id}/rounds`.

### L-05

**Team Americano — рейтинг** · доступ: публичный · фаза: —

**Что делает:** **Роут падает: шаблон `tournaments/team-americano/ranking` не существует** (F-01).

**Целевой API:** Не переносить как отдельную страницу — рейтинг уже есть на L-04.

### L-06

**Team Playoff — публичная** · доступ: публичный · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/team-playoff/{tournamentId}` | `TeamPlayoffViewController#viewPublic` |

**Что делает:** Квалификация + сетка плей-офф, zoom/экспорт PNG (`bracket-zoom-export.js`, snapdom), WebSocket `/topic/team-playoff/{id}`.

**Шаблон:** `templates/tournaments/team-playoff/view.html` — 424 строк, inline JS 42 строк, i18n-ключей 50

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `currentPlayerId`, `isAuthenticated`, `player`, `playoffRounds`, `playoffStarted`, `qualRounds`, `qualStarted`, `ranking`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`

**Сервер вставляет в JS** (`th:inline`): `[[tournament.id]]`

**Формы (действия):**


**Подключённый JS и что он вызывает:**

  - `bracket-zoom-export.js`: fit-to-width, pinch-zoom, экспорт сетки в PNG (snapdom с CDN)
  - `team-playoff-realtime.js`: WS /ws → /topic/team-playoff/{tournamentId}

**Внешние скрипты:** `https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js`, `https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js`, `https://cdn.jsdelivr.net/npm/@zumer/snapdom@3.1.1/dist/snapdom.js`

**Целевой API:** Есть `GET /tournaments/team-playoff/api/{id}/ranking`. NEW `GET /api/tournaments/team-playoff/{id}/state` (qualRounds, playoffRounds, флаги) — сейчас собирается во view-контроллере (`splitQualificationWaves` и т.п.).


---

## Кабинет игрока

### U-01

**Дашборд игрока** · доступ: ROLE_PLAYER (админа редиректит в /admin) · фаза: Ф4

| Маршрут | Контроллер |
|---|---|
| `GET /players/dashboard` | `PlayerDashboardController#dashboard` |

**Что делает:** `tournamentsJson`, `historicalJson`, `myTournamentIdsJson` встраиваются в страницу; регистрация/отмена/парная регистрация прямо с дашборда (`dashboard.js`, 1267 строк), проверка контакта (`contact-check.js`). Сюда ведут письма и Google-логин.

**Шаблон:** `templates/players/dashboard.html` — 559 строк, inline JS 19 строк, i18n-ключей 79

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `historicalJson`, `myTournamentIdsJson`, `player`, `torneosInscritos`, `tournamentsJson`

**Формы (действия):**

- `GET (JS-обработчик)`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `dashboard.js`: POST /players/tournaments/{id}/register; POST /players/tournaments/{id}/cancel; POST /players/tournaments/{id}/cancel-double; GET /players/tournaments/{id}/my-registration; GET /api/players/search?query=&tournamentId=; POST /api/tournaments/double/{id}/register; POST /api/tournaments/double/{id}/register-solo; GET /api/tournaments/active (**эндпоинта нет, функция не вызывается** — F-04)
  - `contact-check.js`: GET /api/players/me/contact-status; PATCH /api/players/me/contact

**SEO/шаринг:** `description`

**Целевой API:** NEW `GET /api/players/me/dashboard` (активные турниры, история, мои id). Действия — существующие.

### U-02

**Профиль** · доступ: ROLE_PLAYER · фаза: Ф4

| Маршрут | Контроллер |
|---|---|
| `GET /perfil` | `PerfilController#verPerfil` |

**Что делает:** Форма профиля + смена пароля + `preferredLocale` (язык писем). Flash `successMessage/errorMessage/infoMessage`.

**Шаблон:** `templates/players/perfil.html` — 318 строк, inline JS 0 строк, i18n-ключей 35, layout-фрагменты: `fragments/nivel-options::options`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `player`

**Формы (действия):**

- `POST @{/perfil/actualizar}` — поля: `telegramUsername`, `nivelJugador`, `preferredLocale`, `currentPassword`, `newPassword`, `confirmPassword`, `nombre`, `apellido`, `email`, `telefono`

**Подключённый JS и что он вызывает:**

  - `profile.js`: клиентская валидация формы профиля

**SEO/шаринг:** `description`

**Целевой API:** NEW `GET /api/players/me` + `PUT /api/players/me` + `POST /api/players/me/password`. Контакт — существующий `PATCH /api/players/me/contact`.


---

## Админ-панель

### A-01

**Админ-панель** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin` | `AdminController#adminPanel` |

**Что делает:** 13 атрибутов: счётчики, последние турниры/игроки, матчи в процессе. CLUB_ADMIN видит только свой клуб (LFPT-376).

**Шаблон:** `templates/admin/panel.html` — 247 строк, inline JS 0 строк, i18n-ключей 19, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `activeTournaments`, `adminName`, `isAdminRole`, `isSuperAdmin`, `matchesInProgress`, `recentPlayers`, `recentTournaments`, `recentTournamentsWithFlags`, `totalOwners`, `totalPlayers`, `totalTournaments`, `totalWaitlist`, `upcomingMatches`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**SEO/шаринг:** `description`

**Целевой API:** NEW `GET /api/admin/dashboard` (с учётом клубной изоляции).

### A-02

**Турниры — список** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin/tournaments` | `AdminController#listTournaments` |

**Что делает:** Фильтр `?clubId=`, деактивация/удаление формами.

**Шаблон:** `templates/admin/tournaments/list.html` — 287 строк, inline JS 0 строк, i18n-ключей 23, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `clubs`, `isAdminRole`, `isSuperAdmin`, `selectedClubId`, `tournaments`

**Параметры запроса:** `RequestParam:clubId:Long?`

**Формы (действия):**

- `GET @{/admin/tournaments}` — поля: `clubId`
- `POST @{'/admin/tournaments/' + ${item.tournament.id} + '/deactivate'}`
- `POST @{'/admin/tournaments/' + ${item.tournament.id} + '/delete'}`
- `POST @{'/admin/tournaments/' + ${item.tournament.id} + '/deactivate'}`
- `POST @{'/admin/tournaments/' + ${item.tournament.id} + '/delete'}`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**SEO/шаринг:** `description`

**Целевой API:** `GET /api/tournaments/my` есть, но без фильтра по клубу и флагов — NEW `GET /api/admin/tournaments?clubId=`. Действия — NEW `POST /api/admin/tournaments/{id}/deactivate`, `DELETE /api/admin/tournaments/{id}` (или существующий `DELETE /api/tournaments/{id}` — сверить права CLUB_ADMIN).

### A-03

**Турнир — форма (создание/правка/копия)** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `POST /admin/tournaments` | `AdminController#createTournament` |
| `GET /admin/tournaments/new` | `AdminController#newTournamentForm` |
| `GET /admin/tournaments/{id}/edit` | `AdminController#showEditForm` |
| `POST /admin/tournaments/{id}/edit` | `AdminController#updateTournament` |
| `GET /admin/tournaments/{id}/copy` | `AdminTournamentCopyController#copyTournament` |

**Что делает:** Один шаблон на три URL (`new`, `{id}/edit`, `{id}/copy`). Справочники: клубы, форматы, уровни, статусы, типы, модальности. Локале-зависимые дата/время (issue #384). Автогенерация названия (LFPT-374).

**Шаблон:** `templates/admin/tournaments/form.html` — 481 строк, inline JS 126 строк, i18n-ключей 49, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `clubs`, `genderFormats`, `isCopy`, `modalidades`, `niveles`, `tournament`, `tournamentStatuses`, `tournamentTypes`

**Параметры запроса:** `ModelAttribute:tournament:TournamentDto`, `PathVariable:id:Long`

**Формы (действия):**

- `POST @{${tournament.id == null ? '/admin/tournaments' : '/admin/tournaments/' + tournament.id + '/edit'}}` — поля: `_method`, `clubId`, `fechaInicio`, `horaInicio`, `duracion`, `generoFormato`, `categoriaNivel`, `tipo`, `modalidad`, `cupoMax`, `precio`, `moneda`, `estado`, `deadlineCancelacion`, `infoDetallada`, `contactoOrganizador`, `faqUrl`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**Целевой API:** `POST/PUT /api/tournaments` уже есть — сверить с `TournamentDto` формы. NEW `GET /api/admin/reference-data` (все enum-справочники одним запросом).

### A-04

**Турнир — детали** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin/tournaments/{id}` | `AdminController#viewTournament` |

**Что делает:** Самый большой шаблон (1856 строк): участники, лист ожидания, переносы main↔waitlist, сведение соло-игроков в пары, смена статуса, запуск KoC/Americano/Team Playoff, форма конфигурации Americano (→ preview), ссылки на все live-страницы формата.

**Шаблон:** `templates/admin/tournaments/details.html` — 1856 строк, inline JS 248 строк, i18n-ключей 138, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `activePlayers`, `isAdminRole`, `isInitialized`, `isPlayoffQualStarted`, `isPlayoffStarted`, `isSuperAdmin`, `isTeamAmericanoInitialized`, `kingOfCourtActive`, `kingOfCourtId`, `playoffTeamsCount`, `registrations`, `teamAmericanoRanking`, `tournament`

**Параметры запроса:** `PathVariable:id:Long`

**Сервер вставляет в JS** (`th:inline`): `_pairTournamentId=${tournament.id}`, `TOURNAMENT_ID=${tournament.id}`, `PLAYERS_COUNT=${tournament.inscritosActuales}`, `tournamentName=${tournament.nombre}`, `[[tournament.id]]`, `[[tournament.inscritosActuales]]`, `[[isInitialized]]`, `[[ranking != null]]`, `[[tournament.nombre]]`

**Формы (действия):**

- `POST @{'/admin/tournaments/' + ${tournament.id} + '/status'}` — поля: `status`
- `POST @{'/admin/tournaments/' + ${tournament.id} + '/status'}` — поля: `status`
- `POST @{'/admin/tournaments/' + ${tournament.id} + '/status'}` — поля: `status`
- `POST @{'/admin/tournaments/' + ${tournament.id} + '/status'}` — поля: `status`
- `POST @{'/admin/tournaments/' + ${tournament.id} + '/deactivate'}`
- `POST @{'/admin/tournaments/' + ${tournament.id} + '/delete'}`
- `POST @{'/admin/tournaments/king-of-court/start'}` — поля: `tournamentId`, `maxCourts`, `calibrationRounds`
- `POST @{'/tournaments/americano/admin/' + ${tournament.id} + '/initialize'}` — поля: `tournamentId`, `generationMode`, `pointsPerMatch`, `courts`, `totalRounds`, `rankingType`
- `POST @{'/tournaments/team-playoff/admin/' + ${tournament.id} + '/import-registrations'}`

**fetch из inline-скриптов:** ``/admin/tournaments/${_pairTournamentId}/pair-solo?regId1=${regId1}&regId2=${regId2}``, ``/admin/tournaments/${tournamentId}/move-to-waitlist/${playerId}``, ``/admin/tournaments/${tournamentId}/move-to-main/${playerId}``

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)
  - `share-panel.js`: панель "поделиться" (ссылка/QR на публичную страницу турнира)

**SEO/шаринг:** `description`

**Целевой API:** NEW `GET /api/admin/tournaments/{id}` (турнир + регистрации + флаги формата). Существующие `POST /admin/tournaments/{id}/move-to-*`, `pair-solo` → перенести под `/api/admin/...`. Статус — `PATCH /api/tournaments/{id}/status` есть.

### A-05

**Турнир — сетка матчей (Bracket)** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin/tournaments/{tournamentId}/matches` | `AdminMatchController#viewMatches` |

**Что делает:** LFPT-394: генерация сетки по выбранным игрокам, ввод сетов.

**Шаблон:** `templates/admin/tournaments/matches.html` — 245 строк, inline JS 50 строк, i18n-ключей 13, layout-фрагменты: `fragments/admin-header::header`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `bracket`, `confirmedRegistrations`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`

**Формы (действия):**

- `GET (JS-обработчик)` — поля: `playerIds`
- `POST @{'/admin/tournaments/' + ${tournament.id} + '/matches/' + ${match.id}}` — поля: `ganadorId`, `set1Equipo1`, `set1Equipo2`, `set2Equipo1`, `set2Equipo2`, `set3Equipo1`, `set3Equipo2`, `superSetEquipo1`, `superSetEquipo2`

**Целевой API:** NEW `GET /api/admin/tournaments/{id}/bracket`, `POST .../bracket/generate`, `PUT .../matches/{matchId}`.

### A-06

**Платежи турнира** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin/tournaments/{tournamentId}/payments` | `PaymentManagementController#paymentManagementPage` |

**Что делает:** Табличная форма с индексированными полями `payments[i].*`, save и save-and-add-team.

**Шаблон:** `templates/admin/tournaments/payments.html` — 314 строк, inline JS 0 строк, i18n-ключей 28, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `paymentMethods`, `paymentStatuses`, `players`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`

**Формы (действия):**

- `POST @{'/admin/tournaments/' + ${tournament.id} + '/payments/save'}` — поля: `'payments[' + ${iterStat.index} + '].registrationId'`, `'payments[' + ${iterStat.index} + '].paymentId'`, `'payments[' + ${iterStat.index} + '].hasPayment'`, `'payments[' + ${iterStat.index} + '].partnerRow'`, `registrationId`, `'payments[' + ${iterStat.index} + '].participationConfirmed'`, `'payments[' + ${iterStat.index} + '].attended'`, `'payments[' + ${iterStat.index} + '].amount'`, `'payments[' + ${iterStat.index} + '].currency'`, `'payments[' + ${iterStat.index} + '].paymentStatus'`, `'payments[' + ${iterStat.index} + '].paymentMethod'`, `'payments[' + ${iterStat.index} + '].transactionId'`, `'payments[' + ${iterStat.index} + '].notes'`

**Подключённый JS и что он вызывает:**

  - `payment-management.js`: клиентская логика формы платежей (без fetch)

**Целевой API:** NEW `GET /api/admin/tournaments/{id}/payments` + `PUT` (массив). 

### A-07

**King of Court — пульт** · доступ: админ-роли · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /admin/tournaments/king-of-court/{kingId}` | `AdminKingOfCourtController#viewTournament` |

**Что делает:** Раунды/корты/ввод результатов (модалки), next-round/rollback/finish/reset/YouTube, WebSocket.

**Шаблон:** `templates/admin/tournaments/king-of-court.html` — 262 строк, inline JS 46 строк, i18n-ключей 27, layout-фрагменты: `fragments/admin-header::header`, `fragments/king-of-court-modals::resultModal`, `fragments/king-of-court-modals::historyModal`, `fragments/king-of-court-modals::finalResultsModal`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `admin`, `kingTournament`, `state`, `tournament`

**Параметры запроса:** `PathVariable:kingId:Long`

**Сервер вставляет в JS** (`th:inline`): `kingId=${kingTournament.id}`, `tournamentName=${tournament.nombre}`, `[[kingTournament.id]]`, `[[tournament.id]]`, `[[kingTournament.currentRound]]`, `[[kingTournament.calibrationRounds]]`, `[[kingTournament.maxCourts]]`, `[[admin]]`, `[[kingTournament.isFinished]]`, `[[kingTournament.youtubeLink]]`, `[[tournament.nombre]]`

**Формы (действия):**

- `POST @{'/admin/tournaments/king-of-court/' + ${kingTournament.id} + '/reset'}`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `king-of-court.js`: GET /api/king-of-court/tournaments/{kingId}/state; GET /api/king-of-court/debug/tournaments/{kingId}/check-players; POST /api/king-of-court/tournaments/{tournamentId}/initialize; POST /api/king-of-court/matches/result; PUT /api/king-of-court/matches/result/{resultId}; POST /api/king-of-court/tournaments/{kingId}/next-round; POST /api/king-of-court/tournaments/{kingId}/rollback; POST /api/king-of-court/tournaments/{kingId}/finish; POST /api/king-of-court/tournaments/{kingId}/youtube?youtubeLink=; GET /api/king-of-court/tournaments/{kingId}/players/{playerId}/history; WS /ws → /topic/tournament/{kingId}
  - `share-panel.js`: панель "поделиться" (ссылка/QR на публичную страницу турнира)

**Внешние скрипты:** `https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js`, `https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js`

**Целевой API:** Почти всё уже в `/api/king-of-court/**`. Нет REST для `reset` и `start` (сейчас form POST в `AdminKingOfCourtController`).

### A-08

**Americano — пульт** · доступ: админ-роли* · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/americano/admin/{tournamentId}` | `AmericanoViewController#viewAdminTournament` |

**Что делает:** 655 строк inline JS: старт/завершение раундов, ввод счёта, лимит очков раунда, рейтинг с сортировкой, finish. *Страница доступна любому залогиненному (issue #391).

**Шаблон:** `templates/admin/americano/tournament.html` — 1127 строк, inline JS 655 строк, i18n-ключей 32, layout-фрагменты: `fragments/admin-header::header`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `activePlayers`, `ascending`, `isInitialized`, `ranking`, `rounds`, `sortBy`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`, `RequestParam:ascending:boolean?`, `RequestParam:sortBy:String?`

**Сервер вставляет в JS** (`th:inline`): `TOURNAMENT_ID=${tournament.id}`, `tournamentId=${tournament.id}`, `tournamentName=${tournament.nombre}`, `[[match.id]]`, `[[tournament.id]]`, `[[tournament.nombre]]`

**Формы (действия):**

- `POST @{'/tournaments/americano/' + ${tournament.id} + '/finish'}` — поля: `sortBy`

**fetch из inline-скриптов:** `'/api/tournaments/americano/matches/'`, ``/api/tournaments/americano/${tournamentId}/ranking?sortBy=${sortBy}&ascending=${ascending}``, `'/api/tournaments/americano/rounds/'`, ``/api/tournaments/americano/rounds/${roundId}/points-limit``, ``/api/tournaments/americano/${TOURNAMENT_ID}/ranking?sortBy=${sortType}&ascending=false``

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `share-panel.js`: панель "поделиться" (ссылка/QR на публичную страницу турнира)

**Целевой API:** API уже в `/api/tournaments/americano/**`. NEW сводный `GET` состояния (tournament + rounds + ranking + isInitialized).

### A-09

**Americano — предпросмотр раундов** · доступ: админ-роли · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `POST /tournaments/americano/{tournamentId}/preview-rounds` | `AmericanoViewController#previewRounds` |

**Что делает:** Отдаётся **ответом на POST** формы конфигурации (нет своего GET-URL). Кнопка "инициализировать" — form POST.

**Шаблон:** `templates/admin/americano/preview-rounds.html` — 540 строк, inline JS 35 строк, i18n-ключей 19, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `config`, `isInitialized`, `matchesPerCourt`, `previewRounds`, `totalMatches`, `tournament`

**Параметры запроса:** `ModelAttribute:config:AmericanoConfigDto`, `PathVariable:tournamentId:Long`

**Формы (действия):**

- `POST @{'/tournaments/americano/admin/' + ${tournament.id} + '/initialize'}` — поля: `tournamentId`, `pointsPerMatch`, `courts`, `totalRounds`, `generationMode`

**SEO/шаринг:** `description`

**Целевой API:** NEW `POST /api/tournaments/americano/{id}/preview` (JSON config → JSON раунды) + `POST .../initialize` (есть в API).

### A-10

**Team Americano — пульт** · доступ: админ-роли* · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/team-americano/admin/{tournamentId}` | `TeamAmericanoViewController#viewAdminTournament` |

**Что делает:** Ввод результатов через `/tournaments/team-americano/api/**`, finish формой.

**Шаблон:** `templates/admin/americano/tournament-double.html` — 535 строк, inline JS 179 строк, i18n-ключей 27, layout-фрагменты: `fragments/admin-header::header`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `isInitialized`, `ranking`, `rounds`, `teams`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`

**Сервер вставляет в JS** (`th:inline`): `TOURNAMENT_ID=${tournament.id}`, `[[tournament.id]]`

**Формы (действия):**

- `POST @{'/tournaments/team-americano/admin/' + ${tournament.id} + '/finish'}`

**fetch из inline-скриптов:** `'/tournaments/team-americano/api/matches/'`, `'/tournaments/team-americano/api/'`, `'/api/tournaments/americano/rounds/'`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `share-panel.js`: панель "поделиться" (ссылка/QR на публичную страницу турнира)

**Целевой API:** Перенести `/tournaments/team-americano/api/**` под `/api/tournaments/team-americano/**`. NEW сводный `GET` состояния.

### A-11

**Team Americano — предпросмотр** · доступ: админ-роли · фаза: Ф3

| Маршрут | Контроллер |
|---|---|
| `POST /tournaments/team-americano/preview` | `TeamAmericanoViewController#previewRounds` |

**Что делает:** Тоже ответ на POST.

**Шаблон:** `templates/admin/americano/preview-double-rounds.html` — 303 строк, inline JS 33 строк, i18n-ключей 22, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `config`, `isTeamAmericanoInitialized`, `previewRounds`, `totalMatches`, `totalTeams`, `tournament`

**Параметры запроса:** `ModelAttribute:config:TeamAmericanoConfigDto`, `RequestParam:tournamentId:Long`

**Формы (действия):**

- `POST @{'/tournaments/team-americano/initialize'}` — поля: `tournamentId`, `pointsPerMatch`, `courts`

**Целевой API:** NEW `POST /api/tournaments/team-americano/{id}/preview` + initialize.

### A-12

**Team Playoff — пульт** · доступ: админ-роли* · фаза: Ф3 (последним в фазе)

| Маршрут | Контроллер |
|---|---|
| `GET /tournaments/team-playoff/admin/{tournamentId}` | `TeamPlayoffViewController#viewAdmin` |

**Что делает:** 1372 строки, 616 строк inline JS: команды (добавить/изменить/удалить/импорт регистраций), квалификация в две колонки (LFPT-415), очередь матчей без корта, смена корта/команды, swap-кандидаты, результаты, плей-офф, WebSocket. Живой турнир — самый рискованный экран.

**Шаблон:** `templates/admin/americano/tournament-playoff.html` — 1372 строк, inline JS 616 строк, i18n-ключей 89, layout-фрагменты: `fragments/admin-header::header`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `playoffMatches`, `playoffRounds`, `playoffStarted`, `qualRounds`, `qualStarted`, `ranking`, `teams`, `tournament`

**Параметры запроса:** `PathVariable:tournamentId:Long`

**Сервер вставляет в JS** (`th:inline`): `TOURNAMENT_ID=${tournament.id}`, `[[tournament.id]]`

**Формы (действия):**

- `POST @{'/tournaments/team-playoff/admin/' + ${tournament.id} + '/import-registrations'}`
- `POST @{'/tournaments/team-playoff/admin/' + ${tournament.id} + '/teams/add'}` — поля: `player1Id`, `player2Id`, `player2Name`, `player2Phone`, `registrationSource`, `hasPaid`, `attended`, `adminComment`
- `POST @{'/tournaments/team-playoff/admin/teams/' + ${team.id} + '/update'}` — поля: `tournamentId`, `hasPaid`
- `POST @{'/tournaments/team-playoff/admin/teams/' + ${team.id} + '/update'}` — поля: `tournamentId`, `attended`
- `POST @{'/tournaments/team-playoff/admin/teams/' + ${team.id} + '/delete'}` — поля: `tournamentId`
- `POST @{'/tournaments/team-playoff/admin/' + ${tournament.id} + '/init-qualification'}` — поля: `courts`
- `POST @{'/tournaments/team-playoff/admin/' + ${tournament.id} + '/init-playoff'}`
- `POST @{'/tournaments/team-playoff/admin/' + ${tournament.id} + '/regenerate-playoff'}`

**fetch из inline-скриптов:** `'/tournaments/team-playoff/api/'`, `'/tournaments/team-playoff/api/matches/'`, `'/tournaments/team-playoff/api/qual-matches/'`, `'/tournaments/team-playoff/api/playoff-matches/'`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `team-playoff-realtime.js`: WS /ws → /topic/team-playoff/{tournamentId}
  - `share-panel.js`: панель "поделиться" (ссылка/QR на публичную страницу турнира)

**Внешние скрипты:** `https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js`, `https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js`

**Целевой API:** Мутации уже частично в `/tournaments/team-playoff/api/**` (перенести под `/api/`). Формы команд/инициализации — NEW REST. NEW `GET .../state` (тот же, что для L-06 + админские поля).

### A-13

**Клубы — список** · доступ: админ-роли · фаза: Ф1 (пилот)

| Маршрут | Контроллер |
|---|---|
| `GET /admin/clubs` | `AdminClubController#listClubs` |

**Что делает:** toggle-status формами. CLUB_ADMIN видит свой клуб.

**Шаблон:** `templates/admin/clubs/list.html` — 174 строк, inline JS 0 строк, i18n-ключей 17, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `clubs`, `isAdminRole`, `isSuperAdmin`

**Формы (действия):**

- `POST @{'/admin/clubs/' + ${club.id} + '/toggle-status'}`
- `POST @{'/admin/clubs/' + ${club.id} + '/toggle-status'}`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**Целевой API:** `GET /api/clubs` есть (сверить клубную изоляцию для CLUB_ADMIN — в REST её может не быть!). NEW `POST /api/clubs/{id}/toggle-status`.

### A-14

**Клубы — форма** · доступ: админ-роли · фаза: Ф1 (пилот)

| Маршрут | Контроллер |
|---|---|
| `GET /admin/clubs/new` | `AdminClubController#newClubForm` |
| `GET /admin/clubs/{id}/edit` | `AdminClubController#editClubForm` |
| `POST /admin/clubs` | `AdminClubController#createClub` |
| `POST /admin/clubs/{id}/edit` | `AdminClubController#updateClub` |

**Что делает:** 10 полей, `_method` для edit.

**Шаблон:** `templates/admin/clubs/form.html` — 209 строк, inline JS 13 строк, i18n-ключей 28, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `club`

**Параметры запроса:** `ModelAttribute:club:ClubDto`, `PathVariable:id:Long`

**Формы (действия):**

- `POST @{${club.id == null ? '/admin/clubs' : '/admin/clubs/' + club.id + '/edit'}}` — поля: `_method`, `nombre`, `zonaCiudad`, `direccion`, `telefonoContacto`, `emailContacto`, `websiteUrl`, `mapaUrl`, `logoUrl`, `descripcion`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**Целевой API:** `POST /api/clubs`, `PUT /api/clubs/{id}` есть — сверить права (в REST PUT только SUPER_ADMIN, а в UI может редактировать CLUB_ADMIN).

### A-15

**Клубы — детали** · доступ: админ-роли · фаза: Ф1 (пилот)

| Маршрут | Контроллер |
|---|---|
| `GET /admin/clubs/{id}` | `AdminClubController#viewClub` |

**Шаблон:** `templates/admin/clubs/details.html` — 196 строк, inline JS 0 строк, i18n-ключей 22, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `club`, `isSuperAdmin`

**Параметры запроса:** `PathVariable:id:Long`

**Формы (действия):**

- `POST @{'/admin/clubs/' + ${club.id} + '/toggle-status'}`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**SEO/шаринг:** `description`

**Целевой API:** `GET /api/clubs/{id}` есть.

### A-16

**Игроки — список** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin/players` | `AdminPlayerController#listPlayers` |

**Что делает:** Смена уровня игрока через fetch по `data-url` (`POST /admin/players/{id}/nivel` с `X-Requested-With`), удаление.

**Шаблон:** `templates/admin/players/list.html` — 501 строк, inline JS 173 строк, i18n-ключей 31, layout-фрагменты: `fragments/admin-header::header`, `fragments/nivel-options::options`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `isSuperAdmin`, `players`, `playersWithRegistrations`, `totalPlayers`

**Формы (действия):**

- `POST @{'/admin/players/' + ${player.id} + '/delete'}`
- `POST @{'/admin/players/' + ${player.id} + '/delete'}`

**fetch из inline-скриптов:** `'/admin/players/'`, `select.dataset.url`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**SEO/шаринг:** `description`

**Целевой API:** NEW `GET /api/admin/players` (с регистрациями), `PATCH /api/admin/players/{id}/nivel`, `DELETE /api/admin/players/{id}`. (`GET /players/api` — постраничный, только SUPER_ADMIN/ORGANIZER.)

### A-17

**Игрок — детали** · доступ: админ-роли · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin/players/{id}` | `AdminPlayerController#viewPlayer` |

**Что делает:** Генерация гостевых учётных данных (`POST /admin/players/{id}/guest-credentials`), уровень, удаление.

**Шаблон:** `templates/admin/players/details.html` — 337 строк, inline JS 48 строк, i18n-ключей 39, layout-фрагменты: `fragments/admin-header::header`, `fragments/nivel-options::options`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `hasRegistrations`, `isGuestAccount`, `isSuperAdmin`, `player`, `registrations`

**Параметры запроса:** `PathVariable:id:Long`

**Формы (действия):**

- `POST @{'/admin/players/' + ${player.id} + '/nivel'}` — поля: `nivel`
- `POST @{'/admin/players/' + ${player.id} + '/delete'}`

**fetch из inline-скриптов:** `guestCredentialsUrl`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN
  - `admin.js`: GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03); DELETE/PATCH по data-url (подтверждения удаления)

**SEO/шаринг:** `description`

**Целевой API:** NEW `GET /api/admin/players/{id}`; guest-credentials перенести под `/api/admin/`.

### A-18

**Telegram-конвейер** · доступ: SUPER_ADMIN (иначе редирект) · фаза: Ф2

| Маршрут | Контроллер |
|---|---|
| `GET /admin/telegram-pipeline` | `AdminTelegramPipelineController#show` |

**Что делает:** Пауза/возобновление AI-конвейера.

**Шаблон:** `templates/admin/telegram-pipeline.html` — 72 строк, inline JS 0 строк, i18n-ключей 6, layout-фрагменты: `fragments/admin-header::header`, `fragments/admin-footer::footer`

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `paused`

**Формы (действия):**

- `POST @{/admin/telegram-pipeline/toggle}`

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN

**Целевой API:** NEW `GET/POST /api/admin/telegram-pipeline`.

### A-19

**Экспорт в Excel** · доступ: SUPER_ADMIN, OWNER · фаза: Ф2

**Что делает:** Скачивание `.xlsx`.

**Целевой API:** Оставить как есть — обычная ссылка на скачивание, работает из SPA без изменений.


---

## Внутренние инструменты

### T-01

**Тестовые турниры** · доступ: SUPER_ADMIN · фаза: не переносим

| Маршрут | Контроллер |
|---|---|
| `GET /test/tournaments` | `TestTournamentController#listTournaments` |

**Что делает:** Dev-инструмент (3159 строк, JDBC). Своё API `/test/tournaments/**`.

**Шаблон:** `templates/test/tournaments.html` — 3159 строк, inline JS 1671 строк, i18n-ключей 0

**Данные, которые сейчас приходят в модели** (станут полями ответа API): `activeTournamentsCount`, `availablePlayersCount`, `now`, `statsMap`, `totalRegistrations`, `tournaments`

**Формы (действия):**

- `GET (JS-обработчик)`

**fetch из inline-скриптов:** `'/test/tournaments/stats-all'`, `'/test/tournaments/create-test-player'`, ``/test/tournaments/create-test-players-bulk?count=${count}``, ``/test/tournaments/delete-test-players-bulk?count=${count}``, `'/test/tournaments/test-players-count'`, `'/test/tournaments/debug/player-count'`, ``/test/tournaments/available-players-for-pairs?tournamentId=${currentTournamentId}``, ``/test/tournaments/available-pairs?tournamentId=${currentTournamentId}``, ``/test/tournaments/available-players?tournamentId=${currentTournamentId}``, ``/test/tournaments/search-players?query=${encodeURIComponent(query)}&tournamentId=${currentTournamentId}``, ``/test/tournaments/${currentTournamentId}/register-guest-pair``, ``/test/tournaments/${currentTournamentId}/register-guest-player``, ``/test/tournaments/${tournamentId}/stats``, ``/test/tournaments/${currentTournamentId}/register-pair-with-players``, ``/test/tournaments/${currentTournamentId}/register-real-players``, ``/test/tournaments/${tournamentId}/players``, ``/test/tournaments/${tournamentId}/remove-player/${playerId}``, ``/test/tournaments/${tournamentId}/clear-test-players``

**Подключённый JS и что он вызывает:**

  - `csrf.js`: патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN

**Внешние скрипты:** `https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js`

**Целевой API:** Не переносится (ADR-0007). Остаётся Thymeleaf до удаления.


---

## Редирект-флоу без собственных экранов

Эти URL приходят **из писем и внешних систем** — их нельзя просто удалить: ссылка в уже отправленном письме должна работать.

| ID | Входной URL | Откуда приходит | Что происходит сейчас | Что делать при переезде |
|---|---|---|---|---|
| F-a | `GET /players/confirmar-email?codigo=` | ссылка из письма `confirmacion` | → `/login?confirmed=true` или `/login?error=confirm` | Оставить серверным редиректом; целевой URL — фронтовый `/login?...`. |
| F-b | `GET /double-registration/complete?token=` | ссылка из письма `new-partner-invitation` | flash success/error → `/` | Flash не переживёт SPA — заменить на `/?notice=<код>` (ADR-0011). |
| F-c | `GET /double-registration/accept-pair?token=` | письмо `pair-proposal` | рендер `accept-pair` (P-11) | Становится SPA-роутом. |
| F-d | `GET /recuperar-password?token=` | письмо `recuperar-password` | → `/?token=` (модалка на лендинге); при просроченном токене — **падает** (шаблон `error` не существует, F-02) | SPA-роут `/recuperar-password?token=` с формой. |
| F-e | `GET /torneo/{id}/king-of-court` | ссылки из карточки турнира | рендер L-01 по id турнира | SPA резолвит kingId через API. |
| F-f | `GET /oauth2/authorization/google → /login/oauth2/code/google` | кнопка Google | → `/players/dashboard` (+ remember-me) | Остаётся на бэкенде; редирект после успеха — на `app.frontend-url` (ADR-0005). |
| F-g | `POST /logout` | форма в шапках | → `/` | SPA делает `POST /logout` (с CSRF) и сам переходит на `/`. |
| F-h | `POST /tournaments/americano/{id}/preview-rounds (ошибка) → GET /tournaments/americano/{id}/initialize` | catch-блок | → `/admin/tournaments/{id}` (LFPT-316) | Уходит вместе с A-09. |

---

## Мёртвые и сломанные маршруты (не переносить)

Контроллер ссылается на шаблон, которого нет — маршрут отвечает ошибкой. В React **не переносятся**; удалить из бэкенда в рамках соответствующих issue.

| Маршрут | Несуществующий шаблон | Issue |
|---|---|---|
| `GET /tournaments/americano/{tournamentId}/register` | `tournaments/americano/register` | #330 |
| `GET /tournaments/americano/{tournamentId}/rounds` | `tournaments/americano/rounds` | #330 |
| `GET /tournaments/americano/rounds/{roundId}` | `tournaments/americano/round` | #330 |
| `GET /tournaments/americano/matches/{matchId}` | `tournaments/americano/match` | #330 |
| `GET /tournaments/americano/matches/{matchId}/result` | `tournaments/americano/match-result` | #330 |
| `GET /tournaments/americano/{tournamentId}/players/{playerId}` | `tournaments/americano/player-stats` | #330 |
| `GET /tournaments/team-americano/{tournamentId}/ranking` | `tournaments/team-americano/ranking` | нет — F-01 |
| `GET /tournaments/team-americano/matches/{matchId}/result` | `admin/americano/match-result` | #335 |
| `GET /recuperar-password` | `error` | нет — F-02 |
