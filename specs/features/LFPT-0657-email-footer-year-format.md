# LFPT-0657: Fix год в подвале писем отображается как 2.026 — форматировать без разделителя разрядов

## Статус
approved

## Источник
Спека написана напрямую, без клиентского запроса. Issue [#657](https://github.com/UstinKO/padel-core-service/issues/657), найдено тестировщиком при проверке [#617](https://github.com/UstinKO/padel-core-service/issues/617).

## Контекст / зачем
В подвале 8 email-шаблонов строка копирайта выводится через сообщение с аргументом: `th:text="#{email.footer.rights(${year})}"`, ключ `email.footer.rights=© {0} 1-Padel. …`. `EmailService` передаёт `year` числом (`java.time.Year.now().getValue()`), и `MessageFormat` форматирует `{0}` как число с разделителем разрядов по локали письма. Получатели видят `© 2.026` (es), `© 2 026` (ru, неразрывный пробел U+00A0), `© 2,026` (en). Это почти все письма, которые получает игрок: приветствие, запись на турнир, лист ожидания, сброс пароля.

## Связь с MASTER.md
Не затрагивает бизнес-инварианты MASTER.md. Меняется только отображение года в подвале писем.

## Требования

### Функциональные
- В `src/main/resources/i18n/messages_{es,ru,en}.properties` в значении ключа `email.footer.rights` заменить `{0}` на `{0,number,#}`. Остальной текст значения не меняется.
- В подвале всех писем с этим ключом год выводится четырьмя цифрами без разделителя: `© 2026 1-Padel. …` на es, ru и en.
- Java-код и шаблоны не меняются: `year` во все затронутые шаблоны уже передаётся числом.

Шаблоны с ключом (все в `src/main/resources/templates/email/`) и методы `EmailService`, которые их рендерят:

| Шаблон | Метод `EmailService` |
|---|---|
| `confirmacion.html` | `sendConfirmationEmail` (нигде не вызывается, см. «Вне скоупа») |
| `new-partner-invitation.html` | `sendNewPartnerInvitationEmail` (цепочка не вызывается, см. «Вне скоупа») |
| `bienvenida.html` | `sendWelcomeEmail` |
| `vacancy-invitation.html` | `sendWaitlistAutoConfirmEmail` |
| `no-spots-left.html` | `sendNoSpotsLeftEmail` |
| `tournament-confirmation.html` | `sendRegistrationConfirmationEmail`, `sendTournamentConfirmationEmail` |
| `recuperar-password.html` | `sendPasswordResetEmail` |
| `waitlist-notification.html` | `sendWaitlistNotificationEmail` |

### Нефункциональные
- i18n: новых ключей нет. Меняется формат аргумента одного ключа `email.footer.rights` в трёх языках.
- Безопасность, производительность: не затрагиваются.

## Вне скоупа
- Шаблоны, которые выводят год напрямую через `th:text="${year}"`, без сообщения: `admin-solo-registration`, `looking-for-partner-notification`, `partner-data-reminder`, `partner-deadline-reserve`, `partner-final-reminder`, `pair-tournament-reminder`, `individual-tournament-reminder`, `cancha-abierta-reminder`. То же на страницах `legal/cookies.html`, `legal/privacidad.html`, `legal/terminos.html` (`LegalController`). Там год уже выводится как `2026`.
- `String.valueOf(...)` для `year` в `EmailService.sendPartnerReserveEmail` и `sendPartnerFinalReminderEmail` не трогаем. Их шаблоны (`partner-deadline-reserve`, `partner-final-reminder`) печатают `${year}` напрямую, ключ `email.footer.rights` не используют.
- Цепочка `new-partner-invitation` (шаблон, `EmailService.sendNewPartnerInvitationEmail`, `TournamentNotificationService.sendNewPartnerInvitation`) после LFPT-0687 не вызывается, её удаление — [#689](https://github.com/UstinKO/padel-core-service/issues/689). Фикс ключа на неё распространяется автоматически, если #689 удалит шаблон раньше, здесь ничего не меняется.
- `EmailService.sendConfirmationEmail` (шаблон `confirmacion.html`) в `src/main` нигде не вызывается: одноимённый `TournamentService.sendConfirmationEmail` — собственный приватный метод, он отправляет `tournament-confirmation` через `EmailService.sendTournamentConfirmationEmail`. Найдено тестировщиком при проверке LFPT-0657. Фикс ключа распространяется на шаблон автоматически, удаление мёртвого метода — отдельной задачей (тот же класс проблемы, что #689).
- Базовый `messages.properties` (fallback для локалей вне es/ru/en) не содержит ключей `email.*` и не меняется.
- Статический текст-прототип внутри тегов (`© 2026 1-Padel. …`) Thymeleaf заменяет на результат `th:text`, его не трогаем.

## Изменения в системе

### API
Нет.

### БД
Нет, миграция не нужна.

### UI
Разметка шаблонов не меняется, меняется вывод года в подвале 8 email-шаблонов (см. таблицу выше).

### Код
- `src/main/resources/i18n/messages_es.properties`, `messages_ru.properties`, `messages_en.properties`: ключ `email.footer.rights`, `{0}` → `{0,number,#}`.
- Тест: новый класс `EmailFooterYearTemplateTest` в `src/test/java/com/padle/core/padelcoreservice/service/` (пакет `com.padle.core.padelcoreservice.service`, рядом с `TournamentConfirmationEmailTemplateTest`). Те же `@SpringBootTest` и `@TestPropertySource`, что в `TournamentConfirmationEmailTemplateTest`, чтобы Spring переиспользовал закешированный контекст. Параметризованный тест по локалям `es`, `ru`, `en`: рендер `email/tournament-confirmation` через `SpringTemplateEngine` с `year = 2026` (число, как передаёт `EmailService`) и проверка, что HTML содержит `2026 1-Padel`.

### Документация
- `docs/i18n/stage-9-email-keys.md`: в примере секции «Общий footer и header email» ключ `email.footer.rights` привести к `{0,number,#}`.

## Критерии приёмки
- [x] Тест `EmailFooterYearTemplateTest` до правки properties падает на всех трёх локалях (в HTML `2.026 1-Padel`, `2 026 1-Padel`, `2,026 1-Padel`), после правки зелёный. 2026-10-23: до правки 3 из 3 падений с `© 2.026` (es), `© 2 026` (ru), `© 2,026` (en); после правки 3 из 3 зелёные.
- [x] В `email.footer.rights` во всех трёх `messages_{es,ru,en}.properties` стоит `{0,number,#}`. Проверка: `git grep -n 'email.footer.rights' -- src/main/resources/i18n` — три строки, во всех `{0,number,#}`.
- [x] Диф кода затрагивает только три `messages_*.properties` (по одной строке) и новый тест; шаблоны и `EmailService.java` не изменены. Плюс одна строка в `docs/i18n/stage-9-email-keys.md`.
- [x] Живая проверка (локальная SMTP-заглушка mailpit): письмо из списка, например сброс пароля (`recuperar-password`), на es и ru — в подвале `© 2026 1-Padel. …`. 2026-10-23, профиль `dev`, сброс пароля из модалки на `/login`: es — «🔐 Recuperación de contraseña — 1-Padel», подвал `© 2026 1-Padel. Todos los derechos reservados.`; ru — «🔐 Восстановление пароля — 1-Padel», подвал `© 2026 1-Padel. Все права защищены.`, неразрывного пробела в годе нет. en вживую не проверялся, покрыт тестом.
- [x] `./mvnw clean verify` зелёный — 2026-10-23: 444 теста, 0 падений (JDK 21, локальный PostgreSQL в Docker).
- [x] `python3 .github/bin/docs-drift.py` — 0 расхождений (2026-10-23).

## Edge cases
- `{0,number,#}` требует числовой аргумент. Если в шаблон с этим ключом передать `year` строкой, `MessageFormat` бросит `IllegalArgumentException: Cannot format given Object as a Number`, рендер упадёт, и письмо не уйдёт (исключение поймает `catch` в методе `EmailService` и запишет в лог). Сейчас все 8 шаблонов с ключом получают `int`. Если подвал с ключом добавят в шаблоны `partner-deadline-reserve` / `partner-final-reminder`, `year` в `sendPartnerReserveEmail` / `sendPartnerFinalReminderEmail` нужно перевести на число.
- В ru разделитель разрядов — неразрывный пробел U+00A0, поэтому в тесте не проверять отсутствие `2 026` с обычным пробелом: такая проверка проходит и с багом. Достаточно положительной проверки `2026 1-Padel`.
- `#` внутри значения в `.properties` не комментарий (комментарий — только `#` в начале строки), Spring читает значение как есть.
- Апострофов в значениях `email.footer.rights` нет, экранировать для `MessageFormat` ничего не нужно.
- Конкурентный доступ, существующие данные, миграции: не применимо.

## Открытые вопросы
Нет.
