# LFPT-403: Исправить неверный URL турнира в письмах игрокам

## Статус
approved

## Источник
GitHub issue [#403](https://github.com/UstinKO/padel-core-service/issues/403), заведён напрямую конвейером `/develop-feature` (роль Architect) по сообщению из Telegram-темы "AI Разработка" (`[отправитель: architect]`, `telegram_launch_message_id: 4553`): "Там пришло письмо на почту. В нем есть кнопочка 🏆 Посмотреть турнир. Она куда то не туда редериктится и не открыветс сайт". Прямая техническая постановка, `specs/requests/` не создавался.

Telegram-сообщение (для reply): 4553

## Контекст / зачем

Игрок получил письмо об авто-подтверждении из листа ожидания (`vacancy-invitation.html`) с кнопкой "🏆 Посмотреть турнир" (`email.vacancy.btn`), кликнул — сайт не открылся. Причина: `TournamentService.sendWaitlistAutoConfirmEmail(...)` строит `tournamentUrl` как `{baseUrl}/tournaments/{id}` (английский, множественное число), а реального роута `/tournaments/{id}` в приложении нет — только `/torneo/{id}` (`TournamentViewController`, `@RequestMapping("/torneo")`, см. `.claude/CLAUDE.md` § Navigation Map). Переход по этой ссылке даёт 404 — ровно то, что описал игрок ("куда-то не туда редиректится и не открывается сайт").

Тот же неверный паттерн (копипаста) найден ещё в двух местах, формирующих ссылки на турнир в других письмах игрокам:
- `SoloRegistrationSchedulerService.sendPartnerDataReminders()` — 48-часовое напоминание игрокам с `SOLO_ADD_LATER` донести данные партнёра.
- `DoubleTournamentRegistrationService` (ветка `SOLO_SEARCH`) — уведомление другим "ищущим пару" игрокам о новом кандидате.

Корректный паттерн уже есть в проекте: `TelegramReminderScheduler.java:124` строит `baseUrl + "/torneo/" + tournament.getId()`.

## Связь с MASTER.md

Не меняет ни один инвариант `MASTER.md` — чисто техническая правка URL в служебных email-уведомлениях, упомянутых в § "Уведомления игрокам". Раздел `MASTER.md` не редактируется.

## Требования

### Функциональные
- В `TournamentService.sendWaitlistAutoConfirmEmail(...)` заменить `String.format("%s/tournaments/%d", baseUrl, tournament.getId())` на корректный публичный URL турнира `{baseUrl}/torneo/{id}`.
- В `SoloRegistrationSchedulerService.sendPartnerDataReminders()` заменить `baseUrl + "/tournaments/" + reg.getTournament().getId()` на `{baseUrl}/torneo/{id}`.
- В `DoubleTournamentRegistrationService` (метод, формирующий `tournamentUrl` для `sendLookingForPartnerNotification`, ветка `SOLO_SEARCH`) заменить `baseUrl + "/tournaments/" + tournamentId` на `{baseUrl}/torneo/{id}`.
- Никакой новой абстракции/хелпера не вводить ради трёх похожих строк (KISS/YAGNI, CODE_REVIEW.md §3) — точечная правка на месте, паттерн и так уже виден по аналогии с `TelegramReminderScheduler`.

### Нефункциональные
- i18n: новых пользовательских строк нет — правка чисто в построении URL, тексты кнопок/писем не меняются.
- Безопасность: новой точки входа пользовательских данных нет, `tournamentId`/`tournament.getId()` — те же уже валидированные значения, что и раньше.

## Вне скоупа
- Другие email-шаблоны и их ссылки (`adminTournamentUrl`, `dashboardUrl`, `unsubscribeUrl` и т.д.) — не трогать, они уже указывают на существующие роуты.
- Общий рефакторинг/вынесение построения `tournamentUrl` в общий хелпер — не требуется для фикса, за пределами минимального изменения.
- Кнопка "Compartir torneo" (issue #104, уже закрыт ранее) — не относится к этой задаче.

## Изменения в системе

### API
Нет изменений в REST/контроллерах — правка только в построении строки URL внутри сервисов, отправляющих email.

### БД
Нет.

### UI
Email-шаблоны (`src/main/resources/templates/email/vacancy-invitation.html`, `partner-data-reminder`/аналогичный шаблон для `sendPartnerDataReminder`, шаблон для `sendLookingForPartnerNotification`) не редактируются — они уже корректно используют переданную переменную `tournamentUrl`/`${tournamentUrl}`, меняется только значение, которое им передаётся из Java-кода.

## Критерии приёмки
- [ ] `TournamentService.sendWaitlistAutoConfirmEmail(...)`: `tournamentUrl`, переданный в `emailService.sendWaitlistAutoConfirmEmail(...)`, равен `{baseUrl}/torneo/{id}` для турнира с данным ID (не `/tournaments/{id}`).
- [ ] `SoloRegistrationSchedulerService.sendPartnerDataReminders()`: `tournamentUrl`, переданный в `emailService.sendPartnerDataReminder(...)`, равен `{baseUrl}/torneo/{id}`.
- [ ] `DoubleTournamentRegistrationService`: `tournamentUrl`, переданный в `emailService.sendLookingForPartnerNotification(...)` для ветки `SOLO_SEARCH`, равен `{baseUrl}/torneo/{id}`.
- [ ] `GET /torneo/{id}` с реальным существующим `id` турнира отдаёт 200 и рендерит `tournament-details.html` (подтверждает, что новая ссылка из писем действительно рабочая, не гипотетически правильная).
- [ ] Регрессия: остальные ссылки в затронутых письмах (`adminTournamentUrl` в `DoubleTournamentRegistrationService`, `dashboardUrl`, `unsubscribeUrl` и т.п.) не изменились.
- [ ] `./mvnw compile -DskipTests` и `./mvnw verify` проходят без новых падений.

## Edge cases
- `tournament.getId()` — всегда существующий, ранее провалидированный ID (турнир уже создан и найден в БД на момент отправки письма) — дополнительная валидация ID не нужна.
- Конкурентный доступ/2 реплики — не релевантно, чистая строковая правка без состояния.
- Существующие уже отправленные письма со старой (битой) ссылкой — не переотправляются задним числом, это не входит в скоуп (пользователи, которым уже пришло письмо с 404-ссылкой, не получают повторное письмо — вне скоупа фикса).

## Открытые вопросы
Нет — техническая неоднозначность отсутствует, единственно верный паттерн URL (`/torneo/{id}`) уже используется в проекте (`TelegramReminderScheduler`) и в `.claude/CLAUDE.md` Navigation Map.
