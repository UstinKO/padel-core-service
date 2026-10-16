import json, sys, re

S = sys.argv[1]
ctrl = json.load(open(f'{S}/ctrl.json'))
tpl = json.load(open(f'{S}/tpl.json'))

# JS-файлы: вызовы API, WebSocket, глобальные объекты (собрано отдельным проходом, см. analysis)
JS = {
    'admin-americano.js': ['POST /api/tournaments/americano/rounds/{roundId}/start', 'POST /api/tournaments/americano/rounds/{roundId}/complete', 'POST /api/tournaments/americano/matches/{matchId}/result', 'sessionStorage (UI-состояние)'],
    'admin.js': ['GET /api/admin/stats (**эндпоинта нет — 404 на каждой admin-странице**, F-03)', 'DELETE/PATCH по data-url (подтверждения удаления)'],
    'contact-check.js': ['GET /api/players/me/contact-status', 'PATCH /api/players/me/contact'],
    'cookie-consent.js': ['POST /api/cookies/accept', 'POST /api/cookies/reject', 'POST /api/cookies/customize?analytics=&marketing=', 'document.cookie'],
    'csrf.js': ['патчит window.fetch: добавляет X-XSRF-TOKEN из cookie XSRF-TOKEN'],
    'dashboard.js': ['POST /players/tournaments/{id}/register', 'POST /players/tournaments/{id}/cancel', 'POST /players/tournaments/{id}/cancel-double', 'GET /players/tournaments/{id}/my-registration', 'GET /api/players/search?query=&tournamentId=', 'POST /api/tournaments/double/{id}/register', 'POST /api/tournaments/double/{id}/register-solo', 'GET /api/tournaments/active (**эндпоинта нет, функция не вызывается** — F-04)'],
    'king-of-court-viewer.js': ['GET /api/king-of-court/tournaments/{kingId}/state', 'GET /api/king-of-court/tournaments/{kingId}/players/{playerId}/history', 'WS /ws → /topic/tournament/{kingId}'],
    'king-of-court.js': ['GET /api/king-of-court/tournaments/{kingId}/state', 'GET /api/king-of-court/debug/tournaments/{kingId}/check-players', 'POST /api/king-of-court/tournaments/{tournamentId}/initialize', 'POST /api/king-of-court/matches/result', 'PUT /api/king-of-court/matches/result/{resultId}', 'POST /api/king-of-court/tournaments/{kingId}/next-round', 'POST /api/king-of-court/tournaments/{kingId}/rollback', 'POST /api/king-of-court/tournaments/{kingId}/finish', 'POST /api/king-of-court/tournaments/{kingId}/youtube?youtubeLink=', 'GET /api/king-of-court/tournaments/{kingId}/players/{playerId}/history', 'WS /ws → /topic/tournament/{kingId}'],
    'password-reset.js': ['POST /recuperar-password/solicitar', 'POST /recuperar-password/confirmar', 'читает ?token= на / и /login → модалка нового пароля'],
    'team-playoff-realtime.js': ['WS /ws → /topic/team-playoff/{tournamentId}'],
    'tournament-details.js': ['POST /players/tournaments/{id}/register', 'GET /api/players/search', 'POST /api/tournaments/double/{id}/register', 'POST /api/tournaments/double/{id}/register-solo', 'POST /api/tournaments/double/{id}/add-partner', 'GET /api/tournaments/double/{id}/looking-for-partner', 'POST /api/tournaments/double/{id}/propose-pair/{targetRegId}', 'шаринг: WhatsApp/Facebook/Twitter/mailto'],
    'share-panel.js': ['панель "поделиться" (ссылка/QR на публичную страницу турнира)'],
    'bracket-zoom-export.js': ['fit-to-width, pinch-zoom, экспорт сетки в PNG (snapdom с CDN)'],
    'payment-management.js': ['клиентская логика формы платежей (без fetch)'],
    'torneos.js': ['фильтры/рендер карточек из tournamentsJson (без fetch)'],
    'home.js': ['рендер ближайших турниров из tournamentsJson (без fetch)'],
    'ranking.js': ['поиск/сортировка по rankingJson (без fetch)'],
    'profile.js': ['клиентская валидация формы профиля'],
    'auth.js': ['клиентская валидация login/registro'],
    'notifications.js': ['тосты'], 'main.js': ['навбар/общие хелперы'], 'admin-menu.js': ['мобильное меню админки'],
    'logger.js': ['console-обёртка'], 'i18n/t.js': ['t(key, ...args) по window.APP_MESSAGES'],
}

