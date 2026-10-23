# LFPT-0620: Fix мёртвый код EmailService.sendPartnerInvitationEmail — несуществующий шаблон email/partner-invitation, как и #617

## Статус
done

## Источник
Спека написана напрямую, без клиентского запроса. Issue [#620](https://github.com/UstinKO/padel-core-service/issues/620), побочная находка аудита писем в LFPT-0618. Решение согласовано с [#617](https://github.com/UstinKO/padel-core-service/issues/617) (PR #658): мёртвый метод удаляется, шаблон не заводится.

## Контекст / зачем
`EmailService.sendPartnerInvitationEmail(...)` рендерит шаблон `email/partner-invitation`, которого нет в `src/main/resources/templates/email/` (есть только `new-partner-invitation.html` для незарегистрированного партнёра). Метод нигде не вызывается: обёртка с тем же именем `TournamentNotificationService.sendPartnerInvitationEmail` отправляет партнёру `EmailService.sendTournamentConfirmationEmail`. Сейчас ничего не ломается, но случайный вызов даст `TemplateInputException`. Одноимённые методы в двух классах, один из которых мёртвый, мешают аудиту писем.

## Связь с MASTER.md
Не затрагивает бизнес-инварианты MASTER.md. Это чистка мёртвого кода, поведение отправки писем не меняется.

## Требования

### Функциональные
- Удалить метод `EmailService.sendPartnerInvitationEmail` целиком: javadoc, аннотации `@Timed`, `@TrackErrors`, `@Async` и тело. Удалить, а не закомментировать.
- Удалить всё, что использовалось только этим методом. Метрический тип `PARTNER_INVITATION` и тег `type=partner_invitation` встречаются только внутри метода и уходят вместе с ним.
- Поведение отправки писем при регистрации пары и замене игрока в паре не меняется.

### Нефункциональные
- i18n: новых строк нет. Ключи `email.partner_invitation.*` во всех `messages_{es,ru,en}.properties` **не удаляются**. К удаляемому методу они относятся только по имени: их использует шаблон `new-partner-invitation.html`, а `email.partner_invitation.subject` — тема письма в `EmailService.sendNewPartnerInvitationEmail`. Эта цепочка после LFPT-0687 тоже не вызывается, решение по ней — отдельной задачей (см. «Вне скоупа»).
- Безопасность, производительность: не затрагиваются.

## Вне скоупа
- `TournamentNotificationService.sendPartnerInvitationEmail` — рабочая обёртка, вызывается из `DoubleTournamentRegistrationService.registerWithExistingPartner` и `DoubleTournamentRegistrationService.replacePlayerInPair`, отправляет `tournament-confirmation.html`. Остаётся без изменений, в том числе без переименования.
- Цепочка письма `new-partner-invitation`: `TournamentNotificationService.sendNewPartnerInvitation` → `EmailService.sendNewPartnerInvitationEmail` → шаблон `new-partner-invitation.html`, ключи `email.partner_invitation.*`, метрика `type=new_partner_invitation` / `NEW_PARTNER_INVITATION`. После LFPT-0687 (#690, `1ee7afe`) `sendNewPartnerInvitation` нигде не вызывается: партнёру без аккаунта аккаунт создаётся сразу, и регистрация идёт через `registerWithExistingPartner`. Тот же класс проблемы, найден при тестировании LFPT-0620, решается отдельной задачей. Туда же — таблица писем в `.claude/CLAUDE.md`, где `new-partner-invitation.html` описан как действующее письмо.
- Создание шаблона `email/partner-invitation.html`: уведомление зарегистрированного партнёра уже закрыто письмом `tournament-confirmation.html`.
- Исторические упоминания в `specs/done/**` не правятся.

## Изменения в системе

### API
Нет.

### БД
Нет, миграция не нужна.

### UI
Нет.

### Код
`src/main/java/com/padle/core/padelcoreservice/service/EmailService.java`: удалить метод `sendPartnerInvitationEmail`. Импорты не меняются: `MetricTag`, `Timed`, `TrackErrors`, `Async`, `Context`, `Locale` используются другими методами класса.

## Критерии приёмки
- [x] В репозитории (кроме `specs/`) нет ссылок на шаблон `email/partner-invitation`, метрический тип `"PARTNER_INVITATION"` и тег `type=partner_invitation`, в том числе в закомментированном коде. Проверка: `git grep -nE '"PARTNER_INVITATION"|type=partner_invitation|email/partner-invitation' -- . ':!specs'` пусто.
- [x] В `EmailService.java` нет `sendPartnerInvitationEmail`; в `TournamentNotificationService` и `DoubleTournamentRegistrationService` он на месте. Проверка: `git grep -n "sendPartnerInvitationEmail" -- src` выдаёт только эти два класса.
- [x] Диф кода затрагивает только `EmailService.java` (−56 строк); `messages_*.properties` и шаблоны не изменены.
- [x] Регистрация пары с уже зарегистрированным партнёром: партнёр получает письмо «Inscripción confirmada / Запись подтверждена» на своём языке (путь `TournamentNotificationService.sendPartnerInvitationEmail` → `EmailService.sendTournamentConfirmationEmail`). Тестировщик 2026-10-22 проверил вживую (локальная SMTP-заглушка mailpit): партнёр с `preferred_locale=ru` получил «✅ Запись подтверждена — …», основной игрок (es) — «✅ Inscripción confirmada — …»; `TemplateInputException` и ошибок отправки в логе нет.
- [x] Регистрация пары с партнёром без аккаунта (`POST /api/tournaments/double/{tournamentId}/register` с данными нового партнёра): ответ 200, регистрация `CONFIRMED`, партнёр получает `tournament-confirmation` («✅ Inscripción confirmada — …»). Так код ведёт себя с LFPT-0687; письмо `new-partner-invitation` в этом сценарии не отправляется (см. «Вне скоупа»). Проверено тестировщиком 2026-10-22 вживую (mailpit).
- [x] `./mvnw clean verify` зелёный — 2026-10-22: 438 тестов, 0 падений (JDK 21, локальный PostgreSQL в Docker).
- [x] `python3 .github/bin/docs-drift.py` — 0 расхождений (2026-10-22).

## Edge cases
- Одноимённый метод в `TournamentNotificationService`: grep по `sendPartnerInvitationEmail` без пути находит живой код, удалять нужно только в `EmailService`.
- Ключи `email.partner_invitation.*` названы по удаляемому методу, но принадлежат шаблону `new-partner-invitation.html` и `sendNewPartnerInvitationEmail`. Эта цепочка тоже не вызывается (LFPT-0687), но удалять ключи здесь нельзя: шаблон `new-partner-invitation.html` остаётся в проекте и без них сломается. Решать всю цепочку целиком — отдельной задачей.
- Ссылок на `EmailService.sendPartnerInvitationEmail` из тестов и `docs/` нет (проверено grep по репозиторию), компиляция после удаления проходит.
- Метрики `email.send.time` и `email.send.errors` с тегом `type=partner_invitation` уходят вместе с методом; дашбордов и алертов на них в репозитории нет. Серий на проде тоже нет: метод ни разу не вызывался.
- Конкурентный доступ, существующие данные, миграции: не применимо.

## Открытые вопросы
Нет.
