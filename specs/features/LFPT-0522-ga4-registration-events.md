# LFPT-0522: Feature аналитика регистраций в GA4 — события начала/завершения регистрации, cookie-consent на всех публичных страницах

## Статус
approved

## Источник
[specs/requests/LFPT-0522-ga4-registration-events.md](../requests/LFPT-0522-ga4-registration-events.md), issue [#522](https://github.com/UstinKO/padel-core-service/issues/522). Поглощает issue [#469](https://github.com/UstinKO/padel-core-service/issues/469).

Telegram-сообщение (для reply): 4937

## Контекст / зачем

Заказчик проводит аудит GA4-аналитики 1-padel.com: в отчёте 0 "важных событий" (нет события конверсии регистрации) и много сессий с источником `(not set)`. Нужно добавить событие завершённой регистрации на турнир и устранить пробел с баннером согласия на cookies, который объясняет часть `(not set)`-сессий.

## Связь с MASTER.md

Не меняет бизнес-инварианты (статусы регистрации, флоу парной регистрации из `specs/MASTER.md` — используются как есть, только читаются для определения, какой статус считать "успехом"). `MASTER.md` не меняется.

## Важные находки (подтверждено анализом кода)

1. `static/js/cookie-consent.js` (баннер согласия) до этой задачи был подключён вручную только в `index.html` (`<link rel="stylesheet" .../css/cookie-consent.css>` в `<head>`, `<script src=".../cookie-consent.js">` в конце `<body>`) — ни в одном из остальных 22 шаблонов с `fragments/analytics.html :: ga4` такого подключения не было (issue #469). Пользователь, попавший на сайт не через главную (типично — прямая ссылка на `/torneo/{id}` из WhatsApp/Telegram), никогда не видел баннер и оставался в режиме `analytics_storage: denied` на весь визит — такие визиты хуже атрибутируются GA4 (ожидаемо часть `(not set)`).
2. Регистрация на одиночный турнир — AJAX (`@ResponseBody`) эндпоинт `POST /players/tournaments/{id}/register`, вызывается из `static/js/tournament-details.js` (карточка турнира) и `static/js/dashboard.js` (дашборд игрока) с идентичным паттерном: кнопка дизейблится → `fetch` → `if (data.success)` → показывается модалка успеха.
3. Парная регистрация — три успешных пути, все AJAX, тот же паттерн кнопка-дизейбл → `fetch` → `if (response.ok)`:
   - `POST /api/tournaments/double/{id}/register-solo` (режимы `SEARCH`/`ADD_LATER` — "ищу пару"/"добавлю позже") — в обоих файлах (`tournament-details.js`, `dashboard.js`);
   - `POST /api/tournaments/double/{id}/register` (регистрация с заполненным партнёром) — в обоих файлах;
   - `POST /api/tournaments/double/accept-pair` (`accept-pair.html`, партнёр подтверждает приглашение по ссылке из письма) — отдельная минимальная страница без доступа к `tournamentId` (есть только токен), финализирует регистрацию обеих сторон в `CONFIRMED`.
4. Ни один из этих путей не перезагружает страницу и не рендерит её заново на успехе (чистый AJAX + модалка) — риск "повторной отправки события при перерисовке/обновлении страницы" из клиентского запроса структурно не применим: кнопка дизейблится на время запроса (уже существующий паттерн), событие может сработать только как прямое следствие одного реального ответа сервера.
5. `data.status`/ответ сервера нигде не содержит PII — только `TournamentRegistrationDto.status` (enum `RegistrationStatus`) и числовой `tournamentId`. `register-solo` не возвращает `status` в ответе — используется исходный `mode` (`SEARCH`/`ADD_LATER`) как описание результата.

## Требования

### Функциональные

- **Cookie-consent на всех страницах** (закрывает #469): `fragments/analytics.html` получает новый независимый фрагмент `th:fragment="cookie-consent"` (CSS + JS баннера), не привязанный к наличию GA4/Clarity ID (баннер управляет и будущими маркетинговыми cookie, должен работать даже если аналитика выключена в dev/cloud). Подключается (`th:replace`) во всех 23 шаблонах, где уже подключён `:: ga4`. `index.html` — убраны старые ручные подключения (дублировали бы баннер).
- **Новый общий JS-хелпер** `static/js/registration-tracking.js` — `window.trackTournamentRegistrationStart(tournamentId)` и `window.trackTournamentRegistrationComplete(tournamentId, registrationStatus)`, оба — тонкая обёртка над `gtag('event', ...)` с guard `typeof gtag === 'function'`. Без PII: только `tournament_id` (строка из числового ID) и `registration_status` (enum/mode-строка). Переиспользуется между `tournament-details.js`, `dashboard.js`, `accept-pair.html` (DRY — один код события, не три копии).
- `tournament_registration_start` — отправляется в начале каждого из 4 реальных сценариев регистрации (одиночная × 2 файла, парная solo/pair × 2 файла), сразу после дизейбла кнопки и до `fetch`-вызова.
- `tournament_registration_complete` — отправляется строго в ветке успешного ответа сервера (`data.success`/`response.ok`), с `registration_status`, равным фактическому статусу/режиму из ответа: `CONFIRMED`/`WAITLIST` (одиночная), `PARTNER_INVITED`/`CONFIRMED`/`WAITLIST` (парная с партнёром), `SEARCH`/`ADD_LATER` (solo-режим), `CONFIRMED` (подтверждение партнёром на `accept-pair.html`, без `tournament_id` — страница не располагает им).

### Нефункциональные
- Безопасность: новых точек входа пользовательских данных нет; `tournament_id`/`registration_status` — не PII (см. находка 5).
- i18n: новых пользовательских строк нет (события не видны пользователю).
- Производительность: не применимо (клиентский код, один вызов `gtag` на успешный ответ).

## Вне скоупа

- Изоляция Measurement ID от dev/cloud/test — отдельная backend-задача LFPT-0521 (issue #521, другой слой, `GIT_WORKFLOW.md §1.4`).
- Аудит UTM коротких ссылок (`/oct-tg`, `/oct-wa`, `/oct-ig`, `/ig-bio`) и объяснение разделения источников `instagram`/`ig` — аналитическая находка, не код (все три ссылки уже корректно прокидывают UTM, см. `MarketingRedirectControllerTest`); выносится в финальный отчёт архитектора.
- Инструкция заказчику по пометке события как "ключевого" в кабинете GA4 — не код.
- Americano/King of Court/Team Playoff регистрация — не публичная self-service регистрация игрока через эти два JS-файла (администрируется организатором через `/test/tournaments` или admin-панель, либо отдельный механизм), не инструментируется в этой задаче — нет сопоставимого "клика игрока по кнопке регистрации" с которым можно связать `start`/`complete`.
- Распространение события на случаи отмены регистрации (`cancel`/`cancel-double`) — не запрошено клиентом (только "завершённая регистрация"), не делается (YAGNI).

## Изменения в системе

### API
Нет новых/изменённых REST-эндпоинтов.

### БД
Нет.

### UI
- Новый `src/main/resources/templates/fragments/analytics.html :: cookie-consent`.
- 22 шаблона получают `th:replace="~{fragments/analytics :: cookie-consent}"` рядом с существующим `:: ga4` (список — см. `.claude/CLAUDE.md`/находка 1 выше); `index.html` — замена ручных тегов на тот же `th:replace`.
- Новый `static/js/registration-tracking.js`, подключён в `tournament-details.html`, `players/dashboard.html`, `accept-pair.html`.
- Точечные правки `static/js/tournament-details.js`, `static/js/dashboard.js` (вызовы хелпера в существующих обработчиках регистрации), `accept-pair.html` (вызов хелпера в существующем обработчике подтверждения).

## Критерии приёмки

- [ ] На всех 23 страницах с `fragments/analytics.html :: ga4` (список — `.claude/CLAUDE.md`) при первом визите без cookie `cookieConsent` показывается баннер согласия — независимо от точки входа (прямой переход на `/torneo/{id}`, `/clubs`, `/ranking` и т.д., не только `/`).
- [ ] `index.html` — баннер по-прежнему работает (без визуальных изменений, без дублирования DOM-элемента `#cookieConsentBanner`).
- [ ] Одиночная регистрация (дашборд и страница турнира) — после `data.success === true` в `dataLayer` фиксируется `tournament_registration_complete` с `registration_status` равным `data.status` (`CONFIRMED`/`WAITLIST`); до ответа сервера (в момент дизейбла кнопки) — `tournament_registration_start`.
- [ ] Парная регистрация (solo-режим и режим с партнёром, оба файла) — аналогично: `start` в момент инициации, `complete` на успешном ответе с соответствующим статусом/режимом.
- [ ] `accept-pair.html` — `tournament_registration_complete` с `registration_status: 'CONFIRMED'` при успешном подтверждении.
- [ ] Ни в одном из событий нет полей с именем/телефоном/email/токеном (проверено по коду хелпера — передаются только `tournament_id`/`registration_status`).
- [ ] Повторное открытие/обновление той же страницы без нового клика по кнопке регистрации — событие не отправляется повторно (структурно гарантировано: событие только внутри обработчика клика/успешного ответа, не в коде рендера страницы).
- [ ] `./mvnw verify` зелёный.
- [ ] Ручная/Playwright-проверка (см. "Edge cases") пройдена тестировщиком.

## Edge cases

- `gtag` не определён (аналитика выключена в dev/cloud после LFPT-0521, либо пользователь отклонил cookies и `gtag` вообще не создан) — хелпер проверяет `typeof window.gtag === 'function'` и тихо ничего не делает, без ошибки в консоли.
- Двойной клик по кнопке регистрации — кнопка дизейблится на время запроса (существующий паттерн всех 4 сценариев) — второй клик физически не создаёт второй `fetch`, соответственно не создаёт второе событие.
- `register-solo` не возвращает `status` в ответе — используется `mode` (`SEARCH`/`ADD_LATER`), не перепутан со `status` других сценариев (разные домены значений, подписано в хелпере как `registration_status` в обоих случаях — приемлемо для аналитики, не требует различения на уровне GA4-параметра).
- `accept-pair.html` не знает `tournamentId` (только токен) — событие отправляется с `tournament_id: undefined` (параметр просто отсутствует в payload `gtag`), не блокирует отправку `registration_status`.

## Открытые вопросы

Нет открытых бизнес-вопросов — все решения технические, обоснованы в разделе "Важные находки"/"Вне скоупа" (`GIT_WORKFLOW.md §3`).