# Ручные аннотации: id, название, доступ, шаблон, назначение/особенности, целевой API, фаза
SCREENS = [
 # ---------------- Публичные
 ('P-01', 'Лендинг', 'публичный', 'index', 'Главная: статистика, ближайшие турниры (из `tournamentsJson`), модалки сброса пароля по `?token=`, баннер cookie. OG-теги есть — важен для шаринга.', 'NEW `GET /api/public/home` (счётчики + ближайшие турниры) или переиспользовать `GET /api/tournaments/upcoming` + NEW `GET /api/public/stats`. Сброс пароля — существующие `POST /recuperar-password/*`.', 'Ф5 (SEO, см. ADR-0008)'),
 ('P-02', 'Вход', 'публичный', 'login', 'Форма Spring Security (`POST /login`, `username`/`password`/`remember-me`), кнопка Google (`/oauth2/authorization/google`), алерты по `?error=`, `?error=not_confirmed`, `?error=oauth2`, `?confirmed=true`, `?logout`, `?registered`, `?reset=success`. Уже залогиненных редиректит в `/admin` или `/players/dashboard`.', 'Логин через тот же `POST /login`, но с JSON-ответом для XHR (ADR-0005). NEW `GET /api/auth/session` (кто я + роли + флаги) вместо двух форматов `/api/auth/me`.', 'Ф4 (до этого React-экраны используют текущую страницу входа — сессия общая)'),
 ('P-03', 'Регистрация игрока', 'публичный', 'registro', 'reCAPTCHA v3 (`g-recaptcha-response`, скрипт google.com), honeypot `website`, disposable-email, rate limit. После успеха — `redirect:/?welcome=true`.', 'Существующий JSON `POST /players/api/registro` **анонимно недоступен**: в `permitAll` прописан `/api/players/registro`, а реальный путь `/players/api/registro` (F-07). Починить путь + проверить паритет проверок с form-версией (honeypot, reCAPTCHA, rate limit — `RateLimitFilter` сейчас узнаёт только `POST /players/registro`). NEW `GET /api/public/config` (recaptchaEnabled, siteKey).', 'Ф4'),
 ('P-04', 'Список турниров', 'публичный', 'torneos', 'Все турниры отдаются одним `tournamentsJson`, фильтрация на клиенте (`torneos.js`). Показывает имя пользователя, если вошёл.', '`GET /api/tournaments` / `/search` уже есть, **но требуют входа** (`/api/**` → authenticated, публичных исключений для них нет). Нужен публичный доступ (NEW `GET /api/public/tournaments` или permitAll на GET) + сверить поля DTO с тем, что рисует `torneos.js` (статус мест, клуб, уровень).', 'Ф5 (SEO)'),
 ('P-05', 'Карточка турнира', 'публичный (+ действия для игрока)', 'tournament-details', 'Самая нагруженная публичная страница: 26 атрибутов модели (флаги регистрации текущего игрока, лист ожидания, пары, поиск пары, ссылки на live-страницы формата). Регистрация одиночная/парная/"ищу пару", предложения пары, проверка контакта перед регистрацией. OG-теги — ссылку на турнир шарят в WhatsApp/Telegram и шлют в письмах.', 'NEW `GET /api/tournaments/{id}/details` (публичная часть) + NEW `GET /api/tournaments/{id}/my-status` (всё про текущего игрока: isRegistered, isInWaitlist, currentUserIsSolo, userRegistration…). Действия — существующие `/players/tournaments/*` и `/api/tournaments/double/*`.', 'Ф5 (SEO, самая важная для OG)'),
 ('P-06', 'Список клубов', 'публичный', 'clubs/list', 'Простой список.', '`GET /api/clubs/active` сейчас **только для админов** — нужен NEW публичный `GET /api/public/clubs`.', 'Ф5'),
 ('P-07', 'Клуб', 'публичный', 'clubs/view', 'Карточка клуба.', 'NEW публичный `GET /api/public/clubs/{id}` (+ турниры клуба: `GET /api/tournaments/club/{clubId}` существует, но тоже требует входа).', 'Ф5'),
 ('P-08', 'Рейтинг', 'публичный', 'ranking', 'Весь рейтинг — `rankingJson` + `topRankingJson`, поиск/сортировка на клиенте.', 'NEW `GET /api/ranking` (отдельного REST-эндпоинта рейтинга нет).', 'Ф5'),
 ('P-09', 'Юридические страницы', 'публичный', 'legal/cookies, legal/privacidad, legal/terminos', 'Статический текст на 3 языках, атрибут `year`. Ссылки на них из писем и футера.', 'Не нужен API — статические страницы в React (текст в i18n или md).', 'Ф5'),
 ('P-10', 'Подтверждение из листа ожидания', 'публичный (по токену)', 'waitlist-confirmation', 'GET показывает превью, POST подтверждает. 5 состояний (pending/success/warning/error/notfound). **Токен `waitlistConfirmationToken` нигде не генерируется** (F-06) — вероятно, флоу мёртв после авто-подтверждения (LFPT-403).', 'Выяснить, жив ли флоу (F-06). Если жив — NEW `GET/POST /api/waitlist/confirm?token=`.', 'Ф4 (после выяснения)'),
 ('P-11', 'Принять предложение пары', 'публичный (по токену из письма)', 'accept-pair', 'Страница с кнопкой, JS шлёт `POST /api/tournaments/double/accept-pair?token=`. Ссылка приходит в письме `pair-proposal`.', 'API уже есть. Страница — тонкая React-обёртка.', 'Ф4'),
 ('P-12', 'Ошибки', 'публичный', 'error/403, error/rate-limit, error/tournament-not-found', '403 — от `CustomAccessDeniedHandler`; rate-limit — редирект из `RateLimitFilter` с `?retry=&blocked=`; not-found — из `/torneo/{id}`.', 'В SPA — роуты-заглушки; API отдаёт 403/404/429 JSON. Для rate-limit — заголовок `Retry-After` вместо query-параметров.', 'Ф0 (экраны ошибок внутри SPA; Thymeleaf-версии живут до Ф6)'),
 # ---------------- Live публичные
 ('L-01', 'King of Court — зритель', 'публичный', 'king-of-court-view', 'Два URL: по `kingId` и по id турнира. Данные — `kingData` + REST `state`, обновления по WebSocket `/topic/tournament/{kingId}`, модалки истории/итогов (`fragments/king-of-court-modals`).', 'API и WS уже есть. Нужен NEW `GET /api/king-of-court/by-tournament/{tournamentId}` (резолв kingId) — сейчас это делает view-контроллер.', 'Ф3'),
 ('L-02', 'Americano — публичная', 'публичный', 'tournaments/americano/view', 'Раунды, матчи, рейтинг с сортировкой `?sortBy=&ascending=`. Без WebSocket — обновление перезагрузкой.', '`GET /api/tournaments/americano/{id}/rounds`, `/matches`, `/ranking` уже есть. NEW сводный `GET /api/tournaments/{id}` с флагом `isInitialized` уже частично (`/initialized`).', 'Ф3'),
 ('L-03', 'Americano — рейтинг', 'публичный', 'tournaments/americano/ranking', 'Отдельная страница рейтинга.', '`GET /api/tournaments/americano/{id}/ranking` уже есть.', 'Ф3'),
 ('L-04', 'Team Americano — публичная', 'публичный', 'tournaments/team-americano/view', 'Раунды/матчи/рейтинг команд.', 'Есть `GET /tournaments/team-americano/api/{id}/ranking` (не под `/api/`!). NEW `GET /api/tournaments/team-americano/{id}/rounds`.', 'Ф3'),
 ('L-05', 'Team Americano — рейтинг', 'публичный', '— (шаблона нет)', '**Роут падает: шаблон `tournaments/team-americano/ranking` не существует** (F-01).', 'Не переносить как отдельную страницу — рейтинг уже есть на L-04.', '—'),
 ('L-06', 'Team Playoff — публичная', 'публичный', 'tournaments/team-playoff/view', 'Квалификация + сетка плей-офф, zoom/экспорт PNG (`bracket-zoom-export.js`, snapdom), WebSocket `/topic/team-playoff/{id}`.', 'Есть `GET /tournaments/team-playoff/api/{id}/ranking`. NEW `GET /api/tournaments/team-playoff/{id}/state` (qualRounds, playoffRounds, флаги) — сейчас собирается во view-контроллере (`splitQualificationWaves` и т.п.).', 'Ф3'),
 # ---------------- Игрок
 ('U-01', 'Дашборд игрока', 'ROLE_PLAYER (админа редиректит в /admin)', 'players/dashboard', '`tournamentsJson`, `historicalJson`, `myTournamentIdsJson` встраиваются в страницу; регистрация/отмена/парная регистрация прямо с дашборда (`dashboard.js`, 1267 строк), проверка контакта (`contact-check.js`). Сюда ведут письма и Google-логин.', 'NEW `GET /api/players/me/dashboard` (активные турниры, история, мои id). Действия — существующие.', 'Ф4'),
 ('U-02', 'Профиль', 'ROLE_PLAYER', 'players/perfil', 'Форма профиля + смена пароля + `preferredLocale` (язык писем). Flash `successMessage/errorMessage/infoMessage`.', 'NEW `GET /api/players/me` + `PUT /api/players/me` + `POST /api/players/me/password`. Контакт — существующий `PATCH /api/players/me/contact`.', 'Ф4'),
 # ---------------- Админка CRUD
 ('A-01', 'Админ-панель', 'админ-роли', 'admin/panel', '13 атрибутов: счётчики, последние турниры/игроки, матчи в процессе. CLUB_ADMIN видит только свой клуб (LFPT-376).', 'NEW `GET /api/admin/dashboard` (с учётом клубной изоляции).', 'Ф2'),
 ('A-02', 'Турниры — список', 'админ-роли', 'admin/tournaments/list', 'Фильтр `?clubId=`, деактивация/удаление формами.', '`GET /api/tournaments/my` есть, но без фильтра по клубу и флагов — NEW `GET /api/admin/tournaments?clubId=`. Действия — NEW `POST /api/admin/tournaments/{id}/deactivate`, `DELETE /api/admin/tournaments/{id}` (или существующий `DELETE /api/tournaments/{id}` — сверить права CLUB_ADMIN).', 'Ф2'),
 ('A-03', 'Турнир — форма (создание/правка/копия)', 'админ-роли', 'admin/tournaments/form', 'Один шаблон на три URL (`new`, `{id}/edit`, `{id}/copy`). Справочники: клубы, форматы, уровни, статусы, типы, модальности. Локале-зависимые дата/время (issue #384). Автогенерация названия (LFPT-374).', '`POST/PUT /api/tournaments` уже есть — сверить с `TournamentDto` формы. NEW `GET /api/admin/reference-data` (все enum-справочники одним запросом).', 'Ф2'),
 ('A-04', 'Турнир — детали', 'админ-роли', 'admin/tournaments/details', 'Самый большой шаблон (1856 строк): участники, лист ожидания, переносы main↔waitlist, сведение соло-игроков в пары, смена статуса, запуск KoC/Americano/Team Playoff, форма конфигурации Americano (→ preview), ссылки на все live-страницы формата.', 'NEW `GET /api/admin/tournaments/{id}` (турнир + регистрации + флаги формата). Существующие `POST /admin/tournaments/{id}/move-to-*`, `pair-solo` → перенести под `/api/admin/...`. Статус — `PATCH /api/tournaments/{id}/status` есть.', 'Ф2'),
 ('A-05', 'Турнир — сетка матчей (Bracket)', 'админ-роли', 'admin/tournaments/matches', 'LFPT-394: генерация сетки по выбранным игрокам, ввод сетов.', 'NEW `GET /api/admin/tournaments/{id}/bracket`, `POST .../bracket/generate`, `PUT .../matches/{matchId}`.', 'Ф2'),
 ('A-06', 'Платежи турнира', 'админ-роли', 'admin/tournaments/payments', 'Табличная форма с индексированными полями `payments[i].*`, save и save-and-add-team.', 'NEW `GET /api/admin/tournaments/{id}/payments` + `PUT` (массив). ', 'Ф2'),
 ('A-07', 'King of Court — пульт', 'админ-роли', 'admin/tournaments/king-of-court', 'Раунды/корты/ввод результатов (модалки), next-round/rollback/finish/reset/YouTube, WebSocket.', 'Почти всё уже в `/api/king-of-court/**`. Нет REST для `reset` и `start` (сейчас form POST в `AdminKingOfCourtController`).', 'Ф3'),
 ('A-08', 'Americano — пульт', 'админ-роли*', 'admin/americano/tournament', '655 строк inline JS: старт/завершение раундов, ввод счёта, лимит очков раунда, рейтинг с сортировкой, finish. *Страница доступна любому залогиненному (issue #391).', 'API уже в `/api/tournaments/americano/**`. NEW сводный `GET` состояния (tournament + rounds + ranking + isInitialized).', 'Ф3'),
 ('A-09', 'Americano — предпросмотр раундов', 'админ-роли', 'admin/americano/preview-rounds', 'Отдаётся **ответом на POST** формы конфигурации (нет своего GET-URL). Кнопка "инициализировать" — form POST.', 'NEW `POST /api/tournaments/americano/{id}/preview` (JSON config → JSON раунды) + `POST .../initialize` (есть в API).', 'Ф3'),
 ('A-10', 'Team Americano — пульт', 'админ-роли*', 'admin/americano/tournament-double', 'Ввод результатов через `/tournaments/team-americano/api/**`, finish формой.', 'Перенести `/tournaments/team-americano/api/**` под `/api/tournaments/team-americano/**`. NEW сводный `GET` состояния.', 'Ф3'),
 ('A-11', 'Team Americano — предпросмотр', 'админ-роли', 'admin/americano/preview-double-rounds', 'Тоже ответ на POST.', 'NEW `POST /api/tournaments/team-americano/{id}/preview` + initialize.', 'Ф3'),
 ('A-12', 'Team Playoff — пульт', 'админ-роли*', 'admin/americano/tournament-playoff', '1372 строки, 616 строк inline JS: команды (добавить/изменить/удалить/импорт регистраций), квалификация в две колонки (LFPT-415), очередь матчей без корта, смена корта/команды, swap-кандидаты, результаты, плей-офф, WebSocket. Живой турнир — самый рискованный экран.', 'Мутации уже частично в `/tournaments/team-playoff/api/**` (перенести под `/api/`). Формы команд/инициализации — NEW REST. NEW `GET .../state` (тот же, что для L-06 + админские поля).', 'Ф3 (последним в фазе)'),
 ('A-13', 'Клубы — список', 'админ-роли', 'admin/clubs/list', 'toggle-status формами. CLUB_ADMIN видит свой клуб.', '`GET /api/clubs` есть (сверить клубную изоляцию для CLUB_ADMIN — в REST её может не быть!). NEW `POST /api/clubs/{id}/toggle-status`.', 'Ф1 (пилот)'),
 ('A-14', 'Клубы — форма', 'админ-роли', 'admin/clubs/form', '10 полей, `_method` для edit.', '`POST /api/clubs`, `PUT /api/clubs/{id}` есть — сверить права (в REST PUT только SUPER_ADMIN, а в UI может редактировать CLUB_ADMIN).', 'Ф1 (пилот)'),
 ('A-15', 'Клубы — детали', 'админ-роли', 'admin/clubs/details', '', '`GET /api/clubs/{id}` есть.', 'Ф1 (пилот)'),
 ('A-16', 'Игроки — список', 'админ-роли', 'admin/players/list', 'Смена уровня игрока через fetch по `data-url` (`POST /admin/players/{id}/nivel` с `X-Requested-With`), удаление.', 'NEW `GET /api/admin/players` (с регистрациями), `PATCH /api/admin/players/{id}/nivel`, `DELETE /api/admin/players/{id}`. (`GET /players/api` — постраничный, только SUPER_ADMIN/ORGANIZER.)', 'Ф2'),
 ('A-17', 'Игрок — детали', 'админ-роли', 'admin/players/details', 'Генерация гостевых учётных данных (`POST /admin/players/{id}/guest-credentials`), уровень, удаление.', 'NEW `GET /api/admin/players/{id}`; guest-credentials перенести под `/api/admin/`.', 'Ф2'),
 ('A-18', 'Telegram-конвейер', 'SUPER_ADMIN (иначе редирект)', 'admin/telegram-pipeline', 'Пауза/возобновление AI-конвейера.', 'NEW `GET/POST /api/admin/telegram-pipeline`.', 'Ф2'),
 ('A-19', 'Экспорт в Excel', 'SUPER_ADMIN, OWNER', '— (файл)', 'Скачивание `.xlsx`.', 'Оставить как есть — обычная ссылка на скачивание, работает из SPA без изменений.', 'Ф2'),
 # ---------------- Внутреннее
 ('T-01', 'Тестовые турниры', 'SUPER_ADMIN', 'test/tournaments', 'Dev-инструмент (3159 строк, JDBC). Своё API `/test/tournaments/**`.', 'Не переносится (ADR-0007). Остаётся Thymeleaf до удаления.', 'не переносим'),
]

