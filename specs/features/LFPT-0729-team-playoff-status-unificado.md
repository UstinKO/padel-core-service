# LFPT-0729: Feature единый статус команды в Team Playoff — резерв, авто-перевод, статус «не пришла», пересчёт готовности плей-офф (backend)

## Статус
approved

## Источник
[specs/requests/LFPT-0729-reserva-no-show-team-playoff.md](../requests/LFPT-0729-reserva-no-show-team-playoff.md), issue [#729](https://github.com/UstinKO/padel-core-service/issues/729). Пара с фронтенд-задачей LFPT-0730 (зависит от этой).

## Контекст / зачем

Team Playoff (`TournamentType.AMERICANO_TEAMS`) — парный Americano с квалификацией и плей-офф. Резерв пары (`TournamentRegistration.status == WAITLIST`) и неявка команды в день турнира (пока вообще не фиксируется) обрабатываются непоследовательно между разделами: часть кода уже фильтрует по статусу корректно (раздел регистраций, `importFromRegistrations`), часть — нет (`/admin/tournaments/{id}/payments`, посев плей-офф). Для организатора это означает: резерв невозможно увидеть/администрировать на странице оплат, а неявка команды в день турнира может сделать квалификацию "вечно незавершённой" или испортить посев плей-офф.

## Связь с MASTER.md

Не меняет инварианты `specs/MASTER.md` — статусы `CONFIRMED`/`WAITLIST` регистрации пары (раздел «Флоу парной регистрации») остаются источником истины до импорта в Team Playoff; `AmericanoTeam.status` — отдельный, специфичный для Team Playoff статус команды ПОСЛЕ импорта (новое значение `NO_SHOW` добавляется в уже существующий enum `AmericanoPlayerStatus`, не в `RegistrationStatus`). MASTER.md не трогаем.

## Требования

### Функциональные

1. **`AmericanoPlayerStatus.NO_SHOW`** — новое значение enum (рядом с `ACTIVE`/`DROPPED_OUT`/`DISQUALIFIED`).
2. **`TeamPlayoffService.markNoShow(teamId)`** — помечает команду `NO_SHOW`. Блокирует операцию (`InvalidStateException`), если у команды уже есть хотя бы один квалификационный матч (завершённый или нет) — проверяется существующим `getTeamQualMatches`-эквивалентом. После смены статуса — немедленно пересчитывает готовность квалификации (переиспользует существующий `tryAutoCompleteQualification`).
3. **`TeamPlayoffService.cancelNoShow(teamId)`** — обратное действие: возвращает команду из `NO_SHOW` в `ACTIVE` (например, команда всё же приехала, администратор ошибся). Разрешено только из статуса `NO_SHOW`.
4. **Фильтрация по статусу в расчёте готовности и посеве плей-офф.** `canInitPlayoff`, `initPlayoff`, `regeneratePlayoff`, `backfillFirstPlayoffStage` — список команд для подсчёта общего числа и посева должен включать только `ACTIVE` команды (сейчас `AmericanoTeamRepository.findPlayoffRankingByTournamentId` не фильтрует по статусу вообще — неактивная команда участвует и в определении размера сетки, и в самом посеве). `tryAutoCompleteQualification`/`getAvailableTeamsForQualification`/`getQualificationTeamGroups` уже фильтруют по `ACTIVE` — не трогать, использовать как образец.
5. **Авто-перевод из резерва при увеличении `cupoMax`.** `TournamentService.updateTournament` — если новое значение `cupoMax` больше старого, после сохранения вызвать уже существующий `processWaitlistForTournament(tournamentId)` (используется в проекте для промоушена из резерва при освобождении мест; для `DOBLES` уже атомарно подтверждает полные пары по `waitlistPosition` — см. LFPT-0668). Ограничение: `DOBLES`-резерв без партнёра (`SOLO_ADD_LATER`/"ищу пару") этим механизмом не трогается — так же, как и сейчас при обычном освобождении места (намеренно, см. комментарий в коде).
6. **`PaymentService.getPaymentManagementData`** — включить в выдачу регистрации со статусом `WAITLIST` (сейчас исключены полностью), с новым полем `isReserve` на `PaymentManagementViewDto` (`true` для `WAITLIST`, `false` для остальных). Остальная логика метода (платёж/партнёр/позиция) не меняется для `WAITLIST`-строк, кроме `position` (у них его нет — `null`, как и сейчас в БД).
7. **Эндпоинты** в `TeamPlayoffViewController`:
   - `POST /tournaments/team-playoff/admin/teams/{teamId}/no-show` — вызывает `markNoShow`.
   - `POST /tournaments/team-playoff/admin/teams/{teamId}/no-show/cancel` — вызывает `cancelNoShow`.
   Оба — `@RequestParam Long tournamentId`, тот же паттерн прав доступа (`tournamentAccessService.assertCanManageAmericanoTeam`), что у `/admin/teams/{teamId}/update`/`delete`; redirect на страницу Team Playoff с flash-сообщением (успех/ошибка).

### Нефункциональные
- i18n не требуется в этой задаче — новые пользовательские строки появляются во фронтенд-задаче LFPT-0730 (кнопки/бейджи/сообщения). Текст исключения `markNoShow` — служебный (для флеш-сообщения фронтенда), на испанском, по аналогии с соседними исключениями этого сервиса (`InvalidStateException("El torneo no es de tipo Americano Parejas")` и т.п.) — переводится в i18n-ключ уже во фронтенд-задаче, не здесь.
- Безопасность: новые эндпоинты — только для ролей `SUPER_ADMIN`/`ADMIN`/`ORGANIZER`/`CLUB_ADMIN`, как и соседние.

## Вне скоупа
- Любые изменения шаблонов/публичной страницы/i18n — LFPT-0730.
- `TestTournamentController.registerGuestPair` не проверяет `cupoMax` вообще (отдельный найденный баг, не часть этой задачи — см. ниже «Побочная находка»).
- Пересчёт/переигровка уже сыгранных результатов — явно вне скоупа по ограничению заказчика.
- `getQualRanking` (таблица квалификации для отображения) не фильтруется по статусу в этой задаче — она уже и сейчас показывает все команды турнира (включая потенциально `DROPPED_OUT`/`DISQUALIFIED`) для прозрачности координатору; вопрос отображения `NO_SHOW` в этой таблице — за фронтенд-задачей, если потребуется.

## Изменения в системе

### API
- `POST /tournaments/team-playoff/admin/teams/{teamId}/no-show` (form, `tournamentId` параметром) — новый.
- `POST /tournaments/team-playoff/admin/teams/{teamId}/no-show/cancel` (form, `tournamentId` параметром) — новый.

### БД
Нет новой миграции — `AmericanoPlayerStatus` хранится как `VARCHAR`/enum по имени (`@Enumerated(EnumType.STRING)`), новое значение не требует ALTER.

### UI
Нет (эта задача — backend; шаблоны — LFPT-0730).

## Критерии приёмки
- [ ] `AmericanoPlayerStatus.NO_SHOW` существует.
- [ ] `markNoShow` на команде без единого квалификационного матча переводит её в `NO_SHOW` и немедленно пересчитывает завершённость квалификации (если остальные активные команды уже доиграли — раунд квалификации переходит в `COMPLETED`).
- [ ] `markNoShow` на команде, у которой есть хотя бы один (в т.ч. не завершённый) квалификационный матч — бросает `InvalidStateException`, статус не меняется.
- [ ] `cancelNoShow` возвращает команду из `NO_SHOW` в `ACTIVE`.
- [ ] Сценарий: турнир с 8 `ACTIVE` командами, 1 помечена `NO_SHOW` до старта квалификации. Оставшиеся 7 играют по 2 квалификационных матча каждая → `canInitPlayoff` возвращает `true`, `initPlayoff` строит сетку на основе 7 команд (не 8) — `NO_SHOW`-команда не входит в список посева.
- [ ] Увеличение `cupoMax` турнира (`DOBLES`) с 8 на 16 при наличии 1 полной пары в `WAITLIST` — после сохранения турнира пара переходит в `CONFIRMED` автоматически.
- [ ] `PaymentService.getPaymentManagementData` возвращает строки для `WAITLIST`-регистраций с `isReserve = true`; для `CONFIRMED`/`PARTNER_INVITED` — `isReserve = false`.
- [ ] `importFromRegistrations`/`addTeamFromRegistration` продолжают исключать `WAITLIST` (регрессионный тест, поведение не менялось).
- [ ] `./mvnw verify` — зелёный. `python3 .github/bin/docs-drift.py` — 0 расхождений.

## Edge cases
- Команда уже `NO_SHOW`, повторный вызов `markNoShow` — не исключён явно требованием (идемпотентность не критична), но сервис не должен падать с NPE; естественно он просто переустановит тот же статус (нет матчей — условие блокировки не сработает, повторная установка безвредна).
- `cancelNoShow` на команде, которая не в статусе `NO_SHOW` (например, `DISQUALIFIED`) — не делает ничего непредвиденного: бросает `InvalidStateException`, т.к. это не входит в заявленный сценарий отмены.
- Авто-перевод из резерва при увеличении `cupoMax`, когда резерв неполный (есть только один игрок пары, `SOLO_ADD_LATER` без партнёра) — не промоутится (существующее ограничение `processWaitlistForTournament`, не меняем).
- Уменьшение `cupoMax` (не увеличение) — `processWaitlistForTournament` не вызывается (нет новых свободных мест, промоушен не нужен); существующих `CONFIRMED` пар сверх нового лимита это не трогает — не часть этой задачи.

## Открытые вопросы
Нет.

## Побочная находка (вне скоупа этой задачи)

`TestTournamentController.registerGuestPair` (`POST /test/tournaments/{tournamentId}/register-guest-pair`) не проверяет `cupoMax` вообще — всегда вставляет регистрацию со статусом `CONFIRMED` напрямую через JDBC, независимо от того, сколько пар уже подтверждено. В отличие от соседнего `registerPair`, который корректно проверяет свободные места и отклоняет регистрацию при `available < 1`. Из-за этого расхождения "резерв", полученный через этот конкретный тест-эндпоинт, может не соответствовать реальному `cupoMax`. Эндпоинт используется только внутренним тестовым инструментом организатора (`/test/tournaments`, доступ `SUPER_ADMIN`), не публичным флоу регистрации — заведено отдельной issue, не блокирует эту задачу.
