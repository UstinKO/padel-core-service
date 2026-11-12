# API аккаунта UI v2

Контроллеры: `AccountController`, `PublicAccountConfigController`; сервис `AccountService`, маппер `AccountMapper`.
Контракт ошибок и успешных ответов — типизированные DTO Java 17; схемы — [OpenAPI](../frontend-react/contract/account.openapi.json).
Все ответы аккаунта `Cache-Control: no-store`. Все мутации требуют cookie `XSRF-TOKEN` и заголовок `X-XSRF-TOKEN`.
Мобильные `POST /api/auth/login` и `/api/auth/refresh` сохраняют JWT и прежний формат.

| Метод | URL | Доступ | Контракт |
|---|---|---|---|
| POST | `/login` | Анонимный + CSRF | form-urlencoded username/password/remember-me/destination; Accept JSON → Login(success, session, destination), неверный вход 401 LOGIN_INVALID. HTML сохраняет redirect |
| POST | `/logout` | CSRF | Accept JSON → Result(LOGGED_OUT); HTML → `/`; Spring очищает сессию и remember-me |
| GET | `/api/public/config` | Публичный | captchaEnabled/captchaSiteKey/googleEnabled/supportedLocales/locale; ключ CAPTCHA только публичный, если включён |
| POST | `/api/players/registro` | Анонимный + CSRF | RegistroRequestDto + website honeypot/destination; 201 REGISTERED, затем обычный `/login`. Правило телефон ИЛИ Telegram, email автоматически подтверждается |
| GET | `/api/players/me` | PlayerPadel или Google-player | Собственный Profile: id/nombre/apellido/email/telefono/telegramUsername/nivelJugador/preferredLocale/guestAccount/oauth2User |
| PUT | `/api/players/me` | PlayerPadel или Google-player + CSRF | Полный ProfileUpdate, те же редактируемые поля; email меняется только у guest; сервер не принимает id/роль/активность |
| POST | `/api/players/me/password` | PlayerPadel или Google-player + CSRF | currentPassword/newPassword/confirmPassword; текущий пароль обязателен, минимальная длина нового 6 как в существующем reset; успех PASSWORD_CHANGED |
| POST | `/api/password-reset/request` | Анонимный + CSRF | email/destination; одинаковый RESET_REQUESTED для известной и неизвестной почты |
| GET | `/api/password-reset/validate` | Публичный | token; valid/code TOKEN_VALID или RESET_TOKEN_INVALID, без изменения токена |
| POST | `/api/password-reset/confirm` | Анонимный + CSRF | token/newPassword/confirmPassword/destination; одноразовое использование под блокировкой строки, PASSWORD_CHANGED или 422 |

Owner с совпадающим id никогда не считается PlayerPadel. Профиль перечитывается из БД, сессия показывает обновлённое имя/язык. Отдельная политика отзыва всех сессий не вводится.

## Ошибки и лимиты

Новые операции возвращают ProblemDetail: type/title/status/detail/instance/code/fieldErrors; fieldErrors — список {field, code, message} без rejectedValue.
422 VALIDATION_ERROR содержит INVALID_FIELD, EMAIL_DOMAIN_INVALID, NAME_INVALID, CONTACT_REQUIRED, PASSWORD_MISMATCH, EMAIL_DUPLICATE, PHONE_DUPLICATE, TELEGRAM_DUPLICATE, CONTACT_DUPLICATE, EMAIL_IMMUTABLE, CURRENT_PASSWORD_INVALID, CAPTCHA_INVALID, RESET_TOKEN_INVALID.
401 SESSION_REQUIRED/LOGIN_INVALID, 403 PLAYER_REQUIRED/CSRF_INVALID/ACCESS_DENIED, 503 ACCOUNT_UNAVAILABLE. Тексты ES/RU/EN, ручная cookie lang важнее Accept-Language.
Все `/api/**` без сессии/Bearer возвращают 401 JSON вместо redirect; старые защищённые HTML-страницы сохраняют переход к `/login`.

RateLimitFilter учитывает метод: AUTH 10/мин только попытки входа/refresh и OAuth, REGISTER 3/мин — мутации регистрации/reset, API 60/мин — чтение session/config и остальные API, GENERAL 200/мин. GET формы не считается регистрацией. Обычные ошибки валидации/дубликаты не увеличивают suspicious counters. Эскалация остаётся для превышения квоты и провала CAPTCHA/honeypot. 429 JSON содержит фактические Retry-After, retryAfter и blockedUntil, в том числе для XHR `/login`.
CAPTCHA при недоступности провайдера не считается успешной; secret/token передаются POST-телом и не логируются.

## Возврат

destination — только проверенный same-origin относительный путь: отклоняются внешние URL, //, обратный слэш, управляющие символы и многократное URL-кодирование. tournamentId сохраняет прежний возврат на запись. OAuth хранит destination в AuthorizationRequest, доверяет ему только после проверки state; мобильный client=app сохраняет прежний токенный callback. Reset-письмо переносит безопасный destination; legacy GET сохраняет его в переходе к действующей модалке. Новые production-маршруты не активируются.
Настройка `app.auth.google-enabled` управляет отображением публичной кнопки; для изолированного локального теста задаётся false, production по умолчанию true.

Для legal/system frontend сервер сохраняет HTTP-статус preview deep link: неизвестный route — 404, `/preview/error/401`, `/403`, `/429`, `/503` — соответствующий статус, offline — 503. Legal preview содержит canonical на действующий публичный URL. Все preview/error страницы noindex; известные auth-маршруты по-прежнему используют SPA, production allowlist не меняется.

## Подтверждение гостевого профиля

| Метод | URL | Доступ | Контракт |
|---|---|---|---|
| POST | `/api/players/me/guest-profile-claims` | Игрок с обычным аккаунтом + CSRF | `phone` → 201, `reference` и `whatsappUrl`. Ответ одинаков для номера с гостем и без него |
| GET | `/api/admin/guest-profile-claims/{reference}` | ORGANIZER / SUPER_ADMIN | Целевые `accountId`, `accountEmail` и признак `approved`. Номер телефона в ответ не входит |
| POST | `/api/admin/guest-profile-claims/{reference}/approve` | ORGANIZER / SUPER_ADMIN + CSRF | `whatsappSenderPhone` → `playerId`. Номер сверяется с заявкой и профилем, заявки живут 7 дней |
| POST | `/api/admin/players/{guestId}/activate-guest-profile` | ORGANIZER / SUPER_ADMIN + CSRF | `whatsappSenderPhone` и `email` → прежний `playerId`. Игрок самостоятельно запускает восстановление пароля |

Для занятого телефона регистрация и обновление контакта возвращают 409 `PHONE_IN_USE` и официальный `whatsappUrl` без данных другого игрока. Устаревшая заявка возвращает 404 `GUEST_CLAIM_NOT_FOUND`. При пересечении историй, неоднозначном номере или конфликте почты возвращается типизированная ошибка `GUEST_CLAIM_*`. Операции привязки не посылают коды и не меняют статус участия.

Для заявок и проверки занятого телефона поддерживаются аргентинские форматы и номера с кодом `+7`. Номер `+7` сравнивается с учётом кода страны. Пробелы, скобки и дефисы не влияют на совпадение.