# Редирект-флоу без собственных экранов — должны пережить переезд
FLOWS = [
 ('F-a', 'GET /players/confirmar-email?codigo=', 'ссылка из письма `confirmacion`', '→ `/login?confirmed=true` или `/login?error=confirm`', 'Оставить серверным редиректом; целевой URL — фронтовый `/login?...`.'),
 ('F-b', 'GET /double-registration/complete?token=', 'ссылка из письма `new-partner-invitation`', 'flash success/error → `/`', 'Flash не переживёт SPA — заменить на `/?notice=<код>` (ADR-0011).'),
 ('F-c', 'GET /double-registration/accept-pair?token=', 'письмо `pair-proposal`', 'рендер `accept-pair` (P-11)', 'Становится SPA-роутом.'),
 ('F-d', 'GET /recuperar-password?token=', 'письмо `recuperar-password`', '→ `/?token=` (модалка на лендинге); при просроченном токене — **падает** (шаблон `error` не существует, F-02)', 'SPA-роут `/recuperar-password?token=` с формой.'),
 ('F-e', 'GET /torneo/{id}/king-of-court', 'ссылки из карточки турнира', 'рендер L-01 по id турнира', 'SPA резолвит kingId через API.'),
 ('F-f', 'GET /oauth2/authorization/google → /login/oauth2/code/google', 'кнопка Google', '→ `/players/dashboard` (+ remember-me)', 'Остаётся на бэкенде; редирект после успеха — на `app.frontend-url` (ADR-0005).'),
 ('F-g', 'POST /logout', 'форма в шапках', '→ `/`', 'SPA делает `POST /logout` (с CSRF) и сам переходит на `/`.'),
 ('F-h', 'POST /tournaments/americano/{id}/preview-rounds (ошибка) → GET /tournaments/americano/{id}/initialize', 'catch-блок', '→ `/admin/tournaments/{id}` (LFPT-316)', 'Уходит вместе с A-09.'),
]


