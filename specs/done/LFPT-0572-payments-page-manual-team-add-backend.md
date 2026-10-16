# LFPT-0572: Feature добавление пары в Team Playoff со страницы оплат — редирект обратно на оплаты (backend)

## Статус
done

## Источник
Клиентский запрос [specs/requests/LFPT-0572-payments-page-manual-team-add.md](requests/LFPT-0572-payments-page-manual-team-add.md) (Telegram, пересланное сообщение от Evgeny_754, `telegram_launch_message_id: 5097`). GitHub issue [#572](https://github.com/UstinKO/padel-core-service/issues/572).

Разбито на бэкенд/фронтенд (`GIT_WORKFLOW.md §1.4`) — эта спека покрывает бэкенд. Зависимая фронтенд-задача — [#573](https://github.com/UstinKO/padel-core-service/issues/573), спека [LFPT-0573-payments-page-manual-team-add-frontend.md](LFPT-0573-payments-page-manual-team-add-frontend.md).

## Контекст / зачем

Этап 1 клиентского запроса объединяет добавление команды Team Playoff и фиксацию оплаты/явки в один рабочий процесс, начинающийся со страницы «Оплаты и посещаемость» (`/admin/tournaments/{tournamentId}/payments`). Форма поиска/создания двух игроков (LFPT-0561/0562, `#addTeamFormEl`, `POST /tournaments/team-playoff/admin/{tournamentId}/teams/add` → `TeamPlayoffViewController.addTeam`) сейчас существует только на странице Team Playoff (`/tournaments/team-playoff/admin/{tournamentId}`) и после сохранения **всегда** редиректит обратно на неё же (`addTeam`, строка 165: `return "redirect:/tournaments/team-playoff/admin/" + tournamentId;`).

Фронтенд-задача (#573) размещает ту же форму на странице оплат. Чтобы после сохранения координатор оставался на странице оплат (а не перескакивал на страницу Team Playoff), контроллеру нужен способ определить, куда редиректить — без этого пункт 2 клиентского запроса физически невыполним.

Дополнительная сложность: страницы используют **разные** имена flash-атрибутов для сообщений об успехе/ошибке — `tournament-playoff.html` читает `${success}`/`${error}` (см. `TeamPlayoffViewController.addTeam`, строки 160/163: `ra.addFlashAttribute("success", ...)` / `"error"`), а `payments.html` читает `${successMessage}`/`${errorMessage}` (см. `PaymentManagementController.saveAndAddTeam`, строки 84/87). Если не учесть это различие, при редиректе на страницу оплат координатор не увидит сообщение о результате.

## Связь с MASTER.md

Не вводит новую роль, статус или инвариант `specs/MASTER.md`. Опирается на существующий инвариант §«Флоу парной регистрации» — результат вызова `addTeam`/`addTeamFromRegistration` (пара `TournamentRegistration` + `AmericanoTeam`) не меняется, меняется только то, на какую страницу координатор попадает после сохранения.

## Требования

### Функциональные

1. **`POST /tournaments/team-playoff/admin/{tournamentId}/teams/add`** (`TeamPlayoffViewController.addTeam`) — принимает новый опциональный параметр формы `returnTo` (строка, например `payments`). Биндится как часть `@ModelAttribute` запроса либо отдельным `@RequestParam(required = false) String returnTo` — на усмотрение реализации (см. "Открытые вопросы" ниже, решение архитектора).
2. Если `returnTo` равен `"payments"` — после `playoffService.addTeam(...)` (и успеха, и перехваченного исключения `InvalidStateException`/`ResourceNotFoundException`) редирект идёт на `/admin/tournaments/{tournamentId}/payments`, а flash-сообщение записывается под ключами `successMessage`/`errorMessage` (не `success`/`error`) — те, что реально читает `payments.html`.
3. Если `returnTo` отсутствует или равен любому другому значению — поведение не меняется: редирект на `/tournaments/team-playoff/admin/{tournamentId}`, flash-ключи `success`/`error`, как сейчас. Это сохраняет обратную совместимость с формой на странице Team Playoff (фронтенд #573 эту форму не трогает — она просто не добавляет туда `returnTo`).
4. Текст самих flash-сообщений не меняется (`"Equipo agregado correctamente"` / `"Error al agregar equipo: " + e.getMessage()"`) — меняется только то, под каким ключом и на какую страницу они попадают. Эти строки уже не затрагиваются i18n-правилом проекта (константы в коде контроллера, не шаблона — существующий паттерн этого конкретного метода, не вводится этой задачей).

### Нефункциональные
- i18n: новых пользовательских строк нет — см. п.4 выше, существующий паттерн метода не меняется.
- Безопасность: `returnTo` — это просто имя логического места редиректа, не URL и не используется для построения перенаправления напрямую (иначе открытый редирект) — конкретное значение URL (`/admin/tournaments/{tournamentId}/payments` или `/tournaments/team-playoff/admin/{tournamentId}`) всегда строится на сервере из фиксированных строк и `tournamentId`, `returnTo` только выбирает, какую из двух веток использовать (`if ("payments".equals(returnTo)) {...} else {...}`) — ни в коем случае не "redirect:" + returnTo.
- Авторизация не меняется — `tournamentAccessService.assertCanManageTournament` уже вызывается в начале метода, как и сейчас.

## Вне скоупа
- Сама форма на странице оплат, кнопка «+ Добавить пару вручную», переименование кнопки «Agregar equipo» → «Incluir en Team Playoff», ссылка вместо кнопки на странице Team Playoff, подсказка по отметке явки, индикация недоступности команды для матча — всё это [#573], frontend-only, использует этот эндпоинт как есть.
- Форма `#addTeamFormEl` на самой странице Team Playoff — не удаляется и не меняется этой задачей (её удаляет/заменяет на ссылку #573, но это изменение шаблона, не контроллера).
- Любые изменения `addTeamFromRegistration`/`import-registrations`/бизнес-правил регистрации — не затрагиваются (см. «Границы этапа» клиентского запроса).

## Изменения в системе

### API
- `POST /tournaments/team-playoff/admin/{tournamentId}/teams/add` — расширяется необязательным параметром `returnTo`; без него поведение прежнее.

### БД
Нет изменений.

### UI
Нет (эта задача — backend-only; UI — [#573]).

## Критерии приёмки
- [ ] `POST .../teams/add` **без** `returnTo` — создание команды работает как раньше, редирект на `/tournaments/team-playoff/admin/{tournamentId}`, flash-сообщение видно на этой странице под `${success}`/`${error}` (регрессия на существующий флоу).
- [ ] `POST .../teams/add` **с `returnTo=payments`**, успешное создание команды — редирект на `/admin/tournaments/{tournamentId}/payments`, на странице видно flash-сообщение об успехе (`${successMessage}`).
- [ ] `POST .../teams/add` **с `returnTo=payments`**, ошибка (например, игрок уже в активной команде турнира — `InvalidStateException`) — редирект на `/admin/tournaments/{tournamentId}/payments`, видно flash-сообщение об ошибке (`${errorMessage}`), команда не создана.
- [ ] `returnTo` с любым значением, кроме `"payments"` (например, случайная строка) — ведёт себя как при отсутствии параметра (редирект на страницу Team Playoff) — не вызывает исключений и не трактуется как `"payments"`.
- [ ] `./mvnw verify` зелёный.

## Edge cases
- `returnTo=payments`, но `tournamentId` не существует или недоступен текущему `Owner` (`tournamentAccessService.assertCanManageTournament` бросает исключение) — исключение всё ещё бросается **до** попытки создать команду, как и сейчас (эта проверка идёт первой строкой метода, до чтения `returnTo`) — поведение не меняется, просто подтвердить регрессией.
- Пустая строка `returnTo=""` — трактуется так же, как отсутствие параметра (редирект на Team Playoff), а не как `"payments"`.

## Открытые вопросы
Нет. Технические решения (аналогично `GIT_WORKFLOW.md §3`):
1. **`returnTo` как плоский `@RequestParam`, а не поле `TeamPlayoffAddTeamRequest`.** Проще прокинуть из шаблона (скрытое поле формы `name="returnTo" value="payments"`) и не раздувает DTO, который и так маппится напрямую на бизнес-данные команды — реализация сама решает конкретную сигнатуру метода.
2. **Сравнение значения строкой `"payments".equals(returnTo)`, а не enum.** Единственное место использования, двух вариантов редиректа достаточно; вводить отдельный enum ради одного сравнения было бы избыточной абстракцией (KISS, `CODE_REVIEW.md §3`).
