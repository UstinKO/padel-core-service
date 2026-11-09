# Контракт: сквозные механизмы

> То, что не принадлежит одному экрану, но сломается по всему сайту, если про это забыть. Каждый пункт — «как сейчас» (с местом в коде) → «что должно быть в React-версии». Снято с кода 2026-09-28 (коммит `eeee06c`).

---

## 1. Вход, сессия, роли

### 1.1. Как сейчас

| Механизм | Где | Поведение |
|---|---|---|
| Form login | `SecurityConfig` `.formLogin()` | `POST /login` (`username`, `password`, `remember-me`) → успех: `302 /admin` (любая админ-роль) или `302 /players/dashboard`; неуспех: `302 /login?error=true`, отключённый аккаунт — `?error=not_confirmed` |
| Google OAuth2 | `.oauth2Login()`, `CustomOAuth2UserService`, `OAuth2AuthenticationSuccessHandler` | `/oauth2/authorization/google` → Google → `/login/oauth2/code/google` → создаёт/обновляет `PlayerPadel`, ставит remember-me **всегда** (#297), `302 /players/dashboard`; ошибка — `/login?error=oauth2` |
| Remember-me | `SecurityBeansConfig#rememberMeServices` (`TokenBasedRememberMeServices`) | Общий для формы и Google |
| Два типа пользователей | `CompositeUserDetailsService` | Сначала `Owner` (админы), потом `PlayerPadel`. Роли: `ROLE_PLAYER`; `ROLE_SUPER_ADMIN`, `ROLE_ORGANIZER`, `ROLE_ADMIN`, `ROLE_CLUB_ADMIN`. **`ROLE_OWNER` не существует**, хотя упоминается в конфиге (F-09) |
| Logout | `.logout()` | `POST /logout` (с CSRF) → `302 /`, чистит `JSESSIONID` |
| Уже вошёл и открыл `/login` | `HomeController#loginPage` | `302` в свой кабинет |
| Игрок/админ не туда | `PlayerDashboardController` | админа с `/players/dashboard` редиректит в `/admin` |
| Нет прав | `CustomAccessDeniedHandler` | `/api/**` → `403 {success:false,message}`; `GET /admin/tournaments/{id}` → `302 /torneo/{id}`; остальное → `302 /error/403` |
| Не вошёл | дефолтный entry point | **везде**, включая `/api/**` → `302 /login` (F-08) |
| Клубная изоляция | LFPT-376: `CLUB_ADMIN` видит только свой `clubId` | реализована в view-контроллерах админки; в REST-контроллерах — не везде (см. [api.md §3](api.md#3-расхождения-в-правах-доступа-проверить-до-перевода-экрана)) |

### 1.2. Что должно быть в React

- Тот же `JSESSIONID` + remember-me, same-origin; никаких токенов в `localStorage` (ADR-0005). JWT — не для веба.
- При старте приложение делает `GET /api/auth/session` и строит меню/гарды от него. Роли в SPA — **только для UI** (что показать); реальная проверка — всегда на сервере.
- Логин: `POST /login` c `Accept: application/json` → `200 {session}` | `401 {code: BAD_CREDENTIALS|NOT_CONFIRMED}` | `429`. После успеха — переход по роли (админ → `/admin`, игрок → `/players/dashboard`) или на `?redirect=` (если пришёл с защищённой страницы).
- Google: кнопка — обычная ссылка `/oauth2/authorization/google` (полный переход страницы, не XHR). Success handler редиректит на `app.frontend-url + /players/dashboard` (сейчас — относительный путь, same-origin работает и так).
- Любой ответ API `401` → глобально: сбросить кэш сессии, увести на `/login?redirect=<текущий путь>`. `403` → экран «нет доступа».
- Гарды роутов: `/admin/**` — админ-роли; admin-страницы форматов (`/tournaments/*/admin/**`) — тоже админ-роли (сейчас доступны игроку, #391); `/players/**`, `/perfil` — вход.

## 2. CSRF

- Сейчас: cookie `XSRF-TOKEN` (не `HttpOnly`) выдаётся на **каждом** ответе (`csrfCookieFilter`); `static/js/csrf.js` подменяет `window.fetch` и добавляет `X-XSRF-TOKEN` для мутирующих запросов; формы Thymeleaf кладут `_csrf` скрытым полем.
- В React: HTTP-клиент (один на всё приложение) читает cookie и ставит `X-XSRF-TOKEN` на `POST/PUT/PATCH/DELETE`. Серверная часть не меняется. **Важно:** `POST /login` и `POST /logout` — тоже под CSRF.
- `/ws/**` исключён из CSRF (#284) — остаётся.

## 3. Локализация

| | Сейчас | В React (ADR-0009) |
|---|---|---|
| Выбор языка | `AcceptHeaderLocaleResolver`, без переключателя; es по умолчанию; es/ru/en | То же + переключатель в UI (сохраняется в cookie/`localStorage` как удобство), `Accept-Language` в каждом запросе к API |
| Строки веб-страниц | `i18n/messages_{es,ru,en}.properties`: **1084 ключа** используются только веб-шаблонами | Переезжают во `frontend/src/i18n/{es,ru,en}.json` **вместе со своим экраном** |
| Строки JS | `static/js/i18n/messages-{es,ru,en}.js` — **250 ключей**, `window.APP_MESSAGES` + `t(key, ...args)` с `{0}`-плейсхолдерами | Сливаются в те же JSON; формат плейсхолдеров — ICU/`{{name}}` (react-i18next) |
| Строки писем | **103 ключа** только в `templates/email/**` | Остаются в `.properties` на бэкенде |
| Строки Java | **13 ключей** (`MessageSource` в `EmailService`, `PlayerService`, `AdminPlayerController`) | Остаются на бэкенде |
| Не используются | **195 ключей** не найдены ни в шаблонах, ни в Java | Не переносить; удалить при чистке (Ф6) |
| Сообщения об ошибках API | В основном **захардкожены по-испански** в контроллерах/сервисах (`"Acceso denegado"`, `"El enlace de recuperación ha expirado…"`) | API отдаёт стабильный `code`; текст ошибки — из словаря фронта по коду. `detail` на языке запроса — запасной вариант |
| Язык писем | `PlayerPadel.preferredLocale` (правится в профиле) | Без изменений; поле остаётся в форме профиля |
| Форматы | даты/время — локале-зависимый ввод ломает сабмит формы турнира (#384); валюта ARS | Даты в API — ISO-8601 (`2026-10-05`, `19:30`), форматирование только на клиенте (`Intl`), зона — `America/Argentina/Buenos_Aires` для отображения. **Контейнер сейчас в `TZ=Europe/Moscow`** (Dockerfile) — проверить, как сериализуются `LocalDateTime` |

Пересечений между веб-ключами, ключами писем и Java **нет** — поэтому деление чистое и безопасное.

## 4. Сообщения после действия (flash) и параметры URL

Сейчас после form POST сервер делает редирект и кладёт flash-атрибут. В SPA редиректов после действий нет — сообщение показывает сам клиент по результату запроса (тост). Но **некоторые сообщения приходят через URL из писем/внешних редиректов** — их нужно сохранить.

| Сейчас | Кто ставит | Кто показывает | В React |
|---|---|---|---|
| flash `successMessage` / `errorMessage` / `infoMessage` | админ-контроллеры, `PerfilController`, `PlayerPadelController`, `PartnerRegistrationController`, `ConfirmacionEmailController` | 13 admin-шаблонов, `registro`, `perfil`, `tournament-details`, `dashboard` | тост по ответу API |
| flash `success` / `error` / `info` (другие имена!) | `AmericanoViewController`, `TeamAmericanoViewController`, `TeamPlayoffViewController` | `admin/americano/*` | тост по ответу API |
| `/?welcome=true` | после регистрации | лендинг (баннер сейчас закомментирован) | `/?notice=welcome` или сразу тост |
| `/?token=…` | `GET /recuperar-password?token=` | `password-reset.js` открывает модалку | отдельный роут `/recuperar-password?token=` |
| `/login?confirmed=true`, `?error=confirm` | подтверждение email | `login.html` | сохраняем эти параметры (ссылки в письмах ведут через бэкенд-редирект) |
| `/login?error=true|not_confirmed|oauth2|rate-limit`, `?logout`, `?registered`, `?reset=success` | Security, `RateLimitFilter`, `password-reset.js` | `login.html` | `oauth2` и `rate-limit` остаются URL-параметрами (приходят от серверного редиректа); остальные — состояние формы |
| `/torneo/{id}?error=no-active-tournament`, `/?error=not-found|server_error` | view-контроллеры | — | 404-экран / тост |
| `/players/registro?error=rate-limit` | `RateLimitFilter` | `registro.html` | `429` от API |
| `/error/rate-limit?retry=&blocked=` | `RateLimitFilter` | `error/rate-limit.html` | экран 429 по заголовку `Retry-After` |

Правило для серверных редиректов, которые приходят извне (письма, OAuth): **код сообщения** в query-параметре (`?notice=pair_confirmed`), текст — из словаря фронта. Flash-атрибуты сессии в SPA не работают (страница не перезагружается сервером).

## 5. Входящие ссылки, которые нельзя сломать

Эти URL уже разосланы в письмах/Telegram или сохранены у пользователей. После переезда они должны либо открывать ту же страницу, либо редиректить на новую.

| URL | Кто генерирует | Где в коде |
|---|---|---|
| `/torneo/{id}` | письма о турнире, напоминания Telegram, авто-сведение пар, шаринг WhatsApp/Telegram/Facebook | `TournamentService:488`, `TelegramReminderScheduler:124`, `SoloRegistrationSchedulerService:52`, `DoubleTournamentRegistrationService:778`, `EmailService` |
| `/players/dashboard` | письма | `EmailService` |
| `/admin/tournaments/{id}` | письмо админу о соло-регистрации | `DoubleTournamentRegistrationService:762` |
| `/players/confirmar-email?codigo=` | письмо подтверждения | `EmailService:75` |
| `/double-registration/complete?token=` | приглашение партнёра | `DoubleTournamentRegistrationService:346,661,918` |
| `/double-registration/accept-pair?token=` | предложение пары | `DoubleTournamentRegistrationService:1035` |
| `/recuperar-password?token=` | сброс пароля | `PasswordResetService:63` |
| `/login`, `/players/registro` | футеры писем | `templates/email/*` |
| `/terminos`, `/privacidad`, контакты, отписка | футеры писем | **переменные не заполняются — ссылки пустые** (F-05) |
| `/tournaments/{format}/{id}`, `/tournaments/king-of-court/{kingId}` | «поделиться» (`share-panel.js`), QR на корте | `share-panel.js` |

Все базовые URL строятся из `app.base-url` (`APP_BASE_URL`). После отделения фронта (ADR-0003) ссылки на страницы должны строиться от `app.frontend-url`, ссылки на серверные действия (подтверждение email, `double-registration/complete`) — от `app.base-url`. Пока same-origin — это одно значение.

## 6. Realtime

См. [api.md §5](api.md#5-websocket). Коротко: `/ws` (SockJS+STOMP), топики `/topic/tournament/{kingId}` и `/topic/team-playoff/{tournamentId}`, только сервер → клиент. В React — один общий STOMP-клиент (`@stomp/stompjs`), подписка на экран, сообщение = «перечитай состояние».

## 7. Внешние скрипты, шрифты, иконки

| Что | Откуда | Где используется | В React |
|---|---|---|---|
| Font Awesome **6.0.0-beta3** (CSS) | cdnjs | 40 шаблонов | npm-пакет иконок (одна система, см. `.agents/DESIGN.md`) — бета-версия с CDN уходит |
| Inter | Google Fonts | 40 шаблонов | self-host через `@fontsource` (или по DESIGN.md) |
| Bootstrap 5.1.3 JS + Bootstrap Icons | jsDelivr | `test/tournaments` и 1 шаблон | не нужен |
| sockjs-client@1, stompjs@2.3.3 | jsDelivr | 4 live-шаблона | `@stomp/stompjs` (+ `sockjs-client`, если фоллбэк нужен) из npm |
| snapdom@3.1.1 | jsDelivr | L-06 (экспорт сетки в PNG) | npm `@zumer/snapdom` |
| reCAPTCHA v3 | google.com | P-03 | загрузка по требованию на экране регистрации |

Всё из npm собирается в бандл → из CSP уходят `cdnjs`/`jsdelivr`, а после ухода inline-скриптов — и `'unsafe-inline'` в `script-src` (сейчас он нужен из-за ~2 800 строк inline JS в шаблонах, не считая `test/tournaments`). Это заметное улучшение безопасности, записать как критерий готовности Фазы 6.

## 8. SEO и шаринг

| Что | Сейчас |
|---|---|
| OG/Twitter-теги | `index`, `login`, `registro`, `tournament-details`, `waitlist-confirmation`. Для `/torneo/{id}` — динамические (`og:title` = название турнира, `og:description` = формат+уровень) |
| `og:image` | `/images/og-image.jpg` — **файла нет** (F-12); относительный URL (краулеры ждут абсолютный) |
| favicon / apple-touch-icon | `/images/favicon.ico`, `/images/apple-touch-icon.png` — **файлов нет** (F-12) |
| `robots.txt` | есть; `sitemap.xml` — нет |
| Индексируемые страницы | `/`, `/torneos`, `/torneo/{id}`, legal |

Почему это важно для решения: превью ссылки на турнир в WhatsApp/Telegram — основной канал продвижения турнира (Аргентина, WhatsApp-группы). Краулеры мессенджеров **не выполняют JS**: чистый SPA отдаст им одинаковый `index.html` для всех турниров. Решение — ADR-0008.

## 9. Хранилище браузера и cookie

| Ключ | Тип | Кто | Зачем | В React |
|---|---|---|---|---|
| `JSESSIONID` | cookie, HttpOnly | Spring | сессия | без изменений |
| `remember-me` | cookie, HttpOnly | Spring | долгий вход | без изменений |
| `XSRF-TOKEN` | cookie | Spring | CSRF | читается HTTP-клиентом |
| `cookieConsent` (`accepted`/`rejected`/`customized`), `analyticsConsent`, `marketingConsent` | cookie, 365 дней, SameSite=Lax | `CookieController` через `/api/cookies/*` | согласие на cookie | тот же API; баннер — компонент |
| выбранный раунд пульта | `sessionStorage` | `admin-americano.js` | вернуться на тот же раунд после перезагрузки | как есть |
| черновики счёта по матчу (`storageKey(matchId)` / `lsKey(id)`) | `localStorage` | inline JS `admin/americano/tournament`, `tournament-double`, `tournament-playoff` | не терять введённый счёт при перезагрузке/обрыве сети на корте | сохранить: черновик счёта не должен пропадать |

Аналитики/маркетинговых скриптов **нет** (согласие собирается, но включать нечего).

## 10. Layout и общие компоненты

| Сейчас | Куда |
|---|---|
| `fragments/public-header`, `public-footer` — используются **только** в `clubs/*`; остальные публичные страницы копируют `<head>`/шапку у себя (3 разных навбара, см. `.agents/DESIGN.md`) | `<PublicLayout>` |
| `fragments/admin-header` (+ `admin-menu.js`), `admin-footer` | `<AdminLayout>` |
| `fragments/king-of-court-modals` (result/history/finalResults/confirm) | модалки KoC |
| `fragments/nivel-options` (список уровней) | `<NivelSelect>` из reference-data |
| `notifications.js` (тосты), `share-panel.js` (поделиться/QR), `contact-check.js` (модалка «укажите телефон/Telegram» перед регистрацией, правило `hasValidContact`) | общие компоненты |

## 11. Ошибки и пограничные состояния

- `404` турнира — отдельный шаблон `error/tournament-not-found`; `403` — `error/403`; `429` — `error/rate-limit` с таймером.
- Спринговый `/error` (whitelabel) — для всего остального. **Шаблона `error.html` нет**, а `PasswordResetController` возвращает view `error` → падение (F-02).
- В React: экраны `NotFound`, `Forbidden`, `RateLimited`, `ServerError`; сетевые ошибки на live-пультах — не терять введённый счёт (см. §9) и показывать «нет связи» вместо молчаливого провала.