def ctrl_for(template):
    out = []
    for x in ctrl:
        if template in x['views']:
            out.append(x)
    return out


def md_list(xs):
    return ', '.join(f'`{x}`' for x in xs) if xs else '—'


lines = []
w = lines.append
w('# Контракт: экраны\n')
w('> Сгенерировано из кода 2026-09-28 (ветка `master`, коммит `eeee06c`). Факты (URL, контроллер, атрибуты модели, формы, fetch, скрипты) извлечены скриптом из `controller/**` и `templates/**`; колонки «Назначение», «Целевой API» и «Фаза» — ручной анализ. Легенда и правила — в [README контракта](README.md).\n')
w('Всего: **%d экранов** + **%d редирект-флоу** без собственных страниц.\n' % (len(SCREENS), len(FLOWS)))
w('## Сводная таблица\n')
w('| ID | Экран | Доступ | Шаблон | Фаза |')
w('|---|---|---|---|---|')
for sid, title, access, t, *_ , phase in SCREENS:
    w(f'| [{sid}](#{sid.lower()}) | {title} | {access} | `{t}` | {phase} |')
w('')

groups = [('P-', 'Публичные страницы'), ('L-', 'Публичные live-страницы форматов'), ('U-', 'Кабинет игрока'), ('A-', 'Админ-панель'), ('T-', 'Внутренние инструменты')]
for prefix, gtitle in groups:
    w(f'\n---\n\n## {gtitle}\n')
    for sid, title, access, t, purpose, target, phase in [s for s in SCREENS if s[0].startswith(prefix)]:
        w(f'### {sid}\n')
        w(f'**{title}** · доступ: {access} · фаза: {phase}\n')
        templates = [x.strip() for x in t.split(',')]
        first = templates[0] if templates[0] in tpl else None
        routes = []
        for tt in templates:
            for c in ctrl_for(tt):
                routes.append(c)
        if routes:
            w('| Маршрут | Контроллер |')
            w('|---|---|')
            for c in routes:
                w(f"| `{c['verb']} {c['paths'][0]}` | `{c['file'].split('/')[-1][:-5]}#{c['method']}` |")
            w('')
        if purpose:
            w(f'**Что делает:** {purpose}\n')
        if first:
            v = tpl[first]
            w(f"**Шаблон:** `templates/{first}.html` — {v['lines']} строк, inline JS {v['inline_js_lines']} строк, i18n-ключей {v['i18n_keys']}" + (f", layout-фрагменты: {md_list(v['fragments'])}" if v['fragments'] else '') + '\n')
            # model
            model = sorted(set(a for c in routes if c['verb'] == 'GET' or c['views'] for a in c['model']))
            if model:
                w(f'**Данные, которые сейчас приходят в модели** (станут полями ответа API): {md_list(model)}\n')
            params = sorted(set(p for c in routes for p in c['params'] if p not in ('auth', 'HttpServletRequest', 'HttpSession')))
            if params:
                w(f'**Параметры запроса:** {md_list(params)}\n')
            js_vars = v['js_vars']
            if js_vars:
                w(f'**Сервер вставляет в JS** (`th:inline`): {md_list(js_vars)}\n')
            if v['forms']:
                w('**Формы (действия):**\n')
                for fm in v['forms']:
                    if fm['action'] in ('@{/logout}',):
                        continue
                    act = fm['action'] or '(JS-обработчик)'
                    w(f"- `{fm['method']} {act}`" + (f" — поля: {md_list(fm['fields'])}" if fm['fields'] else ''))
                w('')
            if v['fetches']:
                w(f"**fetch из inline-скриптов:** {md_list(v['fetches'])}\n")
            scripts = [s for s in v['scripts']]
            local = [re.sub(r"^@\{/js/|\}$", '', s) for s in scripts if s.startswith('@{/js/')]
            cdn = [s for s in scripts if s.startswith('http')]
            calls = []
            for js in local:
                if js in JS and js not in ('logger.js', 'i18n/t.js', 'main.js', 'notifications.js', 'admin-menu.js'):
                    calls.append(f'  - `{js}`: ' + '; '.join(JS[js]))
            if calls:
                w('**Подключённый JS и что он вызывает:**\n')
                w('\n'.join(calls))
                w('')
            if cdn:
                w(f'**Внешние скрипты:** {md_list(cdn)}\n')
            if v['og'] or v['meta']:
                w(f"**SEO/шаринг:** {md_list(v['og'] + v['meta'])}\n")
            flash = sorted(set(f for c in ctrl if any(r.startswith('redirect') for r in c['redirects']) for f in c['flash']))
        w(f'**Целевой API:** {target}\n')

