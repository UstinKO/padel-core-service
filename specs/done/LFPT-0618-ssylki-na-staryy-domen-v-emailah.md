# LFPT-0618: Fix ссылки в email-уведомлениях на старый домен epadel.org — аудит шаблонов и переход на единый APP_BASE_URL

## Статус
done

## Источник
Клиентский запрос [specs/requests/LFPT-0618-ssylki-na-staryy-domen-v-emailah.md](requests/LFPT-0618-ssylki-na-staryy-domen-v-emailah.md) → issue [#618](https://github.com/UstinKO/padel-core-service/issues/618).
Telegram-сообщение (для reply): 5175

## Контекст / зачем
Клиент сообщил: кнопка «Ver página del torneo» в одном из писем ведёт на старый домен `epadel.org` вместо `1-padel.com`, и просил полный аудит всех ссылок во всех email-уведомлениях, устранение хардкода домена и переход на единый base URL.

## Связь с MASTER.md
Не затрагивает бизнес-инварианты MASTER.md — чисто техническая правка (корректность ссылок в уже существующих уведомлениях, раздел «Уведомления игрокам»).

## Результат аудита (полный, по всем 15 шаблонам `templates/email/**`)

Код **уже построен вокруг единой точки** — `app.base-url` (env `APP_BASE_URL`), инжектируется как `@Value` в `EmailService`, `TournamentService`, `DoubleTournamentRegistrationService`, `TelegramReminderScheduler`, `SoloRegistrationSchedulerService`, `PasswordResetService`. Литерального `epadel.org` в пользовательских ссылках **не найдено** ни в одном шаблоне, ни в Java-коде, формирующем URL. Единственные упоминания `epadel.org` в репозитории — технические, не пользовательские ссылки: allowlist CORS-origin в `SecurityConfig` (обратная совместимость), дефолт `EMAIL_FROM` в `application.yml`/`docker-compose.yml` (адрес отправителя, не ссылка в теле письма), креды тестового Postgres в CI. Их не трогаем — вне скоупа (ссылки, не отправитель/CORS).

Экземпляр с самой похожей на жалобу клиента ссылкой — кнопка «Ver página del torneo» в `looking-for-partner-notification.html` — формируется как `${baseUrl} + "/torneo/" + tournamentId` (`DoubleTournamentRegistrationService.java`) и работает корректно на уровне кода.

**Найдены и исправлены в этой задаче** (реальные баги с битыми/захардкоженными ссылками, обнаруженные построчным сопоставлением каждого `context.setVariable(...)` в `EmailService` с каждой `${...}`/`th:href` в соответствующем шаблоне):

| Шаблон / метод | Было | Исправлено |
|---|---|---|
| `tournament-confirmation.html` ← `sendRegistrationConfirmationEmail` | `baseUrl` не передавался в контекст → кнопка «Mis torneos» и ссылка `1-padel.com` в футере рендерились без `href` (битая ссылка). Живой путь: `TournamentNotificationService.sendMainPlayerNotification` при `status == CONFIRMED`. | Добавлен `context.setVariable("baseUrl", baseUrl)`, как в соседнем `sendTournamentConfirmationEmail`. |
| `recuperar-password.html` | Футер: `<a href="https://1-padel.com">` — хардкод домена напрямую в шаблоне (пока совпадает с реальным, но не переживёт смену домена). | `<a th:href="${baseUrl}">`, `baseUrl` добавлен в контекст `sendPasswordResetEmail`. |
| `no-spots-left.html` | Кнопка «Ver todos los torneos»: `<a href="https://1-padel.com/torneos">` — тот же хардкод. | `<a th:href="${baseUrl + '/torneos'}">`. |
| `no-spots-left.html` ← `sendNoSpotsLeftEmail` | Кнопка «Apuntarme a lista de espera» (`waitlistUrl`) не передавалась в контекст вообще → битая ссылка без `href`. | Метод получил параметр `tournamentUrl` (вызывающий `TournamentService.sendNoSpotsLeftEmail` строит его тем же способом, что и соседний `sendWaitlistAutoConfirmEmail` — `baseUrl + "/torneo/" + id`), передаётся в шаблон как `waitlistUrl` — ведёт на страницу турнира. |
| `confirmacion.html`, `bienvenida.html`, `vacancy-invitation.html`, `no-spots-left.html` — общий футер | `terminosUrl`, `privacidadUrl`, `contactoUrl`, `unsubscribeUrl` нигде не устанавливались ни в одном из четырёх отправляющих методов → все 4 ссылки футера (условия, конфиденциальность, контакт, отписка) рендерились без `href` во всех письмах, использующих этот футер. | Новый приватный метод `EmailService.setLegalFooterLinks(context)`: `terminosUrl`/`privacidadUrl` — из `baseUrl` (`/terminos`, `/privacidad` — существующие публичные страницы, `LegalController`); `contactoUrl`/`unsubscribeUrl` — `mailto:soporte@1-padel.com` (страницы контакта/самостоятельной отписки в системе нет — тот же паттерн, что уже используется в `recuperar-password.html`/`waitlist-notification.html`/`tournament-confirmation.html`). Вызывается из `sendConfirmationEmail`, `sendWelcomeEmail`, `sendWaitlistAutoConfirmEmail`, `sendNoSpotsLeftEmail`. |

Регрессия закрыта автотестом (см. «Критерии приёмки»).

## Вне скоупа (побочные находки — отдельные issue, не реализуются в этой задаче)

- **[#619](https://github.com/UstinKO/padel-core-service/issues/619)** — вероятная истинная причина жалобы клиента: переменная `APP_BASE_URL` на проде, похоже, до сих пор указывает на `epadel.org` (инфраструктурный домен сервера, см. уточнение в `docs/architecture.md`), а не на `1-padel.com`. Это конфигурация сервера, а не код — изменение `.env` на проде требует отдельного согласования и SSH-доступа, которых у облачной сессии нет (`GIT_WORKFLOW.md` §7). Без этого шага жалоба клиента не исчезнет даже после мержа этой задачи.
- **[#620](https://github.com/UstinKO/padel-core-service/issues/620)** — `EmailService.sendPartnerInvitationEmail` — мёртвый код с нерабочим шаблоном (`email/partner-invitation.html` не существует), тот же класс проблемы, что уже заведённый `#617` (`sendPairConfirmationEmail`). Не исправляется здесь, чтобы не плодить два разных решения для одной проблемы.
- Функциональность "альтернативные турниры"/"общий спрос" в `no-spots-left.html` (`alternativeTournaments`, `totalDemand`) — переменные нигде не заполняются, но безопасно скрыты через `th:if` (не битая ссылка, просто нереализованная фича). Не в скоупе задачи "починить ссылки".
- Реальная функция самостоятельной отписки/страница контакта — не существует в системе; временное решение (`mailto:`) закрывает битую ссылку, но не создаёт новую фичу — она отдельным скоупом, если понадобится.

## Критерии приёмки

- [x] В `src/main/resources/templates/email/**` нет ни одной ссылки, ведущей на `epadel.org` — автотест `EmailTemplateLinksTest.emailTemplates_noReferenceToOldDomain` (в т.ч. защищает от регрессии: было и остаётся 0 вхождений).
- [x] Все ссылки на разделы сайта построены из `${baseUrl}`/переменных, производных от него, а не хардкод-строкой домена — автотест `EmailTemplateLinksTest.emailTemplates_pageLinksAreBuiltFromBaseUrlNotHardcoded` (разрешены только литеральные ссылки на соцсети и `mailto:`, остальные литеральные `href="http..."` — fail).
- [x] Есть автоматическая проверка (тест), которая не даст в будущем случайно закоммитить хардкод домена в email-шаблоны — оба теста выше; вручную проверено, что тест действительно падает при внесении хардкода (временная регрессия `https://epadel.org/torneos` в `no-spots-left.html` → оба теста красные, затем откат).
- [x] В описании PR — таблица: какие шаблоны проверены, что было не так, что исправлено (см. таблицу выше и «Результат аудита»).
- [x] Побочные находки, не входящие в скоуп, заведены отдельными issue (#619, #620), не реализованы здесь.

## Edge cases
- Методы `EmailService`, у которых шаблон не использует общий футер (`new-partner-invitation.html`, `waitlist-notification.html`, `pair-tournament-reminder.html`, `individual-tournament-reminder.html`, `cancha-abierta-reminder.html`, `admin-solo-registration.html`, `pair-proposal.html`, `partner-data-reminder.html`, `looking-for-partner-notification.html`) — не трогались, у них уже либо нет ссылок на это футер, либо ссылки и так строились из `baseUrl`/переданного параметра.
- `sendPairConfirmationEmail`/`sendPartnerInvitationEmail` (мёртвый код, несуществующие шаблоны) — сознательно не трогаются здесь, см. «Вне скоупа».

## Открытые вопросы
Нет — весь код-скоуп задачи однозначен (устранение хардкода и битых ссылок по явному чек-листу клиентского запроса). Единственный пункт, требующий решения человека с доступом к серверу (проверка/обновление `APP_BASE_URL` на проде), выделен в отдельный issue #619 — он не блокирует эту задачу и не является бизнес-вопросом к заказчику.
