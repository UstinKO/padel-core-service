# LFPT-317: Flash-сообщения error/info не рендерятся на страницах Americano admin

## Статус
approved

## Источник
[GitHub issue #317](https://github.com/UstinKO/padel-core-service/issues/317) — прямая техническая постановка от архитектора/пользователя, без промежуточного клиентского запроса.

## Контекст / зачем
`admin/tournaments/details.html` и `admin/americano/tournament.html` не рендерят все flash-атрибуты, которые реально отправляют им контроллеры. Любой контроллер, который редиректит на эти страницы с `redirectAttributes.addFlashAttribute("error", ...)` / `"info"` / `"success"` под именами, которые страница не читает (или не читает вообще), отправляет сообщение, которое физически никогда не показывается пользователю (ни ошибка о некорректном действии, ни информационное сообщение о статусе турнира). Найдено тестировщиком при верификации PR #315 (LFPT-314).

## Связь с MASTER.md
Чисто техническая правка рендеринга UI, бизнес-инварианты не затрагивает. `specs/MASTER.md` не меняется.

## Анализ (что реально происходит)

В проекте сосуществуют **два независимых соглашения** об именах flash-атрибутов:

1. **`errorMessage` / `successMessage`** — используется пакетом `controller/admin/*` (`AdminController`, `AdminClubController`, `AdminPlayerController`, `AdminMatchController`, `AdminKingOfCourtController`, `AdminTournamentCopyController`) и `TournamentViewController`. Целевая страница `admin/tournaments/details.html` (шаблон `admin/tournaments/details.html:324-331`) читает именно эти имена — и только их.
2. **`success` / `error` / `info`** — используется универсально во всём пакете `controller/view/americano/*` (`AmericanoViewController`, `TeamAmericanoViewController`, `TeamPlayoffViewController`), без единого исключения.

Проблема — не в том, что соглашения разные (это само по себе нормально для разных страниц), а в том, что **некоторые методы `view/americano/*`-контроллеров редиректят на страницы, которые следуют не своей, а другой странице конвенции (или вообще не читают ни одну из них)**:

### A. Редиректы на `/admin/tournaments/{id}` (`admin/tournaments/details.html`) с именем `error` вместо ожидаемого шаблоном `errorMessage`

Шаблон уже умеет рендерить `errorMessage`/`successMessage` (используется десятком других контроллеров на той же странице) — конфликта имён нет, страницу трогать не нужно. Проблема только в 4 конкретных местах, где `view/americano/*`-контроллеры используют "свою" конвенцию (`error`), забыв, что этот конкретный редирект ведёт на чужую страницу:

| Файл | Метод | Строка | Ветка |
|---|---|---|---|
| `AmericanoViewController.java` | `showAdminPreviewForm` | 513-514 | турнир не типа Americano |
| `AmericanoViewController.java` | `initializeAmericano` | 677-678 | catch-блок при ошибке инициализации |
| `TeamAmericanoViewController.java` | `previewRounds` | 180-182 | catch-блок при ошибке предпросмотра |
| `TeamAmericanoViewController.java` | `initializeTeamAmericano` | 135-136 | catch-блок при ошибке инициализации |

Ровно это описано в issue как первый найденный случай ("ветка неверный тип турнира" — `error`), плюс 3 аналогичных места того же паттерна, найденных при поиске по кодовой базе.

### B. Редиректы на `/tournaments/americano/admin/{id}` (`admin/americano/tournament.html`) — шаблон вообще не рендерит ни один flash-атрибут

В отличие от `admin/tournaments/details.html`, страница `admin/americano/tournament.html` **не содержит ни одного `th:if` для flash-сообщений** — ни `error`/`success`/`info`, ни `errorMessage`/`successMessage`. При этом на неё редиректят (в рамках штатной работы, не только catch-блоков) сразу несколько методов `view/americano/*`-контроллеров, каждый раз теряя сообщение:

| Файл | Метод | Строка | Атрибут | Ветка |
|---|---|---|---|---|
| `AmericanoViewController.java` | `showAdminPreviewForm` | 517-519 | `info` | турнир уже инициализирован (второй случай из issue) |
| `AmericanoViewController.java` | `initializeAdminTournament` | 573-574 | `success` | успешная инициализация |
| `AmericanoViewController.java` | `initializeAdminTournament` | 577 | `error` | catch-блок |
| `AmericanoViewController.java` | `initializeAmericano` | 673-674 | `success` | успешная инициализация |
| `TeamAmericanoViewController.java` | `initializeTeamAmericano` | 130-131, 139 | `success` | успешная инициализация Team Americano (редиректит на страницу Americano, не Team Americano — см. "Вне скоупа") |
| `TeamAmericanoViewController.java` | `finishTournament` | 294-300 | `success`/`error` | завершение Team Americano-турнира (тот же редирект на чужую страницу) |

Для этой страницы правильный фикс — **добавить рендеринг**, а не переименовывать атрибуты в контроллерах: все перечисленные вызовы уже согласованно используют `success`/`error`/`info`, это доминирующая конвенция всего семейства `view/americano/*` (используется в 40+ местах в этих трёх контроллерах). Есть прямой прецедент такого же блока на соседней странице того же семейства — `admin/americano/tournament-playoff.html:129-130` уже рендерит `${success}`/`${error}` (без `info`, но паттерн идентичный). CSS-классы `.alert-success`, `.alert-danger`, `.alert-info` уже определены в `admin.css` (страница уже подключает этот файл) — новых стилей не требуется.

## Требования

### Функциональные
1. Все 4 вызова `addFlashAttribute("error", ...)`, перечисленные в разделе A, переключаются на `addFlashAttribute("errorMessage", ...)`. Сам текст сообщения не меняется.
2. В шаблон `admin/americano/tournament.html` добавляется блок рендеринга flash-сообщений, читающий `${success}`, `${error}`, `${info}` (стиль — как в `admin/americano/tournament-playoff.html:129-130`, плюс блок для `info` по аналогии), размещённый сразу после блока заголовка турнира (`tournament-header`, после строки 170) и до баннера формата.
3. Никакие другие шаблоны и контроллеры не трогаются — в частности, `admin/tournaments/details.html` не меняется (уже корректен), `errorMessage`/`successMessage`-вызовы в `controller/admin/*` не трогаются.

### Нефункциональные
- i18n: не применимо — все затронутые flash-сообщения уже являются хардкод-строками на испанском (не через `#{...}` ключи) в исходном коде контроллеров, это существующая конвенция всего семейства `view/americano/*`, задача её не меняет.
- Безопасность: изменение не затрагивает пользовательский ввод, только имена модельных атрибутов и статическую разметку.

## Вне скоупа
- **`admin/americano/tournament-double.html`** (Team Americano, `/tournaments/team-americano/admin/{id}`) — та же самая проблема отсутствия рендеринга flash-сообщений (`TeamAmericanoViewController.showResultForm`/`submitMatchResult` шлют `info`/`success`/`error` на эту страницу, шаблон их не рендерит). Не входит в scope issue #317 (который называет только `admin/tournaments/details.html` и `admin/americano/tournament.html`) — отдельная находка, кандидат на отдельный issue.
- **Редирект Team Americano на чужую страницу Americano** — `TeamAmericanoViewController.initializeTeamAmericano` (строка 139) и `finishTournament` (строка 300) после успешного действия над Team Americano-турниром редиректят на `/tournaments/americano/admin/{id}` (страницу **обычного** Americano), а не на `/tournaments/team-americano/admin/{id}` (свою страницу). Похоже на самостоятельный баг подмены URL, не связанный с flash-сообщениями напрямую — отдельная находка, кандидат на отдельный issue.
- **Несуществующие шаблоны Americano** — `AmericanoViewController` возвращает `tournaments/americano/register`, `tournaments/americano/rounds`, `tournaments/americano/round`, `tournaments/americano/match`, `tournaments/americano/match-result`, `tournaments/americano/player-stats` — ни один из этих файлов не существует в `src/main/resources/templates/tournaments/americano/` (там есть только `view.html` и `ranking.html`). Роуты технически объявлены (`@GetMapping`), но не проверено, достижимы ли они из UI. Отдельная находка того же рода, что LFPT-314/316, не в scope текущей задачи — кандидат на отдельный issue.
- Изменение самого набора flash-сообщений (текстов, уровней severity) — вне scope, меняются только имена атрибутов и добавляется рендеринг существующих сообщений.

## Изменения в системе

### API
Нет изменений в REST/HTTP контрактах — только имя модельного flash-атрибута для 4 существующих редиректов (внутренняя деталь реализации, не наблюдаема снаружи иначе как через видимость сообщения).

### БД
Нет изменений схемы. Liquibase-миграция не требуется.

### UI
- `src/main/resources/templates/admin/americano/tournament.html` — добавляется блок flash-сообщений (`success`/`error`/`info`) после заголовка турнира.
- Контроллеры (без изменений шаблонов): `AmericanoViewController.java` (2 места), `TeamAmericanoViewController.java` (2 места) — `"error"` → `"errorMessage"` там, где редирект ведёт на `/admin/tournaments/{id}`.

## Критерии приёмки
- [ ] `AmericanoViewController.showAdminPreviewForm` для турнира не типа Americano: `error`-сообщение "Este torneo no es de tipo Americano" отображается на `admin/tournaments/details.html` после редиректа (было невидимо).
- [ ] `AmericanoViewController.showAdminPreviewForm` для уже инициализированного турнира: `info`-сообщение "El torneo ya está inicializado" отображается на `admin/americano/tournament.html` после редиректа (было невидимо).
- [ ] `AmericanoViewController.initializeAmericano` при исключении в сервисе: `error`-сообщение отображается на `admin/tournaments/details.html` (было невидимо).
- [ ] `AmericanoViewController.initializeAdminTournament` при успешной инициализации: `success`-сообщение отображается на `admin/americano/tournament.html` (было невидимо).
- [ ] `AmericanoViewController.initializeAdminTournament` при исключении: `error`-сообщение отображается на `admin/americano/tournament.html` (было невидимо).
- [ ] `AmericanoViewController.initializeAmericano` при успешной инициализации (не через preview): `success`-сообщение отображается на `admin/americano/tournament.html` (было невидимо).
- [ ] `TeamAmericanoViewController.previewRounds` при исключении: `error`-сообщение отображается на `admin/tournaments/details.html` (было невидимо).
- [ ] `TeamAmericanoViewController.initializeTeamAmericano` при исключении: `error`-сообщение отображается на `admin/tournaments/details.html` (было невидимо).
- [ ] Существующее поведение `errorMessage`/`successMessage` на `admin/tournaments/details.html` от других контроллеров (`AdminController`, `AdminClubController` и т.д.) не сломано — регрессионная проверка хотя бы одного сценария (например, ошибка при создании турнира).
- [ ] Существующее поведение `success`/`error` на `admin/americano/tournament-playoff.html` не затронуто (этот шаблон не менялся).
- [ ] `./mvnw verify` — зелёный.

## Edge cases
- Одновременно `error` и `info`/`success` в одном редиректе не возникает — контроллеры всегда шлют ровно один флеш-атрибут на запрос (проверено по коду всех затронутых веток), рендерить одновременно несколько блоков не нужно, но блоки в шаблоне не эксклюзивны друг другу (несколько `th:if` подряд) — если оба атрибута случайно окажутся выставлены, оба и отобразятся, что безопасно и не требует дополнительной логики.
- Flash-атрибут отсутствует (обычный GET без предшествующего redirect) — все `th:if` корректно skip'ают блок, поведение не меняется.

## Открытые вопросы
Нет открытых бизнес-вопросов — правка чисто техническая, приведение фактического поведения в соответствие с уже существующей (но не везде соблюдаемой) конвенцией именования flash-атрибутов.
