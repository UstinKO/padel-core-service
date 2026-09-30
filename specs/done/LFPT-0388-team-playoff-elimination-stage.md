# LFPT-0388: Team Playoff — этап выбывания в колонке «Ситуация» вместо общего «Выбыла»

## Статус
done (PR #434)

## Источник
[GitHub issue #388](https://github.com/UstinKO/padel-core-service/issues/388) (заведён напрямую по клиентскому фидбеку в Telegram-теме "AI Разработка"). Клиентский запрос — `specs/requests/LFPT-0388-team-playoff-elimination-stage.md`.

Telegram-сообщение (для reply): 4653

## Контекст / зачем

В таблице рейтинга квалификации Team Playoff (`/tournaments/team-playoff/{tournamentId}`) у всех выбывших команд колонка «Ситуация» показывает одинаковый текст «Выбыла» — без указания, на каком этапе плей-офф команда закончила турнир. Заказчик хочет видеть глубину прохождения по каждой паре: «Выбыла в 1/8» вместо просто «Выбыла» и т.д. Статусы финалистов и чемпиона (`Финалист`/`Чемпион`) уже конкретны — их менять не нужно.

## Связь с MASTER.md

Не затрагивает бизнес-инварианты `specs/MASTER.md` (форматы турниров, роли, статусы регистрации) — чисто отображение уже существующих данных (`AmericanoMatch.playoffStage` персистентен с v1.27, LFPT-исторический). Изменений в `MASTER.md` не требуется.

## Требования

### Функциональные

1. Для команды со статусом `tournamentStatus == "ELIMINATED"` колонка «Ситуация» показывает текст «Выбыла в <этап>», где `<этап>` — этап плей-офф, на котором команда проиграла свой последний матч:
   - `ROUND_OF_16` → «1/8»
   - `QUARTER_FINAL` → «1/4»
   - `SEMI_FINAL` → «1/2»
2. Статусы `CHAMPION` («Чемпион») и `RUNNER_UP` («Финалист») не меняются — выводятся как раньше, без указания этапа (не входит в скоуп запроса заказчика).
3. Этап `FINAL` для статуса `ELIMINATED` невозможен по построению (команда, проигравшая в финале, получает `RUNNER_UP`, а не `ELIMINATED` — см. `TeamPlayoffService.applyTournamentStatus()`, currentPosition == 2 проверяется раньше и возвращает раньше, чем логика ELIMINATED). Специальной обработки не требует.
4. Этап `1/16` (`ROUND_OF_32` и т.п.) в системе не существует — `PlayoffFormat.MAX_TEAMS_WITH_TABLE = 16`, самый ранний возможный этап плей-офф — `ROUND_OF_16` (= «1/8» по принятому в проекте неймингу, уже используется в заголовках сетки, см. п.6 ниже). Реализация не должна закладываться на несуществующий этап — маппинг делается ровно по трём значениям enum `PlayoffStage`, которые фактически используются для `ELIMINATED` (`ROUND_OF_16`, `QUARTER_FINAL`, `SEMI_FINAL`).
5. Расчёт: `TeamPlayoffService.applyTournamentStatus()` (`service/americano/TeamPlayoffService.java:1481-1519`) уже вычисляет `lastPlayoffMatch` — матч, в котором команда выбыла, — непосредственно перед строкой, устанавливающей `"ELIMINATED"` (line 1513). У `lastPlayoffMatch` есть `getPlayoffStage()` (enum `PlayoffStage`), просто сейчас не сохраняется никуда дальше.
6. Нейминг этапов **переиспользуется** — точно такой же маппинг (`ROUND_OF_16`→«1/8 финала», `QUARTER_FINAL`→«Четвертьфинал», `SEMI_FINAL`→«Полуфинал», ключи `team_playoff.stage.round_of_16/quarter_final/semi_final`, es/ru/en) уже используется в заголовках раундов сетки плей-офф в том же шаблоне (`view.html`, `th:switch` по `round.note`, ~line 311-313). Для колонки «Ситуация» — новый, отдельный набор ключей (см. ниже), т.к. заказчик просит короткую форму «1/8»/«1/4»/«1/2» (без слова «финала»/«Четвертьфинал»/«Полуфинал»), а существующие `team_playoff.stage.*` для этого не подходят по формату (`round_of_16` = «1/8 финала», а не «1/8»; `quarter_final` = «Четвертьфинал», а не «1/4»). Переиспользовать сам факт наличия трёх стадий и их маппинга на `PlayoffStage`, но не переиспользовать буквально существующие строки.

### Нефункциональные

- **i18n**: три новых ключа на `es`/`ru`/`en` (см. "Изменения в системе → UI" ниже) — короткая форма, отдельная от уже существующих `team_playoff.stage.*`.
- Производительность: не затрагивает — `lastPlayoffMatch` уже вычислен в существующем коде, просто сохраняется значение уже открытого объекта в DTO, без новых запросов к БД.
- Безопасность: нет новой точки входа пользовательских данных — чистое отображение уже посчитанных на сервере данных.

## Вне скоупа

- Не меняем статусы `CHAMPION`/`RUNNER_UP` (заказчик явно просил оставить как есть).
- Не добавляем этап `1/16` — физически недостижим при текущем ограничении `MAX_TEAMS_WITH_TABLE = 16` (см. Edge cases). Если в будущем лимит увеличат — потребуется отдельная задача (новое значение `PlayoffStage`, новый уровень сетки, новый i18n-ключ) — не в скоупе этой фичи.
- Не трогаем сетку плей-офф (`round.note`/заголовки раундов) — там нейминг этапов уже корректен и не относится к колонке «Ситуация».

## Изменения в системе

### API

Нет. Изменение только в серверном рендере страницы `GET /tournaments/team-playoff/{tournamentId}` (Thymeleaf), REST `/api/tournaments/americano/...` не затрагивается — `AmericanoTeamDto` в API-ответах уже содержит все существующие поля, новое поле `eliminatedStage` в ответе API появится автоматически (сериализуется как обычное поле DTO), но это не является предметом задачи — задача только про Thymeleaf-страницу.

### БД

Нет новой миграции — `eliminatedStage` вычисляется на лету из уже существующего персистентного поля `AmericanoMatch.playoffStage` (никаких новых столбцов).

### UI

- `src/main/java/com/padle/core/padelcoreservice/dto/americano/AmericanoTeamDto.java` — новое поле `private String eliminatedStage;` (имя значения enum `PlayoffStage`: `"ROUND_OF_16"`/`"QUARTER_FINAL"`/`"SEMI_FINAL"`), по аналогии с существующими `queuePosition`/`queueOpponentName` (LFPT-367) — заполняется только когда `tournamentStatus == "ELIMINATED"`.
- `src/main/java/com/padle/core/padelcoreservice/service/americano/TeamPlayoffService.java`, метод `applyTournamentStatus()` (~line 1513) — при простановке `dto.setTournamentStatus("ELIMINATED")` добавить `dto.setEliminatedStage(lastPlayoffMatch.getPlayoffStage().name());`.
- `src/main/resources/templates/tournaments/team-playoff/view.html`, кейс `th:case="'ELIMINATED'"` (~line 208) — вместо статичного `#{team_playoff.status.eliminated}` сделать вложенный `th:switch` по `${team.eliminatedStage}` с тремя кейсами (`ROUND_OF_16`/`QUARTER_FINAL`/`SEMI_FINAL`) на новые i18n-ключи; сохранить текущую иконку `✕` и класс `status-pill eliminated`. Если `eliminatedStage` вдруг `null`/не входит в три известных значения (не должно происходить при корректной работе `applyTournamentStatus()`, но на всякий случай — defensive `th:case="*"`) — показать прежний общий текст `#{team_playoff.status.eliminated}` («Выбыла») как безопасный fallback.
- Новые i18n-ключи (`src/main/resources/i18n/messages_{ru,es,en}.properties`, рядом с существующими `team_playoff.status.*`):
  - `team_playoff.status.eliminated_round_of_16` — ru: `Выбыла в 1/8`, es: `Eliminada en 1/8`, en: `Eliminated in Round of 16`
  - `team_playoff.status.eliminated_quarter_final` — ru: `Выбыла в 1/4`, es: `Eliminada en 1/4`, en: `Eliminated in Quarterfinal`
  - `team_playoff.status.eliminated_semi_final` — ru: `Выбыла в 1/2`, es: `Eliminada en 1/2`, en: `Eliminated in Semifinal`

## Критерии приёмки

- [ ] Команда, выбывшая в раунде на 16 команд (`playoffStage == ROUND_OF_16`), в колонке «Ситуация» показывает «Выбыла в 1/8» (ru-локаль).
- [ ] Команда, выбывшая в четвертьфинале (`QUARTER_FINAL`), показывает «Выбыла в 1/4».
- [ ] Команда, выбывшая в полуфинале (`SEMI_FINAL`), показывает «Выбыла в 1/2».
- [ ] Статусы `Финалист` и `Чемпион` визуально не изменились (тот же текст/иконка, что и до фичи).
- [ ] Тексты переведены на `es`/`ru`/`en` — все три файла `messages_{es,ru,en}.properties` содержат три новых ключа.
- [ ] `./mvnw verify` проходит (существующие тесты `TeamPlayoffEndToEndTest` и связанные не сломаны).

## Edge cases

- Команда, выбывшая в финале — не может получить статус `ELIMINATED` (получает `RUNNER_UP`, проверено по коду — `currentPosition == 2` обрабатывается раньше в `applyTournamentStatus()` и возвращает раньше остальной логики) — отдельного теста на «Выбыла в Финале» не требуется, но стоит явно проверить, что `RUNNER_UP` не задет регрессией.
- `eliminatedStage == null` при `tournamentStatus == "ELIMINATED"` не должен возникать при нормальной работе (`ELIMINATED` всегда ставится вместе с `lastPlayoffMatch.getPlayoffStage()` в одном и том же условном блоке) — но шаблон должен иметь безопасный fallback на старый общий текст «Выбыла», а не падать/показывать пустоту, на случай будущих изменений кода, которые случайно разъединят эти два поля.
- Этап «1/16» — недостижим (см. "Вне скоупа"), тестового сценария на него нет и не может быть при текущих ограничениях формата.

## Открытые вопросы

Нет. Формулировка заказчика однозначна (скриншот + явный список желаемых статусов), техническая реализация не требует бизнес-решений — единственная неточность (этап «1/16», которого не существует в системе) закрывается фактическим ограничением формата (`MAX_TEAMS_WITH_TABLE = 16`), а не выбором из нескольких вариантов.
