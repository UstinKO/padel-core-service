# LFPT-398: RankingService падает при первом результате матча для игрока без строки в ranking_db

## Статус
approved

## Источник
[GitHub issue #398](https://github.com/UstinKO/padel-core-service/issues/398) — прямая техническая постановка от архитектора/пользователя, без промежуточного клиентского запроса.
Telegram-сообщение (для reply): 4506

## Контекст / зачем
`RankingService.registrarVictoria`/`registrarDerrota` (общий код создания/обновления `Ranking` при сохранении результата матча) падает для любого игрока, у которого ещё нет строки в `ranking_db` — то есть для любого нового/малоактивного игрока, впервые получившего результат матча. Это блокирует штатный сценарий "первый матч игрока" в проде. Найдено при тестировании LFPT-394 (новый admin-UI для Bracket/`CANCHA_ABIERTA`), но баг живёт в `RankingService`/`Ranking`, не в Bracket-коде.

## Связь с MASTER.md
Чисто техническая правка (устранение падения при создании/обновлении записи рейтинга), бизнес-инвариантов рейтинга/статусов регистрации не меняет. `specs/MASTER.md` не затрагивается.

## Анализ (что реально происходит)

Две независимые, последовательно проявляющиеся ошибки в одном и том же коде создания `Ranking`:

### 1. `NOT NULL` на `rachas_actual`/`rachas_maxima` — билдер их не заполняет

`Ranking.builder()...build()` в `RankingService.inicializarRanking` (метод, вызываемый явно и из `actualizarRankingJugador`) и `RankingService.inicializarRankingEntity` (приватный, вызывается из `registrarVictoria`/`registrarDerrota` через `.orElseGet(...)`, когда для игрока ещё нет `Ranking`) никогда не устанавливает `rachasActual`/`rachasMaxima`. Оба поля в Java остаются `null`.

Колонки `ranking_db.rachas_actual`/`rachas_maxima` (добавлены в `v1.13-add-missing-ranking-columns.yaml`) имеют `DEFAULT 0`, но также `NOT NULL` (добавлен тем же changelog'ом, безусловно, через `addNotNullConstraint`). Hibernate при `INSERT` явно передаёт `NULL` для полей, не выставленных в билдере (не полагается на DB `DEFAULT`) — вставка падает: `null value in column "rachas_actual" ... violates not-null constraint`.

Проверено на реальной локальной миграции (`./mvnw spring-boot:run` с `cloud`-профилем поднимает свежую БД по всем 41 changelog'ам) — текущая структура `ranking_db`:
```
rachas_actual  | integer | not null | 0   (DEFAULT есть, но NOT NULL — тоже, Hibernate его не использует)
rachas_maxima  | integer | not null | 0   (аналогично)
mejor_posicion | integer |          | 0   (DEFAULT есть, NOT NULL — НЕТ, см. ниже)
posicion_actual   | integer | (nullable)
posicion_anterior | integer | (nullable)
```

### 2. `mejor_posicion` фактически nullable в БД (не то, что предполагал автор issue) — но `calcularMejorPosicion()` не null-safe

В `v1.12-add-mejor-posicion-to-ranking.yaml` changeset `v1.12-update-existing-ranking-mejor-posicion` (тот, что должен был выставить `mejor_posicion` существующим строкам и затем `ALTER ... SET NOT NULL`) защищён `preConditions: sqlCheck expectedResult: 1 ... WHERE mejor_posicion IS NULL` — рассчитан на ситуацию "ровно одна строка с NULL" на момент исходного прод-деплоя. На свежей БД (0 строк) precondition не совпадает → `onFail: MARK_RAN` → changeset помечается выполненным, но **не выполняется** → `SET NOT NULL` на `mejor_posicion` никогда не применяется. Из-за этого расхождения `mejor_posicion` в реальной схеме остаётся nullable — сам факт NOT NULL-нарушения на этой колонке из issue не воспроизводится (и не должен, колонка nullable). Это не в скоупе текущего issue (сама by-design логика миграции — отдельная, более рискованная правка задним числом существующего changelog'а с `onFail`/preConditions; трогать её здесь не нужно, реальная проблема — не в этом).

Реальная причина второй ошибки — `Ranking.calcularMejorPosicion()`:
```java
public void calcularMejorPosicion() {
    if (mejorPosicion == null || posicionActual < mejorPosicion) {
        setMejorPosicion(posicionActual);
    }
}
```
вызывается из `@PrePersist`/`@PreUpdate`. Пока `mejorPosicion == null` — короткое замыкание, NPE не будет (в том числе на самой первой вставке новой записи, где оба поля `null`). Но если к моменту очередного `@PreUpdate` (`registrarVictoria`/`registrarDerrota` на уже существующей записи) `mejorPosicion` не `null` (например, строка была создана в обход обычного Java-пути — вручную через SQL, где `mejor_posicion` не указан явно и получает `DEFAULT 0`, либо любым другим путём, где колонка получила значение раньше, чем `posicionActual`), а `posicionActual` всё ещё `null` (позиции выставляются только батчем в `actualizarPosiciones`, вызываемом из `getRankingCompleto()` — то есть только когда кто-то открывал общий рейтинг) — `posicionActual < mejorPosicion` роняет NPE на auto-unboxing. Это соответствует репро из issue (ручной обход первой ошибки через прямой SQL создал строку, где `mejor_posicion` получил `DEFAULT 0`, а `posicion_actual` остался `NULL`).

Уже есть DB-триггер (`v1.13-add-trigger-for-rachas`, `update_ranking_before_update`) с точно такой же null-safe логикой на уровне БД для `UPDATE`, но он не покрывает Java-уровень (`@PrePersist`/`@PreUpdate` выполняются до похода в БД, независимо от триггера) и не покрывает `INSERT` вообще (`BEFORE UPDATE`, не `BEFORE INSERT OR UPDATE`).

### Уточнение к тексту issue: реальные вызовы `registrarVictoria`/`registrarDerrota`

Issue утверждает, что баг общий для King of Court/Americano/Bracket. По факту в коде `RankingService.registrarVictoria`/`registrarDerrota` сейчас вызываются **только** из `MatchService` (Bracket) — `grep` по проекту не находит вызовов из `KingOfCourtService`/`AmericanoService`/`TeamAmericanoService` (у них собственные, независимые модели статистики — `KingOfCourtPlayerStats`, `AmericanoPlayer` — не пишущие в `ranking_db`). Баг реален и в общем коде `RankingService`/`Ranking`, но сегодня практически воспроизводим только через Bracket-флоу; это не меняет решение (фикс всё равно в общем коде, на случай если другие форматы начнут его использовать), просто уточнение для критериев приёмки/тестов.

## Требования

### Функциональные
1. `RankingService.inicializarRanking` и `RankingService.inicializarRankingEntity` — билдер `Ranking` явно выставляет `rachasActual(0)` и `rachasMaxima(0)`.
2. `Ranking.calcularMejorPosicion()` становится null-safe: обновляет `mejorPosicion` только когда `posicionActual != null` (условие — по образцу уже существующего DB-триггера `update_ranking_before_update`): `if (posicionActual != null && (mejorPosicion == null || posicionActual < mejorPosicion))`.
3. Никаких изменений в `posicionActual`/`posicionAnterior`/`mejorPosicion` при создании `Ranking` не добавляется — они осознанно остаются `null` до первого расчёта позиций в `actualizarPosiciones` (это уже штатно обрабатывается: `getTendencia()` возвращает `"NUEVO"` при `null`, `actualizarPosiciones` явно проверяет `getPosicionActual() == null`).

### Нефункциональные
- i18n: не применимо, изменение не добавляет пользовательских строк.
- Безопасность: не применимо, изменение не затрагивает пользовательский ввод.
- Миграция БД / изменение существующего changelog'а `v1.12`/`v1.13` — явно вне скоупа (см. "Вне скоупа").

## Вне скоупа
- Правка precondition'а в `v1.12-add-mejor-posicion-to-ranking.yaml`, из-за которой `mejor_posicion` не получил `NOT NULL` на свежих БД — самостоятельная находка, не блокирует текущий фикс (колонка nullable — это не баг сама по себе, `calcularMejorPosicion()` после этой задачи корректно работает и с nullable, и с NOT NULL вариантом). Правка задним числом уже выполненного/помеченного как выполненный changelog'а — отдельная, более рискованная задача.
- Полный стектрейс вместо `e.getMessage()` в `AdminMatchController` — явно отмечено самим автором issue как вне скоупа этой задачи.
- Любые изменения `KingOfCourtService`/`AmericanoService`/`TeamAmericanoService` — они не используют `RankingService.registrarVictoria`/`registrarDerrota` сегодня (см. "Уточнение" выше), фикс в общем коде их не требует.
- Пересчёт/бэкфилл существующих строк `ranking_db` с уже сохранёнными некорректными данными (например, руками созданных обходов) — не требуется, баг чисто в пути создания новых строк и последующего обновления, существующие валидные строки не затронуты.

## Изменения в системе

### API
Нет изменений в REST/HTTP контрактах — правка на уровне сервиса/модели, наблюдаемый эффект только в том, что операция, которая раньше падала 500-й, теперь завершается успешно.

### БД
Изменений схемы нет. Новая Liquibase-миграция не требуется (колонки и их `DEFAULT`/`NOT NULL` уже в нужном состоянии — проблема была на Java-стороне, не в схеме).

### UI
Нет прямых изменений шаблонов. Косвенно чинит флоу LFPT-394 (сохранение результата Bracket-матча через `POST /admin/tournaments/{id}/matches/{matchId}`) и любой другой флоу, доходящий до `MatchService.updateMatchResult` для игрока без строки `ranking_db`.

## Критерии приёмки
- [ ] `RankingService.registrarVictoria(playerId, match)` для игрока без существующей строки в `ranking_db` — создаёт `Ranking` и не падает (было: `null value in column "rachas_actual"`).
- [ ] `RankingService.registrarDerrota(playerId, match)` для игрока без существующей строки в `ranking_db` — создаёт `Ranking` и не падает (тот же путь `inicializarRankingEntity`).
- [ ] У созданной таким образом записи `rachasActual == 0` и `rachasMaxima == 0` (не `null`).
- [ ] `RankingService.inicializarRanking(playerId)` (явный публичный вызов, независимый путь создания) — создаёт `Ranking` с `rachasActual == 0`/`rachasMaxima == 0`, не падает.
- [ ] `Ranking.calcularMejorPosicion()` с `posicionActual == null` и `mejorPosicion` уже выставленным в ненулевое значение — не бросает `NullPointerException`, и `mejorPosicion` не изменяется.
- [ ] `Ranking.calcularMejorPosicion()` с `posicionActual` не-`null`, меньшим текущего `mejorPosicion` — по-прежнему обновляет `mejorPosicion` (регрессия на существующее поведение).
- [ ] `Ranking.calcularMejorPosicion()` с `posicionActual` не-`null`, но бо́льшим текущего `mejorPosicion` — `mejorPosicion` не меняется (регрессия).
- [ ] End-to-end сценарий из issue: сохранение результата Bracket-матча (`MatchService.updateMatchResult` → `registrarVictoria`/`registrarDerrota`) для игрока без строки в `ranking_db` — весь флоу (сеты, определение победителя, продвижение сетки) отрабатывает без ошибки, как и было при ручном обходе в issue, но теперь без ручного вмешательства в БД.
- [ ] Регрессия: `getRankingCompleto()` (существующий флоу с `actualizarPosiciones`) для игроков с уже присвоенными позициями продолжает работать штатно (позиции/тенденция считаются как раньше).
- [ ] `./mvnw verify` — зелёный.

## Edge cases
- Игрок уже имеет строку в `ranking_db` (обычный повторный матч) — путь `orElseGet` не срабатывает, поведение не меняется.
- `posicionActual` и `mejorPosicion` оба `null` при первой вставке — короткое замыкание `mejorPosicion == null` уже работало и до этой задачи, не ломается новым условием (эквивалентно: `posicionActual != null && (...)` — не выполнится по первому операнду `false`, `mejorPosicion` останется `null`, как и раньше... но с новым условием ветка `if` в этом случае вообще не войдёт, значит `mejorPosicion` не пере-выставится в `null` явно — эквивалентно текущему поведению, т.к. итоговое значение то же самое, `null`).
- `mejorPosicion` уже не-`null`, `posicionActual` становится `null` повторно (гипотетически, не должно происходить по коду `actualizarPosiciones`, но защищаемся на уровне модели) — с фиксом просто не трогает `mejorPosicion`, не падает.

## Открытые вопросы
Нет открытых бизнес-вопросов — правка чисто техническая, оба фикса явно предложены и обоснованы в самом issue, выбор конкретного варианта (билдер vs. миграция схемы) — техническое решение по п.3 `GIT_WORKFLOW.md` (выбран билдер: не требует новой миграции, DB-`DEFAULT` для этих колонок уже существует и остаётся полезным для прямых SQL-вставок вроде той, что использовал автор issue при ручном обходе).
