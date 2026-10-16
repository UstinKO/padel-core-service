# Документация API — padel-core-service

Здесь собрано описание всех REST и MVC эндпоинтов сервиса. Каждый файл содержит: URL, метод, алгоритм работы, контракт запроса/ответа и замечания по коду для последующего устранения.

---

## Файлы документации

| Файл | Описание |
|------|----------|
| [auth.md](auth.md) | JWT-аутентификация: login, token refresh, /me |
| [players-registration.md](players-registration.md) | Регистрация игроков: web-форма и JSON API |
| [player-actions.md](player-actions.md) | Действия игрока: дашборд, регистрация/отмена на турнир |
| [player-contact.md](player-contact.md) | Контактные данные игрока (телефон/Telegram) |
| [tournaments.md](tournaments.md) | CRUD турниров |
| [double-registration.md](double-registration.md) | Парная регистрация с приглашением партнёра |
| [king-of-court.md](king-of-court.md) | Турнир "King of Court" — управление в реальном времени + WebSocket |
| [americano.md](americano.md) | Americano и Team Americano — раунды, матчи, рейтинг |
| [clubs.md](clubs.md) | CRUD клубов |
| [owners.md](owners.md) | Данные организаторов/владельцев |
| [payments.md](payments.md) | Управление платежами через форму (admin) |
| [admin-metrics.md](admin-metrics.md) | Метрики email-рассылок |
| [cookies.md](cookies.md) | Cookie consent (GDPR) |
| [password-reset.md](password-reset.md) | Сброс пароля по email |
| [view-controllers.md](view-controllers.md) | Thymeleaf view-контроллеры — карта для выноса фронтенда в SPA |

---

## Сводка критичных замечаний

### Безопасность

Сверено с кодом `master` 2026-10-16: все пункты исходного аудита закрыты.

| # | Файл | Проблема (исходный аудит) | Статус |
|---|------|----------|--------|
| 1 | king-of-court.md | Все write-эндпоинты KoC без `@PreAuthorize` | закрыто: `@PreAuthorize` + `assertCanManageKing` |
| 2 | americano.md | `hasRole('ADMIN')` не работает; большинство write без авторизации | закрыто: `@PreAuthorize` для ролей управления на всех write |
| 3 | owners.md | `ORGANIZER` может обновить любого `Owner` и сменить роль на `SUPER_ADMIN` | закрыто: только свой профиль, роль не меняется |
| 4 | clubs.md | `createdBy = 1L`; любой `OWNER` может удалить/изменить чужой клуб | закрыто: `createdBy` — текущий владелец, изменение/удаление — только `SUPER_ADMIN` |
| 5 | auth.md | `GET /api/auth/me` возвращает entity с хешем пароля | закрыто: ответ — DTO через маппер |
| 6 | players-registration.md | `GET /players/api` — публичный список email/телефонов | закрыто: `SUPER_ADMIN`/`ORGANIZER` |
| 7 | admin-metrics.md | Email-метрики без авторизации | закрыто: `SUPER_ADMIN`/`ORGANIZER` |
| 8 | cookies.md | `Secure=false` на всех cookie-эндпоинтах | закрыто: `Secure` по `request.isSecure()` |

Остаются некритичные замечания (помечены «актуально на 2026-10-16» в файлах): нет отзыва refresh-токенов (auth.md), одноразовые токены в URL (double-registration.md, password-reset.md), `success: false` при внутренней ошибке сброса пароля (password-reset.md), нет валидации ссылки YouTube (king-of-court.md), `httpOnly(false)` у consent-cookie — намеренно (cookies.md).

### Баги и технический долг

Сверено с кодом `master` 2026-10-16: все баги исходного аудита исправлены (вход `Owner` через `/api/auth/login` — #124; `SecurityException` → 403 — #125; дубли эндпоинтов контакта удалены; форма оплат — список объектов вместо параллельных массивов; `GET preview-rounds` с телом и REST-`initialize` Americano удалены; `/confirm` парной регистрации обрабатывает ошибки), как и техдолг (заглушки дашборда, `Thread.sleep` в HTTP-потоке, IP за Nginx, refresh-токен в query, base URL сброса пароля из request).

Остаются пометки `[MINOR]` / `[STYLE]` в отдельных файлах — они не сверялись построчно: перед тем как опираться на такую пометку, проверь код.
