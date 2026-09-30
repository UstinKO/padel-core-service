# LFPT-0443: Feature email-напоминание за 5 часов для индивидуальных турниров + логика опоздания (−10 очков) + отдельный сценарий Cancha Abierta

## Статус
done (PR #446)

## Источник
GitHub issue [#443](https://github.com/UstinKO/padel-core-service/issues/443), написанный напрямую по клиентскому запросу — исходный запрос сохранён в [specs/requests/LFPT-0443-individual-reminder-late-arrival.md](../requests/LFPT-0443-individual-reminder-late-arrival.md) (пришёл в Telegram, `[отправитель: customer]`).

Telegram-сообщение (для reply): 4687

## Контекст / зачем

Участники индивидуальных турниров (King of Court, Americano) сейчас не получают заблаговременного email-напоминания о месте/времени начала. Аналогичная задача для парных турниров (`Modalidad.DOBLES`) уже сделана в LFPT-0437 (PR #439, смержен, коммит `2a030e3`) — эта задача реализует ту же механику для индивидуальных турниров, с дополнительной бизнес-логикой (штраф за опоздание), и отдельно — упрощённый вариант письма для Cancha Abierta без каких-либо санкций.

**Разграничение по типу, не по модальности.** `Cancha Abierta` (`TournamentType.CANCHA_ABIERTA`) технически может использовать `Modalidad.INDIVIDUAL` (форма создания турнира явно позволяет выбрать модальность для этого типа — `admin/tournaments/form.html`, `onTipoChange()`), но по требованию заказчика не считается «индивидуальным турниром» в смысле этой задачи. Разграничение — по `tournament.tipo`, не по `tournament.modalidad`:
- `tipo` = `AMERICANO` или `KING_OF_COURT` (оба технически всегда `Modalidad.INDIVIDUAL` — форма это принудительно выставляет) → «индивидуальный турнир» этой задачи: письмо с упоминанием штрафа + доступна логика опоздания.
- `tipo` = `CANCHA_ABIERTA` → отдельное, более мягкое письмо, логика опоздания недоступна вообще, независимо от выбранной модальности.
- `tipo` = `AMERICANO_TEAMS` → вне скоупа (всегда `Modalidad.DOBLES`, уже покрыт LFPT-0437).

## Связь с MASTER.md

Не меняет инвариантов `specs/MASTER.md`. Расширяет раздел «Уведомления игрокам» новым видом email-напоминания (по аналогии с уже существующим для парных турниров) и добавляет новый технический атрибут регистрации (флаг опоздания), не описанный ранее в MASTER.md как отдельный бизнес-инвариант, — специфика конкретной фичи, обновление MASTER.md не требуется.

## Требования

### Функциональные

1. **Email-напоминание за 5 часов, индивидуальные турниры (`AMERICANO`, `KING_OF_COURT`)**
   - Фоновый планировщик (аналог `PairTournamentReminderScheduler`, `@Scheduled(cron = "0 0/30 * * * *")`), окно `[+4ч45м; +5ч15м)` от текущего момента.
   - Кандидаты — активные турниры (`TournamentRepository.findByFechaInicioInAndActive`, уже исключает `CANCELADO`/`FINALIZADO`) с `tipo` в `{AMERICANO, KING_OF_COURT}`.
   - Всем `CONFIRMED`-регистрациям турнира (`TournamentRegistrationRepository.findByTournamentIdAndStatus`) с непустым email — письмо:
     ```
     ¡Hola! Te esperamos hoy para el torneo de pádel en {club} ({dirección}) a las {hora} hs.
     👉 Te pedimos llegar 15 minutos antes.
     ⚠️ En caso de llegar tarde, comenzarás el torneo con −10 puntos.
     🎾 ¡Nos vemos en la cancha!
     ```
   - Текст письма фиксирован на испанском, без i18n (как у `email/pair-tournament-reminder.html`).

2. **Email-напоминание за 5 часов, Cancha Abierta (`tipo=CANCHA_ABIERTA`)**
   - Тот же планировщик (или тот же прогон), окно то же самое.
   - Всем `CONFIRMED`-регистрациям — письмо:
     ```
     ¡Hola! Te esperamos hoy para la Cancha Abierta en {club} ({dirección}) a las {hora} hs.
     👉 Te pedimos llegar aproximadamente 15 minutos antes.
     Si finalmente no podés venir, por favor avisá al organizador con anticipación, así podemos liberar tu lugar para otro jugador.
     🎾 ¡Nos vemos en la cancha!
     ```
   - Без упоминания штрафа за опоздание.

3. **Персистентная защита от повторной отправки** — новое поле `Tournament.startReminderSentAt` (`LocalDateTime`, nullable), по аналогии с `Tournament.pairReminderSentAt` из LFPT-0437. Общее для обоих писем этого планировщика (п.1 и п.2) — на турнир приходится ровно один `tipo`, коллизий нет. Не переиспользует `pairReminderSentAt` (тот — только для `DOBLES`-планировщика LFPT-0437, разные поля — разные ответственности, отдельные миграции).
   - Выставляется **после** попытки отправки, даже если писем не получилось отправить (нет подтверждённых игроков / ошибка при отправке одному из) — защита от дублей приоритетнее повторной попытки, тот же принцип, что и в `PairTournamentReminderScheduler`.
   - Редактирование турнира после отправки (включая изменение времени начала) — повторной отправки не вызывает.

4. **Логика опоздания (`AMERICANO`, `KING_OF_COURT` — только эти два типа)**
   - Новое поле `TournamentRegistration.lateArrival` (`Boolean`, `default false`).
   - Новый метод сервиса `TournamentService.setLateArrival(tournamentId, playerId, lateArrival)`:
     - Тип турнира должен быть `AMERICANO` или `KING_OF_COURT`, иначе — `IllegalStateException` (для `CANCHA_ABIERTA`/`AMERICANO_TEAMS` логика недоступна вообще, п.5 клиентского запроса).
     - Идемпотентно: если `registration.lateArrival` уже равен запрошенному значению — no-op, повторного применения штрафа/возврата не происходит.
     - При переходе `false → true`: −10 к текущему результату игрока в этом турнире (`AmericanoPlayer.totalScore` для `AMERICANO` / `KingOfCourtPlayerStats.totalPoints` для `KING_OF_COURT`, найденных по `tournamentId`+`playerId`).
     - При переходе `true → false` (снятие отметки администратором): возврат +10 (симметрично, на случай ошибочной отметки).
     - Дальнейшие очки, начисляемые через обычный игровой флоу (`AmericanoPlayer.addMatchResult`, обновление `KingOfCourtPlayerStats.totalPoints` в `KingOfCourtService`), прибавляются поверх — специальной доработки не требуют, т.к. штраф применяется непосредственно к тому же полю, которое инкрементируется матчами.
     - Если для игрока ещё нет строки статистики формата (турнир ещё не инициализирован через `AmericanoService.initializeTournament` / `KingOfCourtService.initializeTournament`) — `IllegalStateException` с понятным сообщением.
   - Новый endpoint `POST /admin/tournaments/{tournamentId}/players/{playerId}/late-arrival` (form param `lateArrival=true|false`) в `AdminController`, по образцу `moveToWaitlist`/`moveToMain`: `@ResponseBody`, `tournamentAccessService.assertCanManageTournament(owner, tournamentId)` (соблюдает изоляцию `ROLE_CLUB_ADMIN` по клубу, LFPT-376), `{success, message}` в ответе. **Только backend-endpoint — UI (чекбокс) реализуется отдельной задачей LFPT-0444.**
   - В публичных рейтингах (`AmericanoService`/`KingOfCourtService` ranking-эндпоинты) отдельная колонка «штраф» не нужна — штраф уже часть `totalScore`/`totalPoints`, эти эндпоинты не меняются.

### Нефункциональные
- i18n: письма — фиксированный испанский текст, без i18n-ключей (сознательное решение заказчика и существующий прецедент LFPT-0437/`TelegramReminderScheduler`). Новых пользовательских строк в UI в рамках этой (backend) задачи нет — чекбокс и его подписи будут в LFPT-0444.
- Безопасность: новый endpoint переключения флага — только для `Owner` с правом управления турниром (`assertCanManageTournament`), новых точек входа от публичных пользователей нет.
- Производительность: планировщик работает по тому же паттерну и той же периодичности (`0 0/30 * * * *`), что уже принятый в проекте (`TelegramReminderScheduler`, `PairTournamentReminderScheduler`) — дополнительной нагрузки сверх типичной для таких job не создаёт.

## Вне скоупа
- UI-чекбокс «Опоздал» в админ-панели — отдельная задача LFPT-0444 (frontend).
- Настраиваемое значение штрафа (не только −10) — явно вне скоупа по клиентскому запросу («на первом этапе значение фиксированное»).
- Изменения для `AMERICANO_TEAMS` (парные турниры) — уже покрыты LFPT-0437.
- Публичное отображение факта опоздания — явно не нужно по запросу.

## Изменения в системе

### API
- `POST /admin/tournaments/{tournamentId}/players/{playerId}/late-arrival` — новый, `@RequestParam boolean lateArrival`, JSON-ответ `{success, message}`.

### БД
- `v1.50-add-start-reminder-sent-at-to-tournament.yaml` — `tournaments_db.start_reminder_sent_at TIMESTAMP NULL`.
- `v1.51-add-late-arrival-to-registration.yaml` — `tournament_registrations_db.late_arrival BOOLEAN NOT NULL DEFAULT FALSE`.
- Обе регистрируются в `changelog-master.yaml` следующими по номеру после `v1.49`.

### UI
Нет (эта задача — backend-only; UI-часть — LFPT-0444, шаблон `admin/tournaments/details.html`).

## Критерии приёмки
- [ ] Турнир `tipo=AMERICANO` либо `KING_OF_COURT`, старт через ~5 часов, есть `CONFIRMED`-игрок с email — после прогона планировщика `tournament.startReminderSentAt` не `null`, и (проверяется рендером шаблона) текст письма содержит корректную подстановку `{club}`/`{dirección}`/`{hora}` и фразу про −10 очков.
- [ ] Турнир `tipo=CANCHA_ABIERTA`, старт через ~5 часов, есть `CONFIRMED`-игрок — после прогона `startReminderSentAt` не `null`, письмо (рендер шаблона) НЕ содержит упоминания штрафа/очков, содержит фразу про уведомление организатора.
- [ ] Турнир `tipo=AMERICANO_TEAMS` в той же 5-часовой окне — `startReminderSentAt` остаётся `null` (обрабатывается только LFPT-0437 планировщиком, через `pairReminderSentAt`).
- [ ] Турнир вне окна `[+4ч45м; +5ч15м)` — `startReminderSentAt` остаётся `null`.
- [ ] Турнир, уже помеченный (`startReminderSentAt` в прошлом) — повторный прогон планировщика не меняет `startReminderSentAt`, даже если время начала турнира отредактировано и снова попадает в окно.
- [ ] `AmericanoPlayer` (или `KingOfCourtPlayerStats`) с `totalScore` (`totalPoints`) = N: вызов `setLateArrival(tournamentId, playerId, true)` уменьшает значение на 10, `registration.lateArrival = true`. Повторный вызов с `true` — значение не меняется повторно (идемпотентность).
- [ ] Вызов `setLateArrival(tournamentId, playerId, false)` после включения — возвращает +10, `registration.lateArrival = false`.
- [ ] После установки `lateArrival=true` — обычное начисление результата матча (`addMatchResult` / аналог для KoC) добавляет очки поверх штрафа (штраф не перезаписывается).
- [ ] `setLateArrival` для турнира `tipo=CANCHA_ABIERTA` или `AMERICANO_TEAMS` — бросает `IllegalStateException`, endpoint возвращает `{success:false, ...}`.
- [ ] `./mvnw verify` зелёный, новые Liquibase-миграции зарегистрированы и применяются на чистой БД.

## Edge cases
- Игрок без email в момент отправки напоминания — пропускается с предупреждением в лог (как в `PairTournamentReminderScheduler`), не роняет обработку остальных игроков турнира.
- Турнир без `CONFIRMED`-игроков в момент окна — всё равно помечается обработанным (`startReminderSentAt` выставляется), чтобы не пытаться отправлять повторно при появлении новых `CONFIRMED`-регистраций позже (тот же принцип, что и в LFPT-0437 — защита от дублей приоритетнее).
- `setLateArrival` для игрока, чья регистрация не `CONFIRMED` (например `CANCELLED`) — допустимо технически (штраф не имеет смысла без участия, но явного запрета клиент не просил); не добавляем дополнительную бизнес-проверку статуса регистрации сверх того, что просит issue — YAGNI.
- Турнир `AMERICANO`/`KING_OF_COURT`, для которого формат ещё не инициализирован (нет `AmericanoPlayer`/`KingOfCourtPlayerStats` для игрока) — `setLateArrival` бросает `IllegalStateException` с понятным сообщением, не NPE.
- Два реплики в Docker Swarm: `@Scheduled`-метод без явного distributed lock — тот же риск двойного запуска, что уже принят проектом для `PairTournamentReminderScheduler`/`TelegramReminderScheduler` (нет отдельной защиты, полагается на низкую частоту деплоя с 2 репликами одновременно активными и на то, что персистентный флаг всё равно предотвращает дублирование писем даже при гонке — худший случай — двойная отправка одному турниру при гонке двух реплик в один момент, что уже является принятым в проекте риском для аналогичных schedulers).

## Открытые вопросы
Нет.
