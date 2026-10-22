# LFPT-0617: Fix мёртвый код EmailService.sendPairConfirmationEmail — удалить метод с несуществующим шаблоном

## Статус
approved

## Источник
Спека написана напрямую, без клиентского запроса. Issue [#617](https://github.com/UstinKO/padel-core-service/issues/617), найдено при актуализации docs (#615) и при аудите писем в LFPT-0618.

## Контекст / зачем
`EmailService.sendPairConfirmationEmail(...)` рендерит шаблон `email/pair-confirmation`, которого нет в `src/main/resources/templates/email/`. Метод нигде не вызывается: письма о подтверждении пары отправляет `TournamentNotificationService.sendPairConfirmationEmails` через `sendTournamentConfirmationEmail`. Сейчас ничего не ломается, но случайный вызов даст `TemplateInputException`. Мёртвый код вводит в заблуждение и мешает аудиту писем.

## Связь с MASTER.md
Не затрагивает бизнес-инварианты MASTER.md. Это чистка мёртвого кода, поведение отправки писем не меняется.

## Требования

### Функциональные
- Удалить метод `EmailService.sendPairConfirmationEmail` целиком: javadoc, аннотации `@Timed`, `@TrackErrors`, `@Async` и тело.
- Удалить всё, что использовалось только этим методом. Метрический тип `PAIR_CONFIRMATION` встречается только внутри метода и уходит вместе с ним.
- Поведение отправки писем (в том числе подтверждения пар) не меняется.

### Нефункциональные
- i18n: новых строк нет. Ключ `email.tournament_confirm.subject` не удаляется: он используется в других методах `EmailService`.
- Безопасность, производительность: не затрагиваются.

## Вне скоупа
- `TournamentNotificationService.sendPairConfirmationEmails`, `DoubleTournamentRegistrationService.sendPairConfirmationEmails`, вызов в `PlayerService`. Это рабочий путь отправки, метод во множественном числе остаётся.
- `EmailService.sendPartnerInvitationEmail` — тот же класс проблемы, отдельный issue [#620](https://github.com/UstinKO/padel-core-service/issues/620).
- Создание шаблона `email/pair-confirmation.html`: подтверждение пары уже закрыто существующим письмом.
- Исторические упоминания в `specs/done/**` не правятся.
- NPE в логе `TournamentServiceLevelRestrictionTest` (в фикстуре турнира не задан `horaInicio`) — найден при прогоне `./mvnw verify`, к этой задаче не относится (есть и на `master`), отдельный issue [#650](https://github.com/UstinKO/padel-core-service/issues/650).

## Изменения в системе

### API
Нет.

### БД
Нет, миграция не нужна.

### UI
Нет.

### Код
`src/main/java/com/padle/core/padelcoreservice/service/EmailService.java`: удалить метод `sendPairConfirmationEmail`. После удаления проверить, что не осталось неиспользуемых импортов.

## Критерии приёмки
- [x] В репозитории (кроме `specs/`) нет ссылок на `sendPairConfirmationEmail` (единственное число), `email/pair-confirmation`, тип `PAIR_CONFIRMATION` и метрику с тегом `type=pair_confirmation` (дашборды, алерты). Проверка: `git grep -niE "sendPairConfirmationEmail\b|pair[-_]confirmation" -- . ':!specs'` пусто.
- [x] `sendPairConfirmationEmails` (множественное число) в `TournamentNotificationService`, `DoubleTournamentRegistrationService`, `PlayerService` на месте и не изменён: диф затрагивает только `EmailService.java`.
- [x] Отправка писем при регистрации пары не изменилась: путь `TournamentNotificationService.sendPairConfirmationEmails` → `EmailService.sendTournamentConfirmationEmail` дифом не затронут. Автотестов на этот путь в проекте нет; тестировщик 2026-10-20 проверил вживую (локальная SMTP-заглушка): регистрация пары с существующим партнёром — оба игрока получили «Inscripción confirmada / Запись подтверждена» на своём языке, серий метрики `pair_confirmation` нет.
- [x] `./mvnw clean verify` зелёный — 2026-10-19: 397 тестов, 0 падений (JDK 21, PostgreSQL 15, окружение как в `pr-check.yml`).
- [x] `python3 .github/bin/docs-drift.py` — 0 расхождений.

## Edge cases
- Ссылок на метод из тестов и `docs/` нет (проверено grep по репозиторию), компиляция после удаления проходит.
- Ключ `email.tournament_confirm.subject` используется в `sendTournamentConfirmationEmail` и других методах: его ошибочное удаление сломало бы тему реальных писем.
- Метрики `email.send.time` и `email.send.errors` с тегом `type=pair_confirmation` уходят вместе с методом; дашбордов и алертов на них в репозитории нет.
- Конкурентный доступ, существующие данные, миграции: не применимо.

## Открытые вопросы
Нет.
