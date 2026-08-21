# LFPT-343: Публичный доступ к странице King of Court по прямой ссылке

## Статус
done — смержено в master, [PR #345](https://github.com/UstinKO/padel-core-service/pull/345)

## Источник
[GitHub issue #343](https://github.com/UstinKO/padel-core-service/issues/343) — клиентский репорт через Telegram (архитектор), 2026-08-21.

## Контекст / зачем
Заказчик делится с игроками прямой ссылкой на страницу King of Court (`/tournaments/king-of-court/{kingId}`) — расписание матчей и рейтинг. Ссылка требует логин вместо публичного просмотра, хотя контроллер (`KingOfCourtViewController.viewKingOfCourtByKingId`) уже написан как публичная страница для зрителей (`Authentication authentication` — nullable, `isViewer=true`). Аналогичная ссылка для Americano (`/tournaments/americano/{id}`) в тот же день работала без проблем — там нужный паттерн уже есть в `permitAll()`. Для King of Court такого паттерна нет, поэтому запрос попадает под `.anyRequest().authenticated()` и редиректит на `/login`.

Второй роут того же контроллера, `GET /torneo/{tournamentId}/king-of-court`, уже работает — подпадает под существующий публичный паттерн `/torneo/**`.

## Связь с MASTER.md
Не затрагивает и не меняет ни один инвариант MASTER.md — чисто техническая правка конфигурации доступа (Spring Security), поведение страницы (данные, рендеринг, `isViewer=true`) не меняется.

## Требования

### Функциональные
- `GET /tournaments/king-of-court/{kingId}` (и любые под-пути, если появятся) должен быть доступен без авторизации — как `GET /tournaments/americano/{id}` и как второй маршрут того же контроллера, `GET /torneo/{tournamentId}/king-of-court`.

### Нефункциональные
- Безопасность: маршрут уже отдаёт read-only данные для незалогиненного зрителя (тот же контракт, что и Americano/torneo-маршруты) — новых точек входа данных не создаётся, только снимается требование аутентификации с уже спроектированного как публичный роута.
- i18n: новых пользовательских строк нет.

## Вне скоупа
- Не трогаем остальные маршруты King of Court (`/admin/tournaments/king-of-court/**`, `/api/king-of-court/**`) — они уже либо защищены, либо явно помечены как публичные под конкретные HTTP-методы (см. строки 138-144 `SecurityConfig.java`).
- Не меняем логику `KingOfCourtViewController` — она уже корректно обрабатывает анонимного зрителя.
- Не добавляем публичный доступ к `/torneo/{tournamentId}/king-of-court` — этот маршрут уже публичен через `/torneo/**`.

## Изменения в системе

### API
Нет новых/изменённых REST-эндпоинтов.

### БД
Нет.

### UI
Нет изменений шаблонов. Затронутая страница: `king-of-court-view.html` (уже существует, просто становится доступна без логина по прямой ссылке `/tournaments/king-of-court/{kingId}`).

### Конфигурация
`src/main/java/com/padle/core/padelcoreservice/config/SecurityConfig.java` — в блок `authorizeHttpRequests` (тот же список `requestMatchers(...)` строки 79-127, где уже перечислены `/tournaments/americano/*`, `/tournaments/team-americano/*`, `/tournaments/team-playoff/*`) добавить `/tournaments/king-of-court/*` и разрешить `.permitAll()`.

Обновление `.claude/CLAUDE.md` (Navigation Map уже перечисляет этот URL как Public — сам файл менять не нужно, он уже соответствует ожидаемому поведению) — правка не требуется, `.claude/` не попадает в git-diff PR в любом случае.

## Критерии приёмки
- [ ] Неавторизованный запрос `GET /tournaments/king-of-court/{kingId}` для существующего KoC-турнира возвращает 200 и рендерит `king-of-court-view.html` (не редирект на `/login`).
- [ ] Авторизованный (player или admin) запрос того же URL продолжает работать как раньше (без регрессии).
- [ ] `GET /torneo/{tournamentId}/king-of-court` продолжает работать без логина как раньше (без регрессии — не должен был сломаться этим изменением).
- [ ] Остальные, ранее защищённые маршруты (`/admin/tournaments/king-of-court/**`, POST-эндпоинты `/api/king-of-court/**`, кроме уже публичных GET `state`/`ranking`/`players/*/history`/`ping`) по-прежнему требуют авторизацию — регрессии в защите нет.
- [ ] `./mvnw verify` проходит без новых упавших тестов.

## Edge cases
- Несуществующий `kingId` — поведение контроллера не меняется этой фичей (уже обрабатывается `KingOfCourtViewController` независимо от аутентификации).
- Конкурентный доступ / несколько реплик — не относится: правка чисто в статической конфигурации `SecurityFilterChain`, не в состоянии.

## Открытые вопросы
Нет.
