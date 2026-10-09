# LFPT-0335: Страница ввода результата матча Team Americano отдаёт 500 — убрать мёртвую форму

## Статус
testing

## Источник
[GitHub issue #335](https://github.com/UstinKO/padel-core-service/issues/335). Это прямая техническая постановка, клиентского запроса нет. Баг найден при живой верификации [PR #333](https://github.com/UstinKO/padel-core-service/pull/333) (LFPT-328). Вариант решения («убрать мёртвый роут») и расширение на несуществующий `matchId` согласованы с пользователем 2026-10-09.

## Контекст / зачем
`GET /tournaments/team-americano/matches/{matchId}/result` для незавершённого матча возвращает имя шаблона `admin/americano/match-result`. Такого шаблона нет, поэтому запрос падает с `TemplateInputException` и HTTP 500. Отдельная страница ввода результата не нужна: счёт Team Americano вводится прямо в карточке матча на админке турнира. Мёртвую страницу убираем по образцу LFPT-314: роут остаётся, но превращается в редирект.

## Связь с MASTER.md
Это правка навигации в админке, бизнес-инварианты она не затрагивает. `specs/MASTER.md` не меняется.

## Анализ (что реально происходит)

Номера строк приведены по `master`, `83c620c`, файл `TeamAmericanoViewController.java`.

**Ссылок на страницу в UI нет.** В `templates/**` и `static/**` не найдено ни одной ссылки или формы на `/tournaments/team-americano/matches/{id}/result`, ни на GET, ни на POST. Рабочий путь ввода счёта выглядит так. На `admin/americano/tournament-double.html` (стр. ~430) JS отправляет `fetch` на `POST /tournaments/team-americano/api/matches/{matchId}/result`. Этот запрос обрабатывает `submitResultApi` (стр. 325), который отвечает JSON и сразу обновляет DOM и рейтинг.

Обработчики пути `/matches/{matchId}/result`:

| Метод | Строки | Ветка | Сейчас | Проблема |
|---|---|---|---|---|
| `showResultForm` (GET) | 248–249 | матча нет | `IllegalArgumentException` | **500**: `GlobalExceptionHandler` это исключение не обрабатывает |
| `showResultForm` (GET) | 251–255 | матч завершён | редирект на `/tournaments/team-americano/admin/{id}` с flash `info` | всё в порядке |
| `showResultForm` (GET) | 257–264 | матч не завершён | шаблон `admin/americano/match-result` | **500**: шаблона нет |
| `submitMatchResult` (POST) | 277 | матча нет | `assertCanManageAmericanoMatch` бросает `ResourceNotFoundException` до `try` | **500** |
| `submitMatchResult` (POST) | 280–283 | успех | редирект на админку Team Americano с flash `success` | всё в порядке |
| `submitMatchResult` (POST) | 284–286 | `InvalidStateException` | редирект на `/tournaments/team-americano/matches/{id}/result` | ведёт на мёртвую страницу, итог **500** |
| `submitMatchResult` (POST) | 287–290 | любое другое исключение | то же самое | то же самое |

Получается, что любая ошибка сохранения через form-POST заканчивается 500 вместо сообщения об ошибке.

Доступ. В `SecurityConfig` `permitAll` выдан только `/tournaments/team-americano/*` и `/tournaments/team-americano/*/ranking`. Путь `/matches/{id}/result` под него не попадает, поэтому для него нужна аутентификация. У GET нет `@PreAuthorize`, у POST есть: `SUPER_ADMIN`, `ADMIN`, `ORGANIZER`, `CLUB_ADMIN`. После правки GET ничего не рендерит, а только редиректит, и права проверяет уже целевая админ-страница через `tournamentAccessService`.

Куда редиректить при несуществующем матче. Страница `admin/tournaments/list.html` (стр. 46) показывает flash `errorMessage`, а не `error`.

## Требования

### Функциональные
1. `showResultForm` (`GET /tournaments/team-americano/matches/{matchId}/result`) больше не рендерит шаблон:
   - матч не завершён: редирект на `/tournaments/team-americano/admin/{tournamentId}` без flash;
   - матч завершён: тот же редирект с flash `info` «Este partido ya tiene resultado», как сейчас;
   - матча нет: редирект на `/admin/tournaments` с flash `errorMessage` «Partido no encontrado».
2. `submitMatchResult` (`POST /tournaments/team-americano/matches/{matchId}/result`):
   - матча нет: редирект на `/admin/tournaments` с flash `errorMessage` «Partido no encontrado». Существование матча проверяется до `assertCanManageAmericanoMatch`;
   - ветки `InvalidStateException` и `Exception`: редирект на `/tournaments/team-americano/admin/{tournamentId}`. Flash `error` и его тексты не меняются;
   - успех и проверка прав (`assertCanManageAmericanoMatch` для существующего матча) не меняются.
3. В контроллере не остаётся ни одного `redirect:` на `/tournaments/team-americano/matches/`. Если после правки какие-то зависимости и импорты становятся ненужными, их нужно убрать.
4. JSON-эндпоинт `POST /tournaments/team-americano/api/matches/{matchId}/result` не меняется.

### Нефункциональные
- i18n. Новая строка одна, «Partido no encontrado», и она захардкожена по-испански, как и соседние flash-сообщения этого контроллера. Перенос flash-текстов в i18n входит в [#506](https://github.com/UstinKO/padel-core-service/issues/506).
- Безопасность. Новых точек входа нет. Уровень доступа не меняется: GET по-прежнему требует аутентификации, POST по-прежнему защищён `@PreAuthorize` и `assertCanManageAmericanoMatch`.

## Вне скоупа
- Те же несуществующие шаблоны в обычном Americano (`AmericanoViewController`: `register`, `rounds`, `round`, `match`, `match-result`, `player-stats`). Это [#330](https://github.com/UstinKO/padel-core-service/issues/330).
- Рефакторинг `catch (Exception)`, `log.error(..., e.getMessage())` и `Map<String, Object>` в контроллере. Это [#506](https://github.com/UstinKO/padel-core-service/issues/506). Здесь меняются только цели редиректов и проверка существования матча.
- Удаление form-POST `submitMatchResult` целиком (UI его тоже не использует). Ограничиваемся минимальной правкой: эндпоинт остаётся, но перестаёт вести на 500.
- 500 на публичной странице `GET /tournaments/team-americano/{id}` для несуществующего турнира. Это [#445](https://github.com/UstinKO/padel-core-service/issues/445).
- 500 на `POST /tournaments/team-americano/admin/{id}/finish` для несуществующего турнира (находка тестера LFPT-0329). Отдельной issue на это пока нет.
- Обрыв рендеринга `tournament-double.html` из-за `SpelParseException`. Это [#334](https://github.com/UstinKO/padel-core-service/issues/334).

## Изменения в системе

### API
HTTP-контракты меняются так:
- `GET /tournaments/team-americano/matches/{matchId}/result` всегда отвечает 302: на админку Team Americano или на `/admin/tournaments`. Раньше было 500 или 302.
- `POST /tournaments/team-americano/matches/{matchId}/result`: в ветках ошибок и для неизвестного матча меняется `Location` ответа 302.

### БД
Схема не меняется, Liquibase-миграция не нужна.

### UI
Шаблоны не меняются. Меняется только `src/main/java/com/padle/core/padelcoreservice/controller/view/americano/TeamAmericanoViewController.java`, методы `showResultForm` и `submitMatchResult`.

## Критерии приёмки
- [x] `GET /tournaments/team-americano/matches/{id}/result` для незавершённого матча (под админом) → 302 на `/tournaments/team-americano/admin/{tournamentId}`, ошибки 500 и `TemplateInputException` в логах нет.
- [x] Тот же GET для завершённого матча → тот же редирект, на странице видно «Este partido ya tiene resultado». Это проверка на регрессию.
- [x] Тот же GET для несуществующего `matchId` → 302 на `/admin/tournaments`, на странице видно «Partido no encontrado», в логах нет stack trace уровня ERROR.
- [x] `POST /tournaments/team-americano/matches/{id}/result` с ошибкой сервиса (`InvalidStateException`, например счёт не сходится с лимитом очков) → 302 на `/tournaments/team-americano/admin/{tournamentId}`, на странице видно сообщение об ошибке.
- [x] Тот же POST для несуществующего `matchId` → 302 на `/admin/tournaments` с «Partido no encontrado», а не 500.
- [x] Успешный POST работает как раньше (автотест `submitResult_exito_*`). Инлайн-ввод счёта на `tournament-double.html` идёт через JSON-эндпоинт `/api/matches/{id}/result`, который правка не трогает; вручную не проверялось.
- [x] В `TeamAmericanoViewController` нет `admin/americano/match-result` и `redirect:/tournaments/team-americano/matches/`.
- [x] Автотесты на сценарии выше есть в `TeamAmericanoViewControllerRedirectTest`, старые 5 тестов зелёные, `./mvnw clean verify` — 227/227 (2026-10-09).

Проверка: все пункты закрыты MockMvc-тестами (`TeamAmericanoViewControllerRedirectTest`, 7 новых тестов): тесты проверяют статус, `Location` и flash-атрибуты. Живого прогона на локальной БД не было: что сообщение действительно отображается на целевой странице, следует из того, что `admin/tournaments/list.html` рендерит `errorMessage`, а `tournament-double.html` — `success`/`error`/`info`.

## Edge cases
- Анонимный пользователь: Spring Security, как и раньше, отправляет его на `/login`, до контроллера запрос не доходит.
- `ROLE_PLAYER` на GET: попадает в редирект на админку Team Americano, где доступ режет `tournamentAccessService`. Раньше у такого пользователя был 500, данных матча он не видел ни тогда, ни теперь.
- `matchId` от матча другого формата (обычный Americano лежит в той же таблице `americano_matches_db`): редирект на админку Team Americano с ID того турнира. Как целевая страница обрабатывает чужой формат, эта задача не меняет.
- Несколько реплик (Docker Swarm): правка stateless и на них не влияет.

## Открытые вопросы
Нет.