w('\n---\n\n## Редирект-флоу без собственных экранов\n')
w('Эти URL приходят **из писем и внешних систем** — их нельзя просто удалить: ссылка в уже отправленном письме должна работать.\n')
w('| ID | Входной URL | Откуда приходит | Что происходит сейчас | Что делать при переезде |')
w('|---|---|---|---|---|')
for fid, url, src, now, todo in FLOWS:
    w(f'| {fid} | `{url}` | {src} | {now} | {todo} |')

w('\n---\n\n## Мёртвые и сломанные маршруты (не переносить)\n')
w('Контроллер ссылается на шаблон, которого нет — маршрут отвечает ошибкой. В React **не переносятся**; удалить из бэкенда в рамках соответствующих issue.\n')
w('| Маршрут | Несуществующий шаблон | Issue |')
w('|---|---|---|')
dead = {
 'tournaments/americano/register': '#330', 'tournaments/americano/rounds': '#330', 'tournaments/americano/round': '#330',
 'tournaments/americano/match': '#330', 'tournaments/americano/match-result': '#330', 'tournaments/americano/player-stats': '#330',
 'admin/americano/match-result': '#335', 'tournaments/team-americano/ranking': 'нет — F-01', 'error': 'нет — F-02',
}
for x in ctrl:
    for v in x['views']:
        if v not in tpl:
            w(f"| `{x['verb']} {x['paths'][0]}` | `{v}` | {dead.get(v, '?')} |")

open(sys.argv[2], 'w').write('\n'.join(lines) + '\n')
print('ok', len(lines))
