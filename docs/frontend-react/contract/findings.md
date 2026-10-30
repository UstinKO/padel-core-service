# Находки анализа: баги и мёртвый код

> Всё, что нашлось по ходу инвентаризации фронта (2026-09-28, коммит `eeee06c`) и **не должно переехать в React как есть**. Ни одна находка F-xx на дату анализа не заведена issue — сверено со списком открытых issues. Уже известные баги с issue — в конце.
>
> Статус «подтверждено» = проверено чтением кода (и, где указано, конфигурации прод-nginx только на чтение). Ни одна находка не проверялась запросом к проду.

## Приоритет

| ID | Суть | Серьёзность | Блокирует переезд? |
|---|---|---|---|
| **F-00** | Анонимный доступ к `/test/telegram/**` | **высокая, безопасность** | **закрыто** (#423): только `SUPER_ADMIN`, сверено с кодом 2026-10-16 |
| F-07 | JSON-регистрация недоступна анониму | средняя | да — Ф4 (P-03) |
| F-08 | `/api/**` без входа → `302 /login` вместо `401` | средняя | да — Ф0 |
| F-09 | Роль `OWNER` не существует, но на ней держатся проверки | средняя | да — до переноса A-04/A-19 |
| F-01 | Публичный `/tournaments/team-americano/{id}/ranking` падает | средняя | нет |
| F-02 | Просроченная ссылка сброса пароля → ошибка сервера | средняя | нет (исправится с новым роутом) |
| F-05 | Пустые ссылки в футерах писем | средняя | нет |
| F-06 | Флоу `/waitlist/confirm` недостижим | выяснить | да — решить судьбу P-10 |
| F-10 | Rate limit GENERAL редиректит и `/api/**` | низкая | да — Ф0 |
| F-12 | Нет `og-image.jpg`, `favicon.ico`, `apple-touch-icon.png` | низкая, но влияет на шаринг | нет |
| F-03 | `GET /api/admin/stats` → 404 на каждой admin-странице | низкая | нет |
| F-04 | Мёртвый `refreshTournamentData` → несуществующий `/api/tournaments/active` | косметика | нет |
| F-11 | Документация: неверный WebSocket-топик KoC, нет топика Team Playoff | косметика | нет |
| F-13 | Нетипизированные ответы API, `200` с `success:false` | техдолг | да — по мере перевода экранов |

---

### F-00. `/test/telegram/**` открыт анонимно и без CSRF — **закрыто в #423 (LFPT-0423)**: `hasRole("SUPER_ADMIN")` в `SecurityConfig`, сверено с кодом 2026-10-16

- `SecurityConfig`: `/test/telegram/**` в `permitAll()` и в `csrf.ignoringRequestMatchers(...)`. `TelegramTestController` — без `@Profile`, без `@PreAuthorize`.
- Прод-nginx (`/etc/nginx/sites-enabled/epadel`, прочитано по SSH) отдельного `location` для `/test/` не имеет — запросы уходят в приложение через `location /`.
- Что может сделать любой анонимный запрос: `POST /test/telegram/ping` и `/error` — сообщения в рабочую Telegram-группу; `POST /test/telegram/reminders` и `/reminders/force/{tournamentId}` — **разослать напоминания игрокам турнира**; `POST /test/telegram/attacks` — перезаписать счётчик `RateLimitFilter.BLOCKED_TODAY` (искажает сводку атак); `GET /test/telegram/status` — раскрыть chatId/threadId.
- Предлагаемый фикс: `.requestMatchers("/test/telegram/**").hasRole("SUPER_ADMIN")` (как у `/test/tournaments/**`) и убрать из CSRF-исключений, либо `@Profile("dev")` на контроллер. Отдельный issue, **не ждать фронт-переезда**. Обновить `.agents/THREATS.md` (сейчас там это описано как осознанное исключение).

### F-01. `GET /tournaments/team-americano/{id}/ranking` → ошибка сервера — **подтверждено**

`TeamAmericanoViewController#viewRanking` возвращает `tournaments/team-americano/ranking`, шаблона нет. Маршрут публичный (`permitAll`). Родственник #330/#335, но в них не упомянут. В React отдельной страницы не делаем — рейтинг есть на L-04.

### F-02. Просроченный токен сброса пароля → ошибка сервера — **подтверждено**

`PasswordResetController#mostrarFormularioNuevoPassword` при невалидном токене кладёт `error` и возвращает view `"error"`; `templates/error.html` не существует. Пользователь, кликнувший по старой ссылке из письма, получает страницу ошибки вместо «ссылка устарела, запросите новую».

### F-03. `GET /api/admin/stats` → 404 на каждой admin-странице — **подтверждено**

`static/js/admin.js`: `AdminPanel` создаётся на каждой странице с `admin.js` и в конструкторе зовёт `loadDashboardData()` → `fetch('/api/admin/stats')`. Эндпоинта нет. Ошибка глушится `catch` → лишний запрос и 404 в логах на каждый переход по админке.

### F-04. Мёртвая функция `refreshTournamentData` — **подтверждено**

`static/js/dashboard.js:1152` зовёт несуществующий `GET /api/tournaments/active`; сама функция нигде не вызывается. Не переносить.

### F-05. Пустые ссылки в футерах писем — **подтверждено**

Шаблоны `email/bienvenida`, `no-spots-left`, `vacancy-invitation` (и др.) используют `${terminosUrl}`, `${privacidadUrl}`, `${contactoUrl}`, `${unsubscribeUrl}`, `no-spots-left` — ещё `${waitlistUrl}`. **Ни одна из этих переменных не выставляется** в `EmailService`/сервисах (`context.setVariable` не найден). Ссылки «Términos», «Privacidad», «Contacto», «Cancela tu suscripción» в письмах пустые. Отписка отсутствует — стоит проверить требования к рассылкам в Аргентине.

### F-06. Флоу `/waitlist/confirm` недостижим — **требует выяснения**

`TournamentRegistration.waitlistConfirmationToken` (миграция v1.40, #194) только читается (`findByWaitlistConfirmationToken`) и обнуляется после использования (`TournamentService:1241`), но **нигде не генерируется**, и ни одно письмо не содержит ссылки на `/waitlist/confirm`. Похоже, после перехода на авто-подтверждение из листа ожидания (LFPT-403) страница стала мёртвой. Решить: удалить P-10 вместе с полем или вернуть генерацию токена. Не переносить P-10 до решения.

### F-07. `POST /players/api/registro` недоступен анонимно — **подтверждено**

В `permitAll()` перечислены `/api/players/registro` и `/api/players/confirmar-email/**`, а реальные пути — `/players/api/registro` и `/players/api/confirmar-email` (`PlayerPadelController` c `@RequestMapping("/players")`). Итог: JSON-регистрация требует входа, т.е. не работает. Кроме того, `RateLimitFilter` и honeypot/reCAPTCHA-логика завязаны на form-путь `POST /players/registro` — при починке JSON-пути проверить, что все слои anti-abuse (см. PROJECT.md «Registration Anti-Abuse») применяются и к нему.

### F-08. `/api/**` без входа отвечает редиректом на `/login` — **подтверждено**

В `exceptionHandling` задан только `accessDeniedHandler`. Для неаутентифицированных запросов срабатывает entry point form-login → `302 /login`. `fetch` послушно идёт по редиректу и получает HTML страницы входа со статусом 200. Для SPA и мобильных клиентов нужен `401` (см. [api.md §4.2](api.md#42-что-изменить-на-бэкенде-фаза-0)).

### F-09. Роль `OWNER` не существует — **подтверждено**

`OwnerRole` = `SUPER_ADMIN, ORGANIZER, ADMIN, CLUB_ADMIN`; `Owner.getAuthorities()` выдаёт `ROLE_` + одно из них. При этом `ROLE_OWNER` / `hasRole('OWNER')` встречается в: URL-правиле `/admin/**`, `customAuthenticationSuccessHandler`, `@PreAuthorize` King of Court, `DataExportController` (`SUPER_ADMIN or OWNER`), `TournamentViewController#deleteTournament` (**только** `OWNER` → `POST /torneo/{id}/delete` недоступен никому). В `.agents/PROJECT.md` роль `ROLE_OWNER` тоже заявлена. Безвредно (проверки просто никогда не проходят по этой ветке), но вводит в заблуждение при переносе прав в SPA.

### F-10. Rate limit GENERAL редиректит даже `/api/**` — **подтверждено**

`RateLimitFilter#sendRateLimitResponse`: ветка `BLOCKED` исключает `/api/`, а ветка `GENERAL` — нет, и срабатывает раньше JSON-ответа → API-клиент получает `302 /error/rate-limit?retry=60` вместо `429`. Та же проблема для `POST /login`, если логин из SPA будет JSON-ом.

### F-11. Документация расходится с кодом — **подтверждено**

- `.agents/PROJECT.md` § WebSocket: топик `/topic/king-of-court/{tournamentId}`; в коде — `/topic/tournament/{kingId}` (`WebSocketService`). Топика `/topic/team-playoff/{tournamentId}` в документации нет.
- `docs/screen-archetypes.md`: «только KoC использует WebSocket» — Team Playoff тоже (`team-playoff-realtime.js`); шаблонов 60, а не 56; нет `admin/tournaments/matches`, `admin/telegram-pipeline`.
- `.agents/PROJECT.md` Navigation Map: нет `GET /admin/tournaments/{id}/matches`, `/admin/telegram-pipeline`.
- `SecurityConfig` открывает `/swagger-ui/**` и `/v3/api-docs/**`, но springdoc не подключён.

### F-12. Нет изображений, на которые ссылаются все страницы — **подтверждено**

`static/images/` содержит только `1.svg`, `2.svg`, `logo.svg`. Шаблоны ссылаются на `/images/og-image.jpg` (`og:image` в 5 шаблонах), `/images/favicon.ico`, `/images/apple-touch-icon.png` — все 404. Превью ссылок в мессенджерах — без картинки. Дополнительно `og:image` задан относительным путём.

### F-13. Нетипизированные ответы и разнобой формата ошибок — **подтверждено**

37 JSON-эндпоинтов отвечают `ResponseEntity<?>` или `Map<String,Object>`; 11 мест отдают `200` с `success:false`; ошибки в трёх форматах. Решение — ADR-0011, чинить по мере перевода экранов.

---

## Уже известные баги (есть issue) — учесть при переводе экрана

| Issue | Суть | Экран |
|---|---|---|
| #391 | Admin-страницы Americano/Team Americano/Team Playoff доступны игрокам | A-08, A-10, A-12 |
| #384 | Локале-зависимый формат даты/времени блокирует сабмит формы турнира | A-03 |
| #334 | Team Americano admin обрывается на середине рендера (SpEL) | A-10 |
| #335 | `GET /tournaments/team-americano/matches/{id}/result` → 500 (нет шаблона) | — (мёртвый роут) |
| #330 | `AmericanoViewController` ссылается на 6 несуществующих шаблонов | — (мёртвые роуты) |
| #329 | Редиректы Team Americano ведут на страницу обычного Americano | A-10 |
| #388 | Team Playoff: этап выбывания в колонке «Ситуация» | L-06, A-12 |
| #396 | Аудит локализации: хардкод es-строк вне i18n | все — решается ADR-0009 |
| #408, #413 | Мобильная адаптация рейтингов Americano / дубли названий в Team Americano | L-02, A-08, A-10 |
