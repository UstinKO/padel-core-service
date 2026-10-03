# LFPT-0329: Редиректы Team Americano ведут на админку обычного Americano

## Статус
testing — правка и автотест в ветке `feature/LFPT-0329`, ручная проверка Tester-агентом пройдена 2026-10-02 (все критерии приёмки), PR ещё не открыт.

## Источник
[GitHub issue #329](https://github.com/UstinKO/padel-core-service/issues/329) — прямая техническая постановка, без клиентского запроса. Найдено при верификации [PR #327](https://github.com/UstinKO/padel-core-service/pull/327) (LFPT-317) и повторно отмечено во «Вне скоупа» спеки [LFPT-328](../done/LFPT-328-team-americano-flash-messages.md).

## Контекст / зачем
`TeamAmericanoViewController` после двух действий над Team Americano-турниром (инициализация и завершение) редиректит на `/tournaments/americano/admin/{tournamentId}` — админку **обычного** Americano. Этот обработчик (`AmericanoViewController.viewAdminTournament`) смотрит в таблицы одиночного Americano, где для Team Americano-турнира данных нет, и показывает организатору нерелевантную страницу «турнир не инициализирован» вместо только что созданного фикстура пар или итогов. Своя страница Team Americano — `/tournaments/team-americano/admin/{tournamentId}`.

## Связь с MASTER.md
Чисто техническая правка навигации в админке, бизнес-инварианты не затрагивает. `specs/MASTER.md` не меняется.

## Анализ (что реально происходит)

Все `redirect:` на админ-страницы в `TeamAmericanoViewController` (номера строк — на `master`, `31f0850`):

| Метод | Строка | Ветка | Куда ведёт | Верно? |
|---|---|---|---|---|
| `initializeTeamAmericano` (`POST /initialize`) | 114 | турнир уже инициализирован | `/tournaments/team-americano/admin/{id}` | да |
| `initializeTeamAmericano` | 139 | исключение при инициализации | `/admin/tournaments/{id}` (+ flash `errorMessage`) | да |
| `initializeTeamAmericano` | 142 | успешная инициализация (+ flash `success`) | `/tournaments/americano/admin/{id}` | **нет** |
| `showResultForm` | 253 | матч уже завершён | `/tournaments/team-americano/admin/{id}` | да |
| `submitMatchResult` | 282 | результат сохранён | `/tournaments/team-americano/admin/{id}` | да |
| `finishTournament` (`POST /admin/{id}/finish`) | 315 | после завершения — успех или ошибка (+ flash `success`/`error`) | `/tournaments/americano/admin/{id}` | **нет** |

Расхождение с текстом issue: там указаны строки 139/300 и методы `showAdminPreviewForm` / `initializeTeamAmericano`. Фактически это успешная ветка `initializeTeamAmericano` (142) и конец `finishTournament` (315); номера строк сдвинулись после LFPT-328 и LFPT-376. Суть бага и решение от этого не меняются.

Целевая страница `admin/americano/tournament-double.html` с LFPT-328 рендерит flash `success`/`error`/`info` (строки 121-123) — сообщения, которые выставляют оба метода, после правки будут видны на правильной странице.

## Требования

### Функциональные
1. `TeamAmericanoViewController.initializeTeamAmericano`, успешная ветка: редирект на `/tournaments/team-americano/admin/{tournamentId}`.
2. `TeamAmericanoViewController.finishTournament`: редирект на `/tournaments/team-americano/admin/{tournamentId}` (одинаково для успеха и ошибки — как и сейчас, меняется только URL).
3. Остальные редиректы контроллера, тексты и имена flash-атрибутов не меняются.

### Нефункциональные
- i18n: не применимо — новых строк нет.
- Безопасность: не меняется. Целевой обработчик `GET /tournaments/team-americano/admin/{id}` сам проверяет доступ через `tournamentAccessService`.

## Вне скоупа
- **Обрыв рендеринга `tournament-double.html`** (SpEL parse error из-за вложенного `#{}` в `${}`) — issue [#334](https://github.com/UstinKO/padel-core-service/issues/334). Flash-блок стоит выше точки обрыва, но до починки #334 браузер может отбросить хвост страницы.
- **`GET /tournaments/team-americano/matches/{matchId}/result` → 500** (нет шаблона `admin/americano/match-result`) — issue [#335](https://github.com/UstinKO/padel-core-service/issues/335).
- Разнобой имён flash-атрибутов (`errorMessage` в catch `initializeTeamAmericano` vs `error` в остальных методах) — `admin/tournaments/details.html` рендерит именно `errorMessage`, так что сейчас это работает; не трогаем.

## Изменения в системе

### API
HTTP-контракты не меняются, меняется только `Location` ответа 302 у двух POST-обработчиков:
- `POST /tournaments/team-americano/initialize` (успех)
- `POST /tournaments/team-americano/admin/{tournamentId}/finish`

### БД
Нет изменений схемы. Liquibase-миграция не требуется.

### UI
Шаблоны не меняются. Единственный изменяемый файл — `src/main/java/com/padle/core/padelcoreservice/controller/view/americano/TeamAmericanoViewController.java` (2 строки).

## Критерии приёмки
- [x] Успешная инициализация Team Americano-турнира (`POST /tournaments/team-americano/initialize` с формы на странице предпросмотра `preview-double-rounds.html`) → редирект на `/tournaments/team-americano/admin/{id}`, видно сообщение «¡Team Americano inicializado! …».
- [x] Завершение Team Americano-турнира (`POST /tournaments/team-americano/admin/{id}/finish`) → редирект на `/tournaments/team-americano/admin/{id}`, видно сообщение «Torneo finalizado. Campeón: …».
- [x] Ошибка при завершении → тот же редирект, видно сообщение «Error al finalizar: …».
- [x] Повторный `POST /initialize` для уже инициализированного турнира и ошибка инициализации ведут туда же, куда и раньше (`/tournaments/team-americano/admin/{id}` и `/admin/tournaments/{id}` соответственно) — регрессии нет.
- [x] В `TeamAmericanoViewController` не осталось ни одного `redirect:/tournaments/americano/`.
- [x] `./mvnw clean package -DskipTests` — сборка проходит.
- [x] Автотест `TeamAmericanoViewControllerRedirectTest` (5 тестов: обе исправленные ветки + 3 регрессионные) — 5/5 зелёные; `./mvnw verify` — 174/174.

## Edge cases
- Турнир с данным ID не Team Americano: поведение целевой страницы не меняется этой задачей — раньше пользователь попадал на чужую страницу Americano, теперь на страницу Team Americano, которая сама обрабатывает такой случай.
- Несколько реплик (Docker Swarm): правка stateless, не влияет.

## Находки при верификации (вне скоупа, предсуществующее)

Проверка — Tester-агент 2026-10-02, живой прогон на локальной БД (curl с сессией owner SUPER_ADMIN); `./mvnw verify` — 169/169 до добавления автотеста.

1. **Инициализацию Team Americano нельзя запустить кликом с карточки турнира.** В `admin/tournaments/details.html` для AMERICANO + DOBLES без инициализации показывается форма нового формата (Calificación + Playoff); JS-функция `previewTeamAmericano()` ни из одного элемента не вызывается, `confirmAndLockTeam` ссылается на несуществующий `#teamAmericanoForm`. Форма `POST /initialize` есть только на `preview-double-rounds.html` — поэтому критерий 1 проверен прямым POST. Кандидат на отдельный issue (frontend).
2. **`POST /tournaments/team-americano/admin/{id}/finish` для несуществующего турнира → 500.** `assertCanManageTournament` бросает `ResourceNotFoundException` до `try`, редиректа нет. Правкой не затронуто (старая целевая страница для такого ID тоже отдавала 500). Кандидат на отдельный issue (backend).
3. **Обрыв рендеринга `tournament-double.html`** — [#334](https://github.com/UstinKO/padel-core-service/issues/334), подтверждено; flash-блок выше точки обрыва и присутствует в HTML-ответе.

## Открытые вопросы
Нет.
