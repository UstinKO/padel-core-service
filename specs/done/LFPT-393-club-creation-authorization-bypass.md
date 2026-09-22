# LFPT-393: `CLUB_ADMIN` может создавать новые клубы через `POST /admin/clubs`

## Статус
done — деплой подтверждён успешным (commit `85aea9872e7b9abc8bfd7c8b7e10dcff8f119ddb`, PR [#395](https://github.com/UstinKO/padel-core-service/pull/395), run: https://github.com/UstinKO/padel-core-service/actions/runs/35712738920)

## Источник
[GitHub issue #393](https://github.com/UstinKO/padel-core-service/issues/393) — прямая техническая постановка от архитектора/пользователя (QA-находка по итогам пилота Black Padel, issue #377), без промежуточного клиентского запроса.

Telegram-сообщение (для reply): 4450

## Контекст / зачем
`specs/done/LFPT-376-club-account-access-isolation.md` (раздел "Требования", п.5) прямо утверждает, что создание клуба остаётся `SUPER_ADMIN`-only и что `CLUB_ADMIN` "и так был бы заблокирован существующей проверкой". На практике проверка на `GET /admin/clubs/new` и `POST /admin/clubs` использует не тот метод — пропускает `CLUB_ADMIN`, позволяя ему создавать произвольные клубы от чужого имени. Это подрывает саму модель изоляции по клубу: `CLUB_ADMIN` не должен иметь возможности бесконтрольно расширять список клубов в системе.

## Связь с MASTER.md
`specs/MASTER.md` (строка `ROLE_CLUB_ADMIN`) уже описывает роль как ограниченную своим клубом без права администрировать чужие клубы; создание клуба не упомянуто отдельно, но подразумевается тем же принципом изоляции. Эта спека не меняет инвариант — восстанавливает уже задокументированное и ранее (ошибочно) не реализованное поведение LFPT-376. `MASTER.md` не редактируется.

## Требования

### Функциональные

1. **Причина бага** (подтверждена чтением кода): `AdminClubController.newClubForm` (строка 50) и `AdminClubController.createClub` (строка 103) блокируют попытку создания клуба условием `owner.isAdminRole()` — метод `Owner.isAdminRole()` проверяет `role == OwnerRole.ADMIN`, отдельную от `CLUB_ADMIN` роль (см. `OwnerRole`: `SUPER_ADMIN, ORGANIZER, ADMIN, CLUB_ADMIN` — четыре разных значения). Для `Owner` с ролью `CLUB_ADMIN` условие `owner.isAdminRole()` всегда `false`, поэтому guard не срабатывает и запрос проходит. Остальные мутирующие эндпоинты того же контроллера (`editClubForm`, `updateClub`, `toggleClubStatus`, `deleteClub`) используют корректный паттерн `!owner.isSuperAdmin()` — блокируют всех, кроме `SUPER_ADMIN`, включая `CLUB_ADMIN`.

2. **Исправление** — привести `newClubForm` и `createClub` к тому же паттерну, что уже используют `editClubForm`/`updateClub`/`toggleClubStatus`/`deleteClub` в этом же файле: заменить условие `owner.isAdminRole()` на `!owner.isSuperAdmin()`. Сообщение об ошибке (`"No tienes permiso para crear clubes"`) и поведение (redirect на `/admin/clubs`, flash-attribute `errorMessage`) не меняются — меняется только условие проверки роли.
   - `newClubForm` (`GET /admin/clubs/new`): `if (owner.isAdminRole())` → `if (!owner.isSuperAdmin())`.
   - `createClub` (`POST /admin/clubs`): `if (userDetails instanceof Owner owner && owner.isAdminRole())` → `if (userDetails instanceof Owner owner && !owner.isSuperAdmin())`.

3. **UI-согласованность** — `admin/clubs/list.html` использует модельный атрибут `isAdminRole` (не `isSuperAdmin`) для скрытия кнопки "Nuevo Club"/"Crear Club" от роли, которая не может создавать клубы (`th:unless="${isAdminRole}"` на верхней кнопке, строка 38; безусловная кнопка в пустом состоянии списка, строка 157) — тот же корень проблемы на уровне UI: кнопка видна `CLUB_ADMIN`, ведёт на форму, которая теперь корректно отклонит `GET`, но показывать саму кнопку роли, которая не может ею воспользоваться, — несогласованность. Обе кнопки переводятся на уже передаваемый в модель атрибут `isSuperAdmin` (`th:if="${isSuperAdmin}"`), по аналогии с блоками "Editar/Desactivar" в том же шаблоне (строки 79, 133). Атрибут `isAdminRole` из модели `listClubs()` не убирается (используется только для этой кнопки — но сам контроллер-метод его не создаёт для других целей; убеждаемся, что после правки атрибут не остаётся мёртвым использованием — если да, оставляем как есть, это не предмет данной спеки).

4. **Не трогать** `Owner.isAdminRole()` и другие его вызовы (`AdminController`, `AdminTournamentCopyController`) — метод используется в нескольких местах с другой, самостоятельной семантикой ("роль ровно `ADMIN`", не "любая не-`SUPER_ADMIN`-админская роль"); переименование или замена его поведения — не предмет этого issue и меняло бы поведение несвязанных эндпоинтов без анализа.

### Нефункциональные
- Безопасность: это и есть суть фичи — закрытие обнаруженного broken access control (CWE-863: Incorrect Authorization). Новых точек входа данных нет.
- i18n: новых пользовательских строк нет (сообщение об ошибке уже существует и не меняется).

## Вне скоупа
- Любые другие проверки доступа в проекте (`TournamentAccessService` и его потребители из LFPT-376) — не затронуты, баг локализован в `AdminClubController`.
- Семантика `Owner.isAdminRole()` в остальных контроллерах — не меняется (см. Требования, п.4).
- Восстановление/проверка тестового клуба "Hacker Club CLI" (id=374) — уже деактивирован вручную автором issue при обнаружении, отдельных действий не требует.

## Изменения в системе

### API
Изменённое поведение существующих эндпоинтов (403/redirect вместо 200 для `CLUB_ADMIN`), новых URL нет:
- `GET /admin/clubs/new`
- `POST /admin/clubs`

### БД
Нет изменений.

### UI
- `admin/clubs/list.html` — кнопки "Nuevo Club"/"Crear Club" (строки 38, 157) скрыты по `isSuperAdmin` вместо `isAdminRole`.

## Критерии приёмки
- [ ] `CLUB_ADMIN`: `GET /admin/clubs/new` → redirect на `/admin/clubs` с `errorMessage`, форма не отдаётся (200 с формой — fail)
- [ ] `CLUB_ADMIN`: `POST /admin/clubs` с валидными данными → redirect на `/admin/clubs` с `errorMessage`, клуб в БД не создан (регресс-тест на количество строк в `club_db` до/после)
- [ ] `SUPER_ADMIN`: `GET /admin/clubs/new` и `POST /admin/clubs` — поведение не изменилось (регресс: форма отдаётся, клуб создаётся)
- [ ] `CLUB_ADMIN` в `admin/clubs/list.html` не видит кнопку создания клуба (ни в верхнем блоке, ни в пустом состоянии списка)
- [ ] Интеграционный тест на `AdminClubController.createClub`/`newClubForm`, аналогичный существующим в `ClubAccessIsolationControllerTest` (LFPT-376) — `CLUB_ADMIN` отклонён, `SUPER_ADMIN` проходит (регресс)

## Edge cases
- `CLUB_ADMIN` с `clubId = NULL` — та же проверка `!owner.isSuperAdmin()` блокирует независимо от `clubId`, отдельного поведения не требуется.
- `ORGANIZER`/`OWNER`-роли (не связанные с клубными аккаунтами) — до этого бага уже блокировались через `isAdminRole()==false`... нет, наоборот: `isAdminRole()` тоже `false` для `ORGANIZER`/`OWNER`, то есть они **тоже** были уязвимы тем же багом (не только `CLUB_ADMIN` — любая роль, кроме `ADMIN` и `SUPER_ADMIN`, проходила guard). Issue сфокусирован на `CLUB_ADMIN` (найдено в контексте пилота клубных аккаунтов), но фикс (`!owner.isSuperAdmin()`) закрывает уязвимость для всех ролей одинаково — технически более широкое исправление, чем узкий заголовок issue, но это именно то поведение, которое описывает сама спека LFPT-376 ("создание клуба остаётся `SUPER_ADMIN`-only") и именно тот паттерн, который уже используют соседние методы того же контроллера. Не отдельный бизнес-вопрос — прямое следствие правильного повторения существующего паттерна.

## Открытые вопросы
Нет. Причина бага и корректный фикс однозначно следуют из существующего кода того же контроллера (`editClubForm`/`updateClub`/`toggleClubStatus`/`deleteClub` уже используют `!owner.isSuperAdmin()`) и из явного текста уже одобренной спеки LFPT-376 ("создание... клуба остаются `SUPER_ADMIN`-only").
