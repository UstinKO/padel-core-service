# LFPT-360: Второй игрок команды пропадает из отображения матчей, если он не зарегистрирован на сайте

## Статус
done — PR #361, деплой подтверждён успешным (run https://github.com/UstinKO/padel-core-service/actions/runs/34762137953, commit 579b2a8)

## Источник
Клиентский запрос [specs/requests/LFPT-360-team-partner-not-shown.md](../requests/LFPT-360-team-partner-not-shown.md) (жалоба организатора во время реального турнира) + технический разбор в GitHub issue [#360](https://github.com/UstinKO/padel-core-service/issues/360) (создан отдельно на основе того же репорта, скриншот через Telegram-канал поддержки — тот же баг, тот же турнир).

## Контекст / зачем
Организатор ведёт турнир прямо сейчас (Team Playoff / Team Americano) и видит на публичной странице турнира несогласованное отображение команд: для одних пар видно обоих игроков ("№3 Tomás Jarowisky / Agustín Zeballos"), для других — только одного ("№8 Leandro Randazzo", без партнёра). Это не рандомная непоследовательность верстки, а системный баг: он всегда воспроизводится для команд, где второй игрок пары не зарегистрирован на сайте ("гостевой" партнёр, добавлен по имени/телефону при регистрации). Нужно исправить так, чтобы оба игрока команды были видны всегда, независимо от того, есть ли у партнёра аккаунт.

## Связь с MASTER.md
Не меняет ни один инвариант `specs/MASTER.md` — механика "гостевого" (незарегистрированного) второго игрока пары уже существует и заложена в модели `AmericanoTeam` (см. `.claude/CLAUDE.md` — Americano/Team Americano). Это чисто баг отображения уже существующих данных, а не новая бизнес-логика.

## Анализ кода (root cause)

`AmericanoTeam` (фиксированная пара в Team Americano/Team Playoff) поддерживает партнёра без аккаунта: `player2` (FK на `PlayerPadel`) может быть `null`, тогда имя лежит строкой в `player2Name` (телефон — в `player2Phone`). Это уже корректно учтено в:
- `AmericanoTeam.getDisplayName()`,
- `AmericanoTeamDto` (список команд/рейтинг — `team.player2Name` из БД или гостевого поля),
- `TeamAmericanoService.previewRounds(...)` (генерация предпросмотра раундов — уже есть fallback `else if (t1.getPlayer2Name() != null) matchDto.setTeam1Player2Name(t1.getPlayer2Name())`).

Но при создании **реальных** (не preview) матчей команда копируется в `AmericanoMatch` только как FK-ссылки:
- `TeamAmericanoService` (round-robin, метод генерации реальных раундов) — `.team1Player2(t1.getPlayer2())`, без сохранения имени гостя.
- `TeamPlayoffService` (генерация квалификации ~L419-430, ~L1135-1140, генерация плей-офф ~L1451-1459, ~L1654-1658) — аналогично `.team1Player2(a.getPlayer2())`/`match.setTeam1Player2(a.getPlayer2())`.

Если `player2` команды — гостевой (FK `null`), то `AmericanoMatch.team1Player2`/`team2Player2` остаётся `null` — имя гостя нигде на матче не сохраняется.

Дальше при сборке DTO для отображения (`AmericanoMatchDto.team1Player2Name`/`team2Player2Name`) есть два независимых места, оба без fallback на гостевое имя:
- `TeamAmericanoService`-формат: `TeamAmericanoViewController.toMatchDto(AmericanoMatch m)` (приватный метод, ~L376-412) — `if (m.getTeam1Player2() != null) { ... }`, иначе поле остаётся `null`.
- `TeamPlayoffService.toMatchDto(AmericanoMatch m)` (~L2042-2091, используется и контроллером `TeamPlayoffViewController` для рендера страниц, и самим сервисом для WebSocket-нотификаций) — та же схема, `if (m.getTeam1Player2() != null) { ... }`.

Шаблоны (`tournaments/team-americano/view.html`, `tournaments/team-playoff/view.html`, и их админ-эквиваленты `admin/americano/tournament-double.html`, `admin/americano/tournament-playoff.html`) рендерят вторую строку игрока строго `th:if="${match.teamXPlayer2Name != null}"` — при `null` вторая строка **не рендерится вообще**, без плейсхолдера. Отсюда и наблюдаемая картина: команды с обоими зарегистрированными игроками показывают пару целиком, команды с гостевым партнёром — только первого игрока, без всякого намёка на второго.

**Не входит в фактическую причину (проверено и отброшено):** `AmericanoMatch.getTeam1DisplayName()/getTeam2DisplayName()` действительно имеют аналогичный изъян (подставляют `"?"` вместо гостевого имени), но используются только в `AmericanoMapper` — маппере для одиночного/парного Americano без концепции команд/гостевого партнёра (`AmericanoPlayer` не имеет поля гостевого имени). На Team Playoff/Team Americano эти методы не влияют, там `match.note` строится через `AmericanoTeam.getDisplayName()`, который уже корректен. Трогать `getTeamXDisplayName()` в рамках этой спеки не нужно (YAGNI — нет наблюдаемого бага в этом месте).

**Важное ограничение для фикса:** для реальных (не preview) матчей Team Americano поле `team1Id`/`team2Id` на `AmericanoMatch` **не заполняется вообще** (в отличие от Team Playoff, где оно уже используется для номера команды). Значит, fallback нельзя строить через `team1Id`/`team2Id` для Team Americano без миграции данных — а миграция исторических данных прямо сейчас, во время идущего турнира, рискованна и не нужна: у `AmericanoMatch` всегда есть `team1Player1`/`team2Player1` (главный игрок команды, `player1_id` в `AmericanoTeam` с уникальным ограничением на `(tournament_id, player1_id)`), через которые команду можно найти существующим методом `AmericanoTeamRepository.findByTournamentIdAndPlayer1Id(tournamentId, player1Id)` — без миграции, работает и для уже существующих (созданных до фикса) матчей текущего турнира.

## Требования

### Функциональные
- В `TeamAmericanoViewController.toMatchDto(AmericanoMatch m)`: если `team1Player2`/`team2Player2` (FK) равен `null` — искать команду через `AmericanoTeamRepository.findByTournamentIdAndPlayer1Id(m.getTournament().getId(), teamXPlayer1.getId())` и, если найдена, брать `team.getPlayer2Name()` как имя второго игрока (`teamXPlayer2Id` в этом случае остаётся `null` — у гостя нет аккаунта, это ожидаемо и не ломает сверку "это я" по `currentPlayerId` в шаблоне).
- В `TeamPlayoffService.toMatchDto(AmericanoMatch m)`: аналогичный fallback, но переиспользовать уже существующий в этом методе поиск команды по `team1Id`/`team2Id` (сейчас используется только для `team1Number`/`team2Number`) — не делать два отдельных запроса в БД на одну и ту же команду.
- Если у гостевого партнёра в принципе не заполнено ни `player2`, ни `player2Name` (гипотетически "одиночная" запись без партнёра вообще) — поведение не меняется: поле остаётся `null`, вторая строка по-прежнему не рендерится (это не баг, а команда без второго игрока, такого по бизнес-правилам быть не должно, но код не должен на этом падать).
- Фикс должен быть виден **и** на публичных страницах (`tournaments/team-americano/{id}`, `tournaments/team-playoff/{id}`), **и** на админских (`tournaments/team-americano/admin/{id}`, `tournaments/team-playoff/admin/{id}`) — обе группы страниц читают одни и те же два метода `toMatchDto`, отдельных правок в шаблонах не требуется.
- WebSocket-уведомления о матчах Team Playoff (используют тот же `TeamPlayoffService.toMatchDto`) получают исправление автоматически, без отдельных изменений.

### Нефункциональные
- Производительность: `findByTournamentIdAndPlayer1Id` — точечный запрос по индексированному уникальному ограничению `(tournament_id, player1_id)`, вызывается не чаще, чем сейчас уже вызывается аналогичный `findById` в `TeamPlayoffService` (для Team Americano — новый вызов, но только когда партнёр гостевой, т.е. не на каждый матч).
- i18n: новых пользовательских строк нет — фикс касается только того, откуда берётся уже существующее значение поля.
- Безопасность: новых точек входа данных от пользователя нет.

## Вне скоупа
- Не переделывать саму механику "гостевого" второго игрока (регистрация без аккаунта).
- Не трогать `AmericanoMatch.getTeam1DisplayName()/getTeam2DisplayName()` — не используются в Team-форматах, наблюдаемого бага там нет (см. "Анализ кода").
- Не добавлять `team1Id`/`team2Id` на `AmericanoMatch` для Team Americano и не делать backfill-миграцию — не нужно для этого фикса (см. "Важное ограничение для фикса").
- Не менять сам формат отображения (не добавлять плейсхолдер вместо второй строки, если у команды в принципе нет второго игрока ни как аккаунта, ни как гостя).

## Изменения в системе

### API
Нет новых/изменённых endpoint'ов — меняется только внутреннее построение уже существующих полей `AmericanoMatchDto.team1Player2Name`/`team2Player2Name` в двух местах.

### БД
Миграция не требуется — используется существующее поле `americano_teams_db.player2_name` и существующий метод репозитория.

### UI
Не меняется — шаблоны (`tournaments/team-americano/view.html`, `tournaments/team-playoff/view.html`, `admin/americano/tournament-double.html`, `admin/americano/tournament-playoff.html`) уже корректно рендерят `teamXPlayer2Name`, когда оно не `null`.

## Критерии приёмки
- [ ] Команда Team Americano/Team Playoff, где второй игрок — гостевой (не зарегистрирован на сайте, `player2` = `null`, `player2Name` заполнено): на публичной странице турнира (`/tournaments/team-americano/{id}` и `/tournaments/team-playoff/{id}`) в списке матчей по кортам и в списке квалификационных/плей-офф матчей отображаются **оба** игрока команды — так же, как для полностью зарегистрированных команд.
- [ ] То же самое — на админской странице турнира (`/tournaments/team-americano/admin/{id}` и `/tournaments/team-playoff/admin/{id}`).
- [ ] Команда, где оба игрока зарегистрированы на сайте — поведение не изменилось (оба игрока по-прежнему видны, оба `Id` по-прежнему корректно подсвечивают "это я" через `currentPlayerId`).
- [ ] Уже существующие (созданные до деплоя фикса) матчи текущего турнира с гостевым партнёром — тоже корректно показывают обоих игроков после деплоя (без ручной миграции/пересоздания данных), поскольку fallback ищет команду по `player1Id`, а не по полю, которое раньше не заполнялось.
- [ ] `./mvnw verify` проходит без регрессий.

## Edge cases
- Команда без гостевого имени вовсе (`player2` = `null` и `player2Name` = `null`) — вторая строка по-прежнему не рендерится, без исключения (маловероятный кейс по бизнес-правилам регистрации, но код не должен падать).
- Матч индивидуального (не командного) Americano, где `isDoubles=true` для конкретного раунда, но это НЕ Team Americano/Team Playoff (пара составляется на раунд, не фиксированная команда) — не затронут, использует отдельный маппер (`AmericanoMapper`), не трогается этой спекой.
- Team Playoff-матч, где `team1Id`/`team2Id` почему-то не проставлен (не должно происходить в норме, но код уже терпимо обрабатывает `null` — тогда fallback просто не сработает, поведение как сейчас).

## Открытые вопросы
Нет — техническая природа бага, решение единственное и однозначное (fallback на уже существующее поле `AmericanoTeam.player2Name`), бизнес-логика не меняется.
