# API: Cookie Consent — `/api/cookies`

**Контроллер:** `CookieController`  
**Назначение:** Сохранение выбора пользователя по cookie-согласию (GDPR-баннер). Устанавливает HTTP-куки в браузер.

---

## POST `/api/cookies/accept`

**Аутентификация:** Не требуется.

Пользователь принял все cookie.

### Алгоритм

Устанавливает cookie `cookieConsent=accepted` со сроком 1 год.

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `Secure` выставляется по `request.isSecure()`. Исходное замечание: `cookie.setSecure(false)` захардкожено для всех окружений. В продакшене (HTTPS) cookie без `Secure` может быть передана по незашифрованному соединению. Нужно устанавливать `Secure=true` при наличии HTTPS.

> **[SECURITY — актуально на 2026-10-16]** `cookie.setHttpOnly(false)` — JavaScript на странице может читать consent-cookie. Это намеренно (JS баннер читает его), но стоит задокументировать.

> **[MINOR]** Нет `SameSite` атрибута. Без него браузеры применяют дефолт (обычно `Lax`), что ок, но лучше явно.

---

## POST `/api/cookies/reject`

**Аутентификация:** Не требуется.

Пользователь отклонил все cookie.

### Алгоритм

Устанавливает `cookieConsent=rejected` (1 год). Никаких иных действий — аналитика/маркетинг не отключается серверно.

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[MINOR]** Отклонение cookie сохраняется в самой же cookie — это логически корректно для баннера, но стоит убедиться, что аналитика (если есть) действительно не собирает данные при `cookieConsent=rejected`.

---

## POST `/api/cookies/customize`

**Аутентификация:** Не требуется.

### Контракт

**Query params** (form params):
| Param | Тип | По умолчанию | Описание |
|-------|-----|---------|----------|
| `analytics` | boolean | false | Согласие на аналитику |
| `marketing` | boolean | false | Согласие на маркетинг |

### Алгоритм

Устанавливает три cookie:
- `cookieConsent=customized` (1 год)
- `analyticsConsent=true/false` (1 год)
- `marketingConsent=true/false` (1 год)

**Response 200 OK**: пустое тело

### Замечания (ревью)

> **[MINOR]** `cookie.setSecure(false)` — та же проблема что и выше.

> **[MINOR]** Нет поля `functional` (функциональные cookie) — обычно в GDPR-баннерах есть 3 категории: functional (обязательные), analytics, marketing.
