# LFPT-0460: Feature ссылка на мероприятие в email-напоминаниях за 5 часов — кнопка «Открыть мероприятие»

## Статус
done — PR #461, деплой подтверждён (run 37133413763)

## Источник
Клиентский запрос через Telegram (тема "AI Разработка", отправитель — customer) — `specs/requests/LFPT-0460-event-link-reminder-emails.md`. Issue — [#460](https://github.com/UstinKO/padel-core-service/issues/460).
Telegram-сообщение (для reply): 4730

## Контекст / зачем

Участник получает email-напоминание за 5 часов до начала турнира/мероприятия, но в письме нет прямой ссылки на страницу этого турнира — приходится искать его вручную на сайте, чтобы посмотреть актуальную информацию (сетку, результаты, адрес клуба и т.п.). Нужно добавить в письмо заметную кнопку-ссылку на конкретное мероприятие, с текстом вокруг кнопки, зависящим от формата (есть ли на сайте турнирный интерфейс или нет).

## Связь с MASTER.md

Не меняет инвариантов `MASTER.md`. Затрагивает раздел "Уведомления игрокам" (добавляется новый элемент в существующие email-напоминания), раздел "Форматы турниров" — текст вокруг кнопки различается для форматов с турнирным интерфейсом сайта (Americano, King of Court, Team Americano/Team Playoff) и без него (Cancha Abierta).

## Существующая инфраструктура 5-часовых напоминаний (анализ кода)

Два планировщика, оба `@Scheduled(cron = "0 0/30 * * * *")`, окно `[now+4:45, now+5:15)`, дедуп — персистентный флаг на `Tournament`:

| Планировщик | Кого покрывает | Email-шаблон | Email-метод (`EmailService`) |
|---|---|---|---|
| `IndividualTournamentReminderScheduler` | `AMERICANO`, `KING_OF_COURT` (не DOBLES) | `email/individual-tournament-reminder.html` | `sendIndividualTournamentReminderEmail(...)` |
| `IndividualTournamentReminderScheduler` (та же, отдельная ветка) | `CANCHA_ABIERTA` | `email/cancha-abierta-reminder.html` | `sendCanchaAbiertaReminderEmail(...)` |
| `PairTournamentReminderScheduler` | Любой турнир с `Modalidad.DOBLES` (включает Team Americano и Team Playoff — оба `TournamentType.AMERICANO_TEAMS`, плюс обычный парный bracket) | `email/pair-tournament-reminder.html` | `sendPairTournamentReminderEmail(...)` |

Других 5-часовых email-напоминаний в системе нет. `TournamentType.PADEL_CLINIC` на момент написания этой спеки не существует в `TournamentType` (добавляется отдельной, ещё не смерженной задачей — см. "Вне скоупа").

**Уточнение после ответа на открытый вопрос (было найдено при повторном анализе):** первая версия этой спеки ошибочно утверждала, что у `PlayerPadel` нет поля с предпочитаемым языком. Это неверно — поле `preferredLocale` (колонка `preferred_locale`, default `"es"`) и метод `PlayerPadel.getLocale()` уже существуют и уже используются как стандартный паттерн локализации писем во всём проекте (`PasswordResetService`, `TournamentNotificationService`, `DoubleTournamentRegistrationService`, `AmericanoService`, `TournamentService` и др. — все передают `player.getLocale()` в `EmailService`). Игрок может сам задать язык в профиле (`PerfilController`, `preferredLocale` — es/ru/en). Единственное, что было специфично именно для трёх 5-часовых напоминаний (`sendPairTournamentReminderEmail`, `sendIndividualTournamentReminderEmail`, `sendCanchaAbiertaReminderEmail`) — это то, что они жёстко создавали `new Context(new Locale("es"))` вместо `new Context(locale)`, как все остальные методы `EmailService` (LFPT-437/443 задокументировали это как намеренное решение, но по факту это было упущение при переносе паттерна, не архитектурное ограничение). Соответственно вариант (c) из открытого вопроса ниже — не "больший скоуп", а приведение этих трёх методов к уже существующему во всём проекте паттерну.

## Требования

### Функциональные

1. В шаблоны `email/individual-tournament-reminder.html`, `email/pair-tournament-reminder.html`, `email/cancha-abierta-reminder.html` добавить в верхней части письма (сразу после приветствия/до блока с условиями) заметную кнопку-ссылку на страницу конкретного турнира.
2. Ссылка — на страницу `{app.base-url}/torneo/{tournamentId}` (публичная `TournamentViewController`, работает единообразно для всех типов турниров без разбора по формату — отображает сводку по Americano/King of Court/Team Playoff, если применимо, иначе общие детали турнира; подтверждено чтением кода `TournamentViewController.viewTournament`). Разные специализированные публичные страницы (`/tournaments/americano/{id}`, `/torneo/{id}/king-of-court`, `/tournaments/team-americano/{id}`, `/tournaments/team-playoff/{id}`) не используются для этой кнопки — одна и та же ссылка для всех форматов технически проще и не требует разбора по типу/модальности внутри планировщиков (KISS).
3. Для `individual-tournament-reminder.html` (AMERICANO, KING_OF_COURT) и `pair-tournament-reminder.html` (DOBLES) — под кнопкой дополнительный текст о live-информации (сетка, распределение по кортам, результаты, рейтинг).
4. Для `cancha-abierta-reminder.html` — та же кнопка, без текста про сетку/рейтинг/историю матчей/распределение по кортам.
5. `EmailService.sendIndividualTournamentReminderEmail`, `sendPairTournamentReminderEmail`, `sendCanchaAbiertaReminderEmail` — добавить параметры `Long tournamentId` и `Locale locale`. `eventUrl` строится внутри `EmailService` (`baseUrl + "/torneo/" + tournamentId`, `baseUrl` там уже внедрён `@Value("${app.base-url}")`) — планировщики передают только `tournament.getId()`, без новой зависимости в самих планировщиках. `Context` в этих трёх методах меняется с `new Context(new Locale("es"))` на `new Context(locale)`; планировщики передают `registration.getPlayer().getLocale()` — тот же паттерн, что уже используется для всех остальных писем в проекте.
6. Кнопка-ссылка и сопровождающий текст — через новые i18n-ключи (`email.reminder.button.view_event`, `email.reminder.live_info.heading`, `email.reminder.live_info.body` — для individual/pair, `email.reminder.view_info` — для Cancha Abierta), добавленные в `messages_{es,ru,en}.properties`, резолвятся Thymeleaf `#{...}` по локали игрока. Остальной текст письма (приветствие, адрес, время, предупреждение об опоздании/правилах отмены) остаётся жёстко на испанском, как решено в LFPT-437/443 — локализуется только зона кнопки (решение открытого вопроса, см. ниже).

### Нефункциональные
- i18n: язык кнопки/подписи — по `PlayerPadel.getLocale()` (решение открытого вопроса — вариант (c), см. ниже). Остальной текст письма — не локализуется, остаётся на испанском (вне скоупа и исходного клиентского запроса).
- Безопасность: новых точек входа данных от пользователя нет — `eventUrl` строится из `tournament.getId()` (внутренний Long), не из пользовательского ввода.

## Вне скоупа

- `TournamentType.PADEL_CLINIC` — не существует в системе на момент написания этой спеки (добавляется отдельными задачами #456/#457, открытые PR #458/#459, ещё не смержены). В #456 явно зафиксировано: *«Email-напоминания за 5 часов до начала — не запрошены для нового типа»*. Соответственно у Padel Clinic нет и не будет 5-часового email-напоминания в рамках этой спеки — ссылку туда добавлять не к чему. Если/когда для Padel Clinic появится своё 5-часовое напоминание отдельной задачей — переиспользовать тот же компонент кнопки (тот же `eventUrl`, вариант текста "без сетки/рейтинга", как у Cancha Abierta).
- Изменение текста самого предупреждения про штраф за опоздание (−10 очков) или правил отмены — не трогаем, только добавляем кнопку-ссылку и текст рядом с ней.
- Любая локализация остального содержимого письма (приветствие, адрес, время) — остаётся как есть (испанский), не входит в скоуп.

## Изменения в системе

### API
Нет новых/изменённых REST-эндпоинтов.

### БД
Нет новой Liquibase-миграции — поле `preferred_locale` у `PlayerPadel` уже существует (добавлено отдельной, ранее смерженной задачей), ничего нового создавать не нужно.

### i18n
- `src/main/resources/i18n/messages_{es,ru,en}.properties` — 4 новых ключа: `email.reminder.button.view_event`, `email.reminder.live_info.heading`, `email.reminder.live_info.body`, `email.reminder.view_info`.

### UI (email-шаблоны, относятся к backend по `CLAUDE.md` §1.4)
- `src/main/resources/templates/email/individual-tournament-reminder.html` — кнопка (`#{email.reminder.button.view_event}`) + текст live-информации (`#{email.reminder.live_info.*}`).
- `src/main/resources/templates/email/pair-tournament-reminder.html` — та же кнопка + live-info.
- `src/main/resources/templates/email/cancha-abierta-reminder.html` — кнопка + короткая подпись (`#{email.reminder.view_info}`), без блока live-info.
- `src/main/java/.../service/EmailService.java` — три метода (`sendIndividualTournamentReminderEmail`, `sendPairTournamentReminderEmail`, `sendCanchaAbiertaReminderEmail`): новые параметры `Long tournamentId`, `Locale locale`; `Context` строится с `locale` вместо жёсткого `new Locale("es")`; `eventUrl` строится внутри метода из `baseUrl` (уже внедрён) + `tournamentId`.
- `src/main/java/.../service/IndividualTournamentReminderScheduler.java`, `PairTournamentReminderScheduler.java` — передают `tournament.getId()` и `registration.getPlayer().getLocale()` в соответствующие методы `EmailService` (без новых зависимостей в самих планировщиках).

## Критерии приёмки
- [x] Email-напоминание AMERICANO/KING_OF_COURT (не DOBLES) за 5 часов содержит кнопку-ссылку на `{baseUrl}/torneo/{id}` + текст про live-информацию (сетка/корты/результаты/рейтинг)
- [x] Email-напоминание парного турнира (любой тип с `Modalidad.DOBLES`, включая Team Americano/Team Playoff) за 5 часов содержит ту же кнопку + текст про live-информацию
- [x] Email-напоминание Cancha Abierta за 5 часов содержит кнопку-ссылку на `{baseUrl}/torneo/{id}`, но без текста про сетку/рейтинг/историю матчей/корты
- [x] Ссылка во всех трёх письмах ведёт на страницу конкретного турнира по его ID (не на `/torneos`)
- [x] Текст кнопки и сопроводительный текст локализуются по `PlayerPadel.getLocale()` игрока (es/ru/en) — решение открытого вопроса, вариант (c)
- [x] `./mvnw compile -DskipTests` и `./mvnw verify` проходят зелёными (177/177, BUILD SUCCESS)
- [x] Регрессия: существующий текст про штраф за опоздание / правила отмены не изменился ни в одном из трёх писем и остаётся на испанском независимо от локали игрока (локализуется только зона кнопки, не всё письмо)

## Edge cases
- Турнир деактивирован/удалён между моментом отправки напоминания и кликом по ссылке — `TournamentViewController` уже обрабатывает этот случай (404 `error/tournament-not-found`), отдельной логики не требуется.
- `baseUrl` не сконфигурирован (пустая строка) — тот же риск, что и у всех остальных писем, использующих `app.base-url` (`EmailService` уже полагается на это свойство без доп. проверок) — не новая проблема этой задачи.

## Открытые вопросы

1. **Язык кнопки/текста вокруг неё — РЕШЕНО.** Запрос даёт текст кнопки на трёх языках (RU: «Открыть мероприятие», ES: «Ver evento», EN: «View event») и пример сопроводительного текста на испанском. Исходный анализ (первая версия этой спеки) ошибочно посчитал, что у `PlayerPadel` нет инфраструктуры для определения языка игрока, и предложил три варианта:
   - (a) Оставить письма полностью на испанском.
   - (b) Показать кнопку на всех трёх языках одновременно в одном письме.
   - (c) Завести предпочитаемый язык игрока и локализовать кнопку по нему.

   **Ответ заказчика (Telegram, `telegram_launch_message_id: 4734`): вариант (c).** При реализации выяснилось, что инфраструктура для (c) уже существует в проекте (`PlayerPadel.preferredLocale`/`getLocale()`, уже используется во всех остальных письмах системы) — так что это не новая большая задача, а приведение этих трёх конкретных методов `EmailService` к уже стандартному для проекта паттерну. Кнопка и текст рядом с ней локализуются по `player.getLocale()` (es/ru/en, через новые i18n-ключи `email.reminder.*`); остальной текст письма остаётся на испанском, как и раньше (LFPT-437/443) — запрос касался только кнопки, не всего письма.
