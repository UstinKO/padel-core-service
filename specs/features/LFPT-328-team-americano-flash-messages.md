# LFPT-328: Flash-сообщения не рендерятся на странице admin Team Americano

## Статус
approved

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
- [ ] `TeamAmericanoViewController.submitMatchResult` при успешном сохранении результата: `success`-сообщение "Resultado guardado correctamente" видно на странице `/tournaments/team-americano/admin/{tournamentId}` после редиректа (было невидимо).
- [ ] `TeamAmericanoViewController.submitMatchResult` для матча, у которого результат уже есть: `info`-сообщение "Este partido ya tiene resultado" видно на той же странице после редиректа (было невидимо).
- [ ] Модельный атрибут `error`, выставленный на этой странице, рендерится как `alert alert-danger` (проверяется рендерингом шаблона с этим атрибутом).
- [ ] Обычный GET страницы `/tournaments/team-americano/admin/{tournamentId}` без предшествующего редиректа: ни один из трёх блоков не отображается, остальная страница (заголовок, баннер Team Americano, раунды, таблица) рендерится как раньше — регрессия отсутствует.
- [ ] Существующее поведение flash на `admin/americano/tournament.html` (LFPT-317) и `admin/americano/tournament-playoff.html` не затронуто — эти шаблоны не менялись.
- [ ] `./mvnw verify` — зелёный.

## Edge cases
- Несколько flash-атрибутов одновременно: блоки не эксклюзивны (три независимых `th:if`), при одновременно выставленных `success` и `info` отобразятся оба. Безопасно, дополнительной логики не требует — так же устроено в `tournament.html`.
- Flash-атрибут отсутствует (обычный GET) — все `th:if` корректно skip'ают блок, разметка страницы не меняется.
- Пустая строка в flash-атрибуте: Thymeleaf трактует пустую строку как false в `th:if`, блок не отобразится. Контроллер пустых сообщений не шлёт.

## Открытые вопросы
Нет открытых бизнес-вопросов — правка чисто техническая, точное повторение уже принятого и смердженного решения LFPT-317 для соседней страницы.
