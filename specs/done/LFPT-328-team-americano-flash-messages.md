# LFPT-328: Flash-сообщения не рендерятся на странице admin Team Americano

## Статус
done — [PR #333](https://github.com/UstinKO/padel-core-service/pull/333) смержен (2026-08-20T15:53:13Z), деплой подтверждён успешным ([run 32388797934](https://github.com/UstinKO/padel-core-service/actions/runs/32388797934), commit `8835593`, conclusion=success). Первая фича, целиком прошедшая через Telegram-конвейер (см. [telegram-remote-pipeline.md](telegram-remote-pipeline.md), Этап 4) — от сообщения в теме "AI Разработка" до PR, без единого захода в терминал.

## Источник
[GitHub issue #328](https://github.com/UstinKO/padel-core-service/issues/328) — прямая техническая постановка от архитектора/пользователя, без промежуточного клиентского запроса. Находка тестировщика при верификации [PR #327](https://github.com/UstinKO/padel-core-service/pull/327) (LFPT-317), явно вынесенная там в раздел "Вне скоупа".

## Контекст / зачем
`src/main/resources/templates/admin/americano/tournament-double.html` (страница `/tournaments/team-americano/admin/{tournamentId}`) не содержит ни одного блока рендеринга flash-атрибутов. При этом `TeamAmericanoViewController` штатно редиректит на эту страницу с `redirectAttributes.addFlashAttribute(...)` — сообщения физически никогда не показываются администратору. Это тот же класс бага, что был у `admin/americano/tournament.html` до LFPT-317, но для соседней страницы Team Americano.

## Связь с MASTER.md
Чисто техническая правка рендеринга UI, бизнес-инварианты не затрагивает. `specs/MASTER.md` не меняется.

## Анализ (что реально происходит)

`admin/americano/tournament-double.html` — единственный шаблон семейства `admin/americano/*`, который после LFPT-317 остался без блока flash-сообщений:

| Шаблон | Рендерит flash |
|---|---|
| `admin/americano/tournament.html` | `success` / `error` / `info` (строки 173-175, добавлено в LFPT-317) |
| `admin/americano/tournament-playoff.html` | `success` / `error` (строки 129-130, прецедент) |
| `admin/americano/tournament-double.html` | **ничего** |

Редиректы `TeamAmericanoViewController` именно на эту страницу (`redirect:/tournaments/team-americano/admin/{tournamentId}`), которые сейчас теряют сообщение:

| Метод | Строка | Атрибут | Сообщение | Ветка |
|---|---|---|---|---|
| `submitMatchResult` | 241-242 | `info` | `Este partido ya tiene resultado` | результат матча уже сохранён ранее |
| `submitMatchResult` | 268-269 | `success` | `Resultado guardado correctamente` | результат матча успешно сохранён |

Атрибут `error` на эту страницу сейчас ни из одного места не отправляется (в `submitMatchResult` catch-блоки, строки 272-277, редиректят на форму результата `/tournaments/team-americano/matches/{matchId}/result`, а не сюда). Тем не менее блок для `error` добавляется — ровно как в `admin/americano/tournament.html`: это одна и та же конвенция `success`/`error`/`info` всего семейства `controller/view/americano/*`, и наличие блока избавляет от повторения того же бага при будущих правках контроллера. Так же поступили в LFPT-317.

CSS-классы `.alert`, `.alert-success`, `.alert-danger`, `.alert-info` уже определены в `admin.css`, который эта страница уже подключает (строка 14) — новых стилей не требуется.

## Требования

### Функциональные
1. В шаблон `admin/americano/tournament-double.html` добавляется блок рендеринга flash-сообщений, читающий `${success}`, `${error}`, `${info}` — идентичный по разметке блоку в `admin/americano/tournament.html:172-175`.
2. Блок размещается сразу после блока заголовка турнира (`tournament-header`, после строки 118) и до баннера `team-americano-banner` — та же позиция, что и в `admin/americano/tournament.html`.
3. Никакие другие шаблоны и ни один контроллер не трогаются. В частности, `TeamAmericanoViewController` не меняется: все его редиректы на эту страницу уже используют корректные имена атрибутов.

### Нефункциональные
- i18n: не применимо — тексты flash-сообщений остаются хардкод-строками на испанском в контроллере (существующая конвенция всего семейства `controller/view/americano/*`), задача её не меняет. Новых пользовательских строк в шаблоне не появляется.
- Безопасность: `th:text` экранирует содержимое, пользовательский ввод в разметку не попадает.

## Вне скоупа
- **Редирект Team Americano на чужую страницу Americano** — `TeamAmericanoViewController.initializeTeamAmericano` (строка 139) и `finishTournament` (строка 300) после успешного действия над Team Americano-турниром редиректят на `/tournaments/americano/admin/{id}` (страницу обычного Americano). Уже заведено отдельным issue #329, не в scope этой задачи.
- **Страница формы результата матча** `/tournaments/team-americano/matches/{matchId}/result` — catch-блоки `submitMatchResult` (строки 272-277) шлют туда `error`; отрендерит ли его тот шаблон — вне scope issue #328, который называет только `tournament-double.html`.
- **`admin/americano/tournament-playoff.html`** — рендерит `success`/`error`, но не `info`. Не в scope: issue #328 говорит только про Team Americano.
- Изменение самих текстов flash-сообщений, их уровней severity или набора — не делаем.

## Изменения в системе

### API
Нет изменений в REST/HTTP контрактах.

### БД
Нет изменений схемы. Liquibase-миграция не требуется.

### UI
- `src/main/resources/templates/admin/americano/tournament-double.html` — добавляется блок flash-сообщений (`success`/`error`/`info`) после заголовка турнира. Единственный изменяемый файл.

## Критерии приёмки
- [x] `TeamAmericanoViewController.submitMatchResult` при успешном сохранении результата: `success`-сообщение "Resultado guardado correctamente" видно на странице `/tournaments/team-americano/admin/{tournamentId}` после редиректа (было невидимо).
- [x] `TeamAmericanoViewController.submitMatchResult` для матча, у которого результат уже есть: `info`-сообщение "Este partido ya tiene resultado" видно на той же странице после редиректа (было невидимо).
- [x] Модельный атрибут `error`, выставленный на этой странице, рендерится как `alert alert-danger` (проверяется рендерингом шаблона с этим атрибутом).
- [x] Обычный GET страницы `/tournaments/team-americano/admin/{tournamentId}` без предшествующего редиректа: ни один из трёх блоков не отображается, остальная страница рендерится ровно так же, как до правки — регрессия отсутствует (см. оговорку про предсуществующий обрыв рендеринга ниже).
- [x] Существующее поведение flash на `admin/americano/tournament.html` (LFPT-317) и `admin/americano/tournament-playoff.html` не затронуто — эти шаблоны не менялись.
- [x] `./mvnw verify` — зелёный (50/50).

## Edge cases
- Несколько flash-атрибутов одновременно: блоки не эксклюзивны (три независимых `th:if`), при одновременно выставленных `success` и `info` отобразятся оба. Безопасно, дополнительной логики не требует — так же устроено в `tournament.html`.
- Flash-атрибут отсутствует (обычный GET) — все `th:if` корректно skip'ают блок, разметка страницы не меняется.
- Пустая строка в flash-атрибуте: Thymeleaf трактует пустую строку как false в `th:if`, блок не отобразится. Контроллер пустых сообщений не шлёт.

## Находки при верификации (вне скоупа, заведены отдельными issue)

Обе найдены при живом прогоне на этой же странице, обе **предсуществующие** (воспроизводятся на `master` без правки этой задачи), обе намеренно НЕ чинятся здесь.

### 1. Страница обрывается на середине рендеринга (SpEL parse error) — блокирует пользу от этой правки
`tournament-double.html:291` (на `master` — строка 286) содержит невалидное выражение: message-expression `#{...}` вложен внутрь `${...}`:

```
th:text="${ranking != null ? ranking.completedRounds + '/' + ranking.totalRounds + ' ' + #{admin.americano.label.rounds_completed} : ''}"
```

Thymeleaf падает с `SpelParseException: EL1043E: Unexpected token. Expected 'identifier' but was 'lcurly({)'`, ответ обрывается прямо в этом месте (HTTP 200, но HTML без закрывающего `</html>`, размер ~35 КБ). Блок не под `th:if` — обрывается **любой** запрос к `/tournaments/team-americano/admin/{id}`. Введено в LFPT-123 (i18n, merge `74138d7`, 2026-07-24). Единственное вхождение такого паттерна во всех шаблонах проекта.

Практическое следствие для LFPT-328: flash-блок находится **выше** точки обрыва и в HTTP-ответе присутствует корректно (проверено), но headless-Chromium на оборванном chunked-ответе (`net::ERR_INCOMPLETE_CHUNKED_ENCODING`) отбрасывает хвост документа и показывает только шапку админки — то есть до починки этого бага пользователь flash-сообщений всё равно не увидит. Правка LFPT-328 необходима, но сама по себе недостаточна. Заведено как [#334](https://github.com/UstinKO/padel-core-service/issues/334).

### 2. `GET /tournaments/team-americano/matches/{matchId}/result` → HTTP 500
`TeamAmericanoViewController.showResultForm` возвращает шаблон `admin/americano/match-result`, которого не существует (`TemplateInputException`). В каталоге `templates/admin/americano/` есть только `preview-double-rounds.html`, `preview-rounds.html`, `tournament-double.html`, `tournament-playoff.html`, `tournament.html`. Тот же класс бага, что LFPT-314/316. Ветка "матч уже завершён" (редирект с `info`) при этом работает — падает только ветка показа самой формы. Заведено как [#335](https://github.com/UstinKO/padel-core-service/issues/335).

## Открытые вопросы
Нет открытых бизнес-вопросов — правка чисто техническая, точное повторение уже принятого и смердженного решения LFPT-317 для соседней страницы.
